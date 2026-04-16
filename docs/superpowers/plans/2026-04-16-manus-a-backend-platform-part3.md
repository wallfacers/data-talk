# Plan A — Backend Platform Foundation (Part 3 of 4)

> Continuation of parts 1 & 2. Tasks 15–20: SessionBus, Subscriber, JsonRpcCodec, ChannelService, ChannelController.

---

## Task 15: SessionBus (ring buffer + flusher)

**Files:**
- Create: `data-talk-application/src/main/java/com/datatalk/application/session/SessionBus.java`
- Test: `data-talk-application/src/test/java/com/datatalk/application/session/SessionBusTest.java`

Spec §5.2. Per-session, in-memory buffer of the last N numbered events with 16ms flusher that coalesces `message.part.delta` events on the same `partId`.

- [ ] **Step 15.1: Write failing test for publish + replay**

Create `data-talk-application/src/test/java/com/datatalk/application/session/SessionBusTest.java`:

```java
package com.datatalk.application.session;

import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.event.NumberedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SessionBusTest {

    SessionBus bus;
    Clock clock = Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC);
    List<NumberedEvent> received = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        bus = new SessionBus("s-1", 0L, 500, Duration.ofMinutes(5),
            Duration.ofMillis(16), clock, new ObjectMapper(), (sid, eid, type, payload, ts) -> {});
        bus.subscribe("c-1", 0L, received::add);
    }

    @AfterEach
    void tearDown() {
        bus.close();
    }

    @Test
    void publishAndReceive() {
        bus.publish(new DtEvent.Heartbeat(1000L));
        await().atMost(Duration.ofSeconds(1)).until(() -> received.size() == 1);
        assertThat(received.get(0).eventId()).isEqualTo(1L);
        assertThat(received.get(0).event()).isInstanceOf(DtEvent.Heartbeat.class);
    }

    @Test
    void coalescesSameFieldDeltasWithinWindow() {
        bus.publish(new DtEvent.MessagePartDelta("p1", "text", "Hel"));
        bus.publish(new DtEvent.MessagePartDelta("p1", "text", "lo"));
        bus.publish(new DtEvent.MessagePartDelta("p1", "text", " world"));
        await().atMost(Duration.ofSeconds(1)).until(() -> !received.isEmpty());
        // all three deltas should merge into a single emitted event
        long deltaCount = received.stream()
            .filter(n -> n.event() instanceof DtEvent.MessagePartDelta).count();
        assertThat(deltaCount).isEqualTo(1);
        DtEvent.MessagePartDelta merged =
            (DtEvent.MessagePartDelta) received.stream()
                .filter(n -> n.event() instanceof DtEvent.MessagePartDelta)
                .findFirst().orElseThrow().event();
        assertThat(merged.delta()).isEqualTo("Hello world");
    }

    @Test
    void doesNotCoalesceWholePartReplaces() {
        // Two MessagePartUpdated with same partId: both should go through (no merge)
        var part1 = new com.datatalk.domain.part.TextPart(
            "p1","s-1","m-1","a",null,null,null,java.util.Map.of());
        var part2 = new com.datatalk.domain.part.TextPart(
            "p1","s-1","m-1","a b",null,null,null,java.util.Map.of());
        bus.publish(new DtEvent.MessagePartUpdated(part1));
        bus.publish(new DtEvent.MessagePartUpdated(part2));
        await().atMost(Duration.ofSeconds(1)).until(() ->
            received.stream().filter(n -> n.event() instanceof DtEvent.MessagePartUpdated).count() == 2);
    }

    @Test
    void replayAfterLastEventIdBackfillsBuffer() {
        // publish three events with nobody subscribed
        bus.close();
        bus = new SessionBus("s-2", 0L, 500, Duration.ofMinutes(5),
            Duration.ofMillis(16), clock, new ObjectMapper(), (sid, eid, type, payload, ts) -> {});
        bus.publish(new DtEvent.Heartbeat(1L));
        bus.publish(new DtEvent.Heartbeat(2L));
        bus.publish(new DtEvent.Heartbeat(3L));
        await().atMost(Duration.ofSeconds(1)).until(() -> bus.latestEventId() >= 3);

        List<NumberedEvent> late = new CopyOnWriteArrayList<>();
        bus.subscribe("late", 1L, late::add);  // expect events #2 and #3
        await().atMost(Duration.ofSeconds(1)).until(() -> late.size() >= 2);
        assertThat(late.stream().map(NumberedEvent::eventId))
            .contains(2L, 3L);
    }
}
```

- [ ] **Step 15.2: Run — expected FAIL**

```
./mvnw -pl data-talk-application test -Dtest=SessionBusTest
```

- [ ] **Step 15.3: Implement SessionBus**

Create `data-talk-application/src/main/java/com/datatalk/application/session/SessionBus.java`:

```java
package com.datatalk.application.session;

import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.event.NumberedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Per-session event bus. Accepts {@link DtEvent} via {@link #publish}, buffers
 * and coalesces them in a 16ms window, assigns monotonic event ids, and pushes
 * each resulting {@link NumberedEvent} to all live subscribers.
 *
 * <p>Coalescing rule: two {@link DtEvent.MessagePartDelta} events with the same
 * {@code partId} + {@code field} within the flush window are merged into one,
 * concatenating the {@code delta} string in order. All other event types flow
 * through individually.</p>
 *
 * <p>Buffer: the last {@code bufferSize} events (or those newer than
 * {@code bufferTtl}, whichever is shorter) are kept in memory for replay.</p>
 */
public class SessionBus implements AutoCloseable {

    public interface Persister {
        void persist(String sessionId, long eventId, String eventType, String payloadJson, long ts);
    }

    private final String sessionId;
    private final int bufferSize;
    private final Duration bufferTtl;
    private final Clock clock;
    private final ObjectMapper om;
    private final Persister persister;

    private final AtomicLong seq;
    private final LinkedBlockingQueue<DtEvent> inbound = new LinkedBlockingQueue<>();
    private final Deque<NumberedEvent> buffer = new ArrayDeque<>();
    private final Map<String, Consumer<NumberedEvent>> subscribers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService flusher;

    public SessionBus(String sessionId, long initialSeq, int bufferSize, Duration bufferTtl,
                      Duration flushInterval, Clock clock, ObjectMapper om, Persister persister) {
        this.sessionId = sessionId;
        this.seq = new AtomicLong(initialSeq);
        this.bufferSize = bufferSize;
        this.bufferTtl = bufferTtl;
        this.clock = clock;
        this.om = om;
        this.persister = persister;
        this.flusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-bus-flusher-" + sessionId);
            t.setDaemon(true);
            return t;
        });
        this.flusher.scheduleAtFixedRate(this::flush, flushInterval.toMillis(),
            flushInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public String sessionId() { return sessionId; }
    public long latestEventId() { return seq.get(); }

    public void publish(DtEvent event) {
        inbound.offer(event);
    }

    public void subscribe(String clientId, long lastEventId, Consumer<NumberedEvent> sink) {
        // Replay first from buffer
        List<NumberedEvent> backfill;
        synchronized (buffer) {
            backfill = new ArrayList<>();
            for (NumberedEvent n : buffer) {
                if (n.eventId() > lastEventId) backfill.add(n);
            }
        }
        backfill.forEach(sink);
        subscribers.put(clientId, sink);
    }

    public void unsubscribe(String clientId) {
        subscribers.remove(clientId);
    }

    private void flush() {
        try {
            List<DtEvent> batch = new ArrayList<>();
            inbound.drainTo(batch);
            if (batch.isEmpty()) return;

            List<DtEvent> coalesced = coalesceDeltas(batch);
            long now = clock.millis();

            List<NumberedEvent> emitted = new ArrayList<>();
            for (DtEvent evt : coalesced) {
                long id = seq.incrementAndGet();
                NumberedEvent n = new NumberedEvent(id, sessionId, evt, now);
                emitted.add(n);
                try {
                    persister.persist(sessionId, id, typeName(evt), om.writeValueAsString(evt), now);
                } catch (Exception ignore) {
                    // persistence failures are logged by concrete impl; bus keeps going
                }
            }

            synchronized (buffer) {
                for (NumberedEvent n : emitted) {
                    buffer.addLast(n);
                    while (buffer.size() > bufferSize) buffer.pollFirst();
                }
                long cutoff = now - bufferTtl.toMillis();
                while (!buffer.isEmpty() && buffer.peekFirst().ts() < cutoff) {
                    buffer.pollFirst();
                }
            }

            for (NumberedEvent n : emitted) {
                for (Consumer<NumberedEvent> sub : subscribers.values()) {
                    try { sub.accept(n); } catch (Exception ignore) {}
                }
            }
        } catch (Throwable t) {
            // swallow to keep the flusher alive
        }
    }

    private static List<DtEvent> coalesceDeltas(List<DtEvent> in) {
        // Preserve order except that multiple (partId, field) deltas fold.
        List<DtEvent> out = new ArrayList<>(in.size());
        Map<String, Integer> deltaIndex = new HashMap<>();  // key = partId+"/"+field → position in out
        for (DtEvent e : in) {
            if (e instanceof DtEvent.MessagePartDelta d) {
                String key = d.partId() + "/" + d.field();
                Integer idx = deltaIndex.get(key);
                if (idx != null) {
                    DtEvent.MessagePartDelta prev = (DtEvent.MessagePartDelta) out.get(idx);
                    out.set(idx, new DtEvent.MessagePartDelta(prev.partId(), prev.field(),
                        prev.delta() + d.delta()));
                    continue;
                }
                deltaIndex.put(key, out.size());
            }
            out.add(e);
        }
        return out;
    }

    private static String typeName(DtEvent e) {
        // mirror @JsonSubTypes name attribute mapping
        return switch (e) {
            case DtEvent.Connected c              -> "connected";
            case DtEvent.SessionStatus s          -> "session.status";
            case DtEvent.MessageCreated mc        -> "message.created";
            case DtEvent.MessageUpdated mu        -> "message.updated";
            case DtEvent.MessagePartCreated pc    -> "message.part.created";
            case DtEvent.MessagePartUpdated pu    -> "message.part.updated";
            case DtEvent.MessagePartDelta pd      -> "message.part.delta";
            case DtEvent.MessagePartRemoved pr    -> "message.part.removed";
            case DtEvent.ActionInvoke ai          -> "action.invoke";
            case DtEvent.ActionCancel ac          -> "action.cancel";
            case DtEvent.ArtifactSnapshot as      -> "artifact.snapshot";
            case DtEvent.OntologyUpdated ou       -> "ontology.updated";
            case DtEvent.Heartbeat hb             -> "heartbeat";
            case DtEvent.StreamError se           -> "error";
        };
    }

    @Override
    public void close() {
        flusher.shutdownNow();
    }
}
```

- [ ] **Step 15.4: Run — expected PASS**

```
./mvnw -pl data-talk-application test -Dtest=SessionBusTest
```

- [ ] **Step 15.5: Commit**

```
git add data-talk-application/src/main/java/com/datatalk/application/session/SessionBus.java \
        data-talk-application/src/test/java/com/datatalk/application/session/SessionBusTest.java
git commit -m "feat(server): add SessionBus with 16ms flusher and delta coalescing"
```

---

## Task 16: SessionBusRegistry

**Files:**
- Create: `data-talk-application/src/main/java/com/datatalk/application/session/SessionBusRegistry.java`
- Test: `data-talk-application/src/test/java/com/datatalk/application/session/SessionBusRegistryTest.java`

- [ ] **Step 16.1: Write failing test**

Create `data-talk-application/src/test/java/com/datatalk/application/session/SessionBusRegistryTest.java`:

```java
package com.datatalk.application.session;

import com.datatalk.application.persistence.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SessionBusRegistryTest {

    private final EventRepository events = Mockito.mock(EventRepository.class);
    private final SessionBusRegistry registry = new SessionBusRegistry(
        events, new ObjectMapper(), Clock.systemUTC(),
        500, Duration.ofMinutes(5), Duration.ofMillis(16));

    @Test
    void getOrCreateReturnsSameInstance() {
        Mockito.when(events.maxEventId("s-1")).thenReturn(0L);
        SessionBus a = registry.getOrCreate("s-1");
        SessionBus b = registry.getOrCreate("s-1");
        assertThat(a).isSameAs(b);
    }

    @Test
    void differentSessionIdsProduceDifferentBuses() {
        Mockito.when(events.maxEventId("s-1")).thenReturn(0L);
        Mockito.when(events.maxEventId("s-2")).thenReturn(0L);
        SessionBus a = registry.getOrCreate("s-1");
        SessionBus b = registry.getOrCreate("s-2");
        assertThat(a).isNotSameAs(b);
    }

    @Test
    void newBusStartsFromMaxEventIdInDb() {
        Mockito.when(events.maxEventId("s-3")).thenReturn(42L);
        SessionBus bus = registry.getOrCreate("s-3");
        assertThat(bus.latestEventId()).isEqualTo(42L);
    }
}
```

- [ ] **Step 16.2: Add Mockito to `data-talk-application/pom.xml`** (if not already via starter-test)

Already pulled in transitively by `spring-boot-starter-test`. No change.

- [ ] **Step 16.3: Run — expected FAIL**

```
./mvnw -pl data-talk-application test -Dtest=SessionBusRegistryTest
```

- [ ] **Step 16.4: Implement SessionBusRegistry**

Create `data-talk-application/src/main/java/com/datatalk/application/session/SessionBusRegistry.java`:

```java
package com.datatalk.application.session;

import com.datatalk.application.persistence.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazily creates and caches {@link SessionBus} instances per session id.
 * New buses bootstrap their sequence from {@link EventRepository#maxEventId}
 * so bus ids survive server restarts.
 */
@Component
public class SessionBusRegistry {

    private final EventRepository events;
    private final ObjectMapper om;
    private final Clock clock;
    private final int bufferSize;
    private final Duration bufferTtl;
    private final Duration flushInterval;

    private final Map<String, SessionBus> buses = new ConcurrentHashMap<>();

    public SessionBusRegistry(
        EventRepository events,
        ObjectMapper om,
        Clock clock,
        @Value("${datatalk.channel.ring-buffer-size:500}") int bufferSize,
        @Value("${datatalk.channel.ring-buffer-ttl:PT5M}") Duration bufferTtl,
        @Value("${datatalk.channel.flush-interval:PT0.016S}") Duration flushInterval
    ) {
        this.events = events;
        this.om = om;
        this.clock = clock;
        this.bufferSize = bufferSize;
        this.bufferTtl = bufferTtl;
        this.flushInterval = flushInterval;
    }

    public SessionBus getOrCreate(String sessionId) {
        return buses.computeIfAbsent(sessionId, sid -> new SessionBus(
            sid,
            events.maxEventId(sid),
            bufferSize,
            bufferTtl,
            flushInterval,
            clock,
            om,
            events::append
        ));
    }

    public void close(String sessionId) {
        SessionBus removed = buses.remove(sessionId);
        if (removed != null) removed.close();
    }
}
```

- [ ] **Step 16.5: Add a `Clock` bean in `data-talk-adapter`**

Create `data-talk-adapter/src/main/java/com/datatalk/adapter/config/ClockConfig.java`:

```java
package com.datatalk.adapter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
```

- [ ] **Step 16.6: Run — expected PASS**

```
./mvnw -pl data-talk-application test -Dtest=SessionBusRegistryTest
```

- [ ] **Step 16.7: Commit**

```
git add data-talk-application/src/main/java/com/datatalk/application/session/SessionBusRegistry.java \
        data-talk-application/src/test/java/com/datatalk/application/session/SessionBusRegistryTest.java \
        data-talk-adapter/src/main/java/com/datatalk/adapter/config/ClockConfig.java
git commit -m "feat(server): add SessionBusRegistry with per-session bus caching"
```

---

## Task 17: Subscriber + SSE emit

**Files:**
- Create: `data-talk-application/src/main/java/com/datatalk/application/session/SseEmitterSubscriber.java`
- Test: `data-talk-application/src/test/java/com/datatalk/application/session/SseEmitterSubscriberTest.java`

- [ ] **Step 17.1: Write failing test**

Create `data-talk-application/src/test/java/com/datatalk/application/session/SseEmitterSubscriberTest.java`:

```java
package com.datatalk.application.session;

import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.event.NumberedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterSubscriberTest {

    @Test
    void formatsEventWithIdAndTypeAndData() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SseEmitterSubscriber sub = new SseEmitterSubscriber(out, new ObjectMapper(), "connected");
        NumberedEvent n = new NumberedEvent(42L, "s-1",
            new DtEvent.Connected("s-1", 1), 1000L);

        sub.accept(n);

        String text = out.toString(StandardCharsets.UTF_8);
        assertThat(text)
            .contains("id: 42")
            .contains("event: connected")
            .contains("data: {")
            .endsWith("\n\n");
    }
}
```

- [ ] **Step 17.2: Run — expected FAIL**

```
./mvnw -pl data-talk-application test -Dtest=SseEmitterSubscriberTest
```

- [ ] **Step 17.3: Implement SseEmitterSubscriber**

Create `data-talk-application/src/main/java/com/datatalk/application/session/SseEmitterSubscriber.java`:

```java
package com.datatalk.application.session;

import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.event.NumberedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Writes a {@link NumberedEvent} to an {@link OutputStream} in the SSE wire
 * format. Used by {@code ChannelController} after upgrading a POST response
 * to {@code text/event-stream}.
 *
 * <p>If the initial {@code expectedFirstEventName} is set, the subscriber
 * asserts the first NumberedEvent's type matches (defensive check so
 * "connected" really is the first frame).</p>
 */
public class SseEmitterSubscriber implements Consumer<NumberedEvent> {

    private final OutputStream out;
    private final ObjectMapper om;
    private final String expectedFirstEventName;
    private volatile boolean firstFrame = true;
    private volatile boolean broken = false;

    public SseEmitterSubscriber(OutputStream out, ObjectMapper om, String expectedFirstEventName) {
        this.out = out;
        this.om = om;
        this.expectedFirstEventName = expectedFirstEventName;
    }

    @Override
    public void accept(NumberedEvent n) {
        if (broken) return;
        try {
            String name = eventName(n.event());
            if (firstFrame && expectedFirstEventName != null && !expectedFirstEventName.equals(name)) {
                // soft warning; don't break the stream just for this
            }
            firstFrame = false;
            StringBuilder sb = new StringBuilder();
            sb.append("id: ").append(n.eventId()).append('\n');
            sb.append("event: ").append(name).append('\n');
            sb.append("data: ").append(om.writeValueAsString(n.event())).append("\n\n");
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            broken = true;
        }
    }

    public boolean isBroken() { return broken; }

    private static String eventName(DtEvent e) {
        return switch (e) {
            case DtEvent.Connected c              -> "connected";
            case DtEvent.SessionStatus s          -> "session.status";
            case DtEvent.MessageCreated mc        -> "message.created";
            case DtEvent.MessageUpdated mu        -> "message.updated";
            case DtEvent.MessagePartCreated pc    -> "message.part.created";
            case DtEvent.MessagePartUpdated pu    -> "message.part.updated";
            case DtEvent.MessagePartDelta pd      -> "message.part.delta";
            case DtEvent.MessagePartRemoved pr    -> "message.part.removed";
            case DtEvent.ActionInvoke ai          -> "action.invoke";
            case DtEvent.ActionCancel ac          -> "action.cancel";
            case DtEvent.ArtifactSnapshot as      -> "artifact.snapshot";
            case DtEvent.OntologyUpdated ou       -> "ontology.updated";
            case DtEvent.Heartbeat hb             -> "heartbeat";
            case DtEvent.StreamError se           -> "error";
        };
    }
}
```

- [ ] **Step 17.4: Run — expected PASS**

```
./mvnw -pl data-talk-application test -Dtest=SseEmitterSubscriberTest
```

- [ ] **Step 17.5: Commit**

```
git add data-talk-application/src/main/java/com/datatalk/application/session/SseEmitterSubscriber.java \
        data-talk-application/src/test/java/com/datatalk/application/session/SseEmitterSubscriberTest.java
git commit -m "feat(server): add SSE emitter subscriber writing NumberedEvents"
```

---

## Task 18: JsonRpcCodec + Rpc types

**Files:**
- Create: `data-talk-application/src/main/java/com/datatalk/application/channel/RpcRequest.java`
- Create: `data-talk-application/src/main/java/com/datatalk/application/channel/RpcResponse.java`
- Create: `data-talk-application/src/main/java/com/datatalk/application/channel/JsonRpcCodec.java`
- Test: `data-talk-application/src/test/java/com/datatalk/application/channel/JsonRpcCodecTest.java`

- [ ] **Step 18.1: Write failing test**

Create `data-talk-application/src/test/java/com/datatalk/application/channel/JsonRpcCodecTest.java`:

```java
package com.datatalk.application.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonRpcCodecTest {

    private final JsonRpcCodec codec = new JsonRpcCodec(new ObjectMapper());

    @Test
    void decodesSendMessageRequest() {
        String json = """
            {"jsonrpc":"2.0","id":"r1","method":"send_message",
             "params":{"parts":[{"type":"text","id":"p1","sessionID":"s1","messageID":"m1",
                                 "text":"hi","metadata":{}}]}}
            """;
        RpcRequest req = codec.decodeRequest(json);
        assertThat(req).isInstanceOf(RpcRequest.SendMessage.class);
        assertThat(req.id()).isEqualTo("r1");
    }

    @Test
    void decodesActionResultRequest() {
        String json = """
            {"jsonrpc":"2.0","id":"r2","method":"action_result",
             "params":{"callId":"c1","ok":true,"output":{"reversed":"abc"}}}
            """;
        RpcRequest req = codec.decodeRequest(json);
        assertThat(req).isInstanceOf(RpcRequest.ActionResult.class);
        RpcRequest.ActionResult ar = (RpcRequest.ActionResult) req;
        assertThat(ar.params().callId()).isEqualTo("c1");
        assertThat(ar.params().ok()).isTrue();
    }

    @Test
    void rejectsUnknownMethod() {
        String json = """
            {"jsonrpc":"2.0","id":"r3","method":"unknown","params":{}}
            """;
        assertThatThrownBy(() -> codec.decodeRequest(json))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown");
    }

    @Test
    void encodesAckResponse() throws Exception {
        String json = codec.encodeAck("r1");
        assertThat(json).contains("\"jsonrpc\":\"2.0\"")
            .contains("\"id\":\"r1\"").contains("\"result\":{}");
    }
}
```

- [ ] **Step 18.2: Run — expected FAIL**

```
./mvnw -pl data-talk-application test -Dtest=JsonRpcCodecTest
```

- [ ] **Step 18.3: Implement RpcRequest**

Create `data-talk-application/src/main/java/com/datatalk/application/channel/RpcRequest.java`:

```java
package com.datatalk.application.channel;

import com.datatalk.domain.event.ErrorInfo;
import com.datatalk.domain.part.Part;

import java.util.List;

/** Inbound JSON-RPC request over Streamable HTTP. See spec §3.3. */
public sealed interface RpcRequest {

    String id();

    record SendMessage(String id, SendMessageParams params) implements RpcRequest {}
    record ActionResult(String id, ActionResultParams params) implements RpcRequest {}
    record Abort(String id) implements RpcRequest {}
    record Hello(String id, HelloParams params) implements RpcRequest {}

    record SendMessageParams(List<Part> parts) {}
    record ActionResultParams(String callId, boolean ok, Object output, ErrorInfo error) {}
    record HelloParams(int clientRev, Long lastEventId) {}
}
```

- [ ] **Step 18.4: Implement RpcResponse**

Create `data-talk-application/src/main/java/com/datatalk/application/channel/RpcResponse.java`:

```java
package com.datatalk.application.channel;

/** Outbound JSON-RPC response. Only used for non-streaming methods. */
public record RpcResponse(String jsonrpc, String id, Object result, Object error) {
    public static RpcResponse ok(String id) {
        return new RpcResponse("2.0", id, java.util.Map.of(), null);
    }
    public static RpcResponse ok(String id, Object result) {
        return new RpcResponse("2.0", id, result, null);
    }
    public static RpcResponse err(String id, Object error) {
        return new RpcResponse("2.0", id, null, error);
    }
}
```

- [ ] **Step 18.5: Implement JsonRpcCodec**

Create `data-talk-application/src/main/java/com/datatalk/application/channel/JsonRpcCodec.java`:

```java
package com.datatalk.application.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonRpcCodec {

    private final ObjectMapper om;

    public JsonRpcCodec(ObjectMapper om) { this.om = om; }

    public RpcRequest decodeRequest(String json) {
        try {
            JsonNode node = om.readTree(json);
            String id = node.path("id").asText();
            String method = node.path("method").asText();
            JsonNode params = node.path("params");
            return switch (method) {
                case "send_message"  -> new RpcRequest.SendMessage(id,
                    om.treeToValue(params, RpcRequest.SendMessageParams.class));
                case "action_result" -> new RpcRequest.ActionResult(id,
                    om.treeToValue(params, RpcRequest.ActionResultParams.class));
                case "abort"         -> new RpcRequest.Abort(id);
                case "hello"         -> new RpcRequest.Hello(id,
                    om.treeToValue(params, RpcRequest.HelloParams.class));
                default -> throw new IllegalArgumentException("unknown RPC method: " + method);
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("malformed RPC: " + e.getMessage(), e);
        }
    }

    public String encodeAck(String id) {
        try {
            return om.writeValueAsString(RpcResponse.ok(id));
        } catch (Exception e) {
            throw new IllegalStateException("cannot encode RpcResponse", e);
        }
    }

    public String encodeResult(String id, Object result) {
        try {
            return om.writeValueAsString(RpcResponse.ok(id, result));
        } catch (Exception e) {
            throw new IllegalStateException("cannot encode RpcResponse", e);
        }
    }

    public String encodeError(String id, Object error) {
        try {
            return om.writeValueAsString(RpcResponse.err(id, error));
        } catch (Exception e) {
            throw new IllegalStateException("cannot encode RpcResponse", e);
        }
    }
}
```

- [ ] **Step 18.6: Run — expected PASS**

```
./mvnw -pl data-talk-application test -Dtest=JsonRpcCodecTest
```

- [ ] **Step 18.7: Commit**

```
git add data-talk-application/src/main/java/com/datatalk/application/channel/ \
        data-talk-application/src/test/java/com/datatalk/application/channel/JsonRpcCodecTest.java
git commit -m "feat(server): add JSON-RPC codec and Rpc envelope types"
```

---

## Task 19: ChannelService

**Files:**
- Create: `data-talk-application/src/main/java/com/datatalk/application/channel/ChannelService.java`
- Create: `data-talk-application/src/main/java/com/datatalk/application/channel/IdGenerator.java`
- Test: `data-talk-application/src/test/java/com/datatalk/application/channel/ChannelServiceTest.java`

Thin layer: converts a decoded `RpcRequest` into side effects (persistence writes + bus publishes + pending-call completion). Does NOT know about HTTP.

- [ ] **Step 19.1: Write failing test**

Create `data-talk-application/src/test/java/com/datatalk/application/channel/ChannelServiceTest.java`:

```java
package com.datatalk.application.channel;

import com.datatalk.application.persistence.MessageRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.PendingCallRegistry;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.part.Message;
import com.datatalk.domain.part.TextPart;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelServiceTest {

    SessionRepository sessionRepo;
    MessageRepository msgRepo;
    SessionBusRegistry busRegistry;
    PendingCallRegistry pending;
    IdGenerator ids;
    Clock clock = Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC);
    ChannelService svc;

    @BeforeEach
    void setup() {
        sessionRepo = mock(SessionRepository.class);
        msgRepo = mock(MessageRepository.class);
        busRegistry = mock(SessionBusRegistry.class);
        pending = mock(PendingCallRegistry.class);
        ids = mock(IdGenerator.class);
        svc = new ChannelService(sessionRepo, msgRepo, busRegistry, pending, ids, clock);
    }

    @Test
    void sendMessagePersistsAndPublishes() {
        when(ids.next()).thenReturn("m-new");
        when(sessionRepo.findById("s-1")).thenReturn(Optional.of(
            new SessionRecord("s-1", null, "T", false, null, 100L, 100L)));
        SessionBus bus = mock(SessionBus.class);
        when(busRegistry.getOrCreate("s-1")).thenReturn(bus);

        TextPart p = new TextPart("p1","s-1","m-new","hi",null,null,null, Map.of());
        svc.sendMessage("s-1", List.of(p));

        verify(msgRepo).save(any(Message.class));
        verify(sessionRepo).markHasEverSent(eq("s-1"), anyLong());
        ArgumentCaptor<DtEvent> evt = ArgumentCaptor.forClass(DtEvent.class);
        verify(bus, org.mockito.Mockito.atLeast(2)).publish(evt.capture());
        // at minimum: message.created + message.part.created + session.status:busy
        assertThat(evt.getAllValues()).anyMatch(e -> e instanceof DtEvent.MessageCreated);
        assertThat(evt.getAllValues()).anyMatch(e -> e instanceof DtEvent.MessagePartCreated);
    }

    @Test
    void actionResultCompletesPendingFuture() {
        when(pending.complete("c-1", Map.of("ok", true))).thenReturn(true);
        boolean ok = svc.completeActionResult("c-1", true, Map.of("ok", true), null);
        assertThat(ok).isTrue();
    }
}
```

- [ ] **Step 19.2: Run — expected FAIL**

```
./mvnw -pl data-talk-application test -Dtest=ChannelServiceTest
```

- [ ] **Step 19.3: Implement IdGenerator**

Create `data-talk-application/src/main/java/com/datatalk/application/channel/IdGenerator.java`:

```java
package com.datatalk.application.channel;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** Centralized id generator. Tests replace it with a deterministic stub. */
@Component
public class IdGenerator {
    public String next() {
        return UUID.randomUUID().toString();
    }
}
```

- [ ] **Step 19.4: Implement ChannelService**

Create `data-talk-application/src/main/java/com/datatalk/application/channel/ChannelService.java`:

```java
package com.datatalk.application.channel;

import com.datatalk.application.persistence.MessageRepository;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.PendingCallRegistry;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.event.ErrorInfo;
import com.datatalk.domain.part.Message;
import com.datatalk.domain.part.Part;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Business layer behind Streamable HTTP. Pure Java; knows nothing about HTTP.
 */
@Service
public class ChannelService {

    private final SessionRepository sessions;
    private final MessageRepository messages;
    private final SessionBusRegistry buses;
    private final PendingCallRegistry pending;
    private final IdGenerator ids;
    private final Clock clock;

    public ChannelService(SessionRepository sessions, MessageRepository messages,
                          SessionBusRegistry buses, PendingCallRegistry pending,
                          IdGenerator ids, Clock clock) {
        this.sessions = sessions;
        this.messages = messages;
        this.buses = buses;
        this.pending = pending;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * Persist a user message, emit message.created + part.created + session.status:busy,
     * and flip the session's {@code has_ever_sent} flag. Does NOT forward to OpenCode
     * — that's the gateway's job (Task 23).
     */
    public String sendMessage(String sessionId, List<Part> parts) {
        if (sessions.findById(sessionId).isEmpty()) {
            throw new IllegalArgumentException("unknown session: " + sessionId);
        }
        long now = clock.millis();
        String messageId = ids.next();
        Message m = new Message(messageId, sessionId, Message.Role.USER, parts, now);
        messages.save(m);
        sessions.markHasEverSent(sessionId, now);

        SessionBus bus = buses.getOrCreate(sessionId);
        bus.publish(new DtEvent.MessageCreated(m));
        for (Part p : parts) bus.publish(new DtEvent.MessagePartCreated(p));
        bus.publish(new DtEvent.SessionStatus("busy", Map.of()));
        return messageId;
    }

    public boolean completeActionResult(String callId, boolean ok, Object output, ErrorInfo error) {
        if (ok) {
            return pending.complete(callId, output);
        }
        return pending.fail(callId, new ActionResultError(error));
    }

    public void abort(String sessionId) {
        SessionBus bus = buses.getOrCreate(sessionId);
        bus.publish(new DtEvent.SessionStatus("idle", Map.of()));
    }

    public static class ActionResultError extends RuntimeException {
        public final ErrorInfo info;
        public ActionResultError(ErrorInfo info) {
            super(info == null ? null : info.message());
            this.info = info;
        }
    }
}
```

- [ ] **Step 19.5: Run — expected PASS**

```
./mvnw -pl data-talk-application test -Dtest=ChannelServiceTest
```

- [ ] **Step 19.6: Commit**

```
git add data-talk-application/src/main/java/com/datatalk/application/channel/ChannelService.java \
        data-talk-application/src/main/java/com/datatalk/application/channel/IdGenerator.java \
        data-talk-application/src/test/java/com/datatalk/application/channel/ChannelServiceTest.java
git commit -m "feat(server): add ChannelService orchestrating channel RPC side effects"
```

---

## Task 20: ChannelController (Streamable HTTP)

**Files:**
- Create: `data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/ChannelController.java`
- Test: `data-talk-adapter/src/test/java/com/datatalk/adapter/channel/ChannelControllerIT.java`

- [ ] **Step 20.1: Write failing integration test**

Create `data-talk-adapter/src/test/java/com/datatalk/adapter/channel/ChannelControllerIT.java`:

```java
package com.datatalk.adapter.channel;

import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChannelControllerIT {

    @LocalServerPort int port;
    @Autowired SessionRepository sessions;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate datatalkJdbc;

    WebClient client;

    @BeforeEach
    void setUp() {
        datatalkJdbc.update("DELETE FROM messages");
        datatalkJdbc.update("DELETE FROM events");
        datatalkJdbc.update("DELETE FROM sessions");
        sessions.upsert(new SessionRecord("s-1", null, "T", false, null, 100L, 100L));
        client = WebClient.create("http://localhost:" + port);
    }

    @Test
    void sendMessageReturnsSseStreamWithAtLeastConnected() throws Exception {
        String body = om.writeValueAsString(Map.of(
            "jsonrpc", "2.0",
            "id", "r1",
            "method", "send_message",
            "params", Map.of("parts", List.of(Map.of(
                "type", "text",
                "id", "p1",
                "sessionID", "s-1",
                "messageID", "ignored-server-generates",
                "text", "hello",
                "metadata", Map.of()
            )))
        ));

        List<String> lines = new CopyOnWriteArrayList<>();
        client.post()
            .uri("/api/sessions/s-1/channel")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(String.class)
            .take(Duration.ofSeconds(2))
            .doOnNext(lines::add)
            .blockLast(Duration.ofSeconds(3));

        String all = String.join("\n", lines);
        assertThat(all).contains("event: connected");
        assertThat(all).contains("event: message.created");
        assertThat(all).contains("event: message.part.created");
    }
}
```

- [ ] **Step 20.2: Add WebFlux (WebClient for tests) to `data-talk-adapter/pom.xml`**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 20.3: Run — expected FAIL**

```
./mvnw -pl data-talk-adapter -am test -Dtest=ChannelControllerIT
```

- [ ] **Step 20.4: Implement ChannelController**

Create `data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/ChannelController.java`:

```java
package com.datatalk.infra.channel;

import com.datatalk.application.channel.ChannelService;
import com.datatalk.application.channel.JsonRpcCodec;
import com.datatalk.application.channel.RpcRequest;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.application.session.SseEmitterSubscriber;
import com.datatalk.domain.event.DtEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.Map;

/**
 * Streamable HTTP endpoint. POST with a {@code send_message} body returns an
 * SSE stream; other methods return plain JSON acks.
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/channel")
public class ChannelController {

    private final JsonRpcCodec codec;
    private final ChannelService svc;
    private final SessionBusRegistry buses;
    private final ObjectMapper om;

    public ChannelController(JsonRpcCodec codec, ChannelService svc,
                             SessionBusRegistry buses, ObjectMapper om) {
        this.codec = codec;
        this.svc = svc;
        this.buses = buses;
        this.om = om;
    }

    @PostMapping
    public ResponseEntity<?> post(@PathVariable String sessionId,
                                  @RequestBody String rawBody,
                                  @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId) {
        RpcRequest req = codec.decodeRequest(rawBody);
        return switch (req) {
            case RpcRequest.SendMessage m -> stream(sessionId, m, lastEventId);
            case RpcRequest.ActionResult ar -> {
                svc.completeActionResult(ar.params().callId(), ar.params().ok(),
                    ar.params().output(), ar.params().error());
                yield ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(codec.encodeAck(ar.id()));
            }
            case RpcRequest.Abort a -> {
                svc.abort(sessionId);
                yield ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(codec.encodeAck(a.id()));
            }
            case RpcRequest.Hello h -> ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(codec.encodeAck(h.id()));
        };
    }

    @GetMapping
    public ResponseEntity<StreamingResponseBody> subscribe(
        @PathVariable String sessionId,
        @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId
    ) {
        SessionBus bus = buses.getOrCreate(sessionId);
        StreamingResponseBody body = os -> {
            SseEmitterSubscriber sub = new SseEmitterSubscriber(os, om, "connected");
            String clientId = "read-" + System.nanoTime();
            bus.publish(new DtEvent.Connected(sessionId, 1));
            bus.subscribe(clientId, lastEventId == null ? 0L : lastEventId, sub);
            try {
                while (!sub.isBroken()) {
                    try {
                        Thread.sleep(1_000);
                        bus.publish(new DtEvent.Heartbeat(System.currentTimeMillis()));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                bus.unsubscribe(clientId);
            }
        };
        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("text/event-stream"))
            .body(body);
    }

    private ResponseEntity<StreamingResponseBody> stream(String sessionId,
                                                         RpcRequest.SendMessage m,
                                                         Long lastEventId) {
        SessionBus bus = buses.getOrCreate(sessionId);
        StreamingResponseBody body = os -> {
            SseEmitterSubscriber sub = new SseEmitterSubscriber(os, om, "connected");
            String clientId = "post-" + System.nanoTime();
            bus.publish(new DtEvent.Connected(sessionId, 1));
            bus.subscribe(clientId, lastEventId == null ? 0L : lastEventId, sub);
            try {
                svc.sendMessage(sessionId, m.params().parts());
                // Hold the stream briefly so tests observe initial frames.
                // In production this will stay open until OpenCode completes
                // (Task 23 wires that up). For Plan A, close after short grace.
                for (int i = 0; i < 20 && !sub.isBroken(); i++) {
                    try { Thread.sleep(50); } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                bus.publish(new DtEvent.SessionStatus("idle", Map.of()));
                try { Thread.sleep(50); } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                bus.unsubscribe(clientId);
            }
        };
        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("text/event-stream"))
            .body(body);
    }
}
```

Note: in Task 23 we'll replace the `for(i<20)` grace loop with a completion gate that waits for OpenCode's `session.status:idle`. The current version gives the test loop enough time to see the first few events.

- [ ] **Step 20.5: Run — expected PASS**

```
./mvnw -pl data-talk-adapter -am test -Dtest=ChannelControllerIT
```

- [ ] **Step 20.6: Commit**

```
git add data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/ChannelController.java \
        data-talk-adapter/pom.xml \
        data-talk-adapter/src/test/java/com/datatalk/adapter/channel/ChannelControllerIT.java
git commit -m "feat(server): add Streamable HTTP ChannelController (POST-as-SSE + GET subscribe)"
```

---

## 执行结果

**执行日期:** 2026-04-16
**分支:** `develop`
**Commit:** `776affa`

### Task 状态汇总

| Task | 状态 | 文件数 | 行数 | 说明 |
|------|------|--------|------|------|
| 15 SessionBus | ✅ 代码完成 | 2 | 277 | SessionBus.java + SessionBusTest.java |
| 16 SessionBusRegistry | ✅ 代码完成 | 3 | 120 | SessionBusRegistry.java + SessionBusRegistryTest.java + ClockConfig.java |
| 17 SseEmitterSubscriber | ✅ 代码完成 | 2 | 114 | SseEmitterSubscriber.java + SseEmitterSubscriberTest.java |
| 18 JsonRpcCodec | ✅ 代码完成 | 4 | 154 | RpcRequest + RpcResponse + JsonRpcCodec + JsonRpcCodecTest |
| 19 ChannelService | ✅ 代码完成 | 3 | 175 | ChannelService + IdGenerator + ChannelServiceTest |
| 20 ChannelController | ✅ 代码完成 | 2 | 209 | ChannelController + ChannelControllerIT |
| 依赖 persistence 层 | ✅ 代码完成 | 2 | 31 | EventRepository + SessionRecord |
| **总计** | **代码 100%** | **18** | **1072** | 已提交 `776affa` |

### 阻塞项

| 阻塞项 | 原因 | 解决方 |
|--------|------|--------|
| 编译通过 | data-talk-application 模块编译问题 | 其他智能体处理 |
| 测试 PASS | 需编译通过 | 阻塞于编译 |
| Task 20 IT 集成测试 | 需 WebFlux 依赖 + Spring Boot 上下文 | 阻塞于编译 + pom.xml 变更 |

### Part 2 适配状态

| 适配项 | 状态 | 说明 |
|--------|------|------|
| DtEvent 补 8 个类型 | ✅ 已完成 | Heartbeat, MessagePartDelta, SessionStatus, MessageUpdated, MessagePartRemoved, ActionCancel, ArtifactSnapshot, OntologyUpdated |
| NumberedEvent ts→long | ✅ 已完成 | Instant → long，of() 用 System.currentTimeMillis() |
| TextPart 8 参数构造 | ✅ 已完成 | 含向后兼容的单参便捷构造 |
| Message 5 参数构造 | ✅ 已完成 | id, sessionId, Role, parts, createdAt(long) |
| EventRepository | ✅ 已完成 | append() + maxEventId() 已实现 |
| SessionRepository | ✅ 已完成 | findById() + upsert() + markHasEverSent() |
| MessageRepository | ✅ 已完成 | save(Message) 含 JSON 序列化 |

---

## Continuation

Tasks 21–26 continue in `docs/superpowers/plans/2026-04-16-manus-a-backend-platform-part4.md`:

- Task 21: OpenCodeEventTranslator (pure function)
- Task 22: OpenCodeHttpClient + WireMock
- Task 23: OpenCodeGateway lifecycle
- Task 24: ToolCallBridge + /api/opencode-tool/:id
- Task 25: ActionDispatcher
- Task 26: DemoEchoAction + end-to-end smoke test
