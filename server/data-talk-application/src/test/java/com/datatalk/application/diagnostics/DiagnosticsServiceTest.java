package com.datatalk.application.diagnostics;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
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
        service = new DiagnosticsService(registry, connRepo, connSvc, sessionContexts, mock(Translator.class));
    }

    @Test
    void explain_delegatesToProvider() {
        var conn = testConn("mysql");
        when(sessionContexts.get("s1")).thenReturn(new SessionDataContextRecord("s1", "c1", "test", "db", null, "database", 0L));
        when(connRepo.findById("c1")).thenReturn(Optional.of(conn));
        when(connSvc.decryptPassword("c1")).thenReturn("pass");
        when(registry.find("mysql")).thenReturn(Optional.of(mockProvider));
        when(mockProvider.supportedCapabilities()).thenReturn(Set.of(DiagnosticCapability.EXPLAIN));
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
        when(sessionContexts.get("s1")).thenReturn(new SessionDataContextRecord("s1", "c1", "test", "db", null, "database", 0L));
        when(connRepo.findById("c1")).thenReturn(Optional.of(conn));
        when(registry.find("oracle")).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
            () -> service.explain("s1", "SELECT 1"));
    }

    @Test
    void explain_returnsUnsupported_whenCapabilityMissing() {
        var conn = testConn("mysql");
        when(sessionContexts.get("s1")).thenReturn(new SessionDataContextRecord("s1", "c1", "test", "db", null, "database", 0L));
        when(connRepo.findById("c1")).thenReturn(Optional.of(conn));
        when(connSvc.decryptPassword("c1")).thenReturn("pass");
        when(registry.find("mysql")).thenReturn(Optional.of(mockProvider));
        when(mockProvider.supportedCapabilities()).thenReturn(Set.of());

        var result = service.explain("s1", "SELECT 1");

        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void indexHints_chainsExplainFirst() {
        var conn = testConn("mysql");
        when(sessionContexts.get("s1")).thenReturn(new SessionDataContextRecord("s1", "c1", "test", "db", null, "database", 0L));
        when(connRepo.findById("c1")).thenReturn(Optional.of(conn));
        when(connSvc.decryptPassword("c1")).thenReturn("pass");
        when(registry.find("mysql")).thenReturn(Optional.of(mockProvider));
        when(mockProvider.supportedCapabilities()).thenReturn(Set.of(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS));
        var plan = new ExplainPlan("mysql", "raw", List.of(), null, List.of());
        when(mockProvider.explain(any(), any(), any(), any(), any())).thenReturn(DiagnosticResult.ok(plan));
        when(mockProvider.indexHints(any(), eq(plan), any(), any())).thenReturn(DiagnosticResult.ok(List.of()));

        var result = service.indexHints("s1", "SELECT 1");

        assertThat(result.isOk()).isTrue();
        verify(mockProvider).explain(any(), any(), any(), any(), any());
        verify(mockProvider).indexHints(any(), eq(plan), any(), any());
    }

    @Test
    void indexHints_explainUnsupported_propagatesUnsupportedReason() {
        var conn = testConn("mysql");
        when(sessionContexts.get("s1")).thenReturn(new SessionDataContextRecord("s1", "c1", "test", "db", null, "database", 0L));
        when(connRepo.findById("c1")).thenReturn(Optional.of(conn));
        when(connSvc.decryptPassword("c1")).thenReturn("pass");
        when(registry.find("mysql")).thenReturn(Optional.of(mockProvider));
        when(mockProvider.supportedCapabilities()).thenReturn(Set.of(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS));
        when(mockProvider.explain(any(), any(), any(), any(), any()))
            .thenReturn(DiagnosticResult.unsupported("explain unsupported"));

        var result = service.indexHints("s1", "SELECT 1");

        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<List<IndexRecommendation>>) result).reason()).isEqualTo("explain unsupported");
        verify(mockProvider, never()).indexHints(any(), any(), any(), any());
    }

    @Test
    void indexHints_explainError_propagatesDiagnosticError() {
        var conn = testConn("mysql");
        when(sessionContexts.get("s1")).thenReturn(new SessionDataContextRecord("s1", "c1", "test", "db", null, "database", 0L));
        when(connRepo.findById("c1")).thenReturn(Optional.of(conn));
        when(connSvc.decryptPassword("c1")).thenReturn("pass");
        when(registry.find("mysql")).thenReturn(Optional.of(mockProvider));
        when(mockProvider.supportedCapabilities()).thenReturn(Set.of(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS));
        when(mockProvider.explain(any(), any(), any(), any(), any()))
            .thenReturn(DiagnosticResult.error("EXPLAIN_ERROR", "syntax error"));

        var result = service.indexHints("s1", "BAD SQL");

        assertThat(result).isInstanceOf(DiagnosticResult.DiagnosticError.class);
        var err = (DiagnosticResult.DiagnosticError<List<IndexRecommendation>>) result;
        assertThat(err.errorType()).isEqualTo("EXPLAIN_ERROR");
        assertThat(err.message()).isEqualTo("syntax error");
        verify(mockProvider, never()).indexHints(any(), any(), any(), any());
    }

    private ConnectionRecord testConn(String kind) {
        return new ConnectionRecord("c1", "test", kind, "localhost", 3306,
            "db", "user", new byte[0], null, 0L, 5000, null, null);
    }
}
