package com.datatalk.application.persistence;

import com.datatalk.domain.undo.UndoLogEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UndoLogRepositoryPaginationTest {

    private UndoLogRepository repo;
    private Connection conn;
    private DataSource ds;
    private JdbcTemplate jdbc;

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
            CREATE TABLE undo_log (
                id            TEXT PRIMARY KEY,
                session_id    TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
                connection_id TEXT NOT NULL,
                database_name TEXT,
                schema_name   TEXT,
                table_name    TEXT NOT NULL,
                operation     TEXT NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
                original_sql  TEXT NOT NULL,
                inverse_sql   TEXT,
                before_state  TEXT,
                affected_rows INTEGER NOT NULL DEFAULT 0,
                undoable      INTEGER NOT NULL DEFAULT 1,
                status        TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'active', 'undone', 'expired')),
                expires_at    INTEGER NOT NULL,
                created_at    INTEGER NOT NULL,
                undone_at     INTEGER
            )
            """);
        ds = new SingleConnectionDataSource(conn, true);
        jdbc = new JdbcTemplate(ds);
        repo = new UndoLogRepository(jdbc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) conn.close();
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private UndoLogEntry entry(String id, String sessionId, String connectionId,
                               String tableName, String operation, String status,
                               String originalSql, String beforeState, long createdAt) {
        return new UndoLogEntry(
            id, sessionId, connectionId, "mydb", "public",
            tableName, operation, originalSql,
            "INVERSE_SQL", beforeState,
            1, true, status, Long.MAX_VALUE, createdAt, null
        );
    }

    private void insertSession(String id, String title) {
        jdbc.update("INSERT INTO sessions(id, title, has_ever_sent, created_at, updated_at) VALUES(?,?,0,?,?)",
            id, title, 1000L, 1000L);
    }

    // ── findByConnectionId ───────────────────────────────────────────────

    @Test
    void findByConnectionId_returnsPaginatedResults() {
        insertSession("sess-1", "Test Session");
        for (int i = 0; i < 5; i++) {
            repo.insert(entry("log-" + i, "sess-1", "conn-A", "users", "INSERT",
                "active", "INSERT INTO users VALUES(1)", null, 1000L + i));
        }
        // Also insert into a different connection to verify isolation
        repo.insert(entry("log-other", "sess-1", "conn-B", "orders", "DELETE",
            "active", "DELETE FROM orders", null, 2000L));

        var page0 = repo.findByConnectionId("conn-A", 0, 3,
            new UndoLogRepository.OpLogFilters(null, null, null, null, null));
        assertThat(page0.items()).hasSize(3);
        assertThat(page0.total()).isEqualTo(5);
        assertThat(page0.page()).isEqualTo(0);
        assertThat(page0.size()).isEqualTo(3);

        var page1 = repo.findByConnectionId("conn-A", 1, 3,
            new UndoLogRepository.OpLogFilters(null, null, null, null, null));
        assertThat(page1.items()).hasSize(2);
        assertThat(page1.total()).isEqualTo(5);

        // conn-B should only have the one record
        var pageB = repo.findByConnectionId("conn-B", 0, 10,
            new UndoLogRepository.OpLogFilters(null, null, null, null, null));
        assertThat(pageB.items()).hasSize(1);
        assertThat(pageB.total()).isEqualTo(1);
        assertThat(pageB.items().get(0).id()).isEqualTo("log-other");
    }

    @Test
    void findByConnectionId_orderByCreatedAtDesc() {
        insertSession("sess-1", "Test Session");
        repo.insert(entry("log-1", "sess-1", "conn-A", "users", "INSERT",
            "active", "INSERT 1", null, 1000L));
        repo.insert(entry("log-2", "sess-1", "conn-A", "users", "UPDATE",
            "active", "UPDATE 2", null, 2000L));
        repo.insert(entry("log-3", "sess-1", "conn-A", "users", "DELETE",
            "active", "DELETE 3", null, 3000L));

        var result = repo.findByConnectionId("conn-A", 0, 10,
            new UndoLogRepository.OpLogFilters(null, null, null, null, null));
        assertThat(result.items()).hasSize(3);
        // Most recent first
        assertThat(result.items().get(0).id()).isEqualTo("log-3");
        assertThat(result.items().get(1).id()).isEqualTo("log-2");
        assertThat(result.items().get(2).id()).isEqualTo("log-1");
    }

    @Test
    void findByConnectionId_filtersByStatus() {
        insertSession("sess-1", "Test Session");
        repo.insert(entry("log-1", "sess-1", "conn-A", "users", "INSERT",
            "active", "SQL1", null, 1000L));
        repo.insert(entry("log-2", "sess-1", "conn-A", "users", "INSERT",
            "undone", "SQL2", null, 2000L));
        repo.insert(entry("log-3", "sess-1", "conn-A", "users", "INSERT",
            "expired", "SQL3", null, 3000L));

        var result = repo.findByConnectionId("conn-A", 0, 10,
            new UndoLogRepository.OpLogFilters(List.of("active"), null, null, null, null));
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo("log-1");
        assertThat(result.total()).isEqualTo(1);

        // Multiple statuses
        var multiResult = repo.findByConnectionId("conn-A", 0, 10,
            new UndoLogRepository.OpLogFilters(List.of("active", "undone"), null, null, null, null));
        assertThat(multiResult.items()).hasSize(2);
        assertThat(multiResult.total()).isEqualTo(2);
    }

    @Test
    void findByConnectionId_filtersByOperation() {
        insertSession("sess-1", "Test Session");
        repo.insert(entry("log-1", "sess-1", "conn-A", "users", "INSERT",
            "active", "SQL1", null, 1000L));
        repo.insert(entry("log-2", "sess-1", "conn-A", "users", "UPDATE",
            "active", "SQL2", null, 2000L));
        repo.insert(entry("log-3", "sess-1", "conn-A", "users", "DELETE",
            "active", "SQL3", null, 3000L));

        var result = repo.findByConnectionId("conn-A", 0, 10,
            new UndoLogRepository.OpLogFilters(null, List.of("INSERT"), null, null, null));
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).operation()).isEqualTo("INSERT");
    }

    @Test
    void findByConnectionId_filtersByQ() {
        insertSession("sess-1", "Test Session");
        repo.insert(entry("log-1", "sess-1", "conn-A", "user_accounts", "INSERT",
            "active", "SQL1", null, 1000L));
        repo.insert(entry("log-2", "sess-1", "conn-A", "orders", "INSERT",
            "active", "SQL2", null, 2000L));

        // q matches table_name
        var byTable = repo.findByConnectionId("conn-A", 0, 10,
            new UndoLogRepository.OpLogFilters(null, null, null, null, "user"));
        assertThat(byTable.items()).hasSize(1);
        assertThat(byTable.items().get(0).tableName()).isEqualTo("user_accounts");

        // q matches original_sql
        var bySql = repo.findByConnectionId("conn-A", 0, 10,
            new UndoLogRepository.OpLogFilters(null, null, null, null, "SQL2"));
        assertThat(bySql.items()).hasSize(1);
        assertThat(bySql.items().get(0).id()).isEqualTo("log-2");
    }

    @Test
    void findByConnectionId_filtersByDateRange() {
        insertSession("sess-1", "Test Session");
        repo.insert(entry("log-1", "sess-1", "conn-A", "users", "INSERT",
            "active", "SQL1", null, 1000L));
        repo.insert(entry("log-2", "sess-1", "conn-A", "users", "INSERT",
            "active", "SQL2", null, 2000L));
        repo.insert(entry("log-3", "sess-1", "conn-A", "users", "INSERT",
            "active", "SQL3", null, 3000L));

        // from filter
        var fromResult = repo.findByConnectionId("conn-A", 0, 10,
            new UndoLogRepository.OpLogFilters(null, null, null, 1500L, null));
        assertThat(fromResult.items()).hasSize(2);
        assertThat(fromResult.total()).isEqualTo(2);

        // to filter
        var toResult = repo.findByConnectionId("conn-A", 0, 10,
            new UndoLogRepository.OpLogFilters(null, null, null, null, 2500L));
        assertThat(toResult.items()).hasSize(2);
        assertThat(toResult.total()).isEqualTo(2);

        // range filter
        var rangeResult = repo.findByConnectionId("conn-A", 0, 10,
            new UndoLogRepository.OpLogFilters(null, null, null, 1500L, 2500L));
        assertThat(rangeResult.items()).hasSize(1);
        assertThat(rangeResult.total()).isEqualTo(1);
    }

    @Test
    void findByConnectionId_filtersBySqlSearch() {
        insertSession("sess-1", "Test Session");
        repo.insert(entry("log-1", "sess-1", "conn-A", "users", "INSERT",
            "active", "INSERT INTO users (id, name) VALUES (1, 'Alice')", null, 1000L));
        repo.insert(entry("log-2", "sess-1", "conn-A", "users", "UPDATE",
            "active", "UPDATE users SET name = 'Bob' WHERE id = 1", null, 2000L));

        var result = repo.findByConnectionId("conn-A", 0, 10,
            new UndoLogRepository.OpLogFilters(null, null, null, null, "Alice"));
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo("log-1");
    }

    @Test
    void findByConnectionId_joinSessionTitle() {
        insertSession("sess-1", "My Session Title");
        repo.insert(entry("log-1", "sess-1", "conn-A", "users", "INSERT",
            "active", "SQL", null, 1000L));

        var result = repo.findByConnectionId("conn-A", 0, 10,
            new UndoLogRepository.OpLogFilters(null, null, null, null, null));
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).sessionTitle()).isEqualTo("My Session Title");
    }

    @Test
    void findByConnectionId_sessionTitleNullWhenNoSession() {
        // Insert undo_log without a matching session row
        jdbc.update("""
            INSERT INTO undo_log(id, session_id, connection_id, database_name, schema_name,
                table_name, operation, original_sql, inverse_sql, before_state,
                affected_rows, undoable, status, expires_at, created_at, undone_at)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            "log-1", "nonexistent-session", "conn-A", "mydb", "public",
            "users", "INSERT", "SQL", "INV", null,
            1, 1, "active", Long.MAX_VALUE, 1000L, null);

        var result = repo.findByConnectionId("conn-A", 0, 10,
            new UndoLogRepository.OpLogFilters(null, null, null, null, null));
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).sessionTitle()).isNull();
    }

    @Test
    void findByConnectionId_emptyResults_returnsEmptyWithTotalZero() {
        var result = repo.findByConnectionId("conn-nonexistent", 0, 10,
            new UndoLogRepository.OpLogFilters(null, null, null, null, null));
        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualTo(0);
    }

    // ── findByIdAndConnectionId ──────────────────────────────────────────

    @Test
    void findByIdAndConnectionId_returnsFullRecordWithBeforeState() {
        insertSession("sess-1", "Test Session");
        UndoLogEntry e = new UndoLogEntry(
            "log-1", "sess-1", "conn-A", "mydb", "public",
            "users", "UPDATE", "UPDATE users SET name='Bob' WHERE id=1",
            "UPDATE users SET name='Alice' WHERE id=1",
            "[{\"id\":1,\"name\":\"Alice\"}]", 1, true, "active",
            Long.MAX_VALUE, 1000L, null
        );
        repo.insert(e);

        var found = repo.findByIdAndConnectionId("log-1", "conn-A");
        assertThat(found).isPresent();
        assertThat(found.get().beforeState()).isEqualTo("[{\"id\":1,\"name\":\"Alice\"}]");
        assertThat(found.get().inverseSql()).isEqualTo("UPDATE users SET name='Alice' WHERE id=1");
        assertThat(found.get().operation()).isEqualTo("UPDATE");
        assertThat(found.get().tableName()).isEqualTo("users");
    }

    @Test
    void findByIdAndConnectionId_returnsEmptyForWrongConnection() {
        insertSession("sess-1", "Test Session");
        repo.insert(entry("log-1", "sess-1", "conn-A", "users", "INSERT",
            "active", "SQL", null, 1000L));

        var found = repo.findByIdAndConnectionId("log-1", "conn-WRONG");
        assertThat(found).isEmpty();
    }

    @Test
    void findByIdAndConnectionId_returnsEmptyForMissingId() {
        var found = repo.findByIdAndConnectionId("nonexistent", "conn-A");
        assertThat(found).isEmpty();
    }

    // ── findAllById ──────────────────────────────────────────────────────

    @Test
    void findAllById_returnsAllMatchingRecords() {
        insertSession("sess-1", "Test Session");
        repo.insert(entry("log-1", "sess-1", "conn-A", "users", "INSERT",
            "active", "SQL1", null, 1000L));
        repo.insert(entry("log-2", "sess-1", "conn-A", "orders", "DELETE",
            "active", "SQL2", null, 2000L));
        repo.insert(entry("log-3", "sess-1", "conn-A", "products", "UPDATE",
            "active", "SQL3", null, 3000L));

        List<UndoLogEntry> results = repo.findAllById(List.of("log-1", "log-3"));
        assertThat(results).hasSize(2);
        assertThat(results.stream().map(UndoLogEntry::id))
            .containsExactlyInAnyOrder("log-1", "log-3");
    }

    @Test
    void findAllById_returnsEmptyForEmptyInput() {
        List<UndoLogEntry> results = repo.findAllById(List.of());
        assertThat(results).isEmpty();
    }

    @Test
    void findAllById_returnsEmptyForNullInput() {
        List<UndoLogEntry> results = repo.findAllById(null);
        assertThat(results).isEmpty();
    }

    @Test
    void findAllById_ignoresNonExistentIds() {
        insertSession("sess-1", "Test Session");
        repo.insert(entry("log-1", "sess-1", "conn-A", "users", "INSERT",
            "active", "SQL1", null, 1000L));

        List<UndoLogEntry> results = repo.findAllById(List.of("log-1", "nonexistent"));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("log-1");
    }
}
