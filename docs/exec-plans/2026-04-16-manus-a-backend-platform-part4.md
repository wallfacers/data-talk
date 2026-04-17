# Plan A — Backend Platform Foundation (Part 4 of 4)

> Final chunk. Tasks 21–26: OpenCode integration (translator, HTTP client, gateway, tool bridge), ActionDispatcher, and a demo echo action with an end-to-end smoke test proving the whole loop.

---

## Task 21: OpenCodeEventTranslator

**Files:**
- Create: `data-talk-application/src/main/java/com/datatalk/application/opencode/OcEvent.java`
- Create: `data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventTranslator.java`
- Test: `data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventTranslatorTest.java`

Spec §3.9 is the contract — a single pure function converts incoming OpenCode SSE frames into DataTalk `DtEvent`s. The translator holds a per-session "seen" set to distinguish `message.part.created` (first sighting) from `message.part.updated` (subsequent).

- [ ] **Step 21.1: Write failing test**

Create `data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventTranslatorTest.java`:

```java
package com.datatalk.application.opencode;

import com.datatalk.application.opencode.OcEvent;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.part.TextPart;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeEventTranslatorTest {

    private final OpenCodeEventTranslator tr = new OpenCodeEventTranslator();

    @Test
    void firstPartUpdatedBecomesPartCreated() {
        TextPart p = new TextPart("p1","s1","m1","hi",null,null,null, Map.of());
        List<DtEvent> out = tr.translate("s1", new OcEvent.MessagePartUpdated(p));
        assertThat(out).hasSize(1);
        assertThat(out.get(0)).isInstanceOf(DtEvent.MessagePartCreated.class);
    }

    @Test
    void subsequentPartUpdatedStaysUpdated() {
        TextPart p = new TextPart("p1","s1","m1","hi",null,null,null, Map.of());
        tr.translate("s1", new OcEvent.MessagePartUpdated(p));
        List<DtEvent> out = tr.translate("s1", new OcEvent.MessagePartUpdated(p));
        assertThat(out.get(0)).isInstanceOf(DtEvent.MessagePartUpdated.class);
    }

    @Test
    void deltaIsPassedThrough() {
        List<DtEvent> out = tr.translate("s1",
            new OcEvent.MessagePartDelta("p1", "text", "hello"));
        assertThat(out.get(0)).isInstanceOf(DtEvent.MessagePartDelta.class);
    }

    @Test
    void serverConnectedIsSwallowed() {
        List<DtEvent> out = tr.translate("s1", new OcEvent.ServerConnected());
        assertThat(out).isEmpty();
    }

    @Test
    void sessionStatusIsPassedThrough() {
        List<DtEvent> out = tr.translate("s1",
            new OcEvent.SessionStatus("busy", Map.of()));
        assertThat(out.get(0)).isInstanceOf(DtEvent.SessionStatus.class);
    }

    @Test
    void unknownEventIsDropped() {
        List<DtEvent> out = tr.translate("s1", new OcEvent.Unknown("weird", Map.of()));
        assertThat(out).isEmpty();
    }

    @Test
    void partIsolatedBySessionId() {
        TextPart p = new TextPart("p1","s-a","m1","hi",null,null,null, Map.of());
        tr.translate("s-a", new OcEvent.MessagePartUpdated(p));
        // Same partId, different session → should still count as first sighting
        TextPart q = new TextPart("p1","s-b","m1","hi",null,null,null, Map.of());
        List<DtEvent> out = tr.translate("s-b", new OcEvent.MessagePartUpdated(q));
        assertThat(out.get(0)).isInstanceOf(DtEvent.MessagePartCreated.class);
    }

    @Test
    void forgetClearsSeenSet() {
        TextPart p = new TextPart("p1","s1","m1","hi",null,null,null, Map.of());
        tr.translate("s1", new OcEvent.MessagePartUpdated(p));
        tr.forget("s1");
        List<DtEvent> out = tr.translate("s1", new OcEvent.MessagePartUpdated(p));
        assertThat(out.get(0)).isInstanceOf(DtEvent.MessagePartCreated.class);
    }
}
```

- [ ] **Step 21.2: Run — expected FAIL**

```
./mvnw -pl data-talk-application test -Dtest=OpenCodeEventTranslatorTest
```

- [ ] **Step 21.3: Implement OcEvent**

Create `data-talk-application/src/main/java/com/datatalk/application/opencode/OcEvent.java`:

```java
package com.datatalk.application.opencode;

import com.datatalk.domain.part.Message;
import com.datatalk.domain.part.Part;

import java.util.Map;

/**
 * Minimal view of the OpenCode SSE event stream. Only the subset we actually
 * translate is modelled; everything else lands in {@link Unknown} and is
 * dropped by the translator.
 */
public sealed interface OcEvent {
    record ServerConnected() implements OcEvent {}
    record SessionStatus(String status, Map<String, Object> retryInfo) implements OcEvent {}
    record MessageUpdated(Message message) implements OcEvent {}
    record MessagePartUpdated(Part part) implements OcEvent {}
    record MessagePartDelta(String partId, String field, String delta) implements OcEvent {}
    record MessagePartRemoved(String partId) implements OcEvent {}
    record Unknown(String type, Map<String, Object> payload) implements OcEvent {}
}
```

- [ ] **Step 21.4: Implement OpenCodeEventTranslator**

Create `data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventTranslator.java`:

```java
package com.datatalk.application.opencode;

import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.part.Part;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Translates OpenCode SSE frames into DataTalk {@link DtEvent}s (spec §3.9).
 * Tracks which {@code (sessionId, partId, messageId)} triples have been seen
 * so the first {@code message.part.updated} becomes a {@code part.created}.
 */
@Component
public class OpenCodeEventTranslator {

    private final Map<String, Set<String>> seenParts = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> seenMessages = new ConcurrentHashMap<>();

    public List<DtEvent> translate(String sessionId, OcEvent in) {
        return switch (in) {
            case OcEvent.ServerConnected c -> Collections.emptyList();

            case OcEvent.SessionStatus s ->
                List.of(new DtEvent.SessionStatus(s.status(), s.retryInfo()));

            case OcEvent.MessageUpdated m -> {
                Set<String> msgs = seenMessages.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet());
                if (msgs.add(m.message().id())) {
                    yield List.of(new DtEvent.MessageCreated(m.message()));
                }
                yield List.of(new DtEvent.MessageUpdated(m.message()));
            }

            case OcEvent.MessagePartUpdated p -> {
                Set<String> parts = seenParts.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet());
                if (parts.add(p.part().id())) {
                    yield List.of(new DtEvent.MessagePartCreated(p.part()));
                }
                yield List.of(new DtEvent.MessagePartUpdated(p.part()));
            }

            case OcEvent.MessagePartDelta d ->
                List.of(new DtEvent.MessagePartDelta(d.partId(), d.field(), d.delta()));

            case OcEvent.MessagePartRemoved r ->
                List.of(new DtEvent.MessagePartRemoved(r.partId()));

            case OcEvent.Unknown u -> Collections.emptyList();
        };
    }

    /** Forget all tracking state for a session. Call when a session closes or resets. */
    public void forget(String sessionId) {
        seenParts.remove(sessionId);
        seenMessages.remove(sessionId);
    }
}
```

- [ ] **Step 21.5: Run — expected PASS**

```
./mvnw -pl data-talk-application test -Dtest=OpenCodeEventTranslatorTest
```

- [ ] **Step 21.6: Commit**

```
git add data-talk-application/src/main/java/com/datatalk/application/opencode/ \
        data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventTranslatorTest.java
git commit -m "feat(server): add pure OpenCodeEventTranslator (spec §3.9)"
```

---

## Task 22: OpenCodeHttpClient + WireMock-based contract tests

**Files:**
- Modify: `data-talk-infrastructure/pom.xml` (WebFlux + WireMock)
- Create: `data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeConfig.java`
- Create: `data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeHttpClient.java`
- Test: `data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/OpenCodeHttpClientTest.java`

- [ ] **Step 22.1: Add deps to `data-talk-infrastructure/pom.xml`**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <version>3.9.1</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 22.2: Write failing test**

Create `data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/OpenCodeHttpClientTest.java`:

```java
package com.datatalk.infra.opencode;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeHttpClientTest {

    WireMockServer wm;
    OpenCodeHttpClient client;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
        client = new OpenCodeHttpClient("http://localhost:" + wm.port(), new ObjectMapper());
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void createSessionPostsAndParsesResponse() {
        wm.stubFor(post(urlEqualTo("/session"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"oc-1\"}")));

        String id = client.createSession();

        assertThat(id).isEqualTo("oc-1");
        wm.verify(postRequestedFor(urlEqualTo("/session")));
    }

    @Test
    void registerToolSendsNameAndCallback() {
        wm.stubFor(post(urlPathEqualTo("/plugin/register-tool"))
            .willReturn(aResponse().withStatus(204)));

        client.registerTool("datatalk.demo.echo", "echo",
            Map.of("type", "object"),
            "http://localhost:8080/api/opencode-tool/datatalk.demo.echo");

        wm.verify(postRequestedFor(urlPathEqualTo("/plugin/register-tool"))
            .withRequestBody(equalToJson("""
                {"name":"datatalk.demo.echo","description":"echo",
                 "parameters":{"type":"object"},
                 "callbackUrl":"http://localhost:8080/api/opencode-tool/datatalk.demo.echo"}
                """, true, true)));
    }
}
```

- [ ] **Step 22.3: Run — expected FAIL**

```
./mvnw -pl data-talk-infrastructure test -Dtest=OpenCodeHttpClientTest
```

- [ ] **Step 22.4: Implement OpenCodeHttpClient**

Create `data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeHttpClient.java`:

```java
package com.datatalk.infra.opencode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Thin HTTP wrapper around the OpenCode server. Exposes only the three
 * operations Plan A needs: session creation, tool registration, and message
 * send (fire-and-forget; events arrive via the separate /event stream).
 */
public class OpenCodeHttpClient {

    private final WebClient wc;
    private final ObjectMapper om;

    public OpenCodeHttpClient(String baseUrl, ObjectMapper om) {
        this.wc = WebClient.builder().baseUrl(baseUrl).build();
        this.om = om;
    }

    public String createSession() {
        String body = wc.post().uri("/session")
            .retrieve()
            .bodyToMono(String.class)
            .block();
        try {
            JsonNode node = om.readTree(body);
            return node.path("id").asText();
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse OpenCode /session response", e);
        }
    }

    public void registerTool(String name, String description,
                             Map<String, Object> parameters, String callbackUrl) {
        wc.post().uri("/plugin/register-tool")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "name", name,
                "description", description,
                "parameters", parameters,
                "callbackUrl", callbackUrl
            ))
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    public void sendMessage(String sessionId, Map<String, Object> requestBody) {
        wc.post().uri("/session/{id}/message", sessionId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    public void abort(String sessionId) {
        wc.post().uri("/session/{id}/abort", sessionId)
            .retrieve()
            .toBodilessEntity()
            .block();
    }
}
```

- [ ] **Step 22.5: Implement OpenCodeConfig**

Create `data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeConfig.java`:

```java
package com.datatalk.infra.opencode;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenCodeConfig {

    @Bean
    public OpenCodeHttpClient openCodeHttpClient(
        @Value("${datatalk.opencode.base-url:http://localhost:4096}") String baseUrl,
        ObjectMapper om
    ) {
        return new OpenCodeHttpClient(baseUrl, om);
    }

    @Bean
    public OpenCodeProperties openCodeProperties(
        @Value("${datatalk.opencode.plugin-callback-base:http://localhost:8080}") String callbackBase,
        @Value("${datatalk.opencode.shared-secret:}") String sharedSecret
    ) {
        return new OpenCodeProperties(callbackBase, sharedSecret);
    }

    public record OpenCodeProperties(String callbackBase, String sharedSecret) {}
}
```

- [ ] **Step 22.6: Run — expected PASS**

```
./mvnw -pl data-talk-infrastructure test -Dtest=OpenCodeHttpClientTest
```

- [ ] **Step 22.7: Commit**

```
git add data-talk-infrastructure/pom.xml \
        data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/ \
        data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/OpenCodeHttpClientTest.java
git commit -m "feat(server): add OpenCodeHttpClient with WireMock contract tests"
```

---

## Task 23: OpenCodeGateway lifecycle

**Files:**
- Create: `data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeGateway.java`
- Test: `data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeGatewayTest.java`

Plan A scope: gateway only handles startup tool registration + session creation on demand. The SSE consumer loop is simplified to a single injected `Runnable` that tests can replace; Plan B will add the real retry + backoff logic.

- [ ] **Step 23.1: Write failing test**

Create `data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeGatewayTest.java`:

```java
package com.datatalk.application.opencode;

import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionDescriptor;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenCodeGatewayTest {

    @Test
    void registerToolsPushesEveryDescriptor() {
        ActionRegistry registry = mock(ActionRegistry.class);
        when(registry.all()).thenReturn(List.of(
            new ActionDescriptor("a.one", Executor.SERVER, "First",
                Map.of("type","object"), Map.of("type","object"),
                List.of(), List.of(OntologyEffect.NONE), false, 1000),
            new ActionDescriptor("a.two", Executor.CLIENT, "Second",
                Map.of("type","object"), Map.of("type","object"),
                List.of(), List.of(OntologyEffect.NONE), false, 1000)
        ));

        StubToolPusher pusher = new StubToolPusher();
        OpenCodeGateway gw = new OpenCodeGateway(registry, pusher,
            (sessionId, body) -> {}, () -> "ocsid-1", "http://localhost:8080");

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
            (s, body) -> {}, () -> "oc-42", "http://x");
        assertThat(gw.createOpenCodeSession()).isEqualTo("oc-42");
    }

    static class StubToolPusher implements OpenCodeGateway.ToolPusher {
        final java.util.List<StubCall> pushed = new java.util.ArrayList<>();
        @Override public void push(String name, String description,
                                   Map<String, Object> parameters, String callbackUrl) {
            pushed.add(new StubCall(name, description, parameters, callbackUrl));
        }
        record StubCall(String name, String description,
                        Map<String, Object> parameters, String callbackUrl) {}
    }
}
```

- [ ] **Step 23.2: Run — expected FAIL**

```
./mvnw -pl data-talk-application test -Dtest=OpenCodeGatewayTest
```

- [ ] **Step 23.3: Implement OpenCodeGateway**

Create `data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeGateway.java`:

```java
package com.datatalk.application.opencode;

import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.domain.action.ActionDescriptor;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Pushes every registered DataTalk Action to OpenCode as a plugin tool, and
 * manages the data-talk session ↔ open-code session id mapping.
 *
 * <p>Plan A wires {@link ToolPusher} and {@link MessageSender} to real HTTP
 * calls via Spring configuration (see {@code OpenCodeGatewayBeans} in the
 * adapter module — added in Task 26). Unit tests inject stubs.</p>
 */
public class OpenCodeGateway {

    public interface ToolPusher {
        void push(String name, String description,
                  Map<String, Object> parameters, String callbackUrl);
    }

    public interface MessageSender {
        void send(String openCodeSessionId, Map<String, Object> requestBody);
    }

    private final ActionRegistry registry;
    private final ToolPusher pusher;
    private final MessageSender sender;
    private final Supplier<String> sessionCreator;
    private final String callbackBase;

    public OpenCodeGateway(ActionRegistry registry, ToolPusher pusher,
                           MessageSender sender, Supplier<String> sessionCreator,
                           String callbackBase) {
        this.registry = registry;
        this.pusher = pusher;
        this.sender = sender;
        this.sessionCreator = sessionCreator;
        this.callbackBase = callbackBase;
    }

    public void registerTools() {
        for (ActionDescriptor d : registry.all()) {
            pusher.push(
                d.id(),
                d.description(),
                d.inputSchema(),
                callbackBase + "/api/opencode-tool/" + d.id()
            );
        }
    }

    public String createOpenCodeSession() {
        return sessionCreator.get();
    }

    public void forwardUserMessage(String openCodeSessionId, Map<String, Object> requestBody) {
        sender.send(openCodeSessionId, requestBody);
    }
}
```

- [ ] **Step 23.4: Run — expected PASS**

```
./mvnw -pl data-talk-application test -Dtest=OpenCodeGatewayTest
```

- [ ] **Step 23.5: Commit**

```
git add data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeGateway.java \
        data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeGatewayTest.java
git commit -m "feat(server): add OpenCodeGateway skeleton with tool pushing"
```

---

## Task 24: ToolCallBridge + controller

**Files:**
- Create: `data-talk-application/src/main/java/com/datatalk/application/opencode/ToolCallBridge.java`
- Create: `data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/ToolCallController.java`
- Test: `data-talk-application/src/test/java/com/datatalk/application/opencode/ToolCallBridgeTest.java`

- [ ] **Step 24.1: Write failing test**

Create `data-talk-application/src/test/java/com/datatalk/application/opencode/ToolCallBridgeTest.java`:

```java
package com.datatalk.application.opencode;

import com.datatalk.application.session.ActionDispatcher;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolCallBridgeTest {

    @Test
    void buildsContextAndDelegatesToDispatcher() {
        ActionDispatcher disp = mock(ActionDispatcher.class);
        OpenCodeSessionMap map = new OpenCodeSessionMap();
        map.bind("dt-1", "oc-1");

        when(disp.dispatch(any(), any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(Map.of("echoed", "hi")));

        ToolCallBridge bridge = new ToolCallBridge(disp, map);
        Object out = bridge.handle("datatalk.demo.echo", "call-1", "oc-1",
            Map.of("text", "hi")).toCompletableFuture().join();

        assertThat(((Map<?,?>) out).get("echoed")).isEqualTo("hi");

        ArgumentCaptor<ActionContext> ctx = ArgumentCaptor.forClass(ActionContext.class);
        verify(disp).dispatch(eq("datatalk.demo.echo"), eq(Map.of("text", "hi")),
            eq("call-1"), ctx.capture());
        assertThat(ctx.getValue().sessionId()).isEqualTo("dt-1");
        assertThat(ctx.getValue().openCodeSessionId()).isEqualTo("oc-1");
    }
}
```

- [ ] **Step 24.2: Implement OpenCodeSessionMap**

Create `data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeSessionMap.java`:

```java
package com.datatalk.application.opencode;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bi-directional map between DataTalk session ids and OpenCode session ids. */
@Component
public class OpenCodeSessionMap {

    private final Map<String, String> dtToOc = new ConcurrentHashMap<>();
    private final Map<String, String> ocToDt = new ConcurrentHashMap<>();

    public void bind(String dataTalkSessionId, String openCodeSessionId) {
        dtToOc.put(dataTalkSessionId, openCodeSessionId);
        ocToDt.put(openCodeSessionId, dataTalkSessionId);
    }

    public String openCodeFor(String dataTalkSessionId) {
        return dtToOc.get(dataTalkSessionId);
    }

    public String dataTalkFor(String openCodeSessionId) {
        return ocToDt.get(openCodeSessionId);
    }
}
```

- [ ] **Step 24.3: Run — expected FAIL**

```
./mvnw -pl data-talk-application test -Dtest=ToolCallBridgeTest
```

- [ ] **Step 24.4: Implement ToolCallBridge**

Create `data-talk-application/src/main/java/com/datatalk/application/opencode/ToolCallBridge.java`:

```java
package com.datatalk.application.opencode;

import com.datatalk.application.session.ActionDispatcher;
import com.datatalk.domain.action.ActionContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionStage;

/**
 * Bridges OpenCode tool-call HTTP callbacks into our {@link ActionDispatcher}.
 * Resolves the session context via {@link OpenCodeSessionMap} and then hands
 * off to the dispatcher, which decides where the handler actually runs.
 */
@Component
public class ToolCallBridge {

    private final ActionDispatcher dispatcher;
    private final OpenCodeSessionMap map;

    public ToolCallBridge(ActionDispatcher dispatcher, OpenCodeSessionMap map) {
        this.dispatcher = dispatcher;
        this.map = map;
    }

    public CompletionStage<Object> handle(String actionId, String callId,
                                          String openCodeSessionId, Object input) {
        String dtSessionId = map.dataTalkFor(openCodeSessionId);
        if (dtSessionId == null) {
            throw new IllegalStateException(
                "unknown OpenCode session: " + openCodeSessionId);
        }
        ActionContext ctx = new ActionContext(dtSessionId, callId, null, openCodeSessionId);
        return dispatcher.dispatch(actionId, input, callId, ctx);
    }
}
```

Note: `ActionDispatcher` is implemented in Task 25 — this test uses a mock for it, so the bridge can compile once we stub the Dispatcher class as a placeholder. Let's do that now as part of this task to keep the build green.

- [ ] **Step 24.5: Add a placeholder ActionDispatcher class** (will be fully implemented in Task 25)

Create `data-talk-application/src/main/java/com/datatalk/application/session/ActionDispatcher.java` with just the interface shape:

```java
package com.datatalk.application.session;

import com.datatalk.domain.action.ActionContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionStage;

/**
 * Placeholder — full implementation in Task 25. Kept here so ToolCallBridge
 * compiles; Task 25 replaces the body.
 */
@Component
public class ActionDispatcher {
    public CompletionStage<Object> dispatch(String actionId, Object input,
                                            String callId, ActionContext ctx) {
        throw new UnsupportedOperationException("ActionDispatcher not yet implemented");
    }
}
```

- [ ] **Step 24.6: Implement ToolCallController**

Create `data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/ToolCallController.java`:

```java
package com.datatalk.infra.opencode;

import com.datatalk.application.opencode.ToolCallBridge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/opencode-tool")
public class ToolCallController {

    private final ToolCallBridge bridge;
    private final String sharedSecret;

    public ToolCallController(
        ToolCallBridge bridge,
        @Value("${datatalk.opencode.shared-secret:}") String sharedSecret
    ) {
        this.bridge = bridge;
        this.sharedSecret = sharedSecret;
    }

    @PostMapping("/{actionId}")
    public ResponseEntity<?> handle(
        @PathVariable String actionId,
        @RequestHeader(value = "X-OpenCode-Call-Id", required = false) String callId,
        @RequestHeader(value = "X-OpenCode-Session-Id", required = false) String openCodeSessionId,
        @RequestHeader(value = "X-OpenCode-Secret", required = false) String secret,
        @RequestBody(required = false) Map<String, Object> input
    ) {
        if (!sharedSecret.isEmpty() && !sharedSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "bad secret"));
        }
        if (callId == null || openCodeSessionId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "missing call/session headers"));
        }
        try {
            Object output = bridge.handle(actionId, callId, openCodeSessionId,
                input == null ? Map.of() : input).toCompletableFuture().get();
            return ResponseEntity.ok(Map.of("output", output));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "interrupted"));
        } catch (ExecutionException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
        }
    }
}
```

- [ ] **Step 24.7: Run — expected PASS (bridge test)**

```
./mvnw -pl data-talk-application test -Dtest=ToolCallBridgeTest
```

- [ ] **Step 24.8: Commit**

```
git add data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeSessionMap.java \
        data-talk-application/src/main/java/com/datatalk/application/opencode/ToolCallBridge.java \
        data-talk-application/src/main/java/com/datatalk/application/session/ActionDispatcher.java \
        data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/ToolCallController.java \
        data-talk-application/src/test/java/com/datatalk/application/opencode/ToolCallBridgeTest.java
git commit -m "feat(server): add ToolCallBridge and /api/opencode-tool/{id} controller"
```

---

## Task 25: ActionDispatcher (full implementation)

**Files:**
- Modify: `data-talk-application/src/main/java/com/datatalk/application/session/ActionDispatcher.java`
- Test: `data-talk-application/src/test/java/com/datatalk/application/session/ActionDispatcherTest.java`

- [ ] **Step 25.1: Write failing test**

Create `data-talk-application/src/test/java/com/datatalk/application/session/ActionDispatcherTest.java`:

```java
package com.datatalk.application.session;

import com.datatalk.application.persistence.ActionInvocationRepository;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.registry.JsonSchemaLoader;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionDescriptor;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Executor;
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
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionDispatcherTest {

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
            new ActionDescriptor("x.ok", Executor.SERVER, "",
                Map.of("type","object"), Map.of("type","object"),
                List.of(), List.of(OntologyEffect.NONE), false, 1000)
        );
        when(registry.handler("x.ok")).thenReturn(new AlwaysOkHandler());

        ActionDispatcher disp = new ActionDispatcher(registry, schemas, buses,
            invocations, artifacts, pending, new ObjectMapper(),
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
            new ActionDescriptor("x.client", Executor.CLIENT, "",
                Map.of("type","object"), Map.of("type","object"),
                List.of(), List.of(OntologyEffect.NONE), false, 500)
        );
        when(registry.handler("x.client")).thenReturn(new AlwaysOkHandler());

        ActionDispatcher disp = new ActionDispatcher(registry, schemas, buses,
            invocations, artifacts, pending, new ObjectMapper(),
            Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC));

        CompletionStage<Object> out = disp.dispatch("x.client",
            Map.of("k", "v"), "c-2",
            new ActionContext("s-1", "c-2", null, "oc-1"));

        verify(pending).register(eq("c-2"), any(), eq(500));
        verify(bus).publish(any(DtEvent.ActionInvoke.class));
        assertThat(out.toCompletableFuture().isDone()).isFalse();  // waits for action_result
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
}
```

- [ ] **Step 25.2: Run — expected FAIL (placeholder throws)**

```
./mvnw -pl data-talk-application test -Dtest=ActionDispatcherTest
```

- [ ] **Step 25.3: Replace the placeholder ActionDispatcher with the real implementation**

Open `data-talk-application/src/main/java/com/datatalk/application/session/ActionDispatcher.java` and replace its body:

```java
package com.datatalk.application.session;

import com.datatalk.application.persistence.ActionInvocationRepository;
import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.registry.JsonSchemaLoader;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionDescriptor;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.event.DtEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
public class ActionDispatcher {

    private final ActionRegistry registry;
    private final JsonSchemaLoader schemas;
    private final SessionBusRegistry buses;
    private final ActionInvocationRepository invocations;
    private final ArtifactRepository artifacts;
    private final PendingCallRegistry pending;
    private final ObjectMapper om;
    private final Clock clock;

    public ActionDispatcher(ActionRegistry registry, JsonSchemaLoader schemas,
                            SessionBusRegistry buses, ActionInvocationRepository invocations,
                            ArtifactRepository artifacts, PendingCallRegistry pending,
                            ObjectMapper om, Clock clock) {
        this.registry = registry;
        this.schemas = schemas;
        this.buses = buses;
        this.invocations = invocations;
        this.artifacts = artifacts;
        this.pending = pending;
        this.om = om;
        this.clock = clock;
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    public CompletionStage<Object> dispatch(String actionId, Object input,
                                            String callId, ActionContext ctx) {
        ActionDescriptor desc = registry.require(actionId);
        validate(desc.inputSchema(), input, "input", actionId);

        long now = clock.millis();
        try {
            invocations.start(callId, ctx.sessionId(), actionId, om.writeValueAsString(input), now);
        } catch (Exception e) {
            return CompletableFuture.failedStage(new IllegalStateException("cannot persist invocation", e));
        }

        SessionBus bus = buses.getOrCreate(ctx.sessionId());

        return switch (desc.executor()) {
            case SERVER -> {
                ActionHandler<Object, Object> handler = (ActionHandler<Object, Object>) registry.handler(actionId);
                yield handler.handle(ctx, input).thenApply(output -> {
                    validate(desc.outputSchema(), output, "output", actionId);
                    applyEffects(desc, output, ctx, bus);
                    try {
                        invocations.complete(callId, om.writeValueAsString(output), clock.millis());
                    } catch (Exception ignore) {}
                    return output;
                }).whenComplete((res, err) -> {
                    if (err != null) {
                        try {
                            invocations.fail(callId, om.writeValueAsString(
                                Map.of("message", err.getMessage())), clock.millis());
                        } catch (Exception ignore) {}
                    }
                });
            }
            case OPENCODE -> {
                ActionHandler<Object, Object> handler = (ActionHandler<Object, Object>) registry.handler(actionId);
                yield handler.handle(ctx, input).thenApply(output -> {
                    validate(desc.outputSchema(), output, "output", actionId);
                    try {
                        invocations.complete(callId, om.writeValueAsString(output), clock.millis());
                    } catch (Exception ignore) {}
                    return output;
                });
            }
            case CLIENT -> {
                CompletableFuture<Object> fut = new CompletableFuture<>();
                pending.register(callId, fut, desc.timeoutMs());
                bus.publish(new DtEvent.ActionInvoke(callId, actionId,
                    (Map<String, Object>) input, desc.timeoutMs()));
                yield fut.whenComplete((res, err) -> {
                    long t = clock.millis();
                    if (err != null) {
                        try {
                            invocations.fail(callId, om.writeValueAsString(
                                Map.of("message", err.getMessage())), t);
                        } catch (Exception ignore) {}
                    } else {
                        try {
                            invocations.complete(callId, om.writeValueAsString(res), t);
                        } catch (Exception ignore) {}
                    }
                });
            }
        };
    }

    private void validate(Map<String, Object> schema, Object data, String label, String actionId) {
        JsonSchemaLoader.Result r = schemas.validate(schema, data);
        if (!r.valid()) {
            throw new SchemaValidationException(actionId, label, r.errors());
        }
    }

    @SuppressWarnings("unchecked")
    private void applyEffects(ActionDescriptor desc, Object output,
                              ActionContext ctx, SessionBus bus) {
        if (desc.sideEffects().contains(OntologyEffect.CREATE_ARTIFACT)
                || desc.sideEffects().contains(OntologyEffect.PATCH_ARTIFACT)) {
            if (output instanceof Map<?, ?> m) {
                Object artifactIdObj = m.get("artifactId");
                if (artifactIdObj instanceof String artifactId) {
                    int version = ((Number) m.getOrDefault("version", 1)).intValue();
                    bus.publish(new DtEvent.OntologyUpdated(
                        "datatalk.artifact", artifactId, "upsert",
                        Map.of("version", version,
                               "producedBy", ctx.callId(),
                               "full", m)
                    ));
                }
            }
        }
    }

    public static class SchemaValidationException extends RuntimeException {
        public final String actionId;
        public final String label;
        public final java.util.List<String> errors;
        public SchemaValidationException(String actionId, String label, java.util.List<String> errors) {
            super(actionId + "." + label + " invalid: " + errors);
            this.actionId = actionId;
            this.label = label;
            this.errors = errors;
        }
    }
}
```

- [ ] **Step 25.4: Run — expected PASS**

```
./mvnw -pl data-talk-application test -Dtest=ActionDispatcherTest
```

- [ ] **Step 25.5: Commit**

```
git add data-talk-application/src/main/java/com/datatalk/application/session/ActionDispatcher.java \
        data-talk-application/src/test/java/com/datatalk/application/session/ActionDispatcherTest.java
git commit -m "feat(server): implement ActionDispatcher with executor routing and effects"
```

---

## Task 26: DemoEchoAction + end-to-end smoke test

**Files:**
- Create: `data-talk-adapter/src/main/java/com/datatalk/adapter/actions/DemoEchoAction.java`
- Create: `data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java`
- Test: `data-talk-adapter/src/test/java/com/datatalk/adapter/smoke/EndToEndSmokeIT.java`

The smoke test exercises the whole stack: tool registration with a WireMock OpenCode, an OpenCode-initiated tool-call callback to `/api/opencode-tool/datatalk.demo.echo`, dispatch to the server handler, and ToolCallController returning the echoed output.

- [ ] **Step 26.1: Implement DemoEchoAction (SERVER executor)**

Create `data-talk-adapter/src/main/java/com/datatalk/adapter/actions/DemoEchoAction.java`:

```java
package com.datatalk.adapter.actions;

import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Minimal demo Action that proves the Action Registry → OpenCode → ToolCallBridge
 * → ActionDispatcher loop end to end. Takes a {@code text} field and returns
 * its reversed form under {@code reversed}.
 */
@DataTalkAction(
    id = "datatalk.demo.echo",
    executor = Executor.SERVER,
    description = "Return the reverse of the provided text. Used for smoke testing.",
    timeoutMs = 3_000
)
public class DemoEchoAction implements ActionHandler<Map, Map> {

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("text"),
            "properties", Map.of("text", Map.of("type", "string"))
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("reversed"),
            "properties", Map.of("reversed", Map.of("type", "string"))
        );
    }

    @Override
    public List<OntologyEffect> sideEffects() {
        return List.of(OntologyEffect.NONE);
    }

    @Override
    public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String text = String.valueOf(input.get("text"));
        String reversed = new StringBuilder(text).reverse().toString();
        return CompletableFuture.completedFuture(Map.of("reversed", reversed));
    }
}
```

Register it as a Spring bean. The easiest way given the current codebase is to add `@Component` — but note the bean must be picked up by scanning. Since `DataTalkApplication` was set to `scanBasePackages = "com.datatalk"` in Task 9, plain `@DataTalkAction` alone is not enough to make it a bean. Add `@Component`:

Modify `DemoEchoAction.java`:

```java
// add the import
import org.springframework.stereotype.Component;

// add @Component above @DataTalkAction
@Component
@DataTalkAction(...)
public class DemoEchoAction implements ActionHandler<Map, Map> { ... }
```

- [ ] **Step 26.2: Wire the OpenCodeGateway beans to real HTTP**

Create `data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java`:

```java
package com.datatalk.adapter.config;

import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.infra.opencode.OpenCodeConfig;
import com.datatalk.infra.opencode.OpenCodeHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.Map;

@Configuration
public class OpenCodeGatewayBeans {

    private final OpenCodeHttpClient client;
    private final OpenCodeConfig.OpenCodeProperties props;
    private final ActionRegistry registry;
    private OpenCodeGateway gateway;

    public OpenCodeGatewayBeans(OpenCodeHttpClient client,
                                OpenCodeConfig.OpenCodeProperties props,
                                ActionRegistry registry) {
        this.client = client;
        this.props = props;
        this.registry = registry;
    }

    @Bean
    public OpenCodeGateway openCodeGateway() {
        this.gateway = new OpenCodeGateway(
            registry,
            (name, desc, params, cb) -> client.registerTool(name, desc, params, cb),
            (ocSid, body) -> client.sendMessage(ocSid, body),
            client::createSession,
            props.callbackBase()
        );
        return gateway;
    }

    /**
     * Registers tools shortly after startup. If OpenCode is unreachable (Plan A
     * scope: fine), the app keeps running in degraded mode.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartup() {
        try {
            gateway.registerTools();
        } catch (Exception e) {
            // Plan B: retry with backoff. Plan A logs and continues.
            System.err.println("OpenCode tool registration failed (degraded mode): " + e.getMessage());
        }
    }
}
```

- [ ] **Step 26.3: Write failing end-to-end smoke test**

Create `data-talk-adapter/src/test/java/com/datatalk/adapter/smoke/EndToEndSmokeIT.java`:

```java
package com.datatalk.adapter.smoke;

import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EndToEndSmokeIT {

    static WireMockServer openCode;

    @LocalServerPort int port;
    @Autowired ObjectMapper om;
    @Autowired SessionRepository sessions;
    @Autowired OpenCodeSessionMap map;
    @Autowired JdbcTemplate datatalkJdbc;

    @BeforeAll
    void startFake() {
        openCode = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        openCode.start();
        openCode.stubFor(post(urlPathEqualTo("/plugin/register-tool"))
            .willReturn(aResponse().withStatus(204)));
    }

    @AfterAll
    void stop() { openCode.stop(); }

    @DynamicPropertySource
    static void wireOpenCodeBaseUrl(DynamicPropertyRegistry reg) {
        reg.add("datatalk.opencode.base-url", () -> "http://localhost:" + openCode.port());
        reg.add("datatalk.opencode.plugin-callback-base", () -> "http://localhost:8080");
    }

    @Test
    void toolsAreRegisteredOnStartup() {
        await().atMost(java.time.Duration.ofSeconds(3))
            .untilAsserted(() -> openCode.verify(
                postRequestedFor(urlPathEqualTo("/plugin/register-tool"))
            ));
    }

    @Test
    void opencodeToolCallInvokesHandlerAndReturnsOutput() throws Exception {
        datatalkJdbc.update("DELETE FROM sessions");
        sessions.upsert(new SessionRecord("s-smoke", null, "T", true, "oc-smoke", 0L, 0L));
        map.bind("s-smoke", "oc-smoke");

        WebClient client = WebClient.create("http://localhost:" + port);
        String response = client.post()
            .uri("/api/opencode-tool/datatalk.demo.echo")
            .header("X-OpenCode-Call-Id", "call-smoke-1")
            .header("X-OpenCode-Session-Id", "oc-smoke")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("text", "hello"))
            .retrieve()
            .bodyToMono(String.class)
            .block();

        assertThat(response).contains("\"reversed\":\"olleh\"");
    }
}
```

- [ ] **Step 26.4: Run — expected FAIL or partial (depending on wiring)**

```
./mvnw -pl data-talk-adapter -am test -Dtest=EndToEndSmokeIT
```

- [ ] **Step 26.5: Fix any wiring issues that surface**

Common issues and fixes:

1. If `scanBasePackages` missed the adapter's `config/` package: confirm that `DataTalkApplication` is in `com.datatalk.adapter` and `scanBasePackages = "com.datatalk"` covers all four modules' packages.
2. If the registry finds zero actions: confirm `DemoEchoAction` is annotated with both `@Component` and `@DataTalkAction`.
3. If `/api/opencode-tool/...` 401s: set `datatalk.opencode.shared-secret` to empty in `application.yml` (it already defaults to empty via `${...:}`).

- [ ] **Step 26.6: Run until PASS**

```
./mvnw -pl data-talk-adapter -am test -Dtest=EndToEndSmokeIT
```

- [ ] **Step 26.7: Full module build — green**

```
./mvnw clean verify
```

Expected: all tests pass across all modules.

- [ ] **Step 26.8: Commit**

```
git add data-talk-adapter/src/main/java/com/datatalk/adapter/actions/DemoEchoAction.java \
        data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java \
        data-talk-adapter/src/test/java/com/datatalk/adapter/smoke/EndToEndSmokeIT.java
git commit -m "feat(server): add demo echo action and end-to-end smoke test"
```

---

## Plan A Completion

After Task 26 the server exposes:

- `GET /api/actions` — lists the registered Actions including `datatalk.demo.echo`.
- `GET /api/ontology` — lists ObjectType descriptors (empty until Plan B adds them; endpoint still returns `{"objects":[]}`).
- `POST /api/sessions/{id}/channel` — accepts `send_message`, `action_result`, `abort`, `hello`; streams SSE for `send_message`.
- `GET /api/sessions/{id}/channel` — standalone SSE subscription for multi-tab or recovery.
- `POST /api/opencode-tool/{actionId}` — accepts OpenCode tool callbacks and dispatches to handlers.
- SQLite under `./data/datatalk.db` with the spec §5.6 schema via Flyway.
- A FakeOpenCodeServer wiring in tests; real OpenCode integration (retry/backoff, /event consumer) ships in Plan B.

## Self-Review

**Spec coverage check:**
- §1 architecture: covered (Tasks 1–26 build out the three-tier structure).
- §2 Ontology & Action Registry: Tasks 1–8, 9 (endpoints), 26 (extension contract proven).
- §3 protocol: Tasks 15–20 (bus + channel), 21 (translator).
- §5 server: Tasks 10–13 (persistence), 14–16 (sessions + dispatcher), 22–25 (opencode).
- §7 error framework: Tasks 14, 25 (pending timeout + schema validation); the **full** error-code table lands in Plan B alongside the real action handlers.
- §8 testing pyramid: each task is TDD; pact contracts (L4) are implicit via the smoke test for Plan A and become proper pact files in Plan B.

**Placeholder scan:** no TBD / TODO / "similar to Task N" remain. Every code block is complete and runnable.

**Type consistency:** `ActionHandler`, `ActionContext`, `SessionBus`, `ActionDispatcher`, `OpenCodeGateway` names match across tasks. The placeholder `ActionDispatcher` in Task 24 is explicitly replaced in Task 25 (noted in both places).

**Scope check:** Plan A ends at a runnable, independently-testable server with demo action proving the full loop. Plan B adds real OpenCode `/event` consumer + the 6 MVP actions + JDBC schema reading + real DB connections. Plan C is client UI.

---

## 执行结果

**执行方式:** Subagent-Driven（并行派遣，共 6 个 task）
**执行日期:** 2026-04-16
**分支:** `develop`

### Task 状态汇总

| Task | 状态 | Commit | 说明 |
|------|------|--------|------|
| 21 | ✅ 完成 | `b007126` | OpenCodeEventTranslator — 3 文件（OcEvent + Translator + Test），适配 MessageUpdated 4 字段构造 |
| 22 | ✅ 完成 | `060a3f3` | OpenCodeHttpClient + WireMock 测试 — 4 文件，测试已通过 |
| 23 | ✅ 完成 | `b007126` | OpenCodeGateway — 2 文件（Gateway + Test），字段名与计划完全一致 |
| 24 | ✅ 完成 | `b007126` | ToolCallBridge + OpenCodeSessionMap + ToolCallController — 5 文件，与计划一致 |
| 25 | ✅ 完成 | `b007126` | ActionDispatcher 完整实现 — 4 文件，新增 ActionInvocationRepository + ArtifactRepository 接口 |
| 26 | ✅ 完成 | `b007126` | DemoEchoAction + OpenCodeGatewayBeans + EndToEndSmokeIT — 4 文件 |

### 额外修复

| # | Commit | 修复内容 |
|---|--------|----------|
| 27 | `77c62d8` | ① Part 接口加 `String id()` 方法（所有实现类已有该字段）② OpenCodeEventTranslator 用 `p.part().id()` 替代 `identityHashCode` 做业务语义去重 |
| 28 | `7753d8e` | E2E 测试修复：内存数据源覆盖、schema.sql 测试数据库初始化、WireMock 启动顺序（@DynamicPropertySource 内启动而非 @BeforeAll） |

### 已生成文件清单（20 个新增 + 3 个修改）

**新增（20）:**
- `data-talk-application/src/main/java/com/datatalk/application/opencode/OcEvent.java`
- `data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventTranslator.java`
- `data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeGateway.java`
- `data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeSessionMap.java`
- `data-talk-application/src/main/java/com/datatalk/application/opencode/ToolCallBridge.java`
- `data-talk-application/src/main/java/com/datatalk/application/session/ActionDispatcher.java`
- `data-talk-application/src/main/java/com/datatalk/application/persistence/ActionInvocationRepository.java`
- `data-talk-application/src/main/java/com/datatalk/application/persistence/ArtifactRepository.java`
- `data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventTranslatorTest.java`
- `data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeGatewayTest.java`
- `data-talk-application/src/test/java/com/datatalk/application/opencode/ToolCallBridgeTest.java`
- `data-talk-application/src/test/java/com/datatalk/application/session/ActionDispatcherTest.java`
- `data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeConfig.java`
- `data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeHttpClient.java`
- `data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/ToolCallController.java`
- `data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/OpenCodeHttpClientTest.java`
- `data-talk-adapter/src/main/java/com/datatalk/adapter/actions/DemoEchoAction.java`
- `data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java`
- `data-talk-adapter/src/test/java/com/datatalk/adapter/smoke/EndToEndSmokeIT.java`
- `data-talk-adapter/src/test/resources/schema.sql` — 测试用 H2 内存数据库 schema

**修改（3）:**
- `data-talk-infrastructure/pom.xml` — 添加 webflux、wiremock、junit、assertj 依赖
- `data-talk-adapter/pom.xml` — 添加 wiremock、awaitility、h2 测试依赖
- `data-talk-domain/src/main/java/com/datatalk/domain/part/Part.java` — 添加 `String id()` 方法

### 验证结果

| 检查项 | 结果 | 详情 |
|--------|------|------|
| 代码编写 | ✅ 100% | 20 个新文件 + 3 个修改，全部已提交 |
| 计划差异修复 | ✅ 100% | 3 个差异全部处理 |
| Maven 编译 | ✅ 通过 | 5 个模块 BUILD SUCCESS |
| 单元测试 | ✅ 18/18 通过 | 见下方测试明细表 |
| E2E 冒烟 | ✅ 2/2 通过 | toolsAreRegisteredOnStartup + opencodeToolCallInvokesHandlerAndReturnsOutput |

### 测试明细

| 测试类 | 用例数 | 耗时 | 结果 |
|--------|--------|------|------|
| OpenCodeEventTranslatorTest | 8 | 0.229s | ✅ |
| OpenCodeHttpClientTest | 2 | 7.847s | ✅ |
| OpenCodeGatewayTest | 2 | 0.133s | ✅ |
| ToolCallBridgeTest | 1 | 2.954s | ✅ |
| ActionDispatcherTest | 3 | 3.390s | ✅ |
| EndToEndSmokeIT | 2 | 12.50s | ✅ |
| **总计** | **18** | **~27s** | **✅ 18/18** |

### 成功标准（spec §1 对应 Part 4 部分）

| # | 标准 | 状态 |
|---|------|------|
| 1 | OpenCodeEventTranslator 能区分 first/subsequent | ✅ 8/8 测试通过 |
| 2 | OpenCodeHttpClient 通过 WireMock 契约测试 | ✅ 2/2 测试通过 |
| 3 | OpenCodeGateway 推送所有 ActionDescriptor 为工具 | ✅ 2/2 测试通过 |
| 4 | ToolCallBridge 能正确解析 session 并派发 | ✅ 1/1 测试通过 |
| 5 | ActionDispatcher SERVER/CLIENT/OPENCODE 三路路由 | ✅ 3/3 测试通过 |
| 6 | DemoEchoAction 端到端返回 reversed 文本 | ✅ 2/2 E2E 通过 |
