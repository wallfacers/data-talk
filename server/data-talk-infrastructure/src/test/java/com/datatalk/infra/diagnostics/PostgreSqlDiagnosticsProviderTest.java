package com.datatalk.infra.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import com.datatalk.domain.diagnostics.*;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PostgreSqlDiagnosticsProviderTest {

    private final PostgreSqlDiagnosticsProvider provider = new PostgreSqlDiagnosticsProvider();

    @Test
    void supportedDriverTypes_containsBothPostgresqlAndPostgres() {
        assertThat(provider.supportedDriverTypes())
            .containsExactlyInAnyOrder("postgresql", "postgres");
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
    void mapNodeType_seqScanMapsToFullScan() throws Exception {
        assertThat(invokeMapNodeType("Seq Scan")).isEqualTo(ScanType.FULL_SCAN);
    }

    @Test
    void mapNodeType_indexScanMapsToIndexScan() throws Exception {
        assertThat(invokeMapNodeType("Index Scan")).isEqualTo(ScanType.INDEX_SCAN);
    }

    @Test
    void mapNodeType_indexOnlyScanMapsToIndexScan() throws Exception {
        assertThat(invokeMapNodeType("Index Only Scan")).isEqualTo(ScanType.INDEX_SCAN);
    }

    @Test
    void mapNodeType_bitmapIndexScanMapsToIndexRange() throws Exception {
        assertThat(invokeMapNodeType("Bitmap Index Scan")).isEqualTo(ScanType.INDEX_RANGE);
    }

    @Test
    void mapNodeType_bitmapHeapScanMapsToIndexRange() throws Exception {
        assertThat(invokeMapNodeType("Bitmap Heap Scan")).isEqualTo(ScanType.INDEX_RANGE);
    }

    @Test
    void mapNodeType_otherMapsToOther() throws Exception {
        assertThat(invokeMapNodeType("Hash Join")).isEqualTo(ScanType.OTHER);
    }

    @Test
    void indexHints_seqScanAbove1000Rows_givesHighImpact() {
        ExplainNode seqScanNode = new ExplainNode("Seq Scan", "orders", ScanType.FULL_SCAN, 5000L, null, null, List.of());
        ExplainPlan plan = new ExplainPlan("postgresql", "...", List.of(seqScanNode), null, List.of());

        var result = provider.indexHints("SELECT * FROM orders", plan, null, null);
        assertThat(result.isOk()).isTrue();

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).impact()).isEqualTo(Impact.HIGH);
        assertThat(recs.get(0).table()).isEqualTo("orders");
    }

    @Test
    void indexHints_seqScanBelow1000Rows_givesMediumImpact() {
        ExplainNode seqScanNode = new ExplainNode("Seq Scan", "small_table", ScanType.FULL_SCAN, 100L, null, null, List.of());
        ExplainPlan plan = new ExplainPlan("postgresql", "...", List.of(seqScanNode), null, List.of());

        var result = provider.indexHints("SELECT * FROM small_table", plan, null, null);
        assertThat(result.isOk()).isTrue();

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).impact()).isEqualTo(Impact.MEDIUM);
    }

    private ScanType invokeMapNodeType(String nodeType) throws Exception {
        Method method = PostgreSqlDiagnosticsProvider.class.getDeclaredMethod("mapNodeType", String.class);
        method.setAccessible(true);
        return (ScanType) method.invoke(provider, nodeType);
    }
}
