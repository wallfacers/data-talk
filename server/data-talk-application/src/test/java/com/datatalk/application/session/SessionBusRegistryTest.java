package com.datatalk.application.session;

import com.datatalk.application.persistence.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.util.concurrent.TimeUnit;

class SessionBusRegistryTest {

    private final EventRepository events = Mockito.mock(EventRepository.class);
    private final SessionBusRegistry registry = new SessionBusRegistry(
        events, new ObjectMapper(), Clock.systemUTC(),
        500, Duration.ofMinutes(5), Duration.ofMillis(16), Duration.ofSeconds(1));

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

    @Test
    void busEvictedAfterLastSubscriberUnsubscribes() throws InterruptedException {
        Mockito.when(events.maxEventId("s-4")).thenReturn(0L);
        SessionBus bus1 = registry.getOrCreate("s-4");
        registry.onUnsubscribe("s-4");

        // Wait for eviction (1s delay) + buffer
        Thread.sleep(1500);

        // After eviction, a new getOrCreate creates a fresh instance
        SessionBus bus2 = registry.getOrCreate("s-4");
        assertThat(bus2).isNotSameAs(bus1);
    }

    @Test
    void reconnectCancelsPendingEviction() {
        Mockito.when(events.maxEventId("s-5")).thenReturn(0L);
        SessionBus bus1 = registry.getOrCreate("s-5");
        registry.onUnsubscribe("s-5");

        // Reconnect before eviction fires
        SessionBus bus2 = registry.getOrCreate("s-5");
        assertThat(bus2).isSameAs(bus1);
    }
}
