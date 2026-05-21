package com.datatalk.application.session;

import com.datatalk.application.persistence.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lazily creates and caches {@link SessionBus} instances per session id.
 * New buses bootstrap their sequence from {@link EventRepository#maxEventId}
 * so bus ids survive server restarts.
 *
 * <p>Eviction: when the last subscriber unsubscribes, the bus is closed after
 * a {@code evictionDelay} grace period to allow rapid reconnects.</p>
 */
@Component
public class SessionBusRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionBusRegistry.class);

    private final EventRepository events;
    private final ObjectMapper om;
    private final Clock clock;
    private final int bufferSize;
    private final Duration bufferTtl;
    private final Duration flushInterval;
    private final Duration evictionDelay;

    private final Map<String, SessionBus> buses = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> subCounts = new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.ScheduledFuture<?>> evictionTimers = new ConcurrentHashMap<>();

    private final java.util.concurrent.ScheduledExecutorService evictionScheduler =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "bus-eviction");
            t.setDaemon(true);
            return t;
        });

    public SessionBusRegistry(
        EventRepository events,
        ObjectMapper om,
        Clock clock,
        @Value("${datatalk.channel.ring-buffer-size:500}") int bufferSize,
        @Value("${datatalk.channel.ring-buffer-ttl:PT5M}") Duration bufferTtl,
        @Value("${datatalk.channel.flush-interval:PT0.016S}") Duration flushInterval,
        @Value("${datatalk.channel.eviction-delay:PT30S}") Duration evictionDelay
    ) {
        this.events = events;
        this.om = om;
        this.clock = clock;
        this.bufferSize = bufferSize;
        this.bufferTtl = bufferTtl;
        this.flushInterval = flushInterval;
        this.evictionDelay = evictionDelay;
    }

    public SessionBus getOrCreate(String sessionId) {
        cancelEviction(sessionId);
        subCounts.computeIfAbsent(sessionId, k -> new AtomicInteger(0)).incrementAndGet();
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

    public void onUnsubscribe(String sessionId) {
        AtomicInteger count = subCounts.get(sessionId);
        if (count == null) return;
        int n = count.decrementAndGet();
        if (n <= 0) {
            scheduleEviction(sessionId);
        }
    }

    public void close(String sessionId) {
        cancelEviction(sessionId);
        SessionBus removed = buses.remove(sessionId);
        subCounts.remove(sessionId);
        if (removed != null) removed.close();
    }

    private void scheduleEviction(String sessionId) {
        java.util.concurrent.ScheduledFuture<?> timer = evictionScheduler.schedule(() -> {
            SessionBus bus = buses.remove(sessionId);
            subCounts.remove(sessionId);
            evictionTimers.remove(sessionId);
            if (bus != null) {
                bus.close();
                log.debug("Evicted idle bus for session={}", sessionId);
            }
        }, evictionDelay.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        evictionTimers.put(sessionId, timer);
    }

    private void cancelEviction(String sessionId) {
        java.util.concurrent.ScheduledFuture<?> timer = evictionTimers.remove(sessionId);
        if (timer != null) timer.cancel(false);
    }
}
