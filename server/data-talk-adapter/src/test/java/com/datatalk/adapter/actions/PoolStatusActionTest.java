package com.datatalk.adapter.actions;

import com.datatalk.application.diagnostics.DiagnosticsService;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PoolStatusActionTest {

    private final DiagnosticsService service = mock(DiagnosticsService.class);
    private final PoolStatusAction action = new PoolStatusAction(service);
    private final ActionContext ctx = new ActionContext("s1", "call", null, null);

    @Test
    void okSerializesPoolFields() {
        when(service.poolStatus("s1")).thenReturn(DiagnosticResult.ok(new PoolReport(
            "server", 25, 20, 100, 5, 2, "localhost:3306",
            List.of(new DiagnosticRecommendation("warning", "high", null, null, Map.of(), null))
        )));

        Map out = action.handle(ctx, Map.of()).toCompletableFuture().join();

        assertThat(out).containsEntry("scope", "server")
            .containsEntry("activeConnections", 25)
            .containsEntry("idleConnections", 20)
            .containsEntry("maxConnections", 100)
            .containsEntry("threadsRunning", 5)
            .containsEntry("waitingConnections", 2)
            .containsEntry("identifier", "localhost:3306");
        assertThat((List<?>) out.get("recommendations")).hasSize(1);
    }

    @Test
    void unsupportedAndErrorSerialize() {
        when(service.poolStatus("s1")).thenReturn(DiagnosticResult.unsupported("unsupported"));
        assertThat(action.handle(ctx, Map.of()).toCompletableFuture().join()).containsEntry("unsupported", true);

        when(service.poolStatus("s1")).thenReturn(DiagnosticResult.error("X", "msg"));
        Map out = action.handle(ctx, Map.of()).toCompletableFuture().join();
        Map<?, ?> error = (Map<?, ?>) out.get("error");
        assertThat(error.get("type")).isEqualTo("X");
        assertThat(error.get("message")).isEqualTo("msg");
    }
}
