package com.datatalk.infra.diagnostics;

import com.datatalk.application.connection.ConnectionKind;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.DiagnosticCapability;
import com.datatalk.domain.diagnostics.DiagnosticResult;
import com.datatalk.domain.diagnostics.ExplainNode;
import com.datatalk.domain.diagnostics.ExplainPlan;
import com.datatalk.domain.diagnostics.Impact;
import com.datatalk.domain.diagnostics.IndexRecommendation;
import com.datatalk.domain.diagnostics.ScanType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.StaticMessageSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteDiagnosticsProviderTest {

    @TempDir
    Path tempDir;

    private Path dbFile;
    private ConnectionRecord connRecord;
    private SqliteDiagnosticsProvider provider;
    private String dbUrl;

    @BeforeEach
    void setUp() throws Exception {
        provider = new SqliteDiagnosticsProvider(translator());

        // Create temporary SQLite file with test data
        dbFile = tempDir.resolve("test.db");
        dbUrl = "jdbc:sqlite:" + dbFile.toAbsolutePath();

        try (Connection c = DriverManager.getConnection(dbUrl);
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY, user_id INTEGER, amount DECIMAL(10,2))");
            s.execute("CREATE INDEX idx_user ON orders(user_id)");
            s.execute("INSERT INTO orders (user_id, amount) VALUES (1, 100.00)");
            s.execute("INSERT INTO orders (user_id, amount) VALUES (2, 200.00)");
            s.execute("INSERT INTO orders (user_id, amount) VALUES (1, 50.00)");
        }

        // Create connection record for SQLite
        connRecord = new ConnectionRecord(
            "1", "test-sqlite", ConnectionKind.SQLITE, null, 0,
            dbFile.toAbsolutePath().toString(), null, null, null,
            Instant.now().toEpochMilli(), 0, null, null,
            null, 1, true, null, false
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dbFile != null && Files.exists(dbFile)) {
            Files.deleteIfExists(dbFile);
        }
    }

    @Test
    void supportedDriverTypes_containsSqlite() {
        assertThat(provider.supportedDriverTypes()).containsExactly("sqlite");
    }

    @Test
    void supportedCapabilities_containsExplainAndIndexHints() {
        Set<DiagnosticCapability> caps = provider.supportedCapabilities();
        assertThat(caps).containsExactlyInAnyOrder(
            DiagnosticCapability.EXPLAIN,
            DiagnosticCapability.INDEX_HINTS
        );
    }

    @Test
    void explain_fullTableScan_returnsFullScanScanType() {
        // SELECT without WHERE clause on non-indexed column triggers full scan
        DiagnosticResult<ExplainPlan> result = provider.explain(
            "SELECT * FROM orders WHERE amount > 50",
            connRecord, null, null, null
        );

        assertThat(result).isInstanceOf(DiagnosticResult.Ok.class);
        ExplainPlan plan = ((DiagnosticResult.Ok<ExplainPlan>) result).value();
        assertThat(plan.dialect()).isEqualTo("sqlite");
        assertThat(plan.nodes()).isNotEmpty();
        assertThat(plan.warnings()).anyMatch(w -> w.contains("orders"));

        // Find the SCAN node
        ExplainNode scanNode = findNodeByTable(plan.nodes(), "orders");
        assertThat(scanNode).isNotNull();
        assertThat(scanNode.scanType()).isEqualTo(ScanType.FULL_SCAN);
    }

    @Test
    void explain_indexScan_returnsRefOrIndexRange() {
        // SELECT with indexed column in WHERE should use index
        DiagnosticResult<ExplainPlan> result = provider.explain(
            "SELECT * FROM orders WHERE user_id = 1",
            connRecord, null, null, null
        );

        assertThat(result).isInstanceOf(DiagnosticResult.Ok.class);
        ExplainPlan plan = ((DiagnosticResult.Ok<ExplainPlan>) result).value();
        assertThat(plan.nodes()).isNotEmpty();

        // Find the SCAN node
        ExplainNode scanNode = findNodeByTable(plan.nodes(), "orders");
        assertThat(scanNode).isNotNull();
        // SQLite uses SEARCH with index for equality
        assertThat(scanNode.scanType()).isIn(ScanType.REF, ScanType.INDEX_RANGE, ScanType.CONST);
    }

    @Test
    void indexHints_fullScan_returnsBtreeRecommendation() {
        // First get explain plan for full scan query
        DiagnosticResult<ExplainPlan> explainResult = provider.explain(
            "SELECT * FROM orders WHERE amount > 50",
            connRecord, null, null, null
        );
        assertThat(explainResult).isInstanceOf(DiagnosticResult.Ok.class);
        ExplainPlan plan = ((DiagnosticResult.Ok<ExplainPlan>) explainResult).value();

        // Get index hints
        DiagnosticResult<List<IndexRecommendation>> hintsResult = provider.indexHints(
            "SELECT * FROM orders WHERE amount > 50",
            plan, connRecord, null
        );

        assertThat(hintsResult).isInstanceOf(DiagnosticResult.Ok.class);
        List<IndexRecommendation> hints = ((DiagnosticResult.Ok<List<IndexRecommendation>>) hintsResult).value();
        assertThat(hints).isNotEmpty();

        IndexRecommendation rec = hints.stream()
            .filter(r -> "orders".equals(r.table()))
            .findFirst()
            .orElse(null);
        assertThat(rec).isNotNull();
        assertThat(rec.table()).isEqualTo("orders");
        assertThat(rec.indexType()).isEqualTo("BTREE");
        assertThat(rec.impact()).isEqualTo(Impact.MEDIUM);
        assertThat(rec.columns()).contains("amount");
    }

    @Test
    void indexHints_indexScan_returnsEmptyRecommendations() {
        // Query using indexed column should not trigger recommendation
        DiagnosticResult<ExplainPlan> explainResult = provider.explain(
            "SELECT * FROM orders WHERE user_id = 1",
            connRecord, null, null, null
        );
        assertThat(explainResult).isInstanceOf(DiagnosticResult.Ok.class);
        ExplainPlan plan = ((DiagnosticResult.Ok<ExplainPlan>) explainResult).value();

        DiagnosticResult<List<IndexRecommendation>> hintsResult = provider.indexHints(
            "SELECT * FROM orders WHERE user_id = 1",
            plan, connRecord, null
        );

        assertThat(hintsResult).isInstanceOf(DiagnosticResult.Ok.class);
        List<IndexRecommendation> hints = ((DiagnosticResult.Ok<List<IndexRecommendation>>) hintsResult).value();
        // Should not recommend index for already-indexed column scan
        assertThat(hints.stream().anyMatch(r -> "user_id".equals(r.table()))).isFalse();
    }

    @Test
    void lockInfo_returnsUnsupported() {
        DiagnosticResult<?> result = provider.lockInfo(connRecord, null, null);
        assertUnsupported(result, "sqlite lock info is not yet supported");
    }

    @Test
    void poolStatus_returnsUnsupported() {
        DiagnosticResult<?> result = provider.poolStatus(connRecord, null);
        assertUnsupported(result, "sqlite connection pool info is not yet supported");
    }

    @Test
    void tableSpaceInfo_returnsUnsupported() {
        DiagnosticResult<?> result = provider.tableSpaceInfo(connRecord, null, null, List.of("orders"));
        assertUnsupported(result, "sqlite table space info is not yet supported");
    }

    @Test
    void terminateSessionPreview_returnsUnsupported() {
        DiagnosticResult<?> result = provider.terminateSessionPreview(connRecord, null, "1", null);
        assertUnsupported(result, "sqlite session termination is not yet supported");
    }

    @Test
    void terminateSession_returnsUnsupported() {
        DiagnosticResult<?> result = provider.terminateSession(connRecord, null, "1", null);
        assertUnsupported(result, "sqlite session termination is not yet supported");
    }

    @Test
    void optimizeTablePreview_returnsUnsupported() {
        DiagnosticResult<?> result = provider.optimizeTablePreview(connRecord, null, "orders", null, null);
        assertUnsupported(result, "sqlite table optimization is not yet supported");
    }

    @Test
    void optimizeTable_returnsUnsupported() {
        DiagnosticResult<?> result = provider.optimizeTable(connRecord, null, "orders", null, null);
        assertUnsupported(result, "sqlite table optimization is not yet supported");
    }

    @Test
    void explain_withInvalidSql_returnsError() {
        DiagnosticResult<ExplainPlan> result = provider.explain(
            "SELECT * FROM nonexistent_table",
            connRecord, null, null, null
        );

        assertThat(result).isInstanceOf(DiagnosticResult.Error.class);
        DiagnosticResult.Error<ExplainPlan> error = (DiagnosticResult.Error<ExplainPlan>) result;
        assertThat(error.code()).isEqualTo("SQLITE_EXPLAIN_ERROR");
    }

    @Test
    void indexHints_withNullPlan_returnsEmptyList() {
        DiagnosticResult<List<IndexRecommendation>> result = provider.indexHints(
            "SELECT 1", null, connRecord, null
        );

        assertThat(result).isInstanceOf(DiagnosticResult.Ok.class);
        List<IndexRecommendation> hints = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(hints).isEmpty();
    }

    // --- Helper methods ---

    private static void assertUnsupported(DiagnosticResult<?> result, String reason) {
        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<?>) result).reason()).isEqualTo(reason);
    }

    private static ExplainNode findNodeByTable(List<ExplainNode> nodes, String table) {
        for (ExplainNode n : nodes) {
            if (table.equals(n.table())) return n;
            ExplainNode child = findNodeByTable(n.children(), table);
            if (child != null) return child;
        }
        return null;
    }

    private static Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.lock_not_supported", Locale.ENGLISH, "{0} lock info is not yet supported");
        source.addMessage("diagnostics.pool_not_supported", Locale.ENGLISH, "{0} connection pool info is not yet supported");
        source.addMessage("diagnostics.tablespace_not_supported", Locale.ENGLISH, "{0} table space info is not yet supported");
        source.addMessage("diagnostics.terminate_not_supported", Locale.ENGLISH, "{0} session termination is not yet supported");
        source.addMessage("diagnostics.optimize_not_supported", Locale.ENGLISH, "{0} table optimization is not yet supported");
        source.addMessage("diagnostics.warning.full_table_scan", Locale.ENGLISH, "Full table scan on table: {0}");
        source.addMessage("diagnostics.recommendation.full_scan", Locale.ENGLISH, "Consider adding index on {0} for columns: {1}");
        return new Translator(source);
    }
}