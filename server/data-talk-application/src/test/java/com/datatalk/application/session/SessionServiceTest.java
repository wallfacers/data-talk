package com.datatalk.application.session;

import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.opencode.OpenCodeSessionMap;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SessionServiceTest {

    private SessionRepository repo;
    private SessionService svc;
    private OpenCodeGateway gateway;
    private OpenCodeSessionMap sessionMap;
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
        gateway = mock(OpenCodeGateway.class);
        sessionMap = mock(OpenCodeSessionMap.class);
        svc = new SessionService(repo, Clock.fixed(Instant.ofEpochMilli(500L), ZoneOffset.UTC),
            gateway, sessionMap);
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

    @Test
    void create_defaultsBlankTitleToNewSession() {
        SessionRecord fromNull = svc.create(null, null);
        SessionRecord fromBlank = svc.create(null, "   ");
        SessionRecord fromEmpty = svc.create(null, "");

        assertThat(fromNull.title()).isEqualTo("新会话");
        assertThat(fromBlank.title()).isEqualTo("新会话");
        assertThat(fromEmpty.title()).isEqualTo("新会话");
    }

    @Test
    void create_preservesExplicitTitle() {
        SessionRecord rec = svc.create(null, "我的会话");
        assertThat(rec.title()).isEqualTo("我的会话");
    }

    @Test
    void delete_cascadesToOpenCodeWhenOcSidPresent() {
        repo.upsert(new SessionRecord("s1", "c1", "t", true, "ses_xxx", 100L, 100L, false));
        svc.delete("s1");
        assertThat(repo.findById("s1")).isEmpty();
        verify(gateway).deleteOpenCodeSession("ses_xxx");
        verify(sessionMap).unbind("s1");
    }

    @Test
    void delete_skipsOpenCodeWhenOcSidNull() {
        repo.upsert(new SessionRecord("s1", "c1", "t", false, null, 100L, 100L, false));
        svc.delete("s1");
        assertThat(repo.findById("s1")).isEmpty();
        verify(gateway, never()).deleteOpenCodeSession(org.mockito.ArgumentMatchers.anyString());
        verify(sessionMap, never()).unbind(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void delete_swallowsOpenCodeGatewayFailure() {
        repo.upsert(new SessionRecord("s1", "c1", "t", true, "ses_xxx", 100L, 100L, false));
        org.mockito.Mockito.doThrow(new RuntimeException("oc down"))
            .when(gateway).deleteOpenCodeSession("ses_xxx");

        // Local delete must still succeed despite gateway failure
        svc.delete("s1");

        assertThat(repo.findById("s1")).isEmpty();
        verify(sessionMap).unbind("s1");
    }
}