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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SqlServerDiagnosticsProviderTest {

    private Translator translator;
    private ConnectionRecord testConn;

    @BeforeEach
    void setUp() {
        translator = translator();
        testConn = new ConnectionRecord(
            "c1", "test", "sqlserver", "localhost", 1433,
            "testdb", "sa", new byte[0], null, 0L, 5000, null, null,
            null, 1, true, null, false, null, null, null);
    }

    @Test
    void supportedDriverTypes_contains_sqlserver() {
        SqlServerDiagnosticsProvider provider = new SqlServerDiagnosticsProvider(translator);
        assertThat(provider.supportedDriverTypes()).containsExactly("sqlserver");
    }

    @Test
    void supportedCapabilities_containsExplainAndIndexHints() {
        SqlServerDiagnosticsProvider provider = new SqlServerDiagnosticsProvider(translator);
        assertThat(provider.supportedCapabilities())
            .containsExactlyInAnyOrder(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS);
    }

    @Test
    void registryFindsProvider_for_sqlserver_kind() {
        SqlServerDiagnosticsProvider provider = new SqlServerDiagnosticsProvider(translator);
        var registry = new DiagnosticsProviderRegistry(List.of(provider));
        assertThat(registry.find("sqlserver")).isPresent();
        assertThat(registry.find("sqlserver").get()).isSameAs(provider);
    }

    @Test
    void explain_tableScan_marksFullScan() throws Exception {
        String xml = loadFixture("table_scan.xml");
        SqlServerDiagnosticsProvider provider = new TestableSqlServerProvider(translator, xml, null);

        var result = provider.explain("SELECT * FROM orders", testConn, "pw", "testdb", "dbo");

        assertThat(result.isOk()).isTrue();
        ExplainPlan plan = ((DiagnosticResult.Ok<ExplainPlan>) result).value();
        assertThat(plan.dialect()).isEqualTo("sqlserver");
        assertThat(plan.nodes()).hasSize(1);
        assertThat(plan.nodes().get(0).operation()).isEqualTo("Table Scan");
        assertThat(plan.nodes().get(0).scanType()).isEqualTo(ScanType.FULL_SCAN);
        assertThat(plan.nodes().get(0).table()).isEqualTo("orders");
        assertThat(plan.nodes().get(0).rows()).isEqualTo(10000L);
        assertThat(plan.warnings()).hasSize(1);
        assertThat(plan.warnings().get(0)).contains("orders");
    }

    @Test
    void explain_indexSeek_marksIndexRange() throws Exception {
        String xml = loadFixture("index_seek.xml");
        SqlServerDiagnosticsProvider provider = new TestableSqlServerProvider(translator, xml, null);

        var result = provider.explain("SELECT * FROM customers WHERE email = 'test@example.com'", testConn, "pw", "testdb", "dbo");

        assertThat(result.isOk()).isTrue();
        ExplainPlan plan = ((DiagnosticResult.Ok<ExplainPlan>) result).value();
        assertThat(plan.nodes()).hasSize(1);
        assertThat(plan.nodes().get(0).operation()).isEqualTo("Index Seek");
        assertThat(plan.nodes().get(0).scanType()).isEqualTo(ScanType.INDEX_RANGE);
        assertThat(plan.nodes().get(0).table()).isEqualTo("customers");
        assertThat(plan.warnings()).isEmpty();
    }

    @Test
    void explain_hashJoin_withNestedTableScanAndIndexSeek() throws Exception {
        String xml = loadFixture("hash_join.xml");
        SqlServerDiagnosticsProvider provider = new TestableSqlServerProvider(translator, xml, null);

        var result = provider.explain("SELECT * FROM orders o JOIN customers c ON o.customer_id = c.id", testConn, "pw", "testdb", "dbo");

        assertThat(result.isOk()).isTrue();
        ExplainPlan plan = ((DiagnosticResult.Ok<ExplainPlan>) result).value();
        assertThat(plan.nodes()).hasSize(1);
        // Root is Hash Match
        assertThat(plan.nodes().get(0).operation()).isEqualTo("Hash Match");
        // Two children: Table Scan (customers) + Index Seek (orders)
        assertThat(plan.nodes().get(0).children()).hasSize(2);
        assertThat(plan.nodes().get(0).children().get(0).operation()).isEqualTo("Table Scan");
        assertThat(plan.nodes().get(0).children().get(0).scanType()).isEqualTo(ScanType.FULL_SCAN);
        assertThat(plan.nodes().get(0).children().get(1).operation()).isEqualTo("Index Seek");
        assertThat(plan.nodes().get(0).children().get(1).scanType()).isEqualTo(ScanType.INDEX_RANGE);
    }

    @Test
    void explain_nestedLoops_withIndexSeeks() throws Exception {
        String xml = loadFixture("nested.xml");
        SqlServerDiagnosticsProvider provider = new TestableSqlServerProvider(translator, xml, null);

        var result = provider.explain("SELECT * FROM customers c JOIN orders o ON c.id = o.customer_id WHERE c.id = 1", testConn, "pw", "testdb", "dbo");

        assertThat(result.isOk()).isTrue();
        ExplainPlan plan = ((DiagnosticResult.Ok<ExplainPlan>) result).value();
        assertThat(plan.nodes()).hasSize(1);
        assertThat(plan.nodes().get(0).operation()).isEqualTo("Nested Loops");
        assertThat(plan.nodes().get(0).children()).hasSize(2);
        assertThat(plan.nodes().get(0).children().get(0).scanType()).isEqualTo(ScanType.INDEX_RANGE);
        assertThat(plan.nodes().get(0).children().get(1).scanType()).isEqualTo(ScanType.INDEX_RANGE);
    }

    @Test
    void explain_permissionDenied_returnsUnsupported() throws Exception {
        SQLException permissionDenied = new SQLException("SHOWPLAN permission denied", "42000", 262);
        SqlServerDiagnosticsProvider provider = new TestableSqlServerProvider(translator, null, permissionDenied);

        var result = provider.explain("SELECT * FROM orders", testConn, "pw", "testdb", "dbo");

        assertThat(result.isUnsupported()).isTrue();
        assertThat(((DiagnosticResult.Unsupported<ExplainPlan>) result).reason()).contains("permission");
    }

    @Test
    void explain_corruptXml_returnsExplainParseError() throws Exception {
        String corruptXml = loadFixture("corrupt.xml");
        SqlServerDiagnosticsProvider provider = new TestableSqlServerProvider(translator, corruptXml, null);

        var result = provider.explain("SELECT * FROM orders", testConn, "pw", "testdb", "dbo");

        assertThat(result).isInstanceOf(DiagnosticResult.DiagnosticError.class);
        DiagnosticResult.DiagnosticError<ExplainPlan> error = (DiagnosticResult.DiagnosticError<ExplainPlan>) result;
        assertThat(error.errorType()).isEqualTo("EXPLAIN_PARSE_ERROR");
        assertThat(error.message()).contains("Failed to parse SHOWPLAN XML");
    }

    @Test
    void explain_emptyPlan_returnsEmptyNodes() throws Exception {
        String xml = loadFixture("empty.xml");
        SqlServerDiagnosticsProvider provider = new TestableSqlServerProvider(translator, xml, null);

        var result = provider.explain("SELECT 1", testConn, "pw", "testdb", "dbo");

        assertThat(result.isOk()).isTrue();
        ExplainPlan plan = ((DiagnosticResult.Ok<ExplainPlan>) result).value();
        assertThat(plan.nodes()).isEmpty();
    }

    @Test
    void explain_emptyResultSet_returnsEmptyNodes() throws Exception {
        SqlServerDiagnosticsProvider provider = new TestableSqlServerProvider(translator, "", null);

        var result = provider.explain("SELECT 1", testConn, "pw", "testdb", "dbo");

        assertThat(result.isOk()).isTrue();
        ExplainPlan plan = ((DiagnosticResult.Ok<ExplainPlan>) result).value();
        assertThat(plan.nodes()).isEmpty();
    }

    @Test
    void indexHints_tableScan_recommendations() throws Exception {
        String xml = loadFixture("table_scan.xml");
        SqlServerDiagnosticsProvider provider = new TestableSqlServerProvider(translator, xml, null);

        // First get the explain plan
        var explainResult = provider.explain("SELECT * FROM orders WHERE status = 'pending'", testConn, "pw", "testdb", "dbo");
        assertThat(explainResult.isOk()).isTrue();
        ExplainPlan plan = ((DiagnosticResult.Ok<ExplainPlan>) explainResult).value();

        // Now get index hints
        var indexResult = provider.indexHints("SELECT * FROM orders WHERE status = 'pending'", plan, testConn, "pw");

        assertThat(indexResult.isOk()).isTrue();
        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) indexResult).value();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).table()).isEqualTo("orders");
        assertThat(recs.get(0).columns()).contains("status");
        assertThat(recs.get(0).indexType()).isEqualTo("BTREE");
        // rows=10000 > 1000, so HIGH impact
        assertThat(recs.get(0).impact()).isEqualTo(Impact.HIGH);
    }

    @Test
    void indexHints_tableScanMediumRows_mediumImpact() throws Exception {
        // Create a plan with medium row count
        String xml = """
            <?xml version="1.0" encoding="utf-16"?>
            <ShowPlanXML xmlns="http://schemas.microsoft.com/sqlserver/2004/07/showplan" Version="1.539" Build="16.0.4131.2">
              <BatchSequence><Batch><Statements><StmtSimple StatementCompId="1">
                <QueryPlan>
                  <RelOp NodeId="0" PhysicalOp="Table Scan" LogicalOp="Table Scan" EstimateRows="500" EstimatedTotalSubtreeCost="0.3">
                    <OutputList />
                    <RunTimeInformation />
                    <TableScan>
                      <DefinedValues />
                      <Object Database="[testdb]" Schema="[dbo]" Table="[products]" />
                    </TableScan>
                  </RelOp>
                </QueryPlan>
              </StmtSimple></Statements></Batch></BatchSequence>
            </ShowPlanXML>
            """;
        SqlServerDiagnosticsProvider provider = new TestableSqlServerProvider(translator, xml, null);

        var explainResult = provider.explain("SELECT * FROM products WHERE category = 'electronics'", testConn, "pw", "testdb", "dbo");
        ExplainPlan plan = ((DiagnosticResult.Ok<ExplainPlan>) explainResult).value();

        var indexResult = provider.indexHints("SELECT * FROM products WHERE category = 'electronics'", plan, testConn, "pw");

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) indexResult).value();
        assertThat(recs).hasSize(1);
        // rows=500, 100 < rows <= 1000 => MEDIUM
        assertThat(recs.get(0).impact()).isEqualTo(Impact.MEDIUM);
    }

    @Test
    void indexHints_tableScanLowRows_lowImpact() throws Exception {
        String xml = """
            <?xml version="1.0" encoding="utf-16"?>
            <ShowPlanXML xmlns="http://schemas.microsoft.com/sqlserver/2004/07/showplan" Version="1.539" Build="16.0.4131.2">
              <BatchSequence><Batch><Statements><StmtSimple StatementCompId="1">
                <QueryPlan>
                  <RelOp NodeId="0" PhysicalOp="Table Scan" LogicalOp="Table Scan" EstimateRows="50" EstimatedTotalSubtreeCost="0.1">
                    <OutputList />
                    <RunTimeInformation />
                    <TableScan>
                      <DefinedValues />
                      <Object Database="[testdb]" Schema="[dbo]" Table="[settings]" />
                    </TableScan>
                  </RelOp>
                </QueryPlan>
              </StmtSimple></Statements></Batch></BatchSequence>
            </ShowPlanXML>
            """;
        SqlServerDiagnosticsProvider provider = new TestableSqlServerProvider(translator, xml, null);

        var explainResult = provider.explain("SELECT * FROM settings WHERE key = 'app_version'", testConn, "pw", "testdb", "dbo");
        ExplainPlan plan = ((DiagnosticResult.Ok<ExplainPlan>) explainResult).value();

        var indexResult = provider.indexHints("SELECT * FROM settings WHERE key = 'app_version'", plan, testConn, "pw");

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) indexResult).value();
        assertThat(recs).hasSize(1);
        // rows=50 <= 100 => LOW
        assertThat(recs.get(0).impact()).isEqualTo(Impact.LOW);
    }

    @Test
    void indexHints_noScan_noRecommendations() throws Exception {
        String xml = loadFixture("index_seek.xml");
        SqlServerDiagnosticsProvider provider = new TestableSqlServerProvider(translator, xml, null);

        var explainResult = provider.explain("SELECT * FROM customers WHERE email = 'test@example.com'", testConn, "pw", "testdb", "dbo");
        ExplainPlan plan = ((DiagnosticResult.Ok<ExplainPlan>) explainResult).value();

        var indexResult = provider.indexHints("SELECT * FROM customers WHERE email = 'test@example.com'", plan, testConn, "pw");

        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) indexResult).value();
        assertThat(recs).isEmpty();
    }

    @Test
    void indexHints_nullPlan_returnsEmptyList() {
        SqlServerDiagnosticsProvider provider = new SqlServerDiagnosticsProvider(translator);

        var result = provider.indexHints("SELECT 1", null, testConn, "pw");

        assertThat(result.isOk()).isTrue();
        List<IndexRecommendation> recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).isEmpty();
    }

    @Test
    void otherCapabilitiesReturn_unsupported() {
        SqlServerDiagnosticsProvider provider = new SqlServerDiagnosticsProvider(translator);

        assertThat(provider.lockInfo(testConn, "pw", "testdb").isUnsupported()).isTrue();
        assertThat(provider.poolStatus(testConn, "pw").isUnsupported()).isTrue();
        assertThat(provider.tableSpaceInfo(testConn, "pw", "testdb", List.of("t1")).isUnsupported()).isTrue();
        assertThat(provider.terminateSessionPreview(testConn, "pw", "53", "testdb").isUnsupported()).isTrue();
        assertThat(provider.terminateSession(testConn, "pw", "53", "testdb").isUnsupported()).isTrue();
        assertThat(provider.optimizeTablePreview(testConn, "pw", "t1", "dbo", "testdb").isUnsupported()).isTrue();
        assertThat(provider.optimizeTable(testConn, "pw", "t1", "dbo", "testdb").isUnsupported()).isTrue();
    }

    @Test
    void explain_otherSQLException_returnsError() throws Exception {
        SQLException otherError = new SQLException("Connection failed", "08S01", 18456);
        SqlServerDiagnosticsProvider provider = new TestableSqlServerProvider(translator, null, otherError);

        var result = provider.explain("SELECT 1", testConn, "pw", "testdb", "dbo");

        assertThat(result).isInstanceOf(DiagnosticResult.DiagnosticError.class);
        DiagnosticResult.DiagnosticError<ExplainPlan> error = (DiagnosticResult.DiagnosticError<ExplainPlan>) result;
        assertThat(error.errorType()).isEqualTo("SQLSERVER_EXPLAIN_ERROR");
        assertThat(error.message()).contains("Connection failed");
    }

    // --- Test helpers ---

    private String loadFixture(String name) throws IOException {
        return new String(
            getClass().getClassLoader().getResourceAsStream("diagnostics/sqlserver/" + name).readAllBytes(),
            StandardCharsets.UTF_8
        );
    }

    private Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.warning.full_table_scan", Locale.ENGLISH, "Full table scan on {0}");
        source.addMessage("diagnostics.recommendation.full_scan", Locale.ENGLISH, "Full table scan on {0} ({1} rows)");
        source.addMessage("diagnostics.explain.unsupported.sqlserver_permission", Locale.ENGLISH, "Insufficient SHOWPLAN permission; contact your DBA");
        source.addMessage("diagnostics.coming_soon", Locale.ENGLISH, "Coming soon");
        return new Translator(source);
    }

    /**
     * Testable provider that mocks JDBC connection lifecycle for SET SHOWPLAN_XML ON/OFF.
     */
    static class TestableSqlServerProvider extends SqlServerDiagnosticsProvider {
        private final String xmlResult;
        private final SQLException failure;

        TestableSqlServerProvider(Translator translator, String xmlResult, SQLException failure) {
            super(translator);
            this.xmlResult = xmlResult;
            this.failure = failure;
        }

        @Override
        protected Connection openConnection(ConnectionRecord conn, String decryptedPassword) throws SQLException {
            if (failure != null) throw failure;
            Connection mockConn = mock(Connection.class);
            Statement mockStmt = mock(Statement.class);
            ResultSet mockRs = mock(ResultSet.class);

            when(mockConn.createStatement()).thenReturn(mockStmt);
            when(mockStmt.executeQuery(anyString())).thenReturn(mockRs);
            when(mockRs.next()).thenReturn(xmlResult != null && !xmlResult.isBlank());
            when(mockRs.getString(1)).thenReturn(xmlResult != null ? xmlResult : "");

            return mockConn;
        }

        @Override
        protected ConnectionRecord withDatabaseOverride(ConnectionRecord conn, String database) {
            // Skip actual database override for tests
            return conn;
        }
    }
}