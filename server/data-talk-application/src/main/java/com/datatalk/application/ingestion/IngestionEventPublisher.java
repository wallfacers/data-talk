package com.datatalk.application.ingestion;

import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import org.springframework.stereotype.Component;

@Component
public class IngestionEventPublisher {

    private final SessionBusRegistry buses;

    public IngestionEventPublisher(SessionBusRegistry buses) {
        this.buses = buses;
    }

    public void publish(String sessionId, DtEvent event) {
        if (sessionId == null || sessionId.isBlank()) return;
        buses.getOrCreate(sessionId).publish(event);
    }
}
