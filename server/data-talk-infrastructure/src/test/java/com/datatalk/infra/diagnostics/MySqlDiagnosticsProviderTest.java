package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlDiagnosticsProviderTest {

    private TestableMySqlDiagnosticsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TestableMySqlDiagnosticsProvider(translator());
    }

    @Test
    void supportedDriverTypes_returnsMysqlAndMariadb() {
        assertThat(provider.supportedDriverTypes()).containsExactlyInAnyOrder("mysql", "mariadb");
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
        provider.respond("data_lock_waits", rows(row(
            "holder_table", "users",
            "waiter_table", "users",
            "holder_lock_type", "EXCLUSIVE",
            "holder_thread", 42,
            "waiter_thread", 43,
            "wait_ms", 8_000,
            "holder_sql", "UPDATE users SET name='x'",
            "waiter_sql", "SELECT * FROM users"
        )));

        var result = (DiagnosticResult.Ok<LockReport>) provider.lockInfo(testConn("test_store"), "pw", "test_store");

        assertThat(result.value().blockingChain()).hasSize(1);
        var entry = result.value().blockingChain().get(0);
        assertThat(entry.table()).isEqualTo("users");
        assertThat(entry.lockType()).isEqualTo("EXCLUSIVE");
        assertThat(entry.holderId()).isEqualTo("42");
        assertThat(entry.waiterId()).isEqualTo("43");
        assertThat(entry.waitMillis()).isEqualTo(8_000L);
        assertThat(entry.holderSql()).contains("UPDATE users");
        assertThat(result.value().recommendations()).isEmpty();
    }

    @Test
    void lockInfo_permissionDenied_returnsUnsupported() {
        provider.failQueries(new SQLException("PFS denied", "42000", 1142));

        var result = provider.lockInfo(testConn("test_store"), "pw", "test_store");

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<LockReport>) result).reason()).contains("privileges");
    }

    @Test
    void poolStatus_mapsServerCounters() {
        provider.respond("Threads_connected", rows(row("Variable_name", "Threads_connected", "Value", "25")));
        provider.respond("Threads_running", rows(row("Variable_name", "Threads_running", "Value", "5")));
        provider.respond("max_connections", rows(row("Variable_name", "max_connections", "Value", "100")));
        provider.respond("COUNT(*) AS waiting", rows(row("waiting", 2)));

        var result = (DiagnosticResult.Ok<PoolReport>) provider.poolStatus(testConn("test_store"), "pw");

        assertThat(result.value().scope()).isEqualTo("server");
        assertThat(result.value().activeConnections()).isEqualTo(25);
        assertThat(result.value().idleConnections()).isEqualTo(20);
        assertThat(result.value().maxConnections()).isEqualTo(100);
        assertThat(result.value().threadsRunning()).isEqualTo(5);
        assertThat(result.value().waitingConnections()).isEqualTo(2);
        assertThat(result.value().identifier()).isEqualTo("localhost:3306");
    }

    @Test
    void poolStatus_readsValueColumnCaseInsensitively() {
        provider.respond("Threads_connected", rows(row("VARIABLE_NAME", "Threads_connected", "VALUE", "25")));
        provider.respond("Threads_running", rows(row("VARIABLE_NAME", "Threads_running", "VALUE", "5")));
        provider.respond("max_connections", rows(row("VARIABLE_NAME", "max_connections", "VALUE", "100")));
        provider.respond("COUNT(*) AS waiting", rows(row("waiting", 2)));

        var result = (DiagnosticResult.Ok<PoolReport>) provider.poolStatus(testConn("test_store"), "pw");

        assertThat(result.value().activeConnections()).isEqualTo(25);
        assertThat(result.value().idleConnections()).isEqualTo(20);
        assertThat(result.value().maxConnections()).isEqualTo(100);
    }

    @Test
    void tableSpaceInfo_mapsRowsAndCapsAt200() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            rows.add(row(
                "schema_name", "test_store",
                "table_name", "t" + i,
                "row_count", i,
                "data_size", 1000 + i,
                "index_size", 500 + i,
                "free_size", 100 + i
            ));
        }
        provider.respond("information_schema.tables", rows);

        var result = (DiagnosticResult.Ok<SpaceReport>) provider.tableSpaceInfo(testConn("test_store"), "pw", "test_store", null);

        assertThat(result.value().tables()).hasSize(200);
        assertThat(result.value().tables().get(0).schemaName()).isEqualTo("test_store");
        assertThat(result.value().tables().get(0).freeSpaceBytes()).isEqualTo(100L);
    }

    @Test
    void tableSpaceInfo_explicitTablesAddsInClause() {
        provider.respond("information_schema.tables", rows());

        provider.tableSpaceInfo(testConn("test_store"), "pw", "test_store", List.of("users", "orders"));

        assertThat(provider.sqls()).anySatisfy(sql -> assertThat(sql).contains("table_name IN"));
        assertThat(provider.params()).anySatisfy(params -> assertThat(params).contains("users", "orders"));
    }

    @Test
    void tableSpaceInfo_withoutDatabase_returnsUnsupported() {
        var result = provider.tableSpaceInfo(testConn(null), "pw", null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<SpaceReport>) result).reason()).contains("No database selected");
    }

    @Test
    void terminatePreview_blocksSelfTermination() {
        provider.respond("CONNECTION_ID", rows(row("connection_id", 42)));

        var result = provider.terminateSessionPreview(testConn("test_store"), "pw", "42", "test_store");

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<TerminateSessionPreview>) result).reason()).contains("current connection");
    }

    @Test
    void terminatePreview_returnsCurrentSqlWhenAvailable() {
        provider.respond("CONNECTION_ID", rows(row("connection_id", 99)));
        provider.respond("performance_schema.threads", rows(row("current_sql", "SELECT * FROM orders")));

        var result = (DiagnosticResult.Ok<TerminateSessionPreview>) provider.terminateSessionPreview(testConn("test_store"), "pw", "42", "test_store");

        assertThat(result.value().engine()).isEqualTo("mysql");
        assertThat(result.value().sessionId()).isEqualTo("42");
        assertThat(result.value().willRunSql()).isEqualTo("KILL 42");
        assertThat(result.value().currentSql()).isEqualTo("SELECT * FROM orders");
    }

    @Test
    void terminatePreview_targetMissingStillReturnsPreview() {
        provider.respond("CONNECTION_ID", rows(row("connection_id", 99)));
        provider.respond("performance_schema.threads", rows());

        var result = (DiagnosticResult.Ok<TerminateSessionPreview>) provider.terminateSessionPreview(testConn("test_store"), "pw", "42", "test_store");

        assertThat(result.value().currentSql()).isNull();
    }

    @Test
    void terminateSession_successAndTargetMissing() {
        provider.executeResult(0);
        var success = (DiagnosticResult.Ok<TerminateSessionResult>) provider.terminateSession(testConn("test_store"), "pw", "42", "test_store");
        assertThat(success.value().ok()).isTrue();
        assertThat(provider.executedSql()).contains("KILL 42");

        provider.failExecute(new SQLException("Unknown thread", "HY000", 1094));
        var missing = (DiagnosticResult.Ok<TerminateSessionResult>) provider.terminateSession(testConn("test_store"), "pw", "42", "test_store");
        assertThat(missing.value().ok()).isFalse();
        assertThat(missing.value().message()).contains("no longer exists");
    }

    @Test
    void terminateSession_permissionDenied_returnsUnsupported() {
        provider.failExecute(new SQLException("denied", "42000", 1227));

        var result = provider.terminateSession(testConn("test_store"), "pw", "42", "test_store");

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
    }

    @Test
    void optimizePreview_buildsSqlAndCriticalWarning() {
        provider.respond("data_free", rows(row("data_free", 1_024_000)));

        var result = (DiagnosticResult.Ok<OptimizeTablePreview>) provider.optimizeTablePreview(testConn("test_store"), "pw", "users", null, "test_store");

        assertThat(result.value().engine()).isEqualTo("mysql");
        assertThat(result.value().schemaName()).isEqualTo("test_store");
        assertThat(result.value().willRunSql()).isEqualTo("OPTIMIZE TABLE `test_store`.`users`");
        assertThat(result.value().currentDataFree()).isEqualTo(1_024_000L);
        assertThat(result.value().recommendations()).extracting(DiagnosticRecommendation::severity).containsExactly("critical");
    }

    @Test
    void optimizePreview_withoutDatabase_returnsUnsupported() {
        var result = provider.optimizeTablePreview(testConn(null), "pw", "users", null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
    }

    @Test
    void optimizeExecute_computesReclaimedBytes() {
        provider.respond("data_free", rows(row("data_free", 1_024_000)));
        provider.respond("data_free", rows(row("data_free", 200_000)));
        provider.executeResult(0);

        var result = (DiagnosticResult.Ok<OptimizeTableResult>) provider.optimizeTable(testConn("test_store"), "pw", "users", null, "test_store");

        assertThat(result.value().ok()).isTrue();
        assertThat(result.value().reclaimedBytes()).isEqualTo(824_000L);
        assertThat(provider.executedSql()).contains("OPTIMIZE TABLE `test_store`.`users`");
        assertThat(provider.executeStatementCalled()).isTrue();
        assertThat(provider.executeUpdateCalled()).isFalse();
    }

    @Test
    void optimizePreview_quotesMysqlIdentifiers() {
        provider.respond("data_free", rows(row("data_free", 1L)));

        var result = (DiagnosticResult.Ok<OptimizeTablePreview>) provider.optimizeTablePreview(testConn("test`store"), "pw", "tab\"le", "test`store", null);

        assertThat(result.value().willRunSql()).isEqualTo("OPTIMIZE TABLE `test``store`.`tab\"le`");
    }

    @Test
    void mapAccessType_mapsKnownAccessTypes() {
        assertThat(provider.mapAccessType("all")).isEqualTo(ScanType.FULL_SCAN);
        assertThat(provider.mapAccessType("range")).isEqualTo(ScanType.INDEX_RANGE);
        assertThat(provider.mapAccessType("ref")).isEqualTo(ScanType.REF);
        assertThat(provider.mapAccessType("eq_ref")).isEqualTo(ScanType.REF);
        assertThat(provider.mapAccessType("index")).isEqualTo(ScanType.INDEX_SCAN);
        assertThat(provider.mapAccessType("const")).isEqualTo(ScanType.CONST);
        assertThat(provider.mapAccessType("system")).isEqualTo(ScanType.CONST);
        assertThat(provider.mapAccessType("something_else")).isEqualTo(ScanType.OTHER);
    }

    @Test
    void indexHints_fullScanRecommendations() {
        ExplainNode fullScanNode = new ExplainNode("all", "orders", ScanType.FULL_SCAN, 5000L, null, null, List.of());
        ExplainPlan plan = new ExplainPlan("mysql", "...", List.of(fullScanNode), null, List.of());

        var result = provider.indexHints("SELECT * FROM orders WHERE status = 'pending'", plan, null, null);

        assertThat(result.isOk()).isTrue();
        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).impact()).isEqualTo(Impact.HIGH);
        assertThat(recs.get(0).columns()).containsExactly("status");
    }

    private ConnectionRecord testConn(String databaseName) {
        return new ConnectionRecord(
            "c1", "test", "mysql", "localhost", 3306,
            databaseName, "user", new byte[0], null, 0L, 5000, null, null,
            null);
    }

    private Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.warning.full_table_scan", Locale.ENGLISH, "Full table scan on {0}");
        source.addMessage("diagnostics.recommendation.full_scan", Locale.ENGLISH, "Full table scan on {0} ({1} rows)");
        source.addMessage("diagnostics.error.permission_denied", Locale.ENGLISH, "Insufficient privileges to read diagnostic views");
        source.addMessage("diagnostics.terminate.unsupported.self", Locale.ENGLISH, "Cannot terminate the current connection");
        source.addMessage("diagnostics.terminate.session_not_found", Locale.ENGLISH, "Target session no longer exists; it may have already ended");
        source.addMessage("diagnostics.space.unsupported.no_database", Locale.ENGLISH, "No database selected; cannot inspect table space");
        source.addMessage("diagnostics.optimize.unsupported.no_database", Locale.ENGLISH, "No database selected; cannot determine table location");
        source.addMessage("diagnostics.optimize.preview.lock_warning_mysql", Locale.ENGLISH, "OPTIMIZE TABLE locks the table for the duration of the operation; may impact production traffic");
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

    static class TestableMySqlDiagnosticsProvider extends MySqlDiagnosticsProvider {
        private final Map<String, ArrayDeque<List<Map<String, Object>>>> responses = new LinkedHashMap<>();
        private final List<String> sqls = new ArrayList<>();
        private final List<List<Object>> params = new ArrayList<>();
        private SQLException queryFailure;
        private SQLException executeFailure;
        private int executeResult;
        private String executedSql;
        private boolean executeStatementCalled;
        private boolean executeUpdateCalled;

        TestableMySqlDiagnosticsProvider(Translator translator) {
            super(translator);
        }

        void respond(String sqlContains, List<Map<String, Object>> rows) {
            responses.computeIfAbsent(sqlContains, ignored -> new ArrayDeque<>()).add(rows);
        }

        void failQueries(SQLException failure) {
            this.queryFailure = failure;
        }

        void executeResult(int executeResult) {
            this.executeFailure = null;
            this.executeResult = executeResult;
        }

        void failExecute(SQLException failure) {
            this.executeFailure = failure;
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

        boolean executeStatementCalled() {
            return executeStatementCalled;
        }

        boolean executeUpdateCalled() {
            return executeUpdateCalled;
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
        protected int executeUpdate(ConnectionRecord conn, String decryptedPassword, String sql) throws SQLException {
            executeUpdateCalled = true;
            executedSql = sql;
            if (executeFailure != null) throw executeFailure;
            return executeResult;
        }

        @Override
        protected void executeStatement(ConnectionRecord conn, String decryptedPassword, String sql) throws SQLException {
            executeStatementCalled = true;
            executedSql = sql;
            if (executeFailure != null) throw executeFailure;
        }
    }
}
