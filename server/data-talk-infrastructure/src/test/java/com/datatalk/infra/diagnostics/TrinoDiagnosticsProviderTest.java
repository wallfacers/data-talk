package com.datatalk.infra.diagnostics;

import com.datatalk.application.diagnostics.DiagnosticsProviderRegistry;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrinoDiagnosticsProviderTest {

    private TestableTrinoDiagnosticsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TestableTrinoDiagnosticsProvider(translator());
    }

    @Test
    void supportedDriverTypes_contains_trino() {
        assertThat(provider.supportedDriverTypes()).containsExactly("trino");
    }

    @Test
    void supportedCapabilities_containsOnlyExplain() {
        assertThat(provider.supportedCapabilities()).containsExactly(DiagnosticCapability.EXPLAIN);
    }

    @Test
    void explain_tableScan_marksFullScanAndExtractsTable() {
        provider.respond("EXPLAIN", rows(row("Query Plan", loadFixture("table_scan_hive.txt"))));

        var result = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT user_id FROM orders", testConn(), "pw", null, null);

        assertThat(result.value().nodes()).hasSize(1);
        var output = result.value().nodes().get(0);
        assertThat(output.operation()).isEqualTo("Output");
        assertThat(output.children()).hasSize(1);
        var aggregate = output.children().get(0);
        assertThat(aggregate.operation()).isEqualTo("Aggregate");
        assertThat(aggregate.scanType()).isEqualTo(ScanType.OTHER);
        var tableScan = aggregate.children().get(0);
        assertThat(tableScan.operation()).isEqualTo("TableScan");
        assertThat(tableScan.table()).isEqualTo("default.orders");
        assertThat(tableScan.scanType()).isEqualTo(ScanType.FULL_SCAN);
    }

    @Test
    void explain_alwaysCarriesFederatedPushdownWarning() {
        provider.respond("EXPLAIN", rows(row("Query Plan", "- Output[value]")));

        var result = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT 1", testConn(), "pw", null, null);

        assertThat(result.value().warnings()).hasSize(1);
        assertThat(result.value().warnings().get(0)).contains("connector may push down");
    }

    @Test
    void explain_aggregateJoin_parsesMultipleTableScans() {
        provider.respond("EXPLAIN", rows(row("Query Plan", loadFixture("aggregate_join.txt"))));

        var result = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT ...", testConn(), "pw", null, null);

        var tableScans = findAllTableScans(result.value().nodes());
        assertThat(tableScans).hasSize(2);
        assertThat(tableScans.stream().map(ExplainNode::table)).containsExactlyInAnyOrder("default.orders", "default.customers");
        assertThat(tableScans).allMatch(n -> n.scanType() == ScanType.FULL_SCAN);
    }

    @Test
    void explain_emptyPlan_returnsOk() {
        provider.respond("EXPLAIN", rows(row("Query Plan", "- Output[value]")));

        var result = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT 1", testConn(), "pw", null, null);

        assertThat(result.value().nodes()).hasSize(1);
        assertThat(result.value().nodes().get(0).operation()).isEqualTo("Output");
    }

    @Test
    void explain_unknownOp_mapsToOtherScanType() {
        provider.respond("EXPLAIN", rows(row("Query Plan", loadFixture("unknown_op.txt"))));

        var result = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT ...", testConn(), "pw", null, null);

        var customOp = findNodeByOperation(result.value().nodes(), "CustomOperator");
        assertThat(customOp).isPresent();
        assertThat(customOp.get().scanType()).isEqualTo(ScanType.OTHER);
    }

    @Test
    void explain_corruptBracket_extractsNullTable() {
        provider.respond("EXPLAIN", rows(row("Query Plan", loadFixture("corrupt.txt"))));

        var result = (DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT ...", testConn(), "pw", null, null);

        var tableScan = findNodeByOperation(result.value().nodes(), "TableScan");
        assertThat(tableScan).isPresent();
        assertThat(tableScan.get().table()).isNull();
        assertThat(tableScan.get().scanType()).isEqualTo(ScanType.FULL_SCAN);
    }

    @Test
    void explain_accessDenied_returnsUnsupported() {
        provider.failQueries(new SQLException("Access Denied for user", "42000", 100));

        var result = provider.explain("SELECT * FROM t", testConn(), "pw", null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<ExplainPlan>) result).reason()).contains("Access denied");
    }

    @Test
    void indexHints_returnsUnsupportedWithConnectorReason() {
        var result = provider.indexHints("SELECT * FROM t", null, null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<List<IndexRecommendation>>) result).reason()).contains("connector");
    }

    @Test
    void lockInfo_returnsUnsupported() {
        var result = provider.lockInfo(null, null, null);
        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
    }

    @Test
    void poolStatus_returnsUnsupported() {
        var result = provider.poolStatus(null, null);
        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
    }

    @Test
    void tableSpaceInfo_returnsUnsupported() {
        var result = provider.tableSpaceInfo(null, null, null, List.of());
        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
    }

    @Test
    void terminateSessionPreview_returnsUnsupported() {
        var result = provider.terminateSessionPreview(null, null, "1", null);
        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
    }

    @Test
    void terminateSession_returnsUnsupported() {
        var result = provider.terminateSession(null, null, "1", null);
        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
    }

    @Test
    void optimizeTablePreview_returnsUnsupported() {
        var result = provider.optimizeTablePreview(null, null, "users", null, null);
        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
    }

    @Test
    void optimizeTable_returnsUnsupported() {
        var result = provider.optimizeTable(null, null, "users", null, null);
        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
    }

    @Test
    void registryFindsProvider_for_trino_kind() {
        var registry = new DiagnosticsProviderRegistry(List.of(provider));
        assertThat(registry.find("trino")).isPresent();
        assertThat(registry.find("trino").get()).isSameAs(provider);
    }

    // --- Helpers ---

    private static String loadFixture(String name) {
        try (var is = TrinoDiagnosticsProviderTest.class.getResourceAsStream("/diagnostics/trino/" + name)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static ConnectionRecord testConn() {
        return new ConnectionRecord(
            "c1", "test", "trino", "localhost", 8080,
            "default", "user", new byte[0], null, 0L, 5000, null, null,
            null, 1, true, null, false, null, null, null);
    }

    private static Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.explain_unsupported", Locale.ENGLISH, "EXPLAIN not supported for dialect: {0}");
        source.addMessage("diagnostics.index_hints_unsupported", Locale.ENGLISH, "Index hints not supported for dialect: {0}");
        source.addMessage("diagnostics.lock_not_supported", Locale.ENGLISH, "{0} lock info is not yet supported");
        source.addMessage("diagnostics.pool_not_supported", Locale.ENGLISH, "{0} connection pool info is not yet supported");
        source.addMessage("diagnostics.tablespace_not_supported", Locale.ENGLISH, "{0} table space info is not yet supported");
        source.addMessage("diagnostics.terminate_not_supported", Locale.ENGLISH, "{0} session termination is not yet supported");
        source.addMessage("diagnostics.optimize_not_supported", Locale.ENGLISH, "{0} table optimization is not yet supported");
        source.addMessage("diagnostics.index_hints.unsupported.trino", Locale.ENGLISH, "Trino does not store data; indexes are determined by the underlying connector. Check indexes on the source data store (e.g. Hive / Iceberg / MySQL connector).");
        source.addMessage("diagnostics.explain.unsupported.trino_permission", Locale.ENGLISH, "Access denied. Ensure the user has SELECT permission on the queried tables / catalogs.");
        source.addMessage("diagnostics.warning.federated_connector_pushdown", Locale.ENGLISH, "Trino/Presto plan shows TableScan; the underlying connector may push down further. Verify on the source data store (e.g. Hive / Iceberg) for accurate I/O.");
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

    private static List<ExplainNode> findAllTableScans(List<ExplainNode> nodes) {
        List<ExplainNode> result = new ArrayList<>();
        for (ExplainNode node : nodes) {
            if ("TableScan".equals(node.operation())) {
                result.add(node);
            }
            result.addAll(findAllTableScans(node.children()));
        }
        return result;
    }

    private static java.util.Optional<ExplainNode> findNodeByOperation(List<ExplainNode> nodes, String operation) {
        for (ExplainNode node : nodes) {
            if (operation.equals(node.operation())) {
                return java.util.Optional.of(node);
            }
            var found = findNodeByOperation(node.children(), operation);
            if (found.isPresent()) return found;
        }
        return java.util.Optional.empty();
    }

    // --- Testable Provider ---

    static class TestableTrinoDiagnosticsProvider extends TrinoDiagnosticsProvider {
        private final Map<String, ArrayDeque<List<Map<String, Object>>>> responses = new LinkedHashMap<>();
        private SQLException queryFailure;

        TestableTrinoDiagnosticsProvider(Translator translator) {
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