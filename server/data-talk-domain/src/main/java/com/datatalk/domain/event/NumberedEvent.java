package com.datatalk.domain.event;

import java.time.Instant;

/**
 * A domain event enriched with sequencing and metadata for transport
 * over WebSocket / SSE channels.
 */
public record NumberedEvent(
        long eventId,
        String sessionId,
        DtEvent event,
        Instant ts
) {
    public NumberedEvent {
        if (eventId < 0) throw new IllegalArgumentException("eventId must not be negative");
    }

    public static NumberedEvent of(long eventId, String sessionId, DtEvent event) {
        return new NumberedEvent(eventId, sessionId, event, Instant.now());
    }
}
