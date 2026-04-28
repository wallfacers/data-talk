package com.datatalk.infra.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

class H2DiagnosticsProviderTest {

    private H2DiagnosticsProvider provider;

    @BeforeEach
    void setUp() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.h2.explain-failed", Locale.ENGLISH, "EXPLAIN failed");
        provider = new H2DiagnosticsProvider(new Translator(source));
    }

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

        var result = provider.indexHints("SELECT * FROM orders WHERE status = 'pending'", plan, null, null);
        assertThat(result.isOk()).isTrue();

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).impact()).isEqualTo(Impact.MEDIUM);
        assertThat(recs.get(0).columns()).containsExactly("status");
    }

    @Test
    void indexHints_fullScanNoWhereClause_suppressesRecommendation() {
        ExplainNode fullScanNode = new ExplainNode("TABLE_SCAN", "orders", ScanType.FULL_SCAN, 0L, null, null, List.of());
        ExplainPlan plan = new ExplainPlan("h2", "...", List.of(fullScanNode), null, List.of());

        var result = provider.indexHints("SELECT * FROM orders", plan, null, null);
        assertThat(result.isOk()).isTrue();

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).isEmpty();
    }

    @Test
    void indexHints_qualifiedColumnsWithSchema_extracted() {
        ExplainNode fullScanNode = new ExplainNode("TABLE_SCAN", "PUBLIC.ORDERS", ScanType.FULL_SCAN, 0L, null, null, List.of());
        ExplainPlan plan = new ExplainPlan("h2", "...", List.of(fullScanNode), null, List.of());

        var result = provider.indexHints("SELECT * FROM PUBLIC.ORDERS WHERE STATUS = 'ACTIVE'", plan, null, null);
        assertThat(result.isOk()).isTrue();

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).columns()).containsExactly("STATUS");
    }

    @Test
    void withDatabaseOverride_usesOverrideWhenProvided() throws Exception {
        ConnectionRecord conn = testConn("mem:test");

        ConnectionRecord overridden = invokeWithDatabaseOverride(conn, "mem:analytics");

        assertThat(overridden.databaseName()).isEqualTo("mem:analytics");
        assertThat(overridden.id()).isEqualTo(conn.id());
    }

    @Test
    void applySchema_executesSetSchemaWhenSchemaProvided() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);

        invokeApplySchema(connection, "PUBLIC");

        verify(statement).execute("SET SCHEMA PUBLIC");
        verify(statement).close();
    }

    @Test
    void applySchema_noopWhenSchemaBlank() throws Exception {
        Connection connection = mock(Connection.class);

        invokeApplySchema(connection, "");

        verify(connection, never()).createStatement();
    }

    @SuppressWarnings("unchecked")
    private List<ExplainNode> invokeParseH2Text(String text) throws Exception {
        Method method = H2DiagnosticsProvider.class.getDeclaredMethod("parseH2Text", String.class);
        method.setAccessible(true);
        return (List<ExplainNode>) method.invoke(provider, text);
    }

    private ConnectionRecord invokeWithDatabaseOverride(ConnectionRecord conn, String database) throws Exception {
        Method method = H2DiagnosticsProvider.class.getDeclaredMethod(
            "withDatabaseOverride", ConnectionRecord.class, String.class);
        method.setAccessible(true);
        return (ConnectionRecord) method.invoke(provider, conn, database);
    }

    private void invokeApplySchema(Connection connection, String schema) throws Exception {
        Method method = H2DiagnosticsProvider.class.getDeclaredMethod(
            "applySchema", Connection.class, String.class);
        method.setAccessible(true);
        method.invoke(provider, connection, schema);
    }

    private ConnectionRecord testConn(String databaseName) {
        return new ConnectionRecord(
            "c1", "test", "h2", "localhost", 0,
            databaseName, "sa", new byte[0], null, 0L, 5000, null, null
        );
    }
}
