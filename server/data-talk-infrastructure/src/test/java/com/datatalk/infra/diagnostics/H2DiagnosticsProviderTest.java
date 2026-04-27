package com.datatalk.infra.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import com.datatalk.domain.diagnostics.*;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class H2DiagnosticsProviderTest {

    private final H2DiagnosticsProvider provider = new H2DiagnosticsProvider();

    @Test
    void supportedDriverTypes_returnsH2() {
        assertThat(provider.supportedDriverTypes()).containsExactly("h2");
    }

    @Test
    void supportedCapabilities_returnsExplainAndIndexHints() {
        assertThat(provider.supportedCapabilities())
            .containsExactlyInAnyOrder(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS);
    }

    @Test
    void parseH2Text_withTableScanPattern_returnsFullScanNodes() throws Exception {
        String text = "SELECT\n    FROM PUBLIC.ORDERS /* PUBLIC.tableScan */";

        List<ExplainNode> nodes = invokeParseH2Text(text);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).scanType()).isEqualTo(ScanType.FULL_SCAN);
        assertThat(nodes.get(0).table()).isEqualTo("PUBLIC.ORDERS");
    }

    @Test
    void parseH2Text_withIndexPattern_returnsIndexScanNodes() throws Exception {
        String text = "SELECT\n    FROM PUBLIC.ORDERS /* PUBLIC.IDX_ORDER_ID:PK */";

        List<ExplainNode> nodes = invokeParseH2Text(text);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).scanType()).isEqualTo(ScanType.INDEX_SCAN);
        assertThat(nodes.get(0).table()).isEqualTo("PUBLIC.IDX_ORDER_ID");
    }

    @Test
    void parseH2Text_withNoMatchingPatterns_returnsEmptyList() throws Exception {
        String text = "some random text without explain patterns";

        List<ExplainNode> nodes = invokeParseH2Text(text);

        assertThat(nodes).isEmpty();
    }

    @Test
    void indexHints_nonFullScanReturnsEmptyRecommendations() {
        ExplainNode indexScanNode = new ExplainNode("INDEX_SCAN", "orders", ScanType.INDEX_SCAN, 0L, null, null, List.of());
        ExplainPlan plan = new ExplainPlan("h2", "...", List.of(indexScanNode), null, List.of());

        var result = provider.indexHints("SELECT * FROM orders", plan, null, null);
        assertThat(result.isOk()).isTrue();

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).isEmpty();
    }

    @Test
    void indexHints_fullScanReturnsMediumImpact() {
        ExplainNode fullScanNode = new ExplainNode("TABLE_SCAN", "orders", ScanType.FULL_SCAN, 0L, null, null, List.of());
        ExplainPlan plan = new ExplainPlan("h2", "...", List.of(fullScanNode), null, List.of());

        var result = provider.indexHints("SELECT * FROM orders", plan, null, null);
        assertThat(result.isOk()).isTrue();

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).impact()).isEqualTo(Impact.MEDIUM);
    }

    @SuppressWarnings("unchecked")
    private List<ExplainNode> invokeParseH2Text(String text) throws Exception {
        Method method = H2DiagnosticsProvider.class.getDeclaredMethod("parseH2Text", String.class);
        method.setAccessible(true);
        return (List<ExplainNode>) method.invoke(provider, text);
    }
}
