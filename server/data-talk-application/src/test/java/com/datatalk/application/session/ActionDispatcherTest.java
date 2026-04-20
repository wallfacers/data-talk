package com.datatalk.application.session;

import com.datatalk.application.persistence.ActionInvocationRepository;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.registry.JsonSchemaLoader;
import com.datatalk.application.sql.CalciteSqlRiskAnalyzer;
import com.datatalk.application.sql.SqlBearingActionInspector;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionDescriptor;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.event.DtEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionDispatcherTest {

    private final SqlBearingActionInspector inspector = new SqlBearingActionInspector();
    private final CalciteSqlRiskAnalyzer analyzer = new CalciteSqlRiskAnalyzer();

    @Test
    void serverExecutorRunsHandlerAndRecordsInvocation() throws Exception {
        ActionRegistry registry = Mockito.mock(ActionRegistry.class);
        SessionBusRegistry buses = Mockito.mock(SessionBusRegistry.class);
        SessionBus bus = Mockito.mock(SessionBus.class);
        when(buses.getOrCreate(anyString())).thenReturn(bus);
        ActionInvocationRepository invocations = Mockito.mock(ActionInvocationRepository.class);
        ArtifactRepository artifacts = Mockito.mock(ArtifactRepository.class);
        PendingCallRegistry pending = Mockito.mock(PendingCallRegistry.class);
        JsonSchemaLoader schemas = new JsonSchemaLoader(new ObjectMapper());

        when(registry.require("x.ok")).thenReturn(
            new ActionDescriptor("x.ok", com.datatalk.domain.action.Executor.SERVER, "",
                Map.of("type","object"), Map.of("type","object"),
                List.of(), List.of(OntologyEffect.NONE), false, 1000,
                null, Category.MISC)
        );
        Mockito.doReturn(new AlwaysOkHandler()).when(registry).handler("x.ok");

        ActionDispatcher disp = new ActionDispatcher(registry, schemas, buses,
            invocations, artifacts, pending, analyzer, inspector, new ObjectMapper(),
            Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC));

        CompletionStage<Object> out = disp.dispatch("x.ok",
            Map.of("foo", "bar"), "c-1",
            new ActionContext("s-1", "c-1", null, "oc-1"));

        Object result = out.toCompletableFuture().get();
        assertThat(result).isEqualTo(Map.of("echoed", "bar"));
        verify(invocations).start(eq("c-1"), eq("s-1"), eq("x.ok"), any(), anyLong());
        verify(invocations).complete(eq("c-1"), any(), anyLong());
    }

    @Test
    void clientExecutorPushesInvokeAndRegistersPending() {
        ActionRegistry registry = Mockito.mock(ActionRegistry.class);
        SessionBusRegistry buses = Mockito.mock(SessionBusRegistry.class);
        SessionBus bus = Mockito.mock(SessionBus.class);
        when(buses.getOrCreate(anyString())).thenReturn(bus);
        ActionInvocationRepository invocations = Mockito.mock(ActionInvocationRepository.class);
        ArtifactRepository artifacts = Mockito.mock(ArtifactRepository.class);
        PendingCallRegistry pending = Mockito.mock(PendingCallRegistry.class);
        JsonSchemaLoader schemas = new JsonSchemaLoader(new ObjectMapper());

        when(registry.require("x.client")).thenReturn(
            new ActionDescriptor("x.client", com.datatalk.domain.action.Executor.CLIENT, "",
                Map.of("type","object"), Map.of("type","object"),
                List.of(), List.of(OntologyEffect.NONE), false, 500,
                null, Category.MISC)
        );
        Mockito.doReturn(new AlwaysOkHandler()).when(registry).handler("x.client");

        ActionDispatcher disp = new ActionDispatcher(registry, schemas, buses,
            invocations, artifacts, pending, analyzer, inspector, new ObjectMapper(),
            Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC));

        CompletionStage<Object> out = disp.dispatch("x.client",
            Map.of("k", "v"), "c-2",
            new ActionContext("s-1", "c-2", null, "oc-1"));

        verify(pending).register(eq("c-2"), any(), eq(500));
        verify(bus).publish(any(DtEvent.ActionInvoke.class));
        assertThat(out.toCompletableFuture().isDone()).isFalse();  // waits for action_result
    }

    @Test
    void unknownActionThrows() {
        ActionRegistry registry = Mockito.mock(ActionRegistry.class);
        when(registry.require("no.such")).thenThrow(new IllegalArgumentException("Unknown action: no.such"));
        SessionBusRegistry buses = Mockito.mock(SessionBusRegistry.class);
        ActionInvocationRepository invocations = Mockito.mock(ActionInvocationRepository.class);
        ArtifactRepository artifacts = Mockito.mock(ArtifactRepository.class);
        PendingCallRegistry pending = Mockito.mock(PendingCallRegistry.class);
        JsonSchemaLoader schemas = new JsonSchemaLoader(new ObjectMapper());

        ActionDispatcher disp = new ActionDispatcher(registry, schemas, buses,
            invocations, artifacts, pending, analyzer, inspector, new ObjectMapper(),
            Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC));

        assertThatThrownBy(() -> disp.dispatch("no.such", Map.of(), "c-3",
            new ActionContext("s-1", "c-3", null, "oc-1")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown action");
    }

    @Test
    void sqlBearingActionInjectsDynamicRiskIntoContext() throws Exception {
        ActionRegistry registry = Mockito.mock(ActionRegistry.class);
        SessionBusRegistry buses = Mockito.mock(SessionBusRegistry.class);
        when(buses.getOrCreate(anyString())).thenReturn(Mockito.mock(SessionBus.class));
        ActionInvocationRepository invocations = Mockito.mock(ActionInvocationRepository.class);
        ArtifactRepository artifacts = Mockito.mock(ArtifactRepository.class);
        PendingCallRegistry pending = Mockito.mock(PendingCallRegistry.class);
        JsonSchemaLoader schemas = new JsonSchemaLoader(new ObjectMapper());
        CaptureCtxHandler handler = new CaptureCtxHandler();

        when(registry.require("x.sql")).thenReturn(
            new ActionDescriptor("x.sql", com.datatalk.domain.action.Executor.SERVER, "",
                Map.of("type","object"), Map.of("type","object"),
                List.of(), List.of(OntologyEffect.NONE), false, 1000,
                null, Category.MUTATION)
        );
        Mockito.doReturn(handler).when(registry).handler("x.sql");

        ActionDispatcher disp = new ActionDispatcher(registry, schemas, buses,
            invocations, artifacts, pending, analyzer, inspector, new ObjectMapper(),
            Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC));

        Object result = disp.dispatch("x.sql",
            Map.of("sql", "UPDATE orders SET status = 'done'"),
            "c-4",
            new ActionContext("s-1", "c-4", null, "oc-1"))
            .toCompletableFuture().get();

        assertThat(result).isEqualTo(Map.of("ok", true));
        assertThat(handler.ctx.metadata().sqlRisk()).isNotNull();
        assertThat(handler.ctx.metadata().sqlRisk().riskLevel()).isEqualTo(com.datatalk.domain.action.RiskLevel.L3);
        assertThat(handler.ctx.metadata().sqlRisk().requiresStrongConfirmation()).isTrue();
    }

    @Test
    void queryParseFailureFallsBackButStillExecutes() throws Exception {
        ActionRegistry registry = Mockito.mock(ActionRegistry.class);
        SessionBusRegistry buses = Mockito.mock(SessionBusRegistry.class);
        when(buses.getOrCreate(anyString())).thenReturn(Mockito.mock(SessionBus.class));
        ActionInvocationRepository invocations = Mockito.mock(ActionInvocationRepository.class);
        ArtifactRepository artifacts = Mockito.mock(ArtifactRepository.class);
        PendingCallRegistry pending = Mockito.mock(PendingCallRegistry.class);
        JsonSchemaLoader schemas = new JsonSchemaLoader(new ObjectMapper());
        CaptureCtxHandler handler = new CaptureCtxHandler();

        when(registry.require("x.query")).thenReturn(
            new ActionDescriptor("x.query", com.datatalk.domain.action.Executor.SERVER, "",
                Map.of("type","object"), Map.of("type","object"),
                List.of(), List.of(OntologyEffect.NONE), false, 1000,
                null, Category.QUERY)
        );
        Mockito.doReturn(handler).when(registry).handler("x.query");

        ActionDispatcher disp = new ActionDispatcher(registry, schemas, buses,
            invocations, artifacts, pending, analyzer, inspector, new ObjectMapper(),
            Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC));

        Object result = disp.dispatch("x.query",
            Map.of("sql", "SELECT FROM"),
            "c-5",
            new ActionContext("s-1", "c-5", null, "oc-1"))
            .toCompletableFuture().get();

        assertThat(result).isEqualTo(Map.of("ok", true));
        assertThat(handler.ctx.metadata().sqlRisk()).isNotNull();
        assertThat(handler.ctx.metadata().sqlRisk().riskLevel()).isNull();
        assertThat(handler.ctx.metadata().sqlRisk().fallbackUsed()).isTrue();
    }

    static class AlwaysOkHandler implements ActionHandler<Map, Map> {
        @Override public Map<String, Object> inputSchema()  { return Map.of("type","object"); }
        @Override public Map<String, Object> outputSchema() { return Map.of("type","object"); }
        @Override public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.NONE); }
        @Override public Class<Map> inputType() { return Map.class; }
        @SuppressWarnings("unchecked")
        @Override public CompletionStage<Map> handle(ActionContext ctx, Map input) {
            return CompletableFuture.completedFuture(Map.of("echoed", input.get("foo")));
        }
    }

    static class CaptureCtxHandler implements ActionHandler<Map, Map> {
        private ActionContext ctx;

        @Override public Map<String, Object> inputSchema()  { return Map.of("type","object"); }
        @Override public Map<String, Object> outputSchema() { return Map.of("type","object"); }
        @Override public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.NONE); }
        @Override public Class<Map> inputType() { return Map.class; }

        @Override
        public CompletionStage<Map> handle(ActionContext ctx, Map input) {
            this.ctx = ctx;
            return CompletableFuture.completedFuture(Map.of("ok", true));
        }
    }
}
