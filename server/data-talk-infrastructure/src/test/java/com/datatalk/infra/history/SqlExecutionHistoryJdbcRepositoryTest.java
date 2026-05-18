package com.datatalk.infra.history;

import com.datatalk.application.history.SqlExecutionHistoryService;
import com.datatalk.application.history.SqlExecutionRecord;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SqlExecutionHistoryJdbcRepositoryTest {
    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;
    private HikariDataSource ds;
    private SqlExecutionHistoryJdbcRepository repo;

    @BeforeEach
    void setUp() {
        Path dbFile = tempDir.resolve("history.db");
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + dbFile);
        cfg.setMaximumPoolSize(2);
        cfg.setPoolName("history-test");
        ds = new HikariDataSource(cfg);
        jdbc = new JdbcTemplate(ds);
        createSchema(jdbc);
        repo = new SqlExecutionHistoryJdbcRepository(jdbc);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) ds.close();
    }

    @Test
    void recordSuccessRoundTripsAllFields() {
        repo.record(SqlExecutionRecord.success(
            "ses_1", "conn_a", "shop", "public",
            "SELECT count(*) FROM users", 1000L, 45L, 12345));

        List<SqlExecutionRecord> records = repo.list(
            new SqlExecutionHistoryService.SqlExecutionHistoryQuery(
                "ses_1", null, null,
                SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter.SUCCESS, 10));
        assertThat(records).hasSize(1);
        SqlExecutionRecord r = records.get(0);
        assertThat(r.sessionId()).isEqualTo("ses_1");
        assertThat(r.connectionId()).isEqualTo("conn_a");
        assertThat(r.databaseName()).isEqualTo("shop");
        assertThat(r.schemaName()).isEqualTo("public");
        assertThat(r.sqlText()).isEqualTo("SELECT count(*) FROM users");
        assertThat(r.status()).isEqualTo(SqlExecutionRecord.Status.SUCCESS);
        assertThat(r.executedAt()).isEqualTo(1000L);
        assertThat(r.durationMs()).isEqualTo(45L);
        assertThat(r.rowCount()).isEqualTo(12345);
        assertThat(r.errorCode()).isNull();
        assertThat(r.errorMessage()).isNull();
    }

    @Test
    void recordFailureCapturesErrorFields() {
        repo.record(SqlExecutionRecord.failure(
            "ses_1", "conn_a", "shop", null,
            "SELECT * FROM no_such", "TABLE_NOT_FOUND", "no such table: no_such",
            2000L, 5L));

        List<SqlExecutionRecord> records = repo.list(
            new SqlExecutionHistoryService.SqlExecutionHistoryQuery(
                "ses_1", null, null,
                SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter.FAILURE, 10));
        assertThat(records).hasSize(1);
        SqlExecutionRecord r = records.get(0);
        assertThat(r.status()).isEqualTo(SqlExecutionRecord.Status.FAILURE);
        assertThat(r.errorCode()).isEqualTo("TABLE_NOT_FOUND");
        assertThat(r.errorMessage()).contains("no such table");
        assertThat(r.rowCount()).isNull();
    }

    @Test
    void listOrdersByExecutedAtDescending() {
        repo.record(SqlExecutionRecord.success("ses_1", "c", null, null, "old", 1000L, 1L, 1));
        repo.record(SqlExecutionRecord.success("ses_1", "c", null, null, "mid", 2000L, 1L, 1));
        repo.record(SqlExecutionRecord.success("ses_1", "c", null, null, "new", 3000L, 1L, 1));

        List<SqlExecutionRecord> records = repo.list(
            new SqlExecutionHistoryService.SqlExecutionHistoryQuery(
                "ses_1", null, null,
                SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter.SUCCESS, 10));
        assertThat(records).extracting(SqlExecutionRecord::sqlText)
            .containsExactly("new", "mid", "old");
    }

    @Test
    void statusFilterAllIncludesBoth() {
        repo.record(SqlExecutionRecord.success("ses_1", "c", null, null, "ok", 1000L, 1L, 1));
        repo.record(SqlExecutionRecord.failure("ses_1", "c", null, null,
            "bad", "E", "msg", 2000L, 1L));

        List<SqlExecutionRecord> records = repo.list(
            new SqlExecutionHistoryService.SqlExecutionHistoryQuery(
                "ses_1", null, null,
                SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter.ALL, 10));
        assertThat(records).hasSize(2);
    }

    @Test
    void connectionIdFilterIsolatesRows() {
        repo.record(SqlExecutionRecord.success("ses_1", "c1", null, null, "c1-q", 1L, 1L, 1));
        repo.record(SqlExecutionRecord.success("ses_1", "c2", null, null, "c2-q", 2L, 1L, 1));

        List<SqlExecutionRecord> records = repo.list(
            new SqlExecutionHistoryService.SqlExecutionHistoryQuery(
                "ses_1", "c1", null,
                SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter.SUCCESS, 10));
        assertThat(records).hasSize(1);
        assertThat(records.get(0).sqlText()).isEqualTo("c1-q");
    }

    @Test
    void sessionsAreIsolated() {
        repo.record(SqlExecutionRecord.success("ses_a", "c", null, null, "a-q", 1L, 1L, 1));
        repo.record(SqlExecutionRecord.success("ses_b", "c", null, null, "b-q", 2L, 1L, 1));

        List<SqlExecutionRecord> a = repo.list(
            new SqlExecutionHistoryService.SqlExecutionHistoryQuery(
                "ses_a", null, null,
                SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter.SUCCESS, 10));
        assertThat(a).hasSize(1);
        assertThat(a.get(0).sqlText()).isEqualTo("a-q");
    }

    @Test
    void emptyHistoryReturnsEmptyList() {
        List<SqlExecutionRecord> records = repo.list(
            new SqlExecutionHistoryService.SqlExecutionHistoryQuery(
                "ses_nope", null, null,
                SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter.SUCCESS, 10));
        assertThat(records).isEmpty();
    }

    @Test
    void limitCapsResults() {
        for (int i = 0; i < 20; i++) {
            repo.record(SqlExecutionRecord.success("ses_1", "c", null, null,
                "q" + i, 1000L + i, 1L, 1));
        }
        List<SqlExecutionRecord> records = repo.list(
            new SqlExecutionHistoryService.SqlExecutionHistoryQuery(
                "ses_1", null, null,
                SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter.SUCCESS, 5));
        assertThat(records).hasSize(5);
    }

    @Test
    void limitOver50IsClampedTo50() {
        for (int i = 0; i < 60; i++) {
            repo.record(SqlExecutionRecord.success("ses_1", "c", null, null,
                "q" + i, 1000L + i, 1L, 1));
        }
        // Wait for async trim before listing so we can observe the 50-cap, not the 100-retain.
        awaitSessionCountIs("ses_1", 60);

        List<SqlExecutionRecord> records = repo.list(
            new SqlExecutionHistoryService.SqlExecutionHistoryQuery(
                "ses_1", null, null,
                SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter.SUCCESS, 999));
        assertThat(records).hasSize(50);
    }

    @Test
    void sqlTextTruncatedToFourKb() {
        String huge = "SELECT * FROM users WHERE name IN (" + "'a',".repeat(2000) + "'z')";
        assertThat(huge.length()).isGreaterThan(4096);
        repo.record(SqlExecutionRecord.success("ses_1", "c", null, null,
            huge, 1000L, 1L, 1));
        SqlExecutionRecord r = repo.list(
            new SqlExecutionHistoryService.SqlExecutionHistoryQuery(
                "ses_1", null, null,
                SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter.SUCCESS, 10)).get(0);
        assertThat(r.sqlText().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
            .isLessThanOrEqualTo(4096);
        assertThat(r.sqlText()).endsWith("...");
    }

    @Test
    void errorMessageTruncatedToOneKb() {
        String hugeError = "x".repeat(2000);
        repo.record(SqlExecutionRecord.failure("ses_1", "c", null, null,
            "SELECT 1", "E", hugeError, 1000L, 1L));
        SqlExecutionRecord r = repo.list(
            new SqlExecutionHistoryService.SqlExecutionHistoryQuery(
                "ses_1", null, null,
                SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter.FAILURE, 10)).get(0);
        assertThat(r.errorMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
            .isLessThanOrEqualTo(1024);
        assertThat(r.errorMessage()).endsWith("...");
    }

    @Test
    void trimsSessionToHundredAsynchronously() {
        for (int i = 0; i < 110; i++) {
            repo.record(SqlExecutionRecord.success("ses_trim", "c", null, null,
                "q" + i, 1000L + i, 1L, 1));
        }
        // Async trim should bring the count down to 100.
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
            assertThat(repo.countBySession("ses_trim")).isEqualTo(100));
    }

    @Test
    void recentFailuresAndSuccessesAreOrderedAndFiltered() {
        repo.record(SqlExecutionRecord.success("ses_1", "c", null, null, "s1", 1000L, 1L, 1));
        repo.record(SqlExecutionRecord.failure("ses_1", "c", null, null,
            "f1", "E", "boom", 2000L, 1L));
        repo.record(SqlExecutionRecord.success("ses_1", "c", null, null, "s2", 3000L, 1L, 1));
        repo.record(SqlExecutionRecord.failure("ses_1", "c", null, null,
            "f2", "E", "boom2", 4000L, 1L));

        List<SqlExecutionRecord> failures = repo.recentFailures("ses_1", 5);
        assertThat(failures).extracting(SqlExecutionRecord::sqlText).containsExactly("f2", "f1");

        List<SqlExecutionRecord> successes = repo.recentSuccesses("ses_1", 5);
        assertThat(successes).extracting(SqlExecutionRecord::sqlText).containsExactly("s2", "s1");
    }

    private void awaitSessionCountIs(String sessionId, int expected) {
        AtomicBoolean done = new AtomicBoolean(false);
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            int n = repo.countBySession(sessionId);
            // 60 inserts < 100 retain → no trim, so 60 should stick
            return n == expected || done.get();
        });
    }

    private static void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS sql_execution_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                connection_id TEXT NOT NULL,
                database_name TEXT,
                schema_name TEXT,
                sql_text TEXT NOT NULL,
                status TEXT NOT NULL CHECK (status IN ('success', 'failure')),
                error_code TEXT,
                error_message TEXT,
                executed_at INTEGER NOT NULL,
                duration_ms INTEGER,
                row_count INTEGER
            )
            """);
        jdbc.execute("""
            CREATE INDEX IF NOT EXISTS idx_sql_history_session_executed
                ON sql_execution_history(session_id, executed_at DESC)
            """);
        jdbc.execute("""
            CREATE INDEX IF NOT EXISTS idx_sql_history_session_status
                ON sql_execution_history(session_id, status, executed_at DESC)
            """);
    }
}
