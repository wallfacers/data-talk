package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PostgreSqlDiagnosticsProviderTest {

    private TestablePostgreSqlDiagnosticsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TestablePostgreSqlDiagnosticsProvider(translator());
    }

    @Test
    void supportedDriverTypes_containsBothPostgresqlAndPostgres() {
        assertThat(provider.supportedDriverTypes())
            .containsExactlyInAnyOrder("postgresql", "postgres");
    }

    @Test
    void supportedCapabilities_returnsAllDiagnosticsAndMutations() {
        assertThat(provider.supportedCapabilities())
            .containsExactlyInAnyOrder(
                DiagnosticCapability.EXPLAIN,
                DiagnosticCapability.INDEX_HINTS,
                DiagnosticCapability.LOCK_INFO,
                DiagnosticCapability.POOL_STATUS,
                DiagnosticCapability.TABLE_SPACE,
                DiagnosticCapability.TERMINATE_SESSION,
                DiagnosticCapability.OPTIMIZE_TABLE
            );
    }

    @Test
    void lockInfo_mapsBlockingChain() {
        provider.respond("pg_catalog.pg_locks", rows(row(
            "holder_pid", 42,
            "waiter_pid", 43,
            "holder_sql", "UPDATE users SET name='x'",
            "waiter_sql", "SELECT * FROM users",
            "table_name", "public.users",
            "lock_type", "AccessExclusiveLock",
            "wait_ms", 8_000
        )));

        var result = (DiagnosticResult.Ok<LockReport>) provider.lockInfo(testConn("postgres"), "pw", "postgres");

        assertThat(result.value().blockingChain()).hasSize(1);
        var entry = result.value().blockingChain().get(0);
        assertThat(entry.table()).isEqualTo("public.users");
        assertThat(entry.lockType()).isEqualTo("EXCLUSIVE");
        assertThat(entry.holderId()).isEqualTo("42");
        assertThat(entry.waiterId()).isEqualTo("43");
        assertThat(entry.waitMillis()).isEqualTo(8_000L);
    }

    @Test
    void lockInfo_doesNotJoinHolderLocksAgain() {
        provider.respond("pg_catalog.pg_locks", rows());

        provider.lockInfo(testConn("postgres"), "pw", "postgres");

        assertThat(provider.sqls()).anySatisfy(sql -> assertThat(sql).doesNotContain("blocking_locks"));
    }

    @Test
    void lockInfo_permissionDenied_returnsUnsupported() {
        provider.failQueries(new SQLException("permission denied", "42501"));

        var result = provider.lockInfo(testConn("postgres"), "pw", "postgres");

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<LockReport>) result).reason()).contains("privileges");
    }

    @Test
    void poolStatus_mapsServerCounters() {
        provider.respond("pg_stat_activity", rows(row("active", 25, "running", 10, "idle", 15, "waiting", 2)));
        provider.respond("SHOW max_connections", rows(row("max_connections", "100")));

        var result = (DiagnosticResult.Ok<PoolReport>) provider.poolStatus(testConn("postgres"), "pw");

        assertThat(result.value().scope()).isEqualTo("server");
        assertThat(result.value().activeConnections()).isEqualTo(25);
        assertThat(result.value().idleConnections()).isEqualTo(15);
        assertThat(result.value().maxConnections()).isEqualTo(100);
        assertThat(result.value().threadsRunning()).isEqualTo(10);
        assertThat(result.value().waitingConnections()).isEqualTo(2);
        assertThat(provider.sqls()).anySatisfy(sql -> assertThat(sql).contains("idle in transaction"));
    }

    @Test
    void tableSpaceInfo_mapsRowsAndExplicitTables() {
        provider.respond("pg_class", rows(row(
            "schema_name", "public",
            "table_name", "users",
            "row_count", 100,
            "data_size", 900,
            "index_size", 100,
            "free_size", 400
        )));

        var result = (DiagnosticResult.Ok<SpaceReport>) provider.tableSpaceInfo(testConn("postgres"), "pw", "postgres", List.of("users", "orders"));

        assertThat(result.value().tables()).hasSize(1);
        assertThat(result.value().tables().get(0).schemaName()).isEqualTo("public");
        assertThat(result.value().tables().get(0).freeSpaceBytes()).isEqualTo(400L);
        assertThat(provider.sqls()).anySatisfy(sql -> assertThat(sql).contains("c.relname IN"));
        assertThat(provider.params()).anySatisfy(params -> assertThat(params).contains("users", "orders"));
    }

    @Test
    void terminatePreview_blocksSelfTermination() {
        provider.respond("pg_backend_pid", rows(row("pid", 42)));

        var result = provider.terminateSessionPreview(testConn("postgres"), "pw", "42", "postgres");

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
    }

    @Test
    void terminatePreview_returnsCurrentSql() {
        provider.respond("pg_backend_pid", rows(row("pid", 99)));
        provider.respond("pg_stat_activity", rows(row("current_sql", "SELECT * FROM orders")));

        var result = (DiagnosticResult.Ok<TerminateSessionPreview>) provider.terminateSessionPreview(testConn("postgres"), "pw", "42", "postgres");

        assertThat(result.value().engine()).isEqualTo("postgresql");
        assertThat(result.value().willRunSql()).isEqualTo("SELECT pg_terminate_backend(42)");
        assertThat(result.value().currentSql()).isEqualTo("SELECT * FROM orders");
        assertThat(provider.sqls()).anySatisfy(sql -> assertThat(sql).contains("pid = ?::integer"));
        assertThat(provider.params()).anySatisfy(params -> assertThat(params).containsExactly(42));
    }

    @Test
    void terminateSession_handlesTrueAndFalseReturnValues() {
        provider.respond("pg_terminate_backend", rows(row("terminated", true)));
        var success = (DiagnosticResult.Ok<TerminateSessionResult>) provider.terminateSession(testConn("postgres"), "pw", "42", "postgres");
        assertThat(success.value().ok()).isTrue();
        assertThat(provider.sqls()).anySatisfy(sql -> assertThat(sql).contains("pg_terminate_backend(?::integer)"));
        assertThat(provider.params()).anySatisfy(params -> assertThat(params).containsExactly(42));

        provider.respond("pg_terminate_backend", rows(row("terminated", false)));
        var missing = (DiagnosticResult.Ok<TerminateSessionResult>) provider.terminateSession(testConn("postgres"), "pw", "42", "postgres");
        assertThat(missing.value().ok()).isFalse();
        assertThat(missing.value().message()).contains("no longer exists");
    }

    @Test
    void optimizePreview_buildsVacuumSqlAndWarning() {
        provider.respond("pg_total_relation_size", rows(row("total_size", 1_024_000)));

        var result = (DiagnosticResult.Ok<OptimizeTablePreview>) provider.optimizeTablePreview(testConn("postgres"), "pw", "users", null, "postgres");

        assertThat(result.value().schemaName()).isEqualTo("public");
        assertThat(result.value().willRunSql()).isEqualTo("VACUUM (FULL, VERBOSE) \"public\".\"users\"");
        assertThat(result.value().currentTotalSize()).isEqualTo(1_024_000L);
        assertThat(result.value().recommendations()).extracting(DiagnosticRecommendation::severity).containsExactly("critical");
    }

    @Test
    void optimizeExecute_usesAutoCommitVacuumAndComputesReclaimedBytes() {
        provider.respond("pg_total_relation_size", rows(row("total_size", 1_024_000)));
        provider.respond("pg_total_relation_size", rows(row("total_size", 200_000)));

        var result = (DiagnosticResult.Ok<OptimizeTableResult>) provider.optimizeTable(testConn("postgres"), "pw", "users", "ops", "postgres");

        assertThat(result.value().ok()).isTrue();
        assertThat(result.value().schemaName()).isEqualTo("ops");
        assertThat(result.value().reclaimedBytes()).isEqualTo(824_000L);
        assertThat(provider.executedSql()).isEqualTo("VACUUM (FULL, VERBOSE) \"ops\".\"users\"");
    }

    @Test
    void mapNodeType_mapsKnownNodeTypes() {
        assertThat(provider.mapNodeType("Seq Scan")).isEqualTo(ScanType.FULL_SCAN);
        assertThat(provider.mapNodeType("Index Scan")).isEqualTo(ScanType.INDEX_SCAN);
        assertThat(provider.mapNodeType("Index Only Scan")).isEqualTo(ScanType.INDEX_SCAN);
        assertThat(provider.mapNodeType("Bitmap Index Scan")).isEqualTo(ScanType.INDEX_RANGE);
        assertThat(provider.mapNodeType("Bitmap Heap Scan")).isEqualTo(ScanType.INDEX_RANGE);
        assertThat(provider.mapNodeType("Hash Join")).isEqualTo(ScanType.OTHER);
    }

    @Test
    void indexHints_seqScanRecommendations() {
        ExplainNode seqScanNode = new ExplainNode("Seq Scan", "orders", ScanType.FULL_SCAN, 5000L, null, null, List.of());
        ExplainPlan plan = new ExplainPlan("postgresql", "...", List.of(seqScanNode), null, List.of());

        var result = provider.indexHints("SELECT * FROM orders WHERE status = 'pending'", plan, null, null);

        assertThat(result.isOk()).isTrue();
        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).impact()).isEqualTo(Impact.HIGH);
        assertThat(recs.get(0).columns()).containsExactly("status");
    }

    @Test
    void applySchema_executesSetSearchPathWhenSchemaProvided() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);

        provider.applySchema(connection, "public");

        verify(statement).execute("SET search_path TO \"public\"");
        verify(statement).close();
    }

    @Test
    void applySchema_noopWhenSchemaBlank() throws Exception {
        Connection connection = mock(Connection.class);

        provider.applySchema(connection, " ");

        verify(connection, never()).createStatement();
    }

    private ConnectionRecord testConn(String databaseName) {
        return new ConnectionRecord(
            "c1", "test", "postgresql", "localhost", 5432,
            databaseName, "user", new byte[0], null, 0L, 5000, null, null,
            null, 1, true, null, false, null, null, null);
    }

    private Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.warning.sequential_scan", Locale.ENGLISH, "Sequential scan on {0}");
        source.addMessage("diagnostics.recommendation.sequential_scan", Locale.ENGLISH, "Sequential scan on {0} ({1} rows)");
        source.addMessage("diagnostics.error.permission_denied", Locale.ENGLISH, "Insufficient privileges to read diagnostic views");
        source.addMessage("diagnostics.terminate.unsupported.self", Locale.ENGLISH, "Cannot terminate the current connection");
        source.addMessage("diagnostics.terminate.session_not_found", Locale.ENGLISH, "Target session no longer exists; it may have already ended");
        source.addMessage("diagnostics.optimize.preview.lock_warning_pg", Locale.ENGLISH, "VACUUM FULL takes ACCESS EXCLUSIVE lock; the table will be unreadable during the operation");
        return new Translator(source);
    }

    private static List<Map<String, Object>> rows(Map<String, Object>... rows) {
        return List.of(rows);
    }

    private static Map<String, Object> row(Object... values) {
        var row = new LinkedHashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) {
            row.put(String.valueOf(values[i]), values[i + 1]);
        }
        return row;
    }

    static class TestablePostgreSqlDiagnosticsProvider extends PostgreSqlDiagnosticsProvider {
        private final Map<String, ArrayDeque<List<Map<String, Object>>>> responses = new LinkedHashMap<>();
        private final List<String> sqls = new ArrayList<>();
        private final List<List<Object>> params = new ArrayList<>();
        private SQLException queryFailure;
        private String executedSql;

        TestablePostgreSqlDiagnosticsProvider(Translator translator) {
            super(translator);
        }

        void respond(String sqlContains, List<Map<String, Object>> rows) {
            responses.computeIfAbsent(sqlContains, ignored -> new ArrayDeque<>()).add(rows);
        }

        void failQueries(SQLException failure) {
            this.queryFailure = failure;
        }

        List<String> sqls() {
            return sqls;
        }

        List<List<Object>> params() {
            return params;
        }

        String executedSql() {
            return executedSql;
        }

        @Override
        protected List<Map<String, Object>> queryForList(ConnectionRecord conn, String decryptedPassword, String sql, Object... params) throws SQLException {
            sqls.add(sql);
            this.params.add(List.of(params));
            if (queryFailure != null) throw queryFailure;
            for (var entry : responses.entrySet()) {
                if (sql.contains(entry.getKey())) {
                    var queue = entry.getValue();
                    return queue.isEmpty() ? List.of() : queue.removeFirst();
                }
            }
            return List.of();
        }

        @Override
        protected void executeStatementAutoCommit(ConnectionRecord conn, String decryptedPassword, String sql) {
            executedSql = sql;
        }
    }
}
