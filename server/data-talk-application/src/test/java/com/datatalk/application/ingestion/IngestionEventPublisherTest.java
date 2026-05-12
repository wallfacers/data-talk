package com.datatalk.application.ingestion;

import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class IngestionEventPublisherTest {

    @Test
    void publishesToResolvedBus() {
        SessionBusRegistry registry = mock(SessionBusRegistry.class);
        SessionBus bus = mock(SessionBus.class);
        when(registry.getOrCreate("sess-1")).thenReturn(bus);

        IngestionEventPublisher publisher = new IngestionEventPublisher(registry);
        DtEvent.IngestionJobCreated event = new DtEvent.IngestionJobCreated("job-1", "https://example/api");
        publisher.publish("sess-1", event);

        verify(bus).publish(event);
    }

    @Test
    void nullSessionIdIsNoop() {
        SessionBusRegistry registry = mock(SessionBusRegistry.class);
        IngestionEventPublisher publisher = new IngestionEventPublisher(registry);
        publisher.publish(null, new DtEvent.IngestionJobCreated("job-1", "u"));
        verifyNoInteractions(registry);
    }

    @Test
    void blankSessionIdIsNoop() {
        SessionBusRegistry registry = mock(SessionBusRegistry.class);
        IngestionEventPublisher publisher = new IngestionEventPublisher(registry);
        publisher.publish("   ", new DtEvent.IngestionJobCreated("job-1", "u"));
        verifyNoInteractions(registry);
    }
}
