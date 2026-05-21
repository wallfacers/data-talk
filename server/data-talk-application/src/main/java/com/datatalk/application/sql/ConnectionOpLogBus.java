package com.datatalk.application.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Per-connection event bus for operation log SSE broadcast.
 * Lightweight: no buffer, no persistence, no replay — just direct
 * fan-out of JSON event payloads to live subscribers.
 */
public class ConnectionOpLogBus {

    private static final Logger log = LoggerFactory.getLogger(ConnectionOpLogBus.class);

    private final String connectionId;
    private final Map<String, Consumer<String>> subscribers = new ConcurrentHashMap<>();

    public ConnectionOpLogBus(String connectionId) {
        this.connectionId = connectionId;
    }

    public String connectionId() {
        return connectionId;
    }

    public boolean hasSubscribers() {
        return !subscribers.isEmpty();
    }

    public void subscribe(String subscriberId, Consumer<String> sink) {
        subscribers.put(subscriberId, sink);
    }

    public void unsubscribe(String subscriberId) {
        subscribers.remove(subscriberId);
    }

    public void publish(String jsonPayload) {
        for (var entry : subscribers.entrySet()) {
            try {
                entry.getValue().accept(jsonPayload);
            } catch (Exception e) {
                log.warn("Subscriber {} threw during publish for connection={}", entry.getKey(), connectionId, e);
            }
        }
    }
}
