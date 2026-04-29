package com.datatalk.adapter.actions;

import com.datatalk.application.diagnostics.DiagnosticsService;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LockInfoActionTest {

    private final DiagnosticsService service = mock(DiagnosticsService.class);
    private final LockInfoAction action = new LockInfoAction(service);
    private final ActionContext ctx = new ActionContext("s1", "call", null, null);

    @Test
    void okSerializesBlockingChainAndRecommendations() {
        var report = new LockReport(
            List.of(new LockReport.LockEntry("users", "EXCLUSIVE", "42", "43", 6000L, "UPDATE users", "SELECT users")),
            List.of(new DiagnosticRecommendation("warning", "terminate", "datatalk.terminate_session", "datatalk_terminate_session", Map.of("sessionId", "42"), null))
        );
        when(service.lockInfo("s1")).thenReturn(DiagnosticResult.ok(report));

        Map out = action.handle(ctx, Map.of()).toCompletableFuture().join();

        assertThat((List<?>) out.get("blockingChain")).hasSize(1);
        assertThat((List<?>) out.get("recommendations")).hasSize(1);
        assertThat(((Map<?, ?>) ((List<?>) out.get("blockingChain")).get(0)).get("holderId")).isEqualTo("42");
    }

    @Test
    void unsupportedSerializesReason() {
        when(service.lockInfo("s1")).thenReturn(DiagnosticResult.unsupported("no locks"));

        Map out = action.handle(ctx, Map.of()).toCompletableFuture().join();

        assertThat(out).containsEntry("unsupported", true).containsEntry("reason", "no locks");
    }

    @Test
    void diagnosticErrorSerializesErrorObject() {
        when(service.lockInfo("s1")).thenReturn(DiagnosticResult.error("X", "msg"));

        Map out = action.handle(ctx, Map.of()).toCompletableFuture().join();

        Map<?, ?> error = (Map<?, ?>) out.get("error");
        assertThat(error.get("type")).isEqualTo("X");
        assertThat(error.get("message")).isEqualTo("msg");
    }
}
