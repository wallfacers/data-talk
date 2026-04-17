package com.datatalk.application.session;

import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionServiceTest {

    private SessionRepository repo;
    private SessionService svc;
    private Connection conn;
    private DataSource ds;

    @BeforeEach
    void setUp() throws Exception {
        // Use SQLite in-memory with a single connection that stays open
        SQLiteDataSource sqliteDs = new SQLiteDataSource();
        sqliteDs.setUrl("jdbc:sqlite::memory:");
        conn = sqliteDs.getConnection();
        // Create schema with title_locked column
        conn.createStatement().execute("""
            CREATE TABLE sessions (
              id TEXT PRIMARY KEY,
              connection_id TEXT,
              title TEXT NOT NULL,
              has_ever_sent INTEGER NOT NULL DEFAULT 0,
              opencode_sid TEXT,
              created_at BIGINT NOT NULL,
              updated_at BIGINT NOT NULL,
              title_locked INTEGER NOT NULL DEFAULT 0
            )
            """);
        // Use SingleConnectionDataSource to always return the same connection
        ds = new SingleConnectionDataSource(conn, true);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        repo = new SessionRepository(jdbc);
        svc = new SessionService(repo, Clock.fixed(Instant.ofEpochMilli(500L), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) conn.close();
    }

    @Test
    void rename_locksTitleAtomically() {
        repo.upsert(new SessionRecord("s1", "c1", "新会话", false, null, 100L, 100L, false));

        SessionRecord result = svc.rename("s1", "我的查询");

        assertThat(result.title()).isEqualTo("我的查询");
        assertThat(result.titleLocked()).isTrue();
        SessionRecord persisted = repo.findById("s1").orElseThrow();
        assertThat(persisted.titleLocked()).isTrue();
    }

    @Test
    void rename_rejectsBlankTitle() {
        repo.upsert(new SessionRecord("s1", "c1", "新会话", false, null, 100L, 100L, false));

        assertThatThrownBy(() -> svc.rename("s1", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("title must not be blank");
    }

    @Test
    void rename_rejectsNullTitle() {
        repo.upsert(new SessionRecord("s1", "c1", "新会话", false, null, 100L, 100L, false));

        assertThatThrownBy(() -> svc.rename("s1", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("title must not be blank");
    }

    @Test
    void rename_throwsWhenSessionNotFound() {
        assertThatThrownBy(() -> svc.rename("nonexistent", "title"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("session not found: nonexistent");
    }
}