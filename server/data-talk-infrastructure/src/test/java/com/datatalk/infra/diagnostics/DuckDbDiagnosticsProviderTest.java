package com.datatalk.infra.diagnostics;

import com.datatalk.application.diagnostics.DiagnosticsProviderRegistry;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class DuckDbDiagnosticsProviderTest {

    private DuckDbDiagnosticsProvider provider;
    private ConnectionRecord duckdbConn;
    private Connection rawConnection;

    @BeforeEach
    void setUp() throws Exception {
        provider = new DuckDbDiagnosticsProvider(translator());

        // Create DuckDB in-memory database with test data
        String jdbcUrl = "jdbc:duckdb:";
        rawConnection = DriverManager.getConnection(jdbcUrl);
        try (Statement s = rawConnection.createStatement()) {
            s.execute("CREATE TABLE orders (id INTEGER, user_id INTEGER, amount DECIMAL(10,2))");
            s.execute("INSERT INTO orders VALUES (1, 100, 50.00), (2, 101, 75.50), (3, 102, 120.00)");
        }

        duckdbConn = new ConnectionRecord(
            "test-duckdb", "test", "duckdb", null, 0,
            null, null, new byte[0], null, 0L, 5000, null, null,
            null, null, null, null, false
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        if (rawConnection != null) {
            rawConnection.close();
        }
    }

    @Test
    void supportedDriverTypes_containsDuckdb() {
        assertThat(provider.supportedDriverTypes()).containsExactly("duckdb");
    }

    @Test
    void supportedCapabilities_containsOnlyExplain() {
        assertThat(provider.supportedCapabilities())
            .containsExactly(DiagnosticCapability.EXPLAIN)
            .doesNotContain(DiagnosticCapability.INDEX_HINTS);
    }

    @Test
    void explain_returnsOkWithNonEmptyNodes() {
        var result = provider.explain("SELECT * FROM orders WHERE user_id = 100", duckdbConn, null, null, null);

        assertThat(result.isOk()).isTrue();
        ExplainPlan plan = ((DiagnosticResult.Ok<ExplainPlan>) result).value();
        assertThat(plan.dialect()).isEqualTo("duckdb");
        assertThat(plan.raw()).isNotEmpty();
        assertThat(plan.nodes()).isNotEmpty();
    }

    @Test
    void explain_simpleQuery_returnsOkWithNodes() {
        var result = provider.explain("SELECT COUNT(*) FROM orders", duckdbConn, null, null, null);

        assertThat(result.isOk()).isTrue();
        ExplainPlan plan = ((DiagnosticResult.Ok<ExplainPlan>) result).value();
        assertThat(plan.nodes()).isNotEmpty();
    }

    @Test
    void indexHints_returnsUnsupportedWithZoneMapReason() {
        var result = provider.indexHints("SELECT * FROM orders WHERE user_id = 100", null, null, null);

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        String reason = ((DiagnosticResult.Unsupported<List<IndexRecommendation>>) result).reason();
        assertThat(reason).containsIgnoringCase("zone map");
    }

    @Test
    void unsupportedMethodsReturnExplicitReasons() {
        assertThat(((DiagnosticResult.Unsupported<LockReport>) provider.lockInfo(duckdbConn, null, null)).reason())
            .contains("duckdb");
        assertThat(((DiagnosticResult.Unsupported<PoolReport>) provider.poolStatus(duckdbConn, null)).reason())
            .contains("duckdb");
        assertThat(((DiagnosticResult.Unsupported<SpaceReport>) provider.tableSpaceInfo(duckdbConn, null, null, null)).reason())
            .contains("duckdb");
        assertThat(((DiagnosticResult.Unsupported<TerminateSessionPreview>) provider.terminateSessionPreview(duckdbConn, null, "1", null)).reason())
            .contains("duckdb");
        assertThat(((DiagnosticResult.Unsupported<TerminateSessionResult>) provider.terminateSession(duckdbConn, null, "1", null)).reason())
            .contains("duckdb");
        assertThat(((DiagnosticResult.Unsupported<OptimizeTablePreview>) provider.optimizeTablePreview(duckdbConn, null, "orders", null, null)).reason())
            .contains("duckdb");
        assertThat(((DiagnosticResult.Unsupported<OptimizeTableResult>) provider.optimizeTable(duckdbConn, null, "orders", null, null)).reason())
            .contains("duckdb");
    }

    @Test
    void registryFindsProvider_for_duckdb_kind() {
        var registry = new DiagnosticsProviderRegistry(List.of(provider));
        assertThat(registry.find("duckdb")).isPresent();
        assertThat(registry.find("duckdb").get()).isSameAs(provider);
    }

    private Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.index_hints.unsupported.duckdb", Locale.ENGLISH,
            "DuckDB column store typically does not need manual B-tree indexes. If row scans dominate, check zone map hits in EXPLAIN.");
        source.addMessage("diagnostics.lock_not_supported", Locale.ENGLISH, "{0} lock info is not yet supported");
        source.addMessage("diagnostics.pool_not_supported", Locale.ENGLISH, "{0} connection pool info is not yet supported");
        source.addMessage("diagnostics.tablespace_not_supported", Locale.ENGLISH, "{0} table space info is not yet supported");
        source.addMessage("diagnostics.terminate_not_supported", Locale.ENGLISH, "{0} session termination is not yet supported");
        source.addMessage("diagnostics.optimize_not_supported", Locale.ENGLISH, "{0} table optimization is not yet supported");
        return new Translator(source);
    }
}