package com.datatalk.application.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;

import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

class SessionRepositoryTest {

    private SessionRepository repo;
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
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) conn.close();
    }

    @Test
    void applyAutoTitle_updatesWhenUnlocked() {
        repo.upsert(new SessionRecord("s1", "c1", "新会话", false, null, 100L, 100L, false));
        int rows = repo.applyAutoTitle("s1", "AI 生成标题", 200L);
        assertThat(rows).isEqualTo(1);
        assertThat(repo.findById("s1")).get()
            .extracting(SessionRecord::title).isEqualTo("AI 生成标题");
    }

    @Test
    void applyAutoTitle_skipsWhenLocked() {
        repo.upsert(new SessionRecord("s1", "c1", "手动命名", false, null, 100L, 100L, true));
        int rows = repo.applyAutoTitle("s1", "AI 生成标题", 200L);
        assertThat(rows).isEqualTo(0);
        assertThat(repo.findById("s1")).get()
            .extracting(SessionRecord::title).isEqualTo("手动命名");
    }

    @Test
    void updateTitleAndLock_setsBothAtomically() {
        repo.upsert(new SessionRecord("s1", "c1", "新会话", false, null, 100L, 100L, false));
        int rows = repo.updateTitleAndLock("s1", "我的查询", 200L);
        assertThat(rows).isEqualTo(1);
        SessionRecord r = repo.findById("s1").orElseThrow();
        assertThat(r.title()).isEqualTo("我的查询");
        assertThat(r.titleLocked()).isTrue();
    }
}