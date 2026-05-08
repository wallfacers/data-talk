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
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TiDbDiagnosticsProviderTest {

    private TestableTiDbDiagnosticsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TestableTiDbDiagnosticsProvider(translator());
    }

    @Test
    void supportedDriverTypes_contains_tidb() {
        assertThat(provider.supportedDriverTypes()).containsExactly("tidb");
    }

    @Test
    void supportedCapabilities_contains_explain_and_indexHints() {
        assertThat(provider.supportedCapabilities())
            .containsExactlyInAnyOrder(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS);
    }

    @Test
    void explain_fullScan_marksFullScanAndGeneratesWarning() throws IOException {
        provider.respond("EXPLAIN", loadFixture("full_scan.json"));

        var result = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT * FROM t WHERE a < 1", testConn(), "pw", "test", null);

        assertThat(result.value().nodes()).hasSize(1);
        var root = result.value().nodes().get(0);
        assertThat(root.operation()).isEqualTo("TableReader");
        assertThat(root.scanType()).isEqualTo(ScanType.OTHER);
        assertThat(root.children()).hasSize(1);

        var selection = root.children().get(0);
        assertThat(selection.operation()).isEqualTo("Selection");
        assertThat(selection.scanType()).isEqualTo(ScanType.OTHER);
        assertThat(selection.children()).hasSize(1);

        var fullScan = selection.children().get(0);
        assertThat(fullScan.operation()).isEqualTo("TableFullScan");
        assertThat(fullScan.scanType()).isEqualTo(ScanType.FULL_SCAN);
        assertThat(fullScan.table()).isEqualTo("t");
        assertThat(fullScan.rows()).isEqualTo(10000L);

        assertThat(result.value().warnings()).hasSize(1);
        assertThat(result.value().warnings().get(0)).contains("Full table scan on t");
    }

    @Test
    void explain_indexRange_marksIndexRange() throws IOException {
        provider.respond("EXPLAIN", loadFixture("index_range.json"));

        var result = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT * FROM t WHERE user_id = 42", testConn(), "pw", "test", null);

        assertThat(result.value().nodes()).hasSize(1);
        var lookup = result.value().nodes().get(0);
        assertThat(lookup.operation()).isEqualTo("IndexLookUp");
        assertThat(lookup.scanType()).isEqualTo(ScanType.INDEX_RANGE);

        assertThat(lookup.children()).hasSize(2);
        var indexScan = lookup.children().stream()
            .filter(n -> n.operation().equals("IndexRangeScan"))
            .findFirst().orElseThrow();
        assertThat(indexScan.scanType()).isEqualTo(ScanType.INDEX_RANGE);
    }

    @Test
    void explain_empty_returnsOkWithEmptyNodes() throws IOException {
        provider.respond("EXPLAIN", loadFixture("empty.json"));

        var result = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT 1", testConn(), "pw", "test", null);

        assertThat(result.value().nodes()).isEmpty();
        assertThat(result.value().warnings()).isEmpty();
    }

    @Test
    void explain_unknownOp_mapsToOther() throws IOException {
        provider.respond("EXPLAIN", loadFixture("unknown_op.json"));

        var result = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT * FROM t", testConn(), "pw", "test", null);

        assertThat(result.value().nodes()).hasSize(1);
        assertThat(result.value().nodes().get(0).operation()).isEqualTo("UnknownOpType");
        assertThat(result.value().nodes().get(0).scanType()).isEqualTo(ScanType.OTHER);
    }

    @Test
    void explain_corruptEstRows_returnsZero() throws IOException {
        provider.respond("EXPLAIN", loadFixture("corrupt.json"));

        var result = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT * FROM t", testConn(), "pw", "test", null);

        assertThat(result.value().nodes()).hasSize(1);
        assertThat(result.value().nodes().get(0).rows()).isEqualTo(0L);
    }

    @Test
    void explain_permissionDenied_returnsUnsupported() {
        provider.failQueries(new SQLException("Access denied for user 'test'@'localhost'", "28000", 1045));

        var result = provider.explain("SELECT * FROM t", testConn(), "pw", "test", null);

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<ExplainPlan>) result).reason()).contains("tidb_permission");
    }

    @Test
    void explain_otherError_returnsError() {
        provider.failQueries(new SQLException("Connection refused", "08001", 2003));

        var result = provider.explain("SELECT * FROM t", testConn(), "pw", "test", null);

        assertThat(result).isInstanceOf(DiagnosticResult.Error.class);
        assertThat(((DiagnosticResult.Error<ExplainPlan>) result).code()).isEqualTo("TIDB_EXPLAIN_ERROR");
    }

    @Test
    void indexHints_fullScan_recommendsBtreeIndex() throws IOException {
        provider.respond("EXPLAIN", loadFixture("full_scan.json"));

        var planResult = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT * FROM t WHERE a < 1", testConn(), "pw", "test", null);

        var hintsResult = (DiagnosticResult.Ok<List<IndexRecommendation>>) provider.indexHints("SELECT * FROM t WHERE a < 1", planResult.value(), testConn(), "pw");

        assertThat(hintsResult.value()).hasSize(1);
        var rec = hintsResult.value().get(0);
        assertThat(rec.table()).isEqualTo("t");
        assertThat(rec.columns()).containsExactly("a");
        assertThat(rec.indexType()).isEqualTo("BTREE");
        assertThat(rec.impact()).isEqualTo(Impact.HIGH);
        assertThat(rec.rationale()).contains("Full table scan on t").contains("10000");
    }

    @Test
    void indexHints_noFullScan_returnsEmpty() throws IOException {
        provider.respond("EXPLAIN", loadFixture("index_range.json"));

        var planResult = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT * FROM t WHERE user_id = 42", testConn(), "pw", "test", null);

        var hintsResult = (DiagnosticResult.Ok<List<IndexRecommendation>>) provider.indexHints("SELECT * FROM t WHERE user_id = 42", planResult.value(), testConn(), "pw");

        assertThat(hintsResult.value()).isEmpty();
    }

    @Test
    void indexHints_nullPlan_returnsEmpty() {
        var result = provider.indexHints("SELECT * FROM t", null, testConn(), "pw");

        assertThat(result.isOk()).isTrue();
        assertThat(((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value()).isEmpty();
    }

    @Test
    void indexHints_nestedJoin_multipleFullScans() throws IOException {
        provider.respond("EXPLAIN", loadFixture("nested_join.json"));

        var planResult = (DiagnosticResult.Ok<ExplainPlan>) provider.explain(
            "SELECT * FROM users u JOIN orders o ON u.id = o.user_id WHERE u.status = 'active' AND o.amount > 100",
            testConn(), "pw", "test", null);

        var hintsResult = (DiagnosticResult.Ok<List<IndexRecommendation>>) provider.indexHints(
            "SELECT * FROM users u JOIN orders o ON u.id = o.user_id WHERE u.status = 'active' AND o.amount > 100",
            planResult.value(), testConn(), "pw");

        assertThat(hintsResult.value()).hasSize(2);
        // users: estRows=100 -> MEDIUM (100 > 100 threshold, but <= 1000)
        var usersRec = hintsResult.value().stream().filter(r -> r.table().equals("users")).findFirst().orElseThrow();
        assertThat(usersRec.impact()).isEqualTo(Impact.MEDIUM);
        assertThat(usersRec.columns()).contains("status");

        // orders: estRows=2000 -> HIGH (> 1000)
        var ordersRec = hintsResult.value().stream().filter(r -> r.table().equals("orders")).findFirst().orElseThrow();
        assertThat(ordersRec.impact()).isEqualTo(Impact.HIGH);
        assertThat(ordersRec.columns()).contains("amount");
    }

    @Test
    void otherCapabilitiesReturnUnsupported() {
        assertUnsupported(provider.lockInfo(null, null, null), "tidb lock info is not yet supported");
        assertUnsupported(provider.poolStatus(null, null), "tidb connection pool info is not yet supported");
        assertUnsupported(provider.tableSpaceInfo(null, null, null, List.of()), "tidb table space info is not yet supported");
        assertUnsupported(provider.terminateSessionPreview(null, null, "1", null), "tidb session termination is not yet supported");
        assertUnsupported(provider.terminateSession(null, null, "1", null), "tidb session termination is not yet supported");
        assertUnsupported(provider.optimizeTablePreview(null, null, "users", null, null), "tidb table optimization is not yet supported");
        assertUnsupported(provider.optimizeTable(null, null, "users", null, null), "tidb table optimization is not yet supported");
    }

    @Test
    void registryFindsProvider_for_tidb_kind() {
        var registry = new DiagnosticsProviderRegistry(List.of(provider));
        assertThat(registry.find("tidb")).isPresent();
        assertThat(registry.find("tidb").get()).isSameAs(provider);
    }

    private static void assertUnsupported(DiagnosticResult<?> result, String reason) {
        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<?>) result).reason()).isEqualTo(reason);
    }

    private ConnectionRecord testConn() {
        return new ConnectionRecord(
            "c1", "test", "tidb", "localhost", 4000,
            "test", "user", new byte[0], null, 0L, 5000, null, null,
            null, 1, true, null, false);
    }

    private Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.explain_unsupported", Locale.ENGLISH, "EXPLAIN not supported for dialect: {0}");
        source.addMessage("diagnostics.index_hints_unsupported", Locale.ENGLISH, "Index hints not supported for dialect: {0}");
        source.addMessage("diagnostics.lock_not_supported", Locale.ENGLISH, "{0} lock info is not yet supported");
        source.addMessage("diagnostics.pool_not_supported", Locale.ENGLISH, "{0} connection pool info is not yet supported");
        source.addMessage("diagnostics.tablespace_not_supported", Locale.ENGLISH, "{0} table space info is not yet supported");
        source.addMessage("diagnostics.terminate_not_supported", Locale.ENGLISH, "{0} session termination is not yet supported");
        source.addMessage("diagnostics.optimize_not_supported", Locale.ENGLISH, "{0} table optimization is not yet supported");
        source.addMessage("diagnostics.warning.full_table_scan", Locale.ENGLISH, "Full table scan on {0}");
        source.addMessage("diagnostics.recommendation.full_scan", Locale.ENGLISH, "Full table scan on {0} ({1} rows)");
        source.addMessage("diagnostics.explain.unsupported.tidb_permission", Locale.ENGLISH, "Insufficient privileges to run EXPLAIN on TiDB");
        return new Translator(source);
    }

    private List<Map<String, Object>> loadFixture(String filename) throws IOException {
        Path path = Path.of("src/test/resources/diagnostics/tidb/" + filename);
        String json = Files.readString(path);
        return parseJsonRows(json);
    }

    private List<Map<String, Object>> parseJsonRows(String json) {
        // Simple JSON parsing for fixture data
        List<Map<String, Object>> rows = new ArrayList<>();
        json = json.trim();
        if (json.equals("[]")) return rows;

        // Parse JSON array manually for test fixtures
        int i = 1; // skip opening [
        while (i < json.length() - 1) {
            if (json.charAt(i) == '{') {
                int end = json.indexOf('}', i);
                String obj = json.substring(i, end + 1);
                rows.add(parseJsonRow(obj));
                i = end + 1;
                if (i < json.length() && json.charAt(i) == ',') i++;
            } else {
                i++;
            }
        }
        return rows;
    }

    private Map<String, Object> parseJsonRow(String obj) {
        Map<String, Object> row = new LinkedHashMap<>();
        // Remove braces
        obj = obj.substring(1, obj.length() - 1).trim();
        // Split by comma, handling nested content
        String[] pairs = obj.split(",\"");
        for (int j = 0; j < pairs.length; j++) {
            String pair = pairs[j].trim();
            if (j > 0) pair = "\"" + pair; // restore leading quote
            // Find key-value separator
            int colon = pair.indexOf(':');
            if (colon > 0) {
                String key = pair.substring(0, colon).replace("\"", "").trim();
                String value = pair.substring(colon + 1).trim();
                // Remove quotes from string values
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                row.put(key, value);
            }
        }
        return row;
    }

    static class TestableTiDbDiagnosticsProvider extends TiDbDiagnosticsProvider {
        private final Map<String, ArrayDeque<List<Map<String, Object>>>> responses = new LinkedHashMap<>();
        private SQLException queryFailure;

        TestableTiDbDiagnosticsProvider(Translator translator) {
            super(translator);
        }

        void respond(String sqlContains, List<Map<String, Object>> rows) {
            responses.computeIfAbsent(sqlContains, ignored -> new ArrayDeque<>()).add(rows);
        }

        void failQueries(SQLException failure) {
            this.queryFailure = failure;
        }

        @Override
        protected List<Map<String, Object>> queryForList(ConnectionRecord conn, String decryptedPassword, String sql, Object... params) throws SQLException {
            if (queryFailure != null) throw queryFailure;
            for (var entry : responses.entrySet()) {
                if (sql.contains(entry.getKey())) {
                    var queue = entry.getValue();
                    return queue.isEmpty() ? List.of() : queue.removeFirst();
                }
            }
            return List.of();
        }
    }
}