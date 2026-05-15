package com.datatalk.application.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScriptDataBatchService {

    private static final Logger log = LoggerFactory.getLogger(ScriptDataBatchService.class);
    private static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

    private final ScriptDataWriteService writeService;
    private final Clock clock;
    private final ConcurrentHashMap<String, BatchSession> sessions = new ConcurrentHashMap<>();

    public record BatchSession(
        String sessionId, String connectionId, String tableName,
        int totalRowsInserted, Instant lastActivityAt
    ) {}

    public ScriptDataBatchService(ScriptDataWriteService writeService, Clock clock) {
        this.writeService = writeService;
        this.clock = clock;
    }

    public record BatchResult(String sessionId, int rowsInserted, int totalRowsInserted) {}

    public BatchResult writeBatch(String sessionId, String connectionId, String tableName,
                                   List<Map<String, Object>> rows, boolean createTable) {
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }

        ScriptDataWriteService.WriteResult result = writeService.write(connectionId, tableName, rows,
            createTable && !sessions.containsKey(sessionId));

        BatchSession existing = sessions.get(sessionId);
        int newTotal;
        if (existing != null) {
            newTotal = existing.totalRowsInserted() + result.rowsInserted();
        } else {
            newTotal = result.rowsInserted();
        }

        sessions.put(sessionId, new BatchSession(sessionId, connectionId, tableName,
            newTotal, clock.instant()));

        return new BatchResult(sessionId, result.rowsInserted(), newTotal);
    }

    public BatchSession closeSession(String sessionId) {
        return sessions.remove(sessionId);
    }

    public void cleanupExpiredSessions() {
        Instant threshold = clock.instant().minusMillis(SESSION_TIMEOUT_MS);
        sessions.entrySet().removeIf(e -> {
            if (e.getValue().lastActivityAt().isBefore(threshold)) {
                log.warn("Closing expired batch session {} for table {}", e.getKey(), e.getValue().tableName());
                return true;
            }
            return false;
        });
    }
}
