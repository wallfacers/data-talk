package com.datatalk.infra.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

class MySqlDiagnosticsProviderTest {

    private MySqlDiagnosticsProvider provider;

    @BeforeEach
    void setUp() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.mysql.explain-failed", Locale.ENGLISH, "EXPLAIN failed");
        provider = new MySqlDiagnosticsProvider(new Translator(source));
    }

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

        var result = provider.indexHints("SELECT * FROM orders WHERE status = 'pending'", plan, null, null);
        assertThat(result.isOk()).isTrue();

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).impact()).isEqualTo(Impact.HIGH);
        assertThat(recs.get(0).table()).isEqualTo("orders");
        assertThat(recs.get(0).columns()).containsExactly("status");
    }

    @Test
    void indexHints_fullScanBelow1000Rows_givesMediumImpact() {
        ExplainNode fullScanNode = new ExplainNode("all", "small_table", ScanType.FULL_SCAN, 100L, null, null, List.of());
        ExplainPlan plan = new ExplainPlan("mysql", "...", List.of(fullScanNode), null, List.of());

        var result = provider.indexHints("SELECT * FROM small_table WHERE active = true", plan, null, null);
        assertThat(result.isOk()).isTrue();

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).impact()).isEqualTo(Impact.MEDIUM);
        assertThat(recs.get(0).columns()).containsExactly("active");
    }

    @Test
    void indexHints_fullScanNoWhereClause_suppressesRecommendation() {
        ExplainNode fullScanNode = new ExplainNode("all", "orders", ScanType.FULL_SCAN, 5000L, null, null, List.of());
        ExplainPlan plan = new ExplainPlan("mysql", "...", List.of(fullScanNode), null, List.of());

        var result = provider.indexHints("SELECT * FROM orders", plan, null, null);
        assertThat(result.isOk()).isTrue();

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).isEmpty();
    }

    @Test
    void indexHints_qualifiedColumnsWithAlias_extracted() {
        ExplainNode fullScanNode = new ExplainNode("all", "orders", ScanType.FULL_SCAN, 2000L, null, null, List.of());
        ExplainPlan plan = new ExplainPlan("mysql", "...", List.of(fullScanNode), null, List.of());

        var result = provider.indexHints("SELECT * FROM orders o WHERE o.status = 'active' AND o.total > 100", plan, null, null);
        assertThat(result.isOk()).isTrue();

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).columns()).containsExactly("status", "total");
    }

    @Test
    void withDatabaseOverride_usesOverrideWhenProvided() throws Exception {
        ConnectionRecord conn = testConn("base_db");

        ConnectionRecord overridden = invokeWithDatabaseOverride(conn, "analytics");

        assertThat(overridden.databaseName()).isEqualTo("analytics");
        assertThat(overridden.id()).isEqualTo(conn.id());
        assertThat(overridden.username()).isEqualTo(conn.username());
    }

    @Test
    void withDatabaseOverride_keepsOriginalWhenOverrideBlank() throws Exception {
        ConnectionRecord conn = testConn("base_db");

        ConnectionRecord overridden = invokeWithDatabaseOverride(conn, " ");

        assertThat(overridden).isEqualTo(conn);
    }

    private ScanType invokeMapAccessType(String accessType) throws Exception {
        Method method = MySqlDiagnosticsProvider.class.getDeclaredMethod("mapAccessType", String.class);
        method.setAccessible(true);
        return (ScanType) method.invoke(provider, accessType);
    }

    private ConnectionRecord invokeWithDatabaseOverride(ConnectionRecord conn, String database) throws Exception {
        Method method = MySqlDiagnosticsProvider.class.getDeclaredMethod(
            "withDatabaseOverride", ConnectionRecord.class, String.class);
        method.setAccessible(true);
        return (ConnectionRecord) method.invoke(provider, conn, database);
    }

    private ConnectionRecord testConn(String databaseName) {
        return new ConnectionRecord(
            "c1", "test", "mysql", "localhost", 3306,
            databaseName, "user", new byte[0], null, 0L, 5000, null, null
        );
    }
}
