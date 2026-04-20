package com.datatalk.application.session;

import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.persistence.SessionDataContextRepository;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.dto.SessionDataContextUpdateRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.NoSuchElementException;

@Service
public class SessionDataContextService {

    private final SessionRepository sessions;
    private final ConnectionRepository connections;
    private final SessionDataContextRepository contexts;
    private final Clock clock;

    public SessionDataContextService(
        SessionRepository sessions,
        ConnectionRepository connections,
        SessionDataContextRepository contexts,
        Clock clock
    ) {
        this.sessions = sessions;
        this.connections = connections;
        this.contexts = contexts;
        this.clock = clock;
    }

    public SessionDataContextRecord get(String sessionId) {
        var session = sessions.findById(sessionId)
            .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));
        return contexts.findBySessionId(sessionId)
            .orElseGet(() -> new SessionDataContextRecord(
                sessionId, null, null, null, null, null, session.updatedAt()
            ));
    }

    public SessionDataContextRecord set(String sessionId, SessionDataContextUpdateRequest req) {
        sessions.findById(sessionId)
            .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));
        if (req == null) {
            throw new IllegalArgumentException("request body is required");
        }
        long now = clock.millis();
        if (req.connectionId() == null || req.connectionId().isBlank()) {
            SessionDataContextRecord cleared = new SessionDataContextRecord(
                sessionId, null, null, null, null, null, now
            );
            contexts.upsert(cleared);
            return cleared;
        }
        var connection = connections.findById(req.connectionId())
            .orElseThrow(() -> new NoSuchElementException("unknown connection: " + req.connectionId()));
        SessionDataContextRecord record = new SessionDataContextRecord(
            sessionId,
            connection.id(),
            connection.name(),
            req.database(),
            req.schema(),
            req.selectedLevel(),
            now
        );
        contexts.upsert(record);
        return record;
    }
}
