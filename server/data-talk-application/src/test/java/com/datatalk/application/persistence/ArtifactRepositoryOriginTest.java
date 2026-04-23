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

class ArtifactRepositoryOriginTest {

    private ArtifactRepository repo;
    private Connection conn;
    private DataSource ds;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource sqliteDs = new SQLiteDataSource();
        sqliteDs.setUrl("jdbc:sqlite::memory:");
        conn = sqliteDs.getConnection();
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
        conn.createStatement().execute("""
            CREATE TABLE artifacts (
              id TEXT NOT NULL,
              version INTEGER NOT NULL,
              session_id TEXT NOT NULL REFERENCES sessions(id),
              kind TEXT NOT NULL,
              produced_by TEXT NOT NULL,
              payload_ref TEXT NOT NULL,
              payload_size INTEGER NOT NULL,
              supersedes_id TEXT,
              supersedes_ver INTEGER,
              pinned INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL,
              origin_message_id TEXT,
              origin_part_id TEXT,
              PRIMARY KEY(id, version)
            )
            """);
        ds = new SingleConnectionDataSource(conn, true);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        repo = new ArtifactRepository(jdbc);
        jdbc.update("""
            INSERT INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid, created_at, updated_at, title_locked)
            VALUES (?, NULL, ?, 0, NULL, ?, ?, 0)
            """, "s1", "T", 0L, 0L);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) conn.close();
    }

    @Test
    void insertAndReadBackOriginFields() {
        repo.insert(new ArtifactRecord(
            "art_1", 1, "s1", "chart", "call_1",
            "inline:{}", 2, null, null, false, 1_000L,
            "msg_7", "part_2"
        ));

        var got = repo.findLatestById("art_1").orElseThrow();
        assertThat(got.originMessageId()).isEqualTo("msg_7");
        assertThat(got.originPartId()).isEqualTo("part_2");
    }

    @Test
    void nullOriginFieldsRoundTripAsNull() {
        repo.insert(new ArtifactRecord(
            "art_2", 1, "s1", "chart", "call_1",
            "inline:{}", 2, null, null, false, 1_000L,
            null, null
        ));

        var got = repo.findLatestById("art_2").orElseThrow();
        assertThat(got.originMessageId()).isNull();
        assertThat(got.originPartId()).isNull();
    }
}
