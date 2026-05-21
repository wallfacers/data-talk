package com.datatalk.application.session;

import com.datatalk.domain.event.DtEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Test that SessionBus survives when persister throws exceptions.
 * The logging behavior is verified by manual inspection of log output.
 */
class SessionBusFlusherErrorTest {

    SessionBus bus;
    Clock clock = Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC);

    @AfterEach
    void tearDown() {
        if (bus != null) bus.close();
    }

    @Test
    void busSurvivesWhenPersisterThrowsRuntimeException() {
        SessionBus.Persister brokenPersister = (sid, eid, type, payload, ts) -> {
            throw new RuntimeException("database locked");
        };

        bus = new SessionBus("s-err", 0L, 500, Duration.ofMinutes(5),
            Duration.ofMillis(16), clock, new ObjectMapper(), brokenPersister);

        // Subscribe and track received events
        long[] receivedCount = {0};
        bus.subscribe("c-1", 0L, n -> receivedCount[0]++);

        // Publish events - persister throws but bus should survive
        bus.publish(new DtEvent.Heartbeat(1000L));
        bus.publish(new DtEvent.Heartbeat(2000L));

        // Wait for flush cycle - events should be delivered to subscribers despite persistence failure
        await().atMost(Duration.ofSeconds(2)).until(() -> receivedCount[0] >= 2);

        // Bus should still be alive - publish another event
        bus.publish(new DtEvent.Heartbeat(3000L));
        await().atMost(Duration.ofSeconds(2)).until(() -> receivedCount[0] >= 3);

        assertThat(receivedCount[0]).isEqualTo(3);
    }
}