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
