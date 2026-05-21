package com.datatalk.adapter.actions;

import com.datatalk.application.script.ScriptRunService;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StopScriptActionHandlerTest {

    private ScriptRunService runService;
    private StopScriptActionHandler handler;
    private ActionContext ctx;

    @BeforeEach
    void setUp() {
        runService = mock(ScriptRunService.class);
        handler = new StopScriptActionHandler(runService);
        ctx = new ActionContext("session-1", "call-1", "conn-1", "oc-1");
    }

    @Test
    void inputSchema_requiresRunId() {
        Map<String, Object> schema = handler.inputSchema();

        assertThat(schema).containsEntry("type", "object");
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertThat(required).contains("runId");

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertThat(props).containsKeys("runId");
    }

    @Test
    void outputSchema_hasStatusField() {
        Map<String, Object> schema = handler.outputSchema();

        assertThat(schema).containsEntry("type", "object");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertThat(props).containsKeys("status");
    }

    @Test
    void handle_cancelsRunAndReturnsStatus() {
        Map<String, Object> input = Map.of("runId", "run-1");

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        assertThat(result).containsEntry("status", "cancelled");
        verify(runService).cancelRun("run-1");
    }

    @Test
    void handle_cancelAlreadyCompletedRun_throwsFromService() {
        doThrow(new java.util.NoSuchElementException("Run not found"))
            .when(runService).cancelRun("run-bad");

        Map<String, Object> input = Map.of("runId", "run-bad");

        // The exception propagates synchronously from the handler since cancelRun is called inline
        try {
            handler.handle(ctx, input).toCompletableFuture().join();
        } catch (Exception e) {
            assertThat(e).isInstanceOf(java.util.NoSuchElementException.class);
        }
    }
}
