package com.datatalk.adapter.actions;

import com.datatalk.application.script.ScriptRunService;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.script.ScriptLanguage;
import com.datatalk.domain.script.ScriptRun;
import com.datatalk.domain.script.ScriptStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ListScriptRunsActionHandlerTest {

    private ScriptRunService runService;
    private ListScriptRunsActionHandler handler;
    private ActionContext ctx;

    private static final Instant NOW = Instant.parse("2026-05-18T10:00:00Z");

    @BeforeEach
    void setUp() {
        runService = mock(ScriptRunService.class);
        handler = new ListScriptRunsActionHandler(runService);
        ctx = new ActionContext("session-1", "call-1", "conn-1", "oc-1");
    }

    @Test
    void inputSchema_hasOptionalConnectionIdAndLimit() {
        Map<String, Object> schema = handler.inputSchema();

        assertThat(schema).containsEntry("type", "object");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertThat(props).containsKeys("connectionId", "limit");
    }

    @Test
    void outputSchema_requiresRunsArray() {
        Map<String, Object> schema = handler.outputSchema();

        assertThat(schema).containsEntry("type", "object");
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertThat(required).contains("runs");
    }

    @Test
    void handle_returnsRunList() {
        ScriptRun run1 = new ScriptRun(
            "run-1", "print(1)", ScriptLanguage.PYTHON, ScriptStatus.COMPLETED,
            0, "OK", "conn-1", "tbl1", 42,
            "MyScript", "ai", "session-1",
            null, NOW, NOW.plusSeconds(30), 30000L
        );
        ScriptRun run2 = new ScriptRun(
            "run-2", "console.log(1)", ScriptLanguage.JAVASCRIPT, ScriptStatus.FAILED,
            1, null, "conn-1", null, 0,
            "JS Script", "user", "session-2",
            "Error: boom", NOW.minusSeconds(60), NOW.minusSeconds(30), 30000L
        );

        when(runService.listRuns("conn-1", 50)).thenReturn(List.of(run1, run2));

        Map<String, Object> input = Map.of("connectionId", "conn-1");

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        assertThat(result).containsKey("runs");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> runs = (List<Map<String, Object>>) result.get("runs");
        assertThat(runs).hasSize(2);

        Map<String, Object> first = runs.get(0);
        assertThat(first).containsEntry("id", "run-1");
        assertThat(first).containsEntry("language", "python");
        assertThat(first).containsEntry("status", "completed");
        assertThat(first).containsEntry("exitCode", 0);
        assertThat(first).containsEntry("connectionId", "conn-1");
        assertThat(first).containsEntry("targetTable", "tbl1");
        assertThat(first).containsEntry("rowsWritten", 42);
        assertThat(first).containsEntry("name", "MyScript");
        assertThat(first).containsEntry("durationMs", 30000L);

        Map<String, Object> second = runs.get(1);
        assertThat(second).containsEntry("id", "run-2");
        assertThat(second).containsEntry("status", "failed");
        assertThat(second).containsEntry("targetTable", null);
    }

    @Test
    void handle_emptyResults_returnsEmptyList() {
        when(runService.listRuns(null, 50)).thenReturn(List.of());

        Map<String, Object> input = Map.of();

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> runs = (List<Map<String, Object>>) result.get("runs");
        assertThat(runs).isEmpty();
    }

    @Test
    void handle_customLimit_passedToService() {
        when(runService.listRuns("conn-1", 10)).thenReturn(List.of());

        Map<String, Object> input = Map.of("connectionId", "conn-1", "limit", 10);

        handler.handle(ctx, input).toCompletableFuture().join();

        verify(runService).listRuns("conn-1", 10);
    }

    @Test
    void handle_defaultLimit_is50() {
        when(runService.listRuns(null, 50)).thenReturn(List.of());

        Map<String, Object> input = Map.of();

        handler.handle(ctx, input).toCompletableFuture().join();

        verify(runService).listRuns(null, 50);
    }
}
