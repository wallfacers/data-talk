package com.datatalk.application.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages per-connection {@link ConnectionOpLogBus} instances.
 * First subscriber creates the bus; last subscriber triggers 30s grace period eviction.
 * Also listens for Spring {@link UndoLogCreatedEvent} and {@link UndoLogStatusChangedEvent}
 * and routes them to the appropriate bus.
 */
@Component
public class ConnectionOpLogBusRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectionOpLogBusRegistry.class);
    private static final long EVICTION_DELAY_MS = 30_000L;

    private final ObjectMapper om;
    private final Map<String, ConnectionOpLogBus> buses = new ConcurrentHashMap<>();
    private final Map<String, Integer> subCounts = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> evictionTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService evictionScheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "oplog-bus-eviction");
            t.setDaemon(true);
            return t;
        });

    public ConnectionOpLogBusRegistry(ObjectMapper om) {
        this.om = om;
    }

    public ConnectionOpLogBus getOrCreate(String connectionId) {
        cancelEviction(connectionId);
        subCounts.merge(connectionId, 1, Integer::sum);
        return buses.computeIfAbsent(connectionId, ConnectionOpLogBus::new);
    }

    public void onUnsubscribe(String connectionId) {
        Integer count = subCounts.get(connectionId);
        if (count == null) return;
        int n = count - 1;
        if (n <= 0) {
            subCounts.remove(connectionId);
            scheduleEviction(connectionId);
        } else {
            subCounts.put(connectionId, n);
        }
    }

    @EventListener
    public void onUndoLogCreated(UndoLogCreatedEvent event) {
        ConnectionOpLogBus bus = buses.get(event.getConnectionId());
        if (bus == null || !bus.hasSubscribers()) return;
        try {
            Map<String, Object> payload = Map.of(
                "id", event.getUndoLogId(),
                "operation", event.getOperation(),
                "tableName", event.getTableName(),
                "affectedRows", event.getAffectedRows(),
                "createdAt", event.getCreatedAt()
            );
            String json = om.writeValueAsString(payload);
            bus.publish(formatSseFrame("undo_log.created", json));
        } catch (Exception e) {
            log.warn("Failed to publish undo_log.created for connection={}", event.getConnectionId(), e);
        }
    }

    @EventListener
    public void onUndoLogStatusChanged(UndoLogStatusChangedEvent event) {
        ConnectionOpLogBus bus = buses.get(event.getConnectionId());
        if (bus == null || !bus.hasSubscribers()) return;
        try {
            Map<String, Object> payload = event.getUndoneAt() != null
                ? Map.of("id", event.getUndoLogId(), "status", event.getStatus(), "undoneAt", event.getUndoneAt())
                : Map.of("id", event.getUndoLogId(), "status", event.getStatus());
            String json = om.writeValueAsString(payload);
            bus.publish(formatSseFrame("undo_log.status_changed", json));
        } catch (Exception e) {
            log.warn("Failed to publish undo_log.status_changed for connection={}", event.getConnectionId(), e);
        }
    }

    private String formatSseFrame(String eventName, String jsonData) {
        return "event: " + eventName + "\ndata: " + jsonData + "\n\n";
    }

    private void scheduleEviction(String connectionId) {
        ScheduledFuture<?> timer = evictionScheduler.schedule(() -> {
            buses.remove(connectionId);
            evictionTimers.remove(connectionId);
            log.debug("Evicted idle op-log bus for connection={}", connectionId);
        }, EVICTION_DELAY_MS, TimeUnit.MILLISECONDS);
        evictionTimers.put(connectionId, timer);
    }

    private void cancelEviction(String connectionId) {
        ScheduledFuture<?> timer = evictionTimers.remove(connectionId);
        if (timer != null) timer.cancel(false);
    }
}
