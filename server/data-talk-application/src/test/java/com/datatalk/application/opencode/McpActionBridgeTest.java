package com.datatalk.application.opencode;

import com.datatalk.application.channel.ChannelService;
import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.session.ActionDispatcher;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionDescriptor;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        ActionRegistry registry = mock(ActionRegistry.class);
        when(registry.mcpExposed()).thenReturn(List.of(descriptor("datatalk.execute_sql", Executor.SERVER, 1_000)));

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
                    Map.of()
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
        assertThat(outcome.output()).isEqualTo(Map.of("message", "front-end rejected action"));
    }

    private static ActionRegistry registry() {
        ActionRegistry registry = mock(ActionRegistry.class);
        when(registry.mcpExposed()).thenReturn(List.of(descriptor("datatalk.execute_sql", Executor.SERVER, 1_000)));
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
