package com.datatalk.infra.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
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

        var result = provider.indexHints("SELECT * FROM orders WHERE status = 'pending'", plan, null, null);
        assertThat(result.isOk()).isTrue();

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).impact()).isEqualTo(Impact.HIGH);
        assertThat(recs.get(0).table()).isEqualTo("orders");
        assertThat(recs.get(0).columns()).containsExactly("status");
    }

    @Test
    void indexHints_seqScanBelow1000Rows_givesMediumImpact() {
        ExplainNode seqScanNode = new ExplainNode("Seq Scan", "small_table", ScanType.FULL_SCAN, 100L, null, null, List.of());
        ExplainPlan plan = new ExplainPlan("postgresql", "...", List.of(seqScanNode), null, List.of());

        var result = provider.indexHints("SELECT * FROM small_table WHERE active = true", plan, null, null);
        assertThat(result.isOk()).isTrue();

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).impact()).isEqualTo(Impact.MEDIUM);
        assertThat(recs.get(0).columns()).containsExactly("active");
    }

    @Test
    void indexHints_seqScanNoWhereClause_suppressesRecommendation() {
        ExplainNode seqScanNode = new ExplainNode("Seq Scan", "orders", ScanType.FULL_SCAN, 5000L, null, null, List.of());
        ExplainPlan plan = new ExplainPlan("postgresql", "...", List.of(seqScanNode), null, List.of());

        var result = provider.indexHints("SELECT * FROM orders", plan, null, null);
        assertThat(result.isOk()).isTrue();

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).isEmpty();
    }

    @Test
    void indexHints_qualifiedColumnsWithAlias_extracted() {
        ExplainNode seqScanNode = new ExplainNode("Seq Scan", "orders", ScanType.FULL_SCAN, 2000L, null, null, List.of());
        ExplainPlan plan = new ExplainPlan("postgresql", "...", List.of(seqScanNode), null, List.of());

        var result = provider.indexHints("SELECT * FROM orders o WHERE o.status = 'active' AND o.total > 100", plan, null, null);
        assertThat(result.isOk()).isTrue();

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).columns()).containsExactly("status", "total");
    }

    @Test
    void withDatabaseOverride_usesOverrideWhenProvided() throws Exception {
        ConnectionRecord conn = testConn("postgres");

        ConnectionRecord overridden = invokeWithDatabaseOverride(conn, "analytics");

        assertThat(overridden.databaseName()).isEqualTo("analytics");
        assertThat(overridden.id()).isEqualTo(conn.id());
    }

    @Test
    void applySchema_executesSetSearchPathWhenSchemaProvided() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);

        invokeApplySchema(connection, "public");

        verify(statement).execute("SET search_path TO public");
        verify(statement).close();
    }

    @Test
    void applySchema_noopWhenSchemaBlank() throws Exception {
        Connection connection = mock(Connection.class);

        invokeApplySchema(connection, " ");

        verify(connection, never()).createStatement();
    }

    private ScanType invokeMapNodeType(String nodeType) throws Exception {
        Method method = PostgreSqlDiagnosticsProvider.class.getDeclaredMethod("mapNodeType", String.class);
        method.setAccessible(true);
        return (ScanType) method.invoke(provider, nodeType);
    }

    private ConnectionRecord invokeWithDatabaseOverride(ConnectionRecord conn, String database) throws Exception {
        Method method = PostgreSqlDiagnosticsProvider.class.getDeclaredMethod(
            "withDatabaseOverride", ConnectionRecord.class, String.class);
        method.setAccessible(true);
        return (ConnectionRecord) method.invoke(provider, conn, database);
    }

    private void invokeApplySchema(Connection connection, String schema) throws Exception {
        Method method = PostgreSqlDiagnosticsProvider.class.getDeclaredMethod(
            "applySchema", Connection.class, String.class);
        method.setAccessible(true);
        method.invoke(provider, connection, schema);
    }

    private ConnectionRecord testConn(String databaseName) {
        return new ConnectionRecord(
            "c1", "test", "postgresql", "localhost", 5432,
            databaseName, "user", new byte[0], null, 0L, 5000, null, null
        );
    }
}
