package com.datatalk.infra.diagnostics;

import com.datatalk.application.diagnostics.DiagnosticsProviderRegistry;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DorisDiagnosticsProviderTest {

    private TestableDorisDiagnosticsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TestableDorisDiagnosticsProvider(translator());
    }

    @Test
    void supportedDriverTypes_contains_apache_doris() {
        assertThat(provider.supportedDriverTypes()).containsExactly("apache_doris");
    }

    @Test
    void supportedCapabilities_containsExplainOnly() {
        assertThat(provider.supportedCapabilities()).containsExactly(DiagnosticCapability.EXPLAIN);
    }

    @Test
    void explain_olapScanWithPreaggOnNoPredicates_marksFullScan() throws IOException {
        String fixture = loadFixture("full_scan.txt");
        provider.respond(rows(wrapText(fixture)));

        var result = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT id FROM orders", testConn("test"), "pw", "test", null);

        assertThat(result.value().nodes()).hasSize(1);
        var node = result.value().nodes().get(0);
        assertThat(node.operation()).isEqualTo("OlapScanNode");
        assertThat(node.table()).isEqualTo("orders");
        assertThat(node.scanType()).isEqualTo(ScanType.FULL_SCAN);
        assertThat(node.rows()).isEqualTo(10000L);
    }

    @Test
    void explain_olapScanWithPredicateAndRollupHit_marksIndexScan() throws IOException {
        String fixture = loadFixture("index_scan.txt");
        provider.respond(rows(wrapText(fixture)));

        var result = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT id, name FROM orders WHERE status = 'pending'", testConn("test"), "pw", "test", null);

        assertThat(result.value().nodes()).hasSize(1);
        var node = result.value().nodes().get(0);
        assertThat(node.operation()).isEqualTo("OlapScanNode");
        assertThat(node.table()).isEqualTo("orders");
        assertThat(node.scanType()).isEqualTo(ScanType.INDEX_SCAN);
        assertThat(node.rows()).isEqualTo(500L);
    }

    @Test
    void explain_olapScanWithPredicateNoRollupHit_marksIndexRange() throws IOException {
        String fixture = loadFixture("index_range.txt");
        provider.respond(rows(wrapText(fixture)));

        var result = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT id, status FROM orders WHERE status = 'pending'", testConn("test"), "pw", "test", null);

        assertThat(result.value().nodes()).hasSize(1);
        var node = result.value().nodes().get(0);
        assertThat(node.operation()).isEqualTo("OlapScanNode");
        assertThat(node.table()).isEqualTo("orders");
        assertThat(node.scanType()).isEqualTo(ScanType.INDEX_RANGE);
    }

    @Test
    void explain_empty_returnsOkWithEmptyNodes() throws IOException {
        String fixture = loadFixture("empty.txt");
        provider.respond(rows(wrapText(fixture)));

        var result = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT 1", testConn("test"), "pw", "test", null);

        assertThat(result.value().nodes()).isEmpty();
        assertThat(result.value().rawText()).isEmpty();
    }

    @Test
    void explain_unknownOp_mapsToOther() throws IOException {
        String fixture = loadFixture("unknown_op.txt");
        provider.respond(rows(wrapText(fixture)));

        var result = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT id FROM users", testConn("test"), "pw", "test", null);

        assertThat(result.value().nodes()).hasSize(1);
        var node = result.value().nodes().get(0);
        assertThat(node.operation()).isEqualTo("UnknownNode");
        assertThat(node.table()).isEqualTo("users");
        assertThat(node.scanType()).isEqualTo(ScanType.OTHER);
    }

    @Test
    void explain_permissionDenied_returnsUnsupported() {
        provider.failQueries(new SQLException("Access denied for user 'readonly'@'localhost'", "28000", 1045));

        var result = provider.explain("SELECT * FROM orders", testConn("test"), "pw", "test", null);

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<ExplainPlan>) result).reason()).contains("permission");
    }

    @Test
    void explain_driverError_returnsError() {
        provider.failQueries(new SQLException("Connection timeout", "HY000", 0));

        var result = provider.explain("SELECT * FROM orders", testConn("test"), "pw", "test", null);

        assertThat(result).isInstanceOf(DiagnosticResult.DiagnosticError.class);
        var error = (DiagnosticResult.DiagnosticError<ExplainPlan>) result;
        assertThat(error.errorType()).isEqualTo("APACHE_DORIS_EXPLAIN_ERROR");
        assertThat(error.message()).contains("Connection timeout");
    }

    @Test
    void indexHints_returnsUnsupported() {
        var result = provider.indexHints("SELECT * FROM orders", null, null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<List<IndexRecommendation>>) result).reason()).contains("ROLLUP");
    }

    @Test
    void lockInfo_returnsUnsupported() {
        var result = provider.lockInfo(null, null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<LockReport>) result).reason()).contains("apache_doris");
    }

    @Test
    void poolStatus_returnsUnsupported() {
        var result = provider.poolStatus(null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<PoolReport>) result).reason()).contains("apache_doris");
    }

    @Test
    void tableSpaceInfo_returnsUnsupported() {
        var result = provider.tableSpaceInfo(null, null, null, List.of());

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<SpaceReport>) result).reason()).contains("apache_doris");
    }

    @Test
    void terminateSessionPreview_returnsUnsupported() {
        var result = provider.terminateSessionPreview(null, null, "1", null);

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<TerminateSessionPreview>) result).reason()).contains("apache_doris");
    }

    @Test
    void terminateSession_returnsUnsupported() {
        var result = provider.terminateSession(null, null, "1", null);

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<TerminateSessionResult>) result).reason()).contains("apache_doris");
    }

    @Test
    void optimizeTablePreview_returnsUnsupported() {
        var result = provider.optimizeTablePreview(null, null, "users", null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<OptimizeTablePreview>) result).reason()).contains("apache_doris");
    }

    @Test
    void optimizeTable_returnsUnsupported() {
        var result = provider.optimizeTable(null, null, "users", null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<OptimizeTableResult>) result).reason()).contains("apache_doris");
    }

    @Test
    void registryFindsProvider_for_apache_doris_kind() {
        var registry = new DiagnosticsProviderRegistry(List.of(provider));
        assertThat(registry.find("apache_doris")).isPresent();
        assertThat(registry.find("apache_doris").get()).isSameAs(provider);
    }

    @Test
    void explain_multiFragment_returnsMultipleNodes() throws IOException {
        String fixture = loadFixture("multi_fragment.txt");
        provider.respond(rows(wrapText(fixture)));

        var result = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT * FROM users JOIN orders ON users.id = orders.user_id", testConn("test"), "pw", "test", null);

        assertThat(result.value().nodes()).hasSize(2);
        var node1 = result.value().nodes().get(0);
        assertThat(node1.operation()).isEqualTo("OlapScanNode");
        assertThat(node1.table()).isEqualTo("users");
        assertThat(node1.scanType()).isEqualTo(ScanType.FULL_SCAN);
        assertThat(node1.rows()).isEqualTo(2000L);

        var node2 = result.value().nodes().get(1);
        assertThat(node2.operation()).isEqualTo("OlapScanNode");
        assertThat(node2.table()).isEqualTo("orders");
        assertThat(node2.scanType()).isEqualTo(ScanType.INDEX_SCAN);
        assertThat(node2.rows()).isEqualTo(100L);
    }

    private ConnectionRecord testConn(String databaseName) {
        return new ConnectionRecord(
            "c1", "test", "apache_doris", "localhost", 9030,
            databaseName, "user", new byte[0], null, 0L, 5000, null, null,
            null, 1, true, null, false, null, null, null);
    }

    private Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.index_hints.unsupported.apache_doris", Locale.ENGLISH, "Index hints are not supported for Apache Doris due to ROLLUP-based storage architecture");
        source.addMessage("diagnostics.lock_not_supported", Locale.ENGLISH, "{0} lock info is not yet supported");
        source.addMessage("diagnostics.pool_not_supported", Locale.ENGLISH, "{0} connection pool info is not yet supported");
        source.addMessage("diagnostics.tablespace_not_supported", Locale.ENGLISH, "{0} table space info is not yet supported");
        source.addMessage("diagnostics.terminate_not_supported", Locale.ENGLISH, "{0} session termination is not yet supported");
        source.addMessage("diagnostics.optimize_not_supported", Locale.ENGLISH, "{0} table optimization is not yet supported");
        source.addMessage("diagnostics.explain.unsupported.doris_permission", Locale.ENGLISH, "Permission denied: cannot run EXPLAIN on Apache Doris");
        return new Translator(source);
    }

    private String loadFixture(String filename) throws IOException {
        Path path = Path.of("src/test/resources/diagnostics/doris", filename);
        return Files.readString(path);
    }

    private static Map<String, Object> wrapText(String text) {
        var row = new LinkedHashMap<String, Object>();
        row.put("Explain", text);
        return row;
    }

    private static List<Map<String, Object>> rows(Map<String, Object>... rows) {
        return List.of(rows);
    }

    static class TestableDorisDiagnosticsProvider extends DorisDiagnosticsProvider {
        private List<Map<String, Object>> responseRows;
        private SQLException queryFailure;

        TestableDorisDiagnosticsProvider(Translator translator) {
            super(translator);
        }

        void respond(List<Map<String, Object>> rows) {
            this.responseRows = rows;
        }

        void failQueries(SQLException failure) {
            this.queryFailure = failure;
        }

        @Override
        protected List<Map<String, Object>> queryForList(ConnectionRecord conn, String decryptedPassword, String sql, Object... params) throws SQLException {
            if (queryFailure != null) throw queryFailure;
            return responseRows != null ? responseRows : List.of();
        }
    }
}