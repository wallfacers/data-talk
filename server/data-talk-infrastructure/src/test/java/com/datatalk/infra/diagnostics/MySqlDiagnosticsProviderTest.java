package com.datatalk.infra.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import com.datatalk.domain.diagnostics.*;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MySqlDiagnosticsProviderTest {

    private final MySqlDiagnosticsProvider provider = new MySqlDiagnosticsProvider();

    @Test
    void supportedDriverTypes_returnsMysql() {
        assertThat(provider.supportedDriverTypes()).containsExactly("mysql");
    }

    @Test
    void supportedCapabilities_returnsExplainAndIndexHints() {
        assertThat(provider.supportedCapabilities())
            .containsExactlyInAnyOrder(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS);
    }

    @Test
    void lockInfo_returnsUnsupported() {
        var result = provider.lockInfo(null, null, null);
        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void mapAccessType_allMapsToFullScan() throws Exception {
        assertThat(invokeMapAccessType("all")).isEqualTo(ScanType.FULL_SCAN);
    }

    @Test
    void mapAccessType_rangeMapsToIndexRange() throws Exception {
        assertThat(invokeMapAccessType("range")).isEqualTo(ScanType.INDEX_RANGE);
    }

    @Test
    void mapAccessType_refMapsToRef() throws Exception {
        assertThat(invokeMapAccessType("ref")).isEqualTo(ScanType.REF);
    }

    @Test
    void mapAccessType_eqRefMapsToRef() throws Exception {
        assertThat(invokeMapAccessType("eq_ref")).isEqualTo(ScanType.REF);
    }

    @Test
    void mapAccessType_indexMapsToIndexScan() throws Exception {
        assertThat(invokeMapAccessType("index")).isEqualTo(ScanType.INDEX_SCAN);
    }

    @Test
    void mapAccessType_constMapsToConst() throws Exception {
        assertThat(invokeMapAccessType("const")).isEqualTo(ScanType.CONST);
    }

    @Test
    void mapAccessType_systemMapsToConst() throws Exception {
        assertThat(invokeMapAccessType("system")).isEqualTo(ScanType.CONST);
    }

    @Test
    void mapAccessType_unknownMapsToOther() throws Exception {
        assertThat(invokeMapAccessType("something_else")).isEqualTo(ScanType.OTHER);
    }

    @Test
    void indexHints_fullScanAbove1000Rows_givesHighImpact() {
        ExplainNode fullScanNode = new ExplainNode("all", "orders", ScanType.FULL_SCAN, 5000L, null, null, List.of());
        ExplainPlan plan = new ExplainPlan("mysql", "...", List.of(fullScanNode), null, List.of());

        var result = provider.indexHints("SELECT * FROM orders", plan, null, null);
        assertThat(result.isOk()).isTrue();

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).impact()).isEqualTo(Impact.HIGH);
        assertThat(recs.get(0).table()).isEqualTo("orders");
    }

    @Test
    void indexHints_fullScanBelow1000Rows_givesMediumImpact() {
        ExplainNode fullScanNode = new ExplainNode("all", "small_table", ScanType.FULL_SCAN, 100L, null, null, List.of());
        ExplainPlan plan = new ExplainPlan("mysql", "...", List.of(fullScanNode), null, List.of());

        var result = provider.indexHints("SELECT * FROM small_table", plan, null, null);
        assertThat(result.isOk()).isTrue();

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).impact()).isEqualTo(Impact.MEDIUM);
    }

    private ScanType invokeMapAccessType(String accessType) throws Exception {
        Method method = MySqlDiagnosticsProvider.class.getDeclaredMethod("mapAccessType", String.class);
        method.setAccessible(true);
        return (ScanType) method.invoke(provider, accessType);
    }
}
