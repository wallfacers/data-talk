package com.datatalk.application.session;

import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.event.NumberedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Per-session event bus. Accepts {@link DtEvent} via {@link #publish}, buffers
 * and coalesces them in a 16ms window, assigns monotonic event ids, and pushes
 * each resulting {@link NumberedEvent} to all live subscribers.
 *
 * <p>Coalescing rule: two {@link DtEvent.MessagePartDelta} events with the same
 * {@code partId} + {@code field} within the flush window are merged into one,
 * concatenating the {@code delta} string in order. All other event types flow
 * through individually.</p>
 *
 * <p>Buffer: the last {@code bufferSize} events (or those newer than
 * {@code bufferTtl}, whichever is shorter) are kept in memory for replay.</p>
 */
public class SessionBus implements AutoCloseable {

    public interface Persister {
        void persist(String sessionId, long eventId, String eventType, String payloadJson, long ts);
    }

    private final String sessionId;
    private final int bufferSize;
    private final Duration bufferTtl;
    private final Clock clock;
    private final ObjectMapper om;
    private final Persister persister;

    private final AtomicLong seq;
    private final LinkedBlockingQueue<DtEvent> inbound = new LinkedBlockingQueue<>();
    private final Deque<NumberedEvent> buffer = new ArrayDeque<>();
    private final Map<String, Consumer<NumberedEvent>> subscribers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService flusher;

    public SessionBus(String sessionId, long initialSeq, int bufferSize, Duration bufferTtl,
                      Duration flushInterval, Clock clock, ObjectMapper om, Persister persister) {
        this.sessionId = sessionId;
        this.seq = new AtomicLong(initialSeq);
        this.bufferSize = bufferSize;
        this.bufferTtl = bufferTtl;
        this.clock = clock;
        this.om = om;
        this.persister = persister;
        this.flusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-bus-flusher-" + sessionId);
            t.setDaemon(true);
            return t;
        });
        this.flusher.scheduleAtFixedRate(this::flush, flushInterval.toMillis(),
            flushInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public String sessionId() { return sessionId; }
    public long latestEventId() { return seq.get(); }

    public void publish(DtEvent event) {
        inbound.offer(event);
    }

    public void subscribe(String clientId, long lastEventId, Consumer<NumberedEvent> sink) {
        // Replay first from buffer
        List<NumberedEvent> backfill;
        synchronized (buffer) {
            backfill = new ArrayList<>();
            for (NumberedEvent n : buffer) {
                if (n.eventId() > lastEventId) backfill.add(n);
            }
        }
        backfill.forEach(sink);
        subscribers.put(clientId, sink);
    }

    public void unsubscribe(String clientId) {
        subscribers.remove(clientId);
    }

    private void flush() {
        try {
            List<DtEvent> batch = new ArrayList<>();
            inbound.drainTo(batch);
            if (batch.isEmpty()) return;

            List<DtEvent> coalesced = coalesceDeltas(batch);
            long now = clock.millis();

            List<NumberedEvent> emitted = new ArrayList<>();
            for (DtEvent evt : coalesced) {
                long id = seq.incrementAndGet();
                NumberedEvent n = new NumberedEvent(id, sessionId, evt, now);
                emitted.add(n);
                try {
                    persister.persist(sessionId, id, typeName(evt), om.writeValueAsString(evt), now);
                } catch (Exception ignore) {
                    // persistence failures are logged by concrete impl; bus keeps going
                }
            }

            synchronized (buffer) {
                for (NumberedEvent n : emitted) {
                    buffer.addLast(n);
                    while (buffer.size() > bufferSize) buffer.pollFirst();
                }
                long cutoff = now - bufferTtl.toMillis();
                while (!buffer.isEmpty() && buffer.peekFirst().ts() < cutoff) {
                    buffer.pollFirst();
                }
            }

            for (NumberedEvent n : emitted) {
                for (Consumer<NumberedEvent> sub : subscribers.values()) {
                    try { sub.accept(n); } catch (Exception ignore) {}
                }
            }
        } catch (Throwable t) {
            // swallow to keep the flusher alive
        }
    }

    private static List<DtEvent> coalesceDeltas(List<DtEvent> in) {
        // Preserve order except that multiple (partId, field) deltas fold.
        List<DtEvent> out = new ArrayList<>(in.size());
        Map<String, Integer> deltaIndex = new HashMap<>();  // key = partId+"/"+field → position in out
        for (DtEvent e : in) {
            if (e instanceof DtEvent.MessagePartDelta d) {
                String key = d.partId() + "/" + d.field();
                Integer idx = deltaIndex.get(key);
                if (idx != null) {
                    DtEvent.MessagePartDelta prev = (DtEvent.MessagePartDelta) out.get(idx);
                    out.set(idx, new DtEvent.MessagePartDelta(prev.partId(), prev.field(),
                        prev.delta() + d.delta()));
                    continue;
                }
                deltaIndex.put(key, out.size());
            }
            out.add(e);
        }
        return out;
    }

    private static String typeName(DtEvent e) {
        // mirror @JsonSubTypes name attribute mapping
        return switch (e) {
            case DtEvent.Connected c              -> "connected";
            case DtEvent.SessionStatus s          -> "session.status";
            case DtEvent.MessageCreated mc        -> "message.created";
            case DtEvent.MessageUpdated mu        -> "message.updated";
            case DtEvent.MessagePartCreated pc    -> "message.part.created";
            case DtEvent.MessagePartUpdated pu    -> "message.part.updated";
            case DtEvent.MessagePartDelta pd      -> "message.part.delta";
            case DtEvent.MessagePartRemoved pr    -> "message.part.removed";
            case DtEvent.ActionInvoke ai          -> "action.invoke";
            case DtEvent.ActionCancel ac          -> "action.cancel";
            case DtEvent.ArtifactSnapshot as      -> "artifact.snapshot";
            case DtEvent.OntologyUpdated ou       -> "ontology.updated";
            case DtEvent.Heartbeat hb             -> "heartbeat";
            case DtEvent.StreamError se           -> "error";
        };
    }

    @Override
    public void close() {
        flusher.shutdownNow();
    }
}
