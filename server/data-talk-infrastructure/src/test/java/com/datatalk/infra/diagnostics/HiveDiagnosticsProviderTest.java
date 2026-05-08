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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HiveDiagnosticsProviderTest {

    private TestableHiveDiagnosticsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TestableHiveDiagnosticsProvider(translator());
    }

    @Test
    void supportedDriverTypes_contains_hive() {
        assertThat(provider.supportedDriverTypes()).containsExactly("hive");
    }

    @Test
    void supportedCapabilities_containsOnlyExplain() {
        assertThat(provider.supportedCapabilities()).containsExactly(DiagnosticCapability.EXPLAIN);
    }

    @Test
    void explain_tableScan_marksFullScan() {
        provider.loadFixture("simple_table_scan.txt");

        var result = provider.explain("SELECT * FROM orders", testConn(), "pw", null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Ok.class);
        var plan = ((DiagnosticResult.Ok<ExplainPlan>) result).value();

        assertThat(plan.dialect()).isEqualTo("hive");
        assertThat(plan.warnings()).containsExactly("Hive partition check: verify partition column usage");
        assertThat(plan.nodes()).hasSize(1);

        var root = plan.nodes().get(0);
        assertThat(root.operation()).isEqualTo("TableScan");
        assertThat(root.scanType()).isEqualTo(ScanType.FULL_SCAN);
        assertThat(root.rows()).isEqualTo(10000L);
    }

    @Test
    void explain_filterWithPredicate_includesFilterOperator() {
        provider.loadFixture("filter_with_predicate.txt");

        var result = provider.explain("SELECT * FROM users WHERE age > 18", testConn(), "pw", null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Ok.class);
        var plan = ((DiagnosticResult.Ok<ExplainPlan>) result).value();

        assertThat(plan.nodes()).hasSize(1);
        var tableScan = plan.nodes().get(0);
        assertThat(tableScan.operation()).isEqualTo("TableScan");
        assertThat(tableScan.scanType()).isEqualTo(ScanType.FULL_SCAN);
        assertThat(tableScan.rows()).isEqualTo(50000L);

        assertThat(tableScan.children()).hasSize(1);
        var filter = tableScan.children().get(0);
        assertThat(filter.operation()).isEqualTo("Filter");
        assertThat(filter.scanType()).isEqualTo(ScanType.OTHER);
        assertThat(filter.rows()).isEqualTo(30000L);
    }

    @Test
    void explain_join_withMultipleStages() {
        provider.loadFixture("join.txt");

        var result = provider.explain("SELECT * FROM orders o JOIN customers c ON o.customer_id = c.id", testConn(), "pw", null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Ok.class);
        var plan = ((DiagnosticResult.Ok<ExplainPlan>) result).value();

        // Should have multiple TableScans from different stages
        assertThat(plan.nodes()).hasSize(2);
        var ordersScan = plan.nodes().get(0);
        assertThat(ordersScan.operation()).isEqualTo("TableScan");
        assertThat(ordersScan.rows()).isEqualTo(100000L);

        var customersScan = plan.nodes().get(1);
        assertThat(customersScan.operation()).isEqualTo("TableScan");
        assertThat(customersScan.rows()).isEqualTo(10000L);
    }

    @Test
    void explain_emptyStages_returnsOk() {
        provider.loadFixture("empty_stages.txt");

        var result = provider.explain("SELECT 1", testConn(), "pw", null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Ok.class);
        var plan = ((DiagnosticResult.Ok<ExplainPlan>) result).value();

        assertThat(plan.nodes()).isEmpty();
        assertThat(plan.warnings()).containsExactly("Hive partition check: verify partition column usage");
    }

    @Test
    void explain_unknownOp_mapsToOther() {
        provider.loadFixture("unknown_op.txt");

        var result = provider.explain("SELECT * FROM events", testConn(), "pw", null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Ok.class);
        var plan = ((DiagnosticResult.Ok<ExplainPlan>) result).value();

        assertThat(plan.nodes()).hasSize(1);
        var scan = plan.nodes().get(0);
        assertThat(scan.operation()).isEqualTo("TableScan");
        assertThat(scan.scanType()).isEqualTo(ScanType.FULL_SCAN);

        // Unknown operators should map to OTHER scan type
        assertThat(scan.children()).hasSize(2);
        var unknown = scan.children().get(0);
        assertThat(unknown.operation()).isEqualTo("Unknown");
        assertThat(unknown.scanType()).isEqualTo(ScanType.OTHER);

        var custom = scan.children().get(1);
        assertThat(custom.operation()).isEqualTo("CustomAggregate");
        assertThat(custom.scanType()).isEqualTo(ScanType.OTHER);
    }

    @Test
    void explain_corruptPlan_returnsOkWithEmptyNodes() {
        provider.loadFixture("corrupt.txt");

        var result = provider.explain("SELECT invalid", testConn(), "pw", null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Ok.class);
        var plan = ((DiagnosticResult.Ok<ExplainPlan>) result).value();
        assertThat(plan.nodes()).isEmpty();
    }

    @Test
    void explain_alwaysHasPartitionCheckWarning() {
        provider.loadFixture("simple_table_scan.txt");

        var result = provider.explain("SELECT * FROM orders", testConn(), "pw", null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Ok.class);
        var plan = ((DiagnosticResult.Ok<ExplainPlan>) result).value();
        assertThat(plan.warnings()).containsExactly("Hive partition check: verify partition column usage");
    }

    @Test
    void explain_permissionDenied_returnsUnsupported() {
        provider.failQueries(new SQLException("Permission denied: HiveAccessControlException", "42000", 0));

        var result = provider.explain("SELECT * FROM orders", testConn(), "pw", null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<ExplainPlan>) result).reason())
            .isEqualTo("EXPLAIN not supported: Hive permission denied");
    }

    @Test
    void indexHints_returnsUnsupported() {
        var result = provider.indexHints("SELECT * FROM t", null, null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<?>) result).reason())
            .isEqualTo("Index hints not supported for Hive (partitioning only)");
    }

    @Test
    void registryFindsProvider_for_hive_kind() {
        var registry = new DiagnosticsProviderRegistry(List.of(provider));
        assertThat(registry.find("hive")).isPresent();
        assertThat(registry.find("hive").get()).isSameAs(provider);
    }

    @Test
    void otherCapabilitiesReturnUnsupported() {
        assertUnsupported(provider.lockInfo(null, null, null), "Hive lock info is not supported");
        assertUnsupported(provider.poolStatus(null, null), "Hive connection pool info is not supported");
        assertUnsupported(provider.tableSpaceInfo(null, null, null, List.of()), "Hive table space info is not supported");
        assertUnsupported(provider.terminateSessionPreview(null, null, "1", null), "Hive session termination is not supported");
        assertUnsupported(provider.terminateSession(null, null, "1", null), "Hive session termination is not supported");
        assertUnsupported(provider.optimizeTablePreview(null, null, "users", null, null), "Hive table optimization is not supported");
        assertUnsupported(provider.optimizeTable(null, null, "users", null, null), "Hive table optimization is not supported");
    }

    private static void assertUnsupported(DiagnosticResult<?> result, String reason) {
        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<?>) result).reason()).isEqualTo(reason);
    }

    private ConnectionRecord testConn() {
        return new ConnectionRecord(
            "c1", "test", "hive", "localhost", 10000,
            "default", "user", new byte[0], null, 0L, 5000, null, null,
            null, null, null, null, false);
    }

    private Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.warning.hive_partition_check", Locale.ENGLISH, "Hive partition check: verify partition column usage");
        source.addMessage("diagnostics.index_hints.unsupported.hive", Locale.ENGLISH, "Index hints not supported for Hive (partitioning only)");
        source.addMessage("diagnostics.explain.unsupported.hive_permission", Locale.ENGLISH, "EXPLAIN not supported: Hive permission denied");
        source.addMessage("diagnostics.lock.unsupported.hive", Locale.ENGLISH, "Hive lock info is not supported");
        source.addMessage("diagnostics.pool.unsupported.hive", Locale.ENGLISH, "Hive connection pool info is not supported");
        source.addMessage("diagnostics.space.unsupported.hive", Locale.ENGLISH, "Hive table space info is not supported");
        source.addMessage("diagnostics.terminate.unsupported.hive", Locale.ENGLISH, "Hive session termination is not supported");
        source.addMessage("diagnostics.optimize.unsupported.hive", Locale.ENGLISH, "Hive table optimization is not supported");
        return new Translator(source);
    }

    static class TestableHiveDiagnosticsProvider extends HiveDiagnosticsProvider {
        private List<Map<String, Object>> mockRows;
        private SQLException queryFailure;
        private final Path fixturesDir = Paths.get("src/test/resources/diagnostics/hive");

        TestableHiveDiagnosticsProvider(Translator translator) {
            super(translator);
        }

        void loadFixture(String filename) {
            try {
                Path path = fixturesDir.resolve(filename);
                String content = Files.readString(path, StandardCharsets.UTF_8);
                // Hive EXPLAIN returns rows where each row contains the plan text
                var row = new LinkedHashMap<String, Object>();
                row.put("plan", content);
                this.mockRows = List.of(row);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load fixture: " + filename, e);
            }
        }

        void failQueries(SQLException failure) {
            this.queryFailure = failure;
        }

        @Override
        protected List<Map<String, Object>> queryForList(ConnectionRecord conn, String decryptedPassword, String sql, Object... params) throws SQLException {
            if (queryFailure != null) throw queryFailure;
            if (mockRows != null) return mockRows;
            return List.of();
        }
    }
}