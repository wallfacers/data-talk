package com.datatalk.application.session;

import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.event.NumberedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
        ObjectMapper om = new ObjectMapper();
        JsonNode part1 = om.valueToTree(Map.of(
            "type", "text", "id", "p1", "sessionID", "s-1", "messageID", "m-1", "text", "a"
        ));
        JsonNode part2 = om.valueToTree(Map.of(
            "type", "text", "id", "p1", "sessionID", "s-1", "messageID", "m-1", "text", "a b"
        ));
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
