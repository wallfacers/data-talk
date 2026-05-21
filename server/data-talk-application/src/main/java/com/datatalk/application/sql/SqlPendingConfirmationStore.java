package com.datatalk.application.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for SQL operations pending user confirmation.
 * 5-minute TTL, auto-cleanup, not persisted across restarts.
 */
@Component
public class SqlPendingConfirmationStore {

    private static final Logger log = LoggerFactory.getLogger(SqlPendingConfirmationStore.class);
    private static final long TTL_MS = 5 * 60 * 1000;

    public record PendingConfirmation(
        String sql,
        String connectionId,
        String sessionId,
        String database,
        String schema,
        String source,
        List<String> affectedObjects
    ) {}

    private record TimedEntry(PendingConfirmation confirmation, Instant createdAt) {}

    private final ConcurrentHashMap<String, TimedEntry> store = new ConcurrentHashMap<>();

    /**
     * Create a new pending confirmation and return its unique ID.
     */
    public String create(PendingConfirmation confirmation) {
        String id = UUID.randomUUID().toString();
        store.put(id, new TimedEntry(confirmation, Instant.now()));
        log.debug("Created pending confirmation {} for session {}", id, confirmation.sessionId());
        return id;
    }

    /**
     * Retrieve a pending confirmation by ID. Returns empty if not found or expired.
     */
    public Optional<PendingConfirmation> get(String id) {
        TimedEntry entry = store.get(id);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpired(entry)) {
            store.remove(id);
            return Optional.empty();
        }
        return Optional.of(entry.confirmation());
    }

    /**
     * Remove a pending confirmation by ID. Returns true if it existed (even if expired).
     */
    public boolean remove(String id) {
        return store.remove(id) != null;
    }

    /**
     * Scheduled cleanup — evict entries older than 5 minutes. Runs every 60 seconds.
     */
    @Scheduled(fixedRate = 60_000)
    public void cleanup() {
        Instant now = Instant.now();
        store.entrySet().removeIf(entry -> {
            boolean expired = isExpired(entry.getValue());
            if (expired) {
                log.debug("Evicted expired confirmation {}", entry.getKey());
            }
            return expired;
        });
    }

    private boolean isExpired(TimedEntry entry) {
        return entry.createdAt().plusMillis(TTL_MS).isBefore(Instant.now());
    }
}
