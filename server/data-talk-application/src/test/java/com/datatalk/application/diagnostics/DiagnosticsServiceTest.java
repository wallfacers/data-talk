package com.datatalk.application.diagnostics;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.domain.diagnostics.*;
import com.datatalk.infra.diagnostics.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DiagnosticsServiceTest {

    DiagnosticsProviderRegistry registry;
    ConnectionRepository connRepo;
    ConnectionService connSvc;
    SessionDataContextService sessionContexts;
    DiagnosticsService service;
    DiagnosticsProvider mockProvider;

    @BeforeEach
    void setUp() {
        mockProvider = mock(DiagnosticsProvider.class);
        registry = mock(DiagnosticsProviderRegistry.class);
        connRepo = mock(ConnectionRepository.class);
        connSvc = mock(ConnectionService.class);
        sessionContexts = mock(SessionDataContextService.class);
        service = new DiagnosticsService(
            registry,
            connRepo,
            connSvc,
            sessionContexts,
            translator(),
            new DiagnosticsThresholdProperties(null, null, null)
        );
    }

    @Test
    void explain_delegatesToProvider() {
        var conn = arrange("mysql", Set.of(DiagnosticCapability.EXPLAIN));
        var plan = new ExplainPlan("mysql", "raw", List.of(), null, List.of());
        when(mockProvider.explain(eq("SELECT 1"), eq(conn), eq("pass"), eq("db"), isNull()))
            .thenReturn(DiagnosticResult.ok(plan));

        var result = service.explain("s1", "SELECT 1");

        assertThat(result.isOk()).isTrue();
        assertThat(((DiagnosticResult.Ok<ExplainPlan>) result).value().dialect()).isEqualTo("mysql");
    }

    @Test
    void explain_throws_whenProviderNotFound() {
        var conn = testConn("oracle");
        when(sessionContexts.get("s1")).thenReturn(context());
        when(connRepo.findById("c1")).thenReturn(Optional.of(conn));
        when(registry.find("oracle")).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
            () -> service.explain("s1", "SELECT 1"));
    }

    @Test
    void explain_returnsUnsupported_whenCapabilityMissing() {
        arrange("mysql", Set.of());

        var result = service.explain("s1", "SELECT 1");

        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void indexHints_chainsExplainFirst() {
        var conn = arrange("mysql", Set.of(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS));
        var plan = new ExplainPlan("mysql", "raw", List.of(), null, List.of());
        when(mockProvider.explain(any(), any(), any(), any(), any())).thenReturn(DiagnosticResult.ok(plan));
        when(mockProvider.indexHints(any(), eq(plan), any(), any())).thenReturn(DiagnosticResult.ok(List.of()));

        var result = service.indexHints("s1", "SELECT 1");

        assertThat(result.isOk()).isTrue();
        verify(mockProvider).explain(any(), any(), any(), any(), any());
        verify(mockProvider).indexHints(any(), eq(plan), eq(conn), eq("pass"));
    }

    @Test
    void indexHints_explainUnsupported_propagatesUnsupportedReason() {
        arrange("mysql", Set.of(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS));
        when(mockProvider.explain(any(), any(), any(), any(), any()))
            .thenReturn(DiagnosticResult.unsupported("explain unsupported"));

        var result = service.indexHints("s1", "SELECT 1");

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<List<IndexRecommendation>>) result).reason()).isEqualTo("explain unsupported");
        verify(mockProvider, never()).indexHints(any(), any(), any(), any());
    }

    @Test
    void indexHints_explainError_propagatesDiagnosticError() {
        arrange("mysql", Set.of(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS));
        when(mockProvider.explain(any(), any(), any(), any(), any()))
            .thenReturn(DiagnosticResult.error("EXPLAIN_ERROR", "syntax error"));

        var result = service.indexHints("s1", "BAD SQL");

        assertThat(result).isInstanceOf(DiagnosticResult.DiagnosticError.class);
        var err = (DiagnosticResult.DiagnosticError<List<IndexRecommendation>>) result;
        assertThat(err.errorType()).isEqualTo("EXPLAIN_ERROR");
        assertThat(err.message()).isEqualTo("syntax error");
        verify(mockProvider, never()).indexHints(any(), any(), any(), any());
    }

    @Test
    void lockInfo_returnsUnsupported_whenCapabilityMissing() {
        arrange("h2", Set.of(DiagnosticCapability.EXPLAIN));

        var result = service.lockInfo("s1");

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<LockReport>) result).reason()).contains("H2");
        verify(mockProvider, never()).lockInfo(any(), any(), any());
    }

    @Test
    void lockInfo_addsTerminateRecommendationForLongWait() {
        var conn = arrange("mysql", Set.of(DiagnosticCapability.LOCK_INFO));
        var report = new LockReport(List.of(new LockReport.LockEntry(
            "users", "EXCLUSIVE", "42", "43", 6_000L, "UPDATE users", "SELECT users"
        )), List.of());
        when(mockProvider.lockInfo(eq(conn), eq("pass"), eq("db"))).thenReturn(DiagnosticResult.ok(report));

        var result = service.lockInfo("s1");

        var ok = (DiagnosticResult.Ok<LockReport>) result;
        assertThat(ok.value().recommendations()).hasSize(1);
        assertThat(ok.value().recommendations().get(0).suggestedToolName()).isEqualTo("datatalk_terminate_session");
        assertThat(ok.value().recommendations().get(0).suggestedActionArgs()).containsEntry("sessionId", "42");
    }

    @Test
    void poolStatus_addsWarningAndCriticalRecommendationsByRatio() {
        var conn = arrange("mysql", Set.of(DiagnosticCapability.POOL_STATUS));
        when(mockProvider.poolStatus(eq(conn), eq("pass")))
            .thenReturn(DiagnosticResult.ok(new PoolReport("server", 85, 10, 100, 5, 2, "localhost:3306", List.of())))
            .thenReturn(DiagnosticResult.ok(new PoolReport("server", 96, 1, 100, 5, 2, "localhost:3306", List.of())));

        var warning = (DiagnosticResult.Ok<PoolReport>) service.poolStatus("s1");
        var critical = (DiagnosticResult.Ok<PoolReport>) service.poolStatus("s1");

        assertThat(warning.value().recommendations()).extracting(DiagnosticRecommendation::severity).containsExactly("warning");
        assertThat(critical.value().recommendations()).extracting(DiagnosticRecommendation::severity).containsExactly("critical");
    }

    @Test
    void tableSpaceInfo_addsOptimizeRecommendationWhenReclaimableAndLarge() {
        var conn = arrange("mysql", Set.of(DiagnosticCapability.TABLE_SPACE));
        var tables = List.of(new SpaceReport.TableSpaceEntry("users", "app", 100, 900_000_000L, 0L, 400_000_000L));
        when(mockProvider.tableSpaceInfo(eq(conn), eq("pass"), eq("db"), any()))
            .thenReturn(DiagnosticResult.ok(new SpaceReport(tables, List.of())));

        var result = (DiagnosticResult.Ok<SpaceReport>) service.tableSpaceInfo("s1", List.of("users"));

        assertThat(result.value().recommendations()).hasSize(1);
        assertThat(result.value().recommendations().get(0).suggestedToolName()).isEqualTo("datatalk_optimize_table");
        assertThat(result.value().recommendations().get(0).suggestedActionArgs()).containsEntry("table", "users");
    }

    @Test
    void tableSpaceInfo_doesNotRecommendSmallTables() {
        var conn = arrange("mysql", Set.of(DiagnosticCapability.TABLE_SPACE));
        var tables = List.of(new SpaceReport.TableSpaceEntry("tiny", "app", 100, 50_000_000L, 0L, 40_000_000L));
        when(mockProvider.tableSpaceInfo(eq(conn), eq("pass"), eq("db"), any()))
            .thenReturn(DiagnosticResult.ok(new SpaceReport(tables, List.of())));

        var result = (DiagnosticResult.Ok<SpaceReport>) service.tableSpaceInfo("s1", List.of("tiny"));

        assertThat(result.value().recommendations()).isEmpty();
    }

    @Test
    void mutationPreviewAndExecuteDelegateToProvider() {
        var conn = arrange("mysql", Set.of(DiagnosticCapability.TERMINATE_SESSION, DiagnosticCapability.OPTIMIZE_TABLE));
        var terminatePreview = new TerminateSessionPreview("mysql", "42", "KILL 42", "SELECT 1");
        var terminateResult = new TerminateSessionResult(true, "42", "Session terminated");
        var optimizePreview = new OptimizeTablePreview("mysql", "users", "db", "OPTIMIZE TABLE `db`.`users`", 10L, null, List.of());
        var optimizeResult = new OptimizeTableResult(true, "users", "db", 12L, 8L, "ok");
        when(mockProvider.terminateSessionPreview(eq(conn), eq("pass"), eq("42"), eq("db"))).thenReturn(DiagnosticResult.ok(terminatePreview));
        when(mockProvider.terminateSession(eq(conn), eq("pass"), eq("42"), eq("db"))).thenReturn(DiagnosticResult.ok(terminateResult));
        when(mockProvider.optimizeTablePreview(eq(conn), eq("pass"), eq("users"), isNull(), eq("db"))).thenReturn(DiagnosticResult.ok(optimizePreview));
        when(mockProvider.optimizeTable(eq(conn), eq("pass"), eq("users"), isNull(), eq("db"))).thenReturn(DiagnosticResult.ok(optimizeResult));

        assertThat(((DiagnosticResult.Ok<TerminateSessionPreview>) service.terminateSessionPreview("s1", "42")).value()).isEqualTo(terminatePreview);
        assertThat(((DiagnosticResult.Ok<TerminateSessionResult>) service.terminateSession("s1", "42")).value()).isEqualTo(terminateResult);
        assertThat(((DiagnosticResult.Ok<OptimizeTablePreview>) service.optimizeTablePreview("s1", "users", null)).value()).isEqualTo(optimizePreview);
        assertThat(((DiagnosticResult.Ok<OptimizeTableResult>) service.optimizeTable("s1", "users", null)).value()).isEqualTo(optimizeResult);
    }

    @Test
    void terminateAndOptimizeUnsupported_useGenericReasonForUnknownDialects() {
        arrange("sqlite", Set.of());

        var terminate = (DiagnosticResult.Unsupported<TerminateSessionPreview>) service.terminateSessionPreview("s1", "42");
        var optimize = (DiagnosticResult.Unsupported<OptimizeTablePreview>) service.optimizeTablePreview("s1", "users", null);

        assertThat(terminate.reason()).contains("sqlite").doesNotContain("Oracle");
        assertThat(optimize.reason()).contains("sqlite").doesNotContain("Oracle");
    }

    private ConnectionRecord arrange(String kind, Set<DiagnosticCapability> capabilities) {
        var conn = testConn(kind);
        when(sessionContexts.get("s1")).thenReturn(context());
        when(connRepo.findById("c1")).thenReturn(Optional.of(conn));
        when(connSvc.decryptPassword("c1")).thenReturn("pass");
        when(registry.find(kind)).thenReturn(Optional.of(mockProvider));
        when(mockProvider.supportedCapabilities()).thenReturn(capabilities);
        return conn;
    }

    private SessionDataContextRecord context() {
        return new SessionDataContextRecord("s1", "c1", "test", "db", null, "database", 0L);
    }

    private ConnectionRecord testConn(String kind) {
        return new ConnectionRecord("c1", "test", kind, "localhost", 3306,
            "db", "user", new byte[0], null, 0L, 5000, null, null,
            null, 1, true, null, false);
    }

    @Test
    void routesToCorrectProvider_for_allDay2Kinds() {
        var providers = List.of(
            new SqliteDiagnosticsProvider(translator()),
            new SqlServerDiagnosticsProvider(translator()),
            new DuckDbDiagnosticsProvider(translator()),
            new ClickHouseDiagnosticsProvider(translator()),
            new DorisDiagnosticsProvider(translator()),
            new StarrocksDiagnosticsProvider(translator()),
            new PrestoDiagnosticsProvider(translator()),
            new TrinoDiagnosticsProvider(translator()),
            new HiveDiagnosticsProvider(translator()),
            new TiDbDiagnosticsProvider(translator()),
            new MySqlDiagnosticsProvider(translator())  // for mariadb
        );
        var registry = new DiagnosticsProviderRegistry(providers);

        assertThat(registry.find("sqlite")).isPresent().get().isInstanceOf(SqliteDiagnosticsProvider.class);
        assertThat(registry.find("sqlserver")).isPresent().get().isInstanceOf(SqlServerDiagnosticsProvider.class);
        assertThat(registry.find("mariadb")).isPresent();  // routes to MySql via supportedDriverTypes("mariadb")
        assertThat(registry.find("tidb")).isPresent().get().isInstanceOf(TiDbDiagnosticsProvider.class);
        assertThat(registry.find("duckdb")).isPresent().get().isInstanceOf(DuckDbDiagnosticsProvider.class);
        assertThat(registry.find("clickhouse")).isPresent().get().isInstanceOf(ClickHouseDiagnosticsProvider.class);
        assertThat(registry.find("apache_doris")).isPresent().get().isInstanceOf(DorisDiagnosticsProvider.class);
        assertThat(registry.find("starrocks")).isPresent().get().isInstanceOf(StarrocksDiagnosticsProvider.class);
        assertThat(registry.find("presto")).isPresent().get().isInstanceOf(PrestoDiagnosticsProvider.class);
        assertThat(registry.find("trino")).isPresent().get().isInstanceOf(TrinoDiagnosticsProvider.class);
        assertThat(registry.find("hive")).isPresent().get().isInstanceOf(HiveDiagnosticsProvider.class);
    }

    private Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.explain_unsupported", Locale.ENGLISH, "EXPLAIN not supported for dialect: {0}");
        source.addMessage("diagnostics.index_hints_unsupported", Locale.ENGLISH, "Index hints not supported for dialect: {0}");
        source.addMessage("diagnostics.no_provider", Locale.ENGLISH, "No diagnostics provider for dialect: {0}");
        source.addMessage("diagnostics.no_active_session", Locale.ENGLISH, "No active connection in session");
        source.addMessage("diagnostics.connection_not_found", Locale.ENGLISH, "Connection not found: {0}");
        source.addMessage("diagnostics.lock_not_supported", Locale.ENGLISH, "{0} lock info is not yet supported");
        source.addMessage("diagnostics.pool_not_supported", Locale.ENGLISH, "{0} connection pool info is not yet supported");
        source.addMessage("diagnostics.tablespace_not_supported", Locale.ENGLISH, "{0} table space info is not yet supported");
        source.addMessage("diagnostics.lock.unsupported.h2", Locale.ENGLISH, "H2 does not expose lock waits");
        source.addMessage("diagnostics.pool.unsupported.h2_embedded", Locale.ENGLISH, "H2 embedded mode does not expose server connection stats");
        source.addMessage("diagnostics.space.unsupported.oracle", Locale.ENGLISH, "Oracle diagnostics not yet available");
        source.addMessage("diagnostics.terminate.unsupported.h2", Locale.ENGLISH, "H2 does not support session termination");
        source.addMessage("diagnostics.terminate.unsupported.oracle", Locale.ENGLISH, "Oracle diagnostics not yet available");
        source.addMessage("diagnostics.terminate_not_supported", Locale.ENGLISH, "{0} session termination is not yet supported");
        source.addMessage("diagnostics.optimize.unsupported.h2", Locale.ENGLISH, "H2 does not support reclaiming space; ANALYZE only updates statistics");
        source.addMessage("diagnostics.optimize.unsupported.oracle", Locale.ENGLISH, "Oracle diagnostics not yet available");
        source.addMessage("diagnostics.optimize_not_supported", Locale.ENGLISH, "{0} table optimization is not yet supported");
        source.addMessage("diagnostics.lock.recommendation.terminate", Locale.ENGLISH, "Holder session {0} has blocked for {1}s; consider terminating it");
        source.addMessage("diagnostics.pool.recommendation.high_usage_warning", Locale.ENGLISH, "Connection usage is at {0}%, approaching capacity");
        source.addMessage("diagnostics.pool.recommendation.high_usage_critical", Locale.ENGLISH, "Connection usage is at {0}%, near maximum capacity");
        source.addMessage("diagnostics.space.recommendation.optimize_table", Locale.ENGLISH, "Table {0} has {1}% reclaimable space ({2})");
        return new Translator(source);
    }
}
