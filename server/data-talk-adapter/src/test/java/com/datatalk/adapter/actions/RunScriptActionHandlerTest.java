package com.datatalk.adapter.actions;

import com.datatalk.application.script.ScriptRunService;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.script.ScriptLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RunScriptActionHandlerTest {

    private ScriptRunService runService;
    private RunScriptActionHandler handler;
    private ActionContext ctx;

    @BeforeEach
    void setUp() {
        runService = mock(ScriptRunService.class);
        handler = new RunScriptActionHandler(runService);
        ctx = new ActionContext("session-1", "call-1", "conn-1", "oc-1");
    }

    @Test
    void inputSchema_hasRequiredFields() {
        Map<String, Object> schema = handler.inputSchema();

        assertThat(schema).containsEntry("type", "object");
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertThat(required).contains("scriptContent", "language", "connectionId");

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertThat(props).containsKeys("scriptContent", "language", "connectionId", "name",
            "createdByKind");

        @SuppressWarnings("unchecked")
        Map<String, Object> languageProp = (Map<String, Object>) props.get("language");
        @SuppressWarnings("unchecked")
        List<String> enumValues = (List<String>) languageProp.get("enum");
        assertThat(enumValues).contains("python", "javascript");
    }

    @Test
    void outputSchema_hasRunIdAndToken() {
        Map<String, Object> schema = handler.outputSchema();

        assertThat(schema).containsEntry("type", "object");
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertThat(required).contains("runId", "token");

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertThat(props).containsKeys("runId", "token");
    }

    @Test
    void sideEffects_returnsNone() {
        assertThat(handler.sideEffects()).isEqualTo(List.of(
            com.datatalk.domain.action.OntologyEffect.NONE));
    }

    @Test
    void handle_preparesRunAndReturnsResult() {
        when(runService.prepareRun(eq("print('hello')"), eq(ScriptLanguage.PYTHON),
            eq("conn-1"), eq("MyScript"), eq("ai"), eq("session-1")))
            .thenReturn(new ScriptRunService.RunPrepareResult("run-abc", "token-xyz"));

        Map<String, Object> input = Map.of(
            "scriptContent", "print('hello')",
            "language", "python",
            "connectionId", "conn-1",
            "name", "MyScript",
            "createdByKind", "ai"
        );

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        assertThat(result).containsEntry("runId", "run-abc");
        assertThat(result).containsEntry("token", "token-xyz");

        verify(runService).prepareRun("print('hello')", ScriptLanguage.PYTHON,
            "conn-1", "MyScript", "ai", "session-1");
    }

    @Test
    void handle_defaultsCreatedByKindToAi() {
        when(runService.prepareRun(eq("code"), any(), eq("conn-1"), isNull(),
            eq("ai"), eq("session-1")))
            .thenReturn(new ScriptRunService.RunPrepareResult("run-1", "tok-1"));

        Map<String, Object> input = Map.of(
            "scriptContent", "code",
            "language", "python",
            "connectionId", "conn-1"
        );

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        assertThat(result).containsEntry("runId", "run-1");
        verify(runService).prepareRun("code", ScriptLanguage.PYTHON,
            "conn-1", null, "ai", "session-1");
    }

    @Test
    void handle_javascriptLanguage_delegatesCorrectly() {
        when(runService.prepareRun(eq("console.log(1)"), eq(ScriptLanguage.JAVASCRIPT),
            eq("conn-2"), any(), any(), any()))
            .thenReturn(new ScriptRunService.RunPrepareResult("run-js", "tok-js"));

        Map<String, Object> input = Map.of(
            "scriptContent", "console.log(1)",
            "language", "javascript",
            "connectionId", "conn-2"
        );

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        assertThat(result).containsEntry("runId", "run-js");
        assertThat(result).containsEntry("token", "tok-js");
    }
}
