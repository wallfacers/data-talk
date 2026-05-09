package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class H2DiagnosticsProviderTest {

    private H2DiagnosticsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new H2DiagnosticsProvider(translator());
    }

    @Test
    void supportedDriverTypes_returnsH2() {
        assertThat(provider.supportedDriverTypes()).containsExactly("h2");
    }

    @Test
    void supportedCapabilities_returnsExplainIndexHintsAndTableSpace() {
        assertThat(provider.supportedCapabilities())
            .containsExactlyInAnyOrder(
                DiagnosticCapability.EXPLAIN,
                DiagnosticCapability.INDEX_HINTS,
                DiagnosticCapability.TABLE_SPACE
            );
    }

    @Test
    void unsupportedMethodsReturnExplicitReasons() {
        assertThat(((DiagnosticResult.Unsupported<LockReport>) provider.lockInfo(null, null, null)).reason())
            .contains("lock waits");
        assertThat(((DiagnosticResult.Unsupported<PoolReport>) provider.poolStatus(null, null)).reason())
            .contains("embedded mode");
        assertThat(((DiagnosticResult.Unsupported<TerminateSessionPreview>) provider.terminateSessionPreview(null, null, "1", null)).reason())
            .contains("session termination");
        assertThat(((DiagnosticResult.Unsupported<TerminateSessionResult>) provider.terminateSession(null, null, "1", null)).reason())
            .contains("session termination");
        assertThat(((DiagnosticResult.Unsupported<OptimizeTablePreview>) provider.optimizeTablePreview(null, null, "t", null, null)).reason())
            .contains("reclaiming space");
        assertThat(((DiagnosticResult.Unsupported<OptimizeTableResult>) provider.optimizeTable(null, null, "t", null, null)).reason())
            .contains("reclaiming space");
    }

    @Test
    void tableSpaceInfo_returnsPartialRowsFromInformationSchema() throws Exception {
        String db = "mem:h2-space-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        try (var c = DriverManager.getConnection("jdbc:h2:" + db, "sa", "");
             var s = c.createStatement()) {
            s.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50))");
            s.execute("INSERT INTO users VALUES (1, 'a'), (2, 'b')");
        }

        var result = (DiagnosticResult.Ok<SpaceReport>) provider.tableSpaceInfo(testConn(db), "", db, List.of("USERS"));

        assertThat(result.value().tables()).anySatisfy(table -> {
            assertThat(table.table()).isEqualTo("USERS");
            assertThat(table.schemaName()).isEqualTo("PUBLIC");
            assertThat(table.rowCount()).isGreaterThan(0);
            assertThat(table.dataSizeBytes()).isZero();
            assertThat(table.indexSizeBytes()).isZero();
            assertThat(table.freeSpaceBytes()).isNull();
        });
        assertThat(result.value().recommendations()).isEmpty();
    }

    @Test
    void parseH2Text_withTableScanPattern_returnsFullScanNodes() {
        String text = "SELECT\n    FROM PUBLIC.ORDERS /* PUBLIC.tableScan */";

        List<ExplainNode> nodes = provider.parseH2Text(text);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).scanType()).isEqualTo(ScanType.FULL_SCAN);
        assertThat(nodes.get(0).table()).isEqualTo("PUBLIC.ORDERS");
    }

    @Test
    void parseH2Text_withIndexPattern_returnsIndexScanNodes() {
        String text = "SELECT\n    FROM PUBLIC.ORDERS /* PUBLIC.IDX_ORDER_ID:PK */";

        List<ExplainNode> nodes = provider.parseH2Text(text);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).scanType()).isEqualTo(ScanType.INDEX_SCAN);
        assertThat(nodes.get(0).table()).isEqualTo("PUBLIC.IDX_ORDER_ID");
    }

    @Test
    void parseH2Text_withNoMatchingPatterns_returnsEmptyList() {
        assertThat(provider.parseH2Text("some random text without explain patterns")).isEmpty();
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
    void indexHints_nonFullScanOrNoWhereReturnsEmptyRecommendations() {
        ExplainPlan indexPlan = new ExplainPlan("h2", "...",
            List.of(new ExplainNode("INDEX_SCAN", "orders", ScanType.INDEX_SCAN, 0L, null, null, List.of())),
            null, List.of());
        ExplainPlan fullScanNoWhere = new ExplainPlan("h2", "...",
            List.of(new ExplainNode("TABLE_SCAN", "orders", ScanType.FULL_SCAN, 0L, null, null, List.of())),
            null, List.of());

        assertThat(((DiagnosticResult.Ok<List<IndexRecommendation>>) provider.indexHints("SELECT * FROM orders", indexPlan, null, null)).value()).isEmpty();
        assertThat(((DiagnosticResult.Ok<List<IndexRecommendation>>) provider.indexHints("SELECT * FROM orders", fullScanNoWhere, null, null)).value()).isEmpty();
    }

    @Test
    void withDatabaseOverride_usesOverrideWhenProvided() {
        ConnectionRecord conn = testConn("mem:test");

        ConnectionRecord overridden = provider.withDatabaseOverride(conn, "mem:analytics");

        assertThat(overridden.databaseName()).isEqualTo("mem:analytics");
        assertThat(overridden.id()).isEqualTo(conn.id());
    }

    @Test
    void applySchema_executesSetSchemaWhenSchemaProvided() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);

        provider.applySchema(connection, "PUBLIC");

        verify(statement).execute("SET SCHEMA PUBLIC");
        verify(statement).close();
    }

    @Test
    void applySchema_noopWhenSchemaBlank() throws Exception {
        Connection connection = mock(Connection.class);

        provider.applySchema(connection, "");

        verify(connection, never()).createStatement();
    }

    private ConnectionRecord testConn(String databaseName) {
        return new ConnectionRecord(
            "c1", "test", "h2", "localhost", 0,
            databaseName, "sa", new byte[0], null, 0L, 5000, null, null,
            null, 1, true, null, false, null, null, null);
    }

    private Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.recommendation.table_scan", Locale.ENGLISH, "Table scan on {0}");
        source.addMessage("diagnostics.warning.table_scan", Locale.ENGLISH, "Table scan on {0}");
        source.addMessage("diagnostics.lock.unsupported.h2", Locale.ENGLISH, "H2 does not expose lock waits");
        source.addMessage("diagnostics.pool.unsupported.h2_embedded", Locale.ENGLISH, "H2 embedded mode does not expose server connection stats");
        source.addMessage("diagnostics.terminate.unsupported.h2", Locale.ENGLISH, "H2 does not support session termination");
        source.addMessage("diagnostics.optimize.unsupported.h2", Locale.ENGLISH, "H2 does not support reclaiming space; ANALYZE only updates statistics");
        return new Translator(source);
    }
}
