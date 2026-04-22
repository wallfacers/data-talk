package com.datatalk.application.opencode;

import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.domain.action.ActionDescriptor;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenCodeGatewayTest {

    @Test
    void registerToolsPushesEveryDescriptor() {
        ActionRegistry registry = mock(ActionRegistry.class);
        when(registry.all()).thenReturn(List.of(
            new ActionDescriptor("a.one", Executor.SERVER, "First",
                Map.of("type","object"), Map.of("type","object"),
                List.of(), List.of(OntologyEffect.NONE), false, 1000,
                null, null),
            new ActionDescriptor("a.two", Executor.CLIENT, "Second",
                Map.of("type","object"), Map.of("type","object"),
                List.of(), List.of(OntologyEffect.NONE), false, 1000,
                null, null)
        ));

        StubToolPusher pusher = new StubToolPusher();
        OpenCodeGateway gw = new OpenCodeGateway(registry, pusher,
            (sessionId, body) -> {}, () -> "ocsid-1", sid -> {},
            (ocSid, limit) -> { throw new UnsupportedOperationException("lister stub"); },
            "http://localhost:8080");

        gw.registerTools();

        assertThat(pusher.pushed).hasSize(2);
        assertThat(pusher.pushed.get(0).name).isEqualTo("a.one");
        assertThat(pusher.pushed.get(0).callbackUrl)
            .isEqualTo("http://localhost:8080/api/opencode-tool/a.one");
    }

    @Test
    void createSessionReturnsOpenCodeId() {
        ActionRegistry registry = mock(ActionRegistry.class);
        when(registry.all()).thenReturn(List.of());
        OpenCodeGateway gw = new OpenCodeGateway(registry, new StubToolPusher(),
            (s, body) -> {}, () -> "oc-42", sid -> {},
            (ocSid, limit) -> { throw new UnsupportedOperationException("lister stub"); },
            "http://x");
        assertThat(gw.createOpenCodeSession()).isEqualTo("oc-42");
    }

    @Test
    void deleteOpenCodeSessionInvokesDeleter() {
        ActionRegistry registry = mock(ActionRegistry.class);
        when(registry.all()).thenReturn(List.of());
        List<String> deleted = new ArrayList<>();
        OpenCodeGateway gw = new OpenCodeGateway(registry, new StubToolPusher(),
            (s, body) -> {}, () -> "oc-1", deleted::add,
            (ocSid, limit) -> { throw new UnsupportedOperationException("lister stub"); },
            "http://x");
        gw.deleteOpenCodeSession("ses_zzz");
        assertThat(deleted).containsExactly("ses_zzz");
    }

    @Test
    void listMessagesDelegatesToLister() {
        AtomicReference<String> capturedOcSid = new AtomicReference<>();
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode fixture = mapper.createArrayNode();
        fixture.add(mapper.createObjectNode().put("test", 1));

        ActionRegistry registry = mock(ActionRegistry.class);
        when(registry.all()).thenReturn(List.of());
        OpenCodeGateway gateway = new OpenCodeGateway(
            registry,
            new StubToolPusher(),
            (sessionId, body) -> {},
            () -> "ocsid-1",
            sid -> {},
            (ocSid, limit) -> { capturedOcSid.set(ocSid); return fixture; },
            "http://localhost:8080");

        JsonNode result = gateway.listMessages("ses_abc", 50);

        assertThat(capturedOcSid.get()).isEqualTo("ses_abc");
        assertThat(result.isArray()).isTrue();
        assertThat(result).hasSize(1);
    }

    @Test
    void abortOpenCodeSessionInvokesAborter() {
        ActionRegistry registry = mock(ActionRegistry.class);
        when(registry.all()).thenReturn(List.of());
        List<String> abortedSessionIds = new ArrayList<>();

        OpenCodeGateway gateway = new OpenCodeGateway(
            registry,
            new StubToolPusher(),
            (sessionId, body) -> {},
            () -> "ocsid-1",
            sid -> {},
            abortedSessionIds::add,
            (ocSid, limit) -> { throw new UnsupportedOperationException("lister stub"); },
            "http://localhost:8080"
        );

        boolean aborted = gateway.abortOpenCodeSession("ses_abort");

        assertThat(aborted).isTrue();
        assertThat(abortedSessionIds).containsExactly("ses_abort");
    }

    static class StubToolPusher implements OpenCodeGateway.ToolPusher {
        final List<StubCall> pushed = new ArrayList<>();
        @Override public void push(String name, String description,
                                   Map<String, Object> parameters, String callbackUrl) {
            pushed.add(new StubCall(name, description, parameters, callbackUrl));
        }
        record StubCall(String name, String description,
                        Map<String, Object> parameters, String callbackUrl) {}
    }
}
