package com.datatalk.infra.diagnostics;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ClickHouseDiagnosticsProviderTest {

    private TestableClickHouseDiagnosticsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TestableClickHouseDiagnosticsProvider(translator());
    }

    @Test
    void supportedDriverTypes_containsClickhouse() {
        assertThat(provider.supportedDriverTypes()).containsExactly("clickhouse");
    }

    @Test
    void supportedCapabilities_explainOnly() {
        assertThat(provider.supportedCapabilities()).containsExactly(DiagnosticCapability.EXPLAIN);
    }

    @Test
    void explain_readFromMergeTreeAllGranules_marksFullScan() {
        provider.setRows(wrapText(fixture("primary_key_full_scan.txt")));
        var plan = ((DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT *", testConn(), "", null, null)).value();
        assertThat(findFirstByOperator(plan.nodes(), "ReadFromMergeTree").scanType()).isEqualTo(ScanType.FULL_SCAN);
        assertThat(plan.dialect()).isEqualTo("clickhouse");
    }

    @Test
    void explain_readFromMergeTreePartialGranules_marksIndexRange() {
        provider.setRows(wrapText(fixture("primary_key_partial.txt")));
        var plan = ((DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT * WHERE user_id=42", testConn(), "", null, null)).value();
        assertThat(findFirstByOperator(plan.nodes(), "ReadFromMergeTree").scanType()).isEqualTo(ScanType.INDEX_RANGE);
    }

    @Test
    void explain_emptyPlan_returnsOk() {
        provider.setRows(List.of(Map.of("explain", "")));
        var plan = ((DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT 1", testConn(), "", null, null)).value();
        assertThat(plan.nodes()).isEmpty();
    }

    @Test
    void explain_unknownOperator_mapsToOther() {
        provider.setRows(wrapText(fixture("aggregation.txt")));
        var plan = ((DiagnosticResult.Ok<ExplainPlan>) provider.explain("SELECT count(*)", testConn(), "", null, null)).value();
        assertThat(plan.nodes().get(0).scanType()).isEqualTo(ScanType.OTHER);
    }

    @Test
    void explain_accessDenied_returnsUnsupportedWithClickhouseReason() {
        provider.failQueryWith(new SQLException("Code: 497. ACCESS_DENIED", "00000", 497));
        var result = provider.explain("SELECT 1", testConn(), "", null, null);
        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
    }

    @Test
    void indexHints_alwaysReturnsUnsupportedWithClickhouseReason() {
        var plan = new ExplainPlan("clickhouse", "", List.of(
            new ExplainNode("ReadFromMergeTree", "default.orders", ScanType.FULL_SCAN, 819200L, null, null, List.of())
        ), null, List.of());
        var result = provider.indexHints("SELECT * FROM orders", plan, testConn(), "");
        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<?>) result).reason()).contains("ORDER BY");
    }

    private ConnectionRecord testConn() {
        return new ConnectionRecord(
            "c1", "test", "clickhouse", "localhost", 9000,
            "default", "user", new byte[0], null, 0L, 5000, null, null,
            null, 1, true, null, false, null, null, null);
    }

    private Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.index_hints.unsupported.clickhouse", Locale.ENGLISH,
            "ClickHouse index hints are based on ORDER BY and data skipping indices, not traditional B-tree indexes");
        source.addMessage("diagnostics.explain.unsupported.clickhouse", Locale.ENGLISH,
            "EXPLAIN requires sufficient privileges");
        return new Translator(source);
    }

    private ExplainNode findFirstByOperator(List<ExplainNode> nodes, String operator) {
        for (ExplainNode n : nodes) {
            if (n.operation().equals(operator)) return n;
            ExplainNode child = findFirstByOperator(n.children(), operator);
            if (child != null) return child;
        }
        return null;
    }

    private List<Map<String, Object>> wrapText(String text) {
        var row = new LinkedHashMap<String, Object>();
        row.put("explain", text);
        return List.of(row);
    }

    private String fixture(String name) {
        try {
            Path path = Path.of("server/data-talk-infrastructure/src/test/resources/diagnostics/clickhouse/" + name);
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + name, e);
        }
    }

    static class TestableClickHouseDiagnosticsProvider extends ClickHouseDiagnosticsProvider {
        private List<Map<String, Object>> mockRows;
        private SQLException queryFailure;

        TestableClickHouseDiagnosticsProvider(Translator translator) {
            super(translator);
        }

        void setRows(List<Map<String, Object>> rows) {
            this.mockRows = rows;
        }

        void failQueryWith(SQLException failure) {
            this.queryFailure = failure;
        }

        @Override
        protected List<Map<String, Object>> queryForList(ConnectionRecord conn, String decryptedPassword, String sql, Object... params) throws SQLException {
            if (queryFailure != null) throw queryFailure;
            return mockRows != null ? mockRows : List.of();
        }
    }
}