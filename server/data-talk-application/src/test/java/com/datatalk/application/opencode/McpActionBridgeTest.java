package com.datatalk.application.opencode;

import com.datatalk.application.channel.ChannelService;
import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.session.ActionDispatcher;
import com.datatalk.application.stage.StageTabRepository;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionDescriptor;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.event.ErrorInfo;
import com.datatalk.domain.stage.StageTab;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpActionBridgeTest {

    @Test
    void stripsBridgeFieldsAndDelegatesToDispatcher() {
        ActionDispatcher dispatcher = mock(ActionDispatcher.class);
        ActionRegistry registry = registry();

        OpenCodeSessionMap sessionMap = new OpenCodeSessionMap();
        sessionMap.bind("dt-1", "oc-1");
        when(dispatcher.dispatch(any(), any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(Map.of("ok", true)));

        McpActionBridge bridge = new McpActionBridge(dispatcher, registry, sessionMap, "nonce-1");
        var outcome = bridge.handle("execute_sql", new LinkedHashMap<>(Map.of(
            "sql", "select 1",
            "__dtOpenCodeSessionId", "oc-1",
            "__dtCallId", "call-1",
            "__dtBridgeNonce", "nonce-1"
        ))).toCompletableFuture().join();

        assertThat(outcome.isError()).isFalse();
        assertThat(outcome.output()).isEqualTo(Map.of("ok", true));

        ArgumentCaptor<ActionContext> ctx = ArgumentCaptor.forClass(ActionContext.class);
        verify(dispatcher).dispatch(eq("datatalk.execute_sql"), eq(Map.of("sql", "select 1")),
            eq("call-1"), ctx.capture());
        assertThat(ctx.getValue().sessionId()).isEqualTo("dt-1");
        assertThat(ctx.getValue().openCodeSessionId()).isEqualTo("oc-1");
    }

    @Test
    void rejectsMissingSessionContext() {
        McpActionBridge bridge = new McpActionBridge(mock(ActionDispatcher.class), registry(), new OpenCodeSessionMap(), "nonce-1");

        assertThatThrownBy(() -> bridge.handle("execute_sql", Map.of("sql", "select 1")).toCompletableFuture().join())
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(McpActionBridge.McpCallException.class)
            .cause()
            .extracting("code", "message")
            .containsExactly(-32602, "invalid params: missing session context");
    }

    @Test
    void rejectsUnauthenticatedBridgeNonce() {
        OpenCodeSessionMap sessionMap = new OpenCodeSessionMap();
        sessionMap.bind("dt-1", "oc-1");
        McpActionBridge bridge = new McpActionBridge(mock(ActionDispatcher.class), registry(), sessionMap, "nonce-1");

        assertThatThrownBy(() -> bridge.handle("execute_sql", Map.of(
            "sql", "select 1",
            "__dtOpenCodeSessionId", "oc-1",
            "__dtCallId", "call-1",
            "__dtBridgeNonce", "wrong"
        )).toCompletableFuture().join())
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(McpActionBridge.McpCallException.class)
            .cause()
            .extracting("code", "message")
            .containsExactly(-32001, "unauthenticated bridge");
    }

    @Test
    void rejectsUnknownOpenCodeSession() {
        McpActionBridge bridge = new McpActionBridge(mock(ActionDispatcher.class), registry(), new OpenCodeSessionMap(), "nonce-1");

        assertThatThrownBy(() -> bridge.handle("execute_sql", Map.of(
            "sql", "select 1",
            "__dtOpenCodeSessionId", "oc-missing",
            "__dtCallId", "call-1",
            "__dtBridgeNonce", "nonce-1"
        )).toCompletableFuture().join())
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(McpActionBridge.McpCallException.class)
            .cause()
            .extracting("code", "message")
            .containsExactly(-32002, "unknown opencode session");
    }

    @Test
    void mapsClientTimeoutToProtocolError() {
        ActionDispatcher dispatcher = mock(ActionDispatcher.class);
        when(dispatcher.dispatch(any(), any(), any(), any()))
            .thenReturn(CompletableFuture.failedStage(new TimeoutException("timed out")));

        OpenCodeSessionMap sessionMap = new OpenCodeSessionMap();
        sessionMap.bind("dt-1", "oc-1");
        McpActionBridge bridge = new McpActionBridge(dispatcher, registry(), sessionMap, "nonce-1");

        assertThatThrownBy(() -> bridge.handle("execute_sql", Map.of(
            "sql", "select 1",
            "__dtOpenCodeSessionId", "oc-1",
            "__dtCallId", "call-1",
            "__dtBridgeNonce", "nonce-1"
        )).toCompletableFuture().join())
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(McpActionBridge.McpCallException.class)
            .cause()
            .extracting("code", "message")
            .containsExactly(-32003, "client action timed out");
    }

    @Test
    void mapsNoSubscriberToProtocolError() {
        ActionDispatcher dispatcher = mock(ActionDispatcher.class);
        when(dispatcher.dispatch(any(), any(), any(), any()))
            .thenReturn(CompletableFuture.failedStage(new ActionDispatcher.NoClientSubscriberException("no client subscriber")));

        OpenCodeSessionMap sessionMap = new OpenCodeSessionMap();
        sessionMap.bind("dt-1", "oc-1");
        McpActionBridge bridge = new McpActionBridge(dispatcher, registry(), sessionMap, "nonce-1");

        assertThatThrownBy(() -> bridge.handle("execute_sql", Map.of(
            "sql", "select 1",
            "__dtOpenCodeSessionId", "oc-1",
            "__dtCallId", "call-1",
            "__dtBridgeNonce", "nonce-1"
        )).toCompletableFuture().join())
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(McpActionBridge.McpCallException.class)
            .cause()
            .extracting("code", "message")
            .containsExactly(-32004, "no client subscriber");
    }

    @Test
    void turnsToolExecutionErrorsIntoIsErrorOutcome() {
        ActionDispatcher dispatcher = mock(ActionDispatcher.class);
        when(dispatcher.dispatch(any(), any(), any(), any()))
            .thenReturn(CompletableFuture.failedStage(new ChannelService.ActionResultError(
                new com.datatalk.domain.event.ErrorInfo(
                    "client.failed",
                    "front-end rejected action",
                    false,
                    Map.of(
                        "currentState", Map.of("tabId", "qe-1", "payloadVersion", 14),
                        "markdown", "## Edit failed",
                        "details", Map.of("reason", "version drift")
                    )
                )
            )));

        OpenCodeSessionMap sessionMap = new OpenCodeSessionMap();
        sessionMap.bind("dt-1", "oc-1");
        McpActionBridge bridge = new McpActionBridge(dispatcher, registry(), sessionMap, "nonce-1");

        var outcome = bridge.handle("execute_sql", Map.of(
            "sql", "select 1",
            "__dtOpenCodeSessionId", "oc-1",
            "__dtCallId", "call-1",
            "__dtBridgeNonce", "nonce-1"
        )).toCompletableFuture().join();

        assertThat(outcome.isError()).isTrue();
        assertThat(outcome.output())
            .isInstanceOf(Map.class)
            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
            .containsEntry("code", "client.failed")
            .containsEntry("message", "front-end rejected action")
            .containsEntry("markdown", "## Edit failed")
            .containsEntry("currentState", Map.of("tabId", "qe-1", "payloadVersion", 14))
            .containsEntry("details", Map.of("reason", "version drift"));
    }

    @Test
    void clientErrorWithoutDetailsStillCarriesCodeAndMessage() {
        ActionDispatcher dispatcher = mock(ActionDispatcher.class);
        when(dispatcher.dispatch(any(), any(), any(), any()))
            .thenReturn(CompletableFuture.failedStage(new ChannelService.ActionResultError(
                new ErrorInfo(
                    "tab_not_found",
                    "tab no longer exists",
                    false,
                    null
                )
            )));

        OpenCodeSessionMap sessionMap = new OpenCodeSessionMap();
        sessionMap.bind("dt-1", "oc-1");
        McpActionBridge bridge = new McpActionBridge(dispatcher, registry(), sessionMap, "nonce-1");

        var outcome = bridge.handle("execute_sql", Map.of(
            "sql", "select 1",
            "__dtOpenCodeSessionId", "oc-1",
            "__dtCallId", "call-1",
            "__dtBridgeNonce", "nonce-1"
        )).toCompletableFuture().join();

        assertThat(outcome.isError()).isTrue();
        assertThat(outcome.output())
            .isInstanceOf(Map.class)
            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
            .containsEntry("code", "tab_not_found")
            .containsEntry("message", "tab no longer exists")
            .doesNotContainKeys("currentState", "markdown", "details");
    }

    @Test
    void synthesizesMarkdownForVersionConflictWhenClientOmitsMarkdown() {
        ActionDispatcher dispatcher = mock(ActionDispatcher.class);
        when(dispatcher.dispatch(any(), any(), any(), any()))
            .thenReturn(CompletableFuture.failedStage(new ChannelService.ActionResultError(
                new ErrorInfo(
                    "version_conflict",
                    "stale baseVersion",
                    false,
                    Map.of("currentState", Map.of("version", 5))
                )
            )));

        StageTabRepository stageTabRepository = mock(StageTabRepository.class);
        long now = System.currentTimeMillis();
        when(stageTabRepository.findById("qe-1")).thenReturn(Optional.of(new StageTab(
            "qe-1",
            "query_editor",
            "Shared SQL",
            null,
            null,
            null,
            "sess-1",
            5,
            false,
            false,
            null,
            now - 60_000L,
            now - 2_000L
        )));

        OpenCodeSessionMap sessionMap = new OpenCodeSessionMap();
        sessionMap.bind("dt-1", "oc-1");
        OpenCodeBridgeStatus bridgeStatus = new OpenCodeBridgeStatus(Clock.systemUTC());
        bridgeStatus.rotateNonce("nonce-1");
        McpActionBridge bridge = new McpActionBridge(
            dispatcher,
            execRegistry(),
            sessionMap,
            bridgeStatus,
            new McpArgumentsNormalizer(new ObjectMapper()),
            stageTabRepository
        );

        var outcome = bridge.handle("ui_exec", new LinkedHashMap<>(Map.of(
            "object", "query_editor",
            "target", "qe-1",
            "action", "apply_text_edits",
            "params", Map.of("baseVersion", 4),
            "__dtOpenCodeSessionId", "oc-1",
            "__dtCallId", "call-1",
            "__dtBridgeNonce", "nonce-1"
        ))).toCompletableFuture().join();

        assertThat(outcome.isError()).isTrue();
        assertThat(outcome.output())
            .isInstanceOf(Map.class)
            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
            .containsEntry("code", "version_conflict")
            .containsEntry("message", "stale baseVersion")
            .containsEntry("currentState", Map.of("version", 5))
            .extractingByKey("markdown")
            .asString()
            .contains("Reason: version_conflict")
            .contains("baseVersion=4")
            .contains("current version=5")
            .contains("Tab: `qe-1` (Shared SQL)");
    }

    private static ActionRegistry registry() {
        ActionRegistry registry = mock(ActionRegistry.class);
        ActionDescriptor descriptor = descriptor("datatalk.execute_sql", Executor.SERVER, 1_000);
        when(registry.mcpExposed()).thenReturn(List.of(descriptor));
        when(registry.require("datatalk.execute_sql")).thenReturn(descriptor);
        return registry;
    }

    private static ActionRegistry execRegistry() {
        ActionRegistry registry = mock(ActionRegistry.class);
        ActionDescriptor descriptor = descriptor("datatalk.ui.exec", Executor.CLIENT, 1_000);
        when(registry.mcpExposed()).thenReturn(List.of(descriptor));
        when(registry.require("datatalk.ui.exec")).thenReturn(descriptor);
        return registry;
    }

    private static ActionDescriptor descriptor(String id, Executor executor, int timeoutMs) {
        return new ActionDescriptor(
            id,
            executor,
            "desc",
            Map.of("type", "object"),
            Map.of("type", "object"),
            List.of(),
            List.of(OntologyEffect.NONE),
            false,
            timeoutMs,
            null,
            null,
            true
        );
    }
}
