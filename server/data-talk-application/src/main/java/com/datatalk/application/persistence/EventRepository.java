package com.datatalk.application.persistence;

/**
 * Repository interface for event persistence. Used by {@code SessionBusRegistry}
 * to persist events and to bootstrap session buses from the last known event id.
 */
public interface EventRepository {
    /**
     * Append an event to persistent storage.
     */
    void append(String sessionId, long eventId, String eventType, String payloadJson, long ts);

    /**
     * Get the maximum event id for a given session (used for SessionBus recovery).
     */
    long maxEventId(String sessionId);
}
