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
