package com.datatalk.application.opencode;

import com.datatalk.application.channel.ChannelService;
import com.datatalk.application.persistence.ActionInvocationRepository;
import com.datatalk.application.persistence.EventRepository;
import com.datatalk.application.registry.JsonSchemaLoader;
import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.session.ActionDispatcher;
import com.datatalk.application.session.PendingCallRegistry;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.application.stage.StageTabRepository;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionDescriptor;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.event.ErrorInfo;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.stage.StageTab;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Test
    void clientUiExecRoundTripsThroughSessionBusAndActionResultForTwoSessions() throws Exception {
        ObjectMapper om = new ObjectMapper();
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC);
        EventRepository events = mock(EventRepository.class);
        when(events.maxEventId(anyString())).thenReturn(0L);
        org.mockito.Mockito.doNothing().when(events).append(anyString(), anyLong(), anyString(), anyString(), anyLong());
        SessionBusRegistry buses = new SessionBusRegistry(
            events, om, clock, 100, Duration.ofMinutes(5), Duration.ofMillis(1), Duration.ofSeconds(30));
        PendingCallRegistry pending = new PendingCallRegistry();
        ActionInvocationRepository invocations = mock(ActionInvocationRepository.class);

        ActionDispatcher dispatcher = new ActionDispatcher(
            execRegistry(),
            new JsonSchemaLoader(om),
            buses,
            invocations,
            mock(com.datatalk.application.persistence.ArtifactRepository.class),
            pending,
            new com.datatalk.application.sql.CalciteSqlRiskAnalyzer((kind, sql) ->
                java.util.Arrays.stream(sql.split(";"))
                    .map(String::trim)
                    .filter(part -> !part.isEmpty())
                    .toList()
            ),
            new com.datatalk.application.sql.SqlBearingActionInspector(),
            mock(com.datatalk.application.persistence.ConnectionRepository.class),
            om,
            clock
        );
        ChannelService channel = new ChannelService(
            mock(com.datatalk.application.persistence.SessionRepository.class),
            buses,
            pending,
            clock,
            mock(OpenCodeGateway.class),
            new OpenCodeSessionMap(),
            mock(com.datatalk.application.ai.AiUserPrefsRepository.class),
            mock(com.datatalk.application.i18n.Translator.class),
            new com.datatalk.application.channel.PendingFileUploadEchoRegistry()
        );

        StageTabRepository stageTabRepository = mock(StageTabRepository.class);
        when(stageTabRepository.findById("qe-1")).thenReturn(Optional.of(new StageTab(
            "qe-1", "query_editor", "Shared SQL",
            null, null, null, "dt-a",
            7, false, false, null, 900L, 950L
        )));

        OpenCodeSessionMap sessionMap = new OpenCodeSessionMap();
        sessionMap.bind("dt-a", "oc-a");
        sessionMap.bind("dt-b", "oc-b");
        OpenCodeBridgeStatus bridgeStatus = new OpenCodeBridgeStatus(clock);
        bridgeStatus.rotateNonce("nonce-1");
        McpActionBridge bridge = new McpActionBridge(
            dispatcher,
            execRegistry(),
            sessionMap,
            bridgeStatus,
            new McpArgumentsNormalizer(om),
            stageTabRepository
        );

        BlockingQueue<DtEvent.ActionInvoke> sessionAInvokes = subscribeActions(buses.getOrCreate("dt-a"), "client-a");
        BlockingQueue<DtEvent.ActionInvoke> sessionBInvokes = subscribeActions(buses.getOrCreate("dt-b"), "client-b");

        CompletableFuture<McpActionBridge.ToolCallOutcome> okCall = bridge.handle("ui_exec", uiExecArgs(
            "oc-a", "call-ok", "qe-1", 6)).toCompletableFuture();
        CompletableFuture<McpActionBridge.ToolCallOutcome> staleCall = bridge.handle("ui_exec", uiExecArgs(
            "oc-b", "call-stale", "qe-1", 6)).toCompletableFuture();

        DtEvent.ActionInvoke okInvoke = takeAction(sessionAInvokes);
        DtEvent.ActionInvoke staleInvoke = takeAction(sessionBInvokes);

        assertThat(okInvoke.callId()).isEqualTo("call-ok");
        assertThat(okInvoke.actionId()).isEqualTo("datatalk.ui.exec");
        assertThat(okInvoke.input()).containsEntry("target", "qe-1");
        assertThat(staleInvoke.callId()).isEqualTo("call-stale");
        assertThat(staleInvoke.actionId()).isEqualTo("datatalk.ui.exec");
        assertThat(staleInvoke.input()).containsEntry("target", "qe-1");

        channel.completeActionResult(okInvoke.callId(), true, Map.of("success", true), null);
        channel.completeActionResult(staleInvoke.callId(), false, null, new ErrorInfo(
            "expected_text_mismatch",
            "stale expected text",
            false,
            Map.of(
                "currentState", Map.of("tabId", "qe-1", "version", 7),
                "details", Map.of(
                    "editIndex", 0,
                    "expected", "select 1",
                    "actual", "select 2"
                )
            )
        ));

        assertThat(okCall.get(1, TimeUnit.SECONDS).isError()).isFalse();
        assertThat(staleCall.get(1, TimeUnit.SECONDS).isError()).isTrue();
        assertThat(staleCall.get().output())
            .isInstanceOf(Map.class)
            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
            .containsEntry("code", "expected_text_mismatch")
            .containsEntry("message", "stale expected text")
            .extractingByKey("markdown")
            .asString()
            .contains("Reason: expected_text_mismatch")
            .contains("Tab: `qe-1` (Shared SQL)");

        CompletableFuture<McpActionBridge.ToolCallOutcome> versionCall = bridge.handle("ui_exec", uiExecArgs(
            "oc-a", "call-version", "qe-1", 5)).toCompletableFuture();
        DtEvent.ActionInvoke versionInvoke = takeAction(sessionAInvokes);
        assertThat(versionInvoke.callId()).isEqualTo("call-version");
        channel.completeActionResult(versionInvoke.callId(), false, null, new ErrorInfo(
            "version_conflict",
            "stale baseVersion",
            false,
            Map.of("currentState", Map.of("tabId", "qe-1", "version", 7))
        ));

        assertThat(versionCall.get(1, TimeUnit.SECONDS).isError()).isTrue();
        assertThat(versionCall.get().output())
            .isInstanceOf(Map.class)
            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
            .containsEntry("code", "version_conflict")
            .containsEntry("message", "stale baseVersion")
            .extractingByKey("markdown")
            .asString()
            .contains("Reason: version_conflict")
            .contains("baseVersion=5")
            .contains("current version=7");

        buses.close("dt-a");
        buses.close("dt-b");
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

    private static BlockingQueue<DtEvent.ActionInvoke> subscribeActions(SessionBus bus, String clientId) {
        BlockingQueue<DtEvent.ActionInvoke> actions = new LinkedBlockingQueue<>();
        bus.subscribe(clientId, 0, numbered -> {
            if (numbered.event() instanceof DtEvent.ActionInvoke action) {
                actions.offer(action);
            }
        });
        return actions;
    }

    private static DtEvent.ActionInvoke takeAction(BlockingQueue<DtEvent.ActionInvoke> actions) throws InterruptedException {
        DtEvent.ActionInvoke action = actions.poll(1, TimeUnit.SECONDS);
        assertThat(action).isNotNull();
        return action;
    }

    private static Map<String, Object> uiExecArgs(String ocSid, String callId, String target, int baseVersion) {
        return new LinkedHashMap<>(Map.of(
            "object", "query_editor",
            "target", target,
            "action", "apply_text_edits",
            "params", Map.of(
                "baseVersion", baseVersion,
                "edits", List.of(Map.of(
                    "range", Map.of("startLine", 1, "endLine", 1),
                    "text", "select 2",
                    "expectedText", "select 1"
                ))
            ),
            "__dtOpenCodeSessionId", ocSid,
            "__dtCallId", callId,
            "__dtBridgeNonce", "nonce-1"
        ));
    }
}
