package com.datatalk.application.session;

import com.datatalk.application.i18n.Translator;
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
    private final ConnectionTargetDiscoveryService discovery;
    private final Translator translator;
    private final Clock clock;

    public SessionDataContextService(
        SessionRepository sessions,
        ConnectionRepository connections,
        SessionDataContextRepository contexts,
        ConnectionTargetDiscoveryService discovery,
        Translator translator,
        Clock clock
    ) {
        this.sessions = sessions;
        this.connections = connections;
        this.contexts = contexts;
        this.discovery = discovery;
        this.translator = translator;
        this.clock = clock;
    }

    public SessionDataContextRecord get(String sessionId) {
        var session = sessions.findById(sessionId)
            .orElseThrow(() -> new NoSuchElementException(translator.get("error.session.not_found", sessionId)));
        return contexts.findBySessionId(sessionId)
            .orElseGet(() -> contextFromSessionConnection(sessionId, session.connectionId(), session.updatedAt()));
    }

    private SessionDataContextRecord contextFromSessionConnection(String sessionId, String connectionId, long updatedAt) {
        if (connectionId == null || connectionId.isBlank()) {
            return new SessionDataContextRecord(
                sessionId, null, null, null, null, null, updatedAt
            );
        }
        return connections.findById(connectionId)
            .map(connection -> new SessionDataContextRecord(
                sessionId,
                connection.id(),
                connection.name(),
                null,
                null,
                "connection",
                updatedAt
            ))
            .orElseGet(() -> new SessionDataContextRecord(
                sessionId, null, null, null, null, null, updatedAt
            ));
    }

    public SessionDataContextRecord set(String sessionId, SessionDataContextUpdateRequest req) {
        sessions.findById(sessionId)
            .orElseThrow(() -> new NoSuchElementException(translator.get("error.session.not_found", sessionId)));
        if (req == null) {
            throw new IllegalArgumentException(translator.get("error.request_body_required"));
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
            .orElseThrow(() -> new NoSuchElementException(translator.get("error.connection.unknown", req.connectionId())));
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

    public SessionDataContextRecord validate(String sessionId) {
        SessionDataContextRecord current = get(sessionId);
        if (current.connectionId() == null || current.connectionId().isBlank()) {
            return current;
        }
        var connection = connections.findById(current.connectionId())
            .orElseThrow(() -> new NoSuchElementException(translator.get("error.connection.unknown", current.connectionId())));
        long now = clock.millis();
        var targets = discovery.discover(current.connectionId());
        String databaseName = containsIgnoreCase(targets.databaseNames(), current.databaseName()) ? current.databaseName() : null;
        String schemaName = containsIgnoreCase(targets.schemaNames(), current.schemaName()) ? current.schemaName() : null;
        String selectedLevel = switch (current.selectedLevel() == null ? "" : current.selectedLevel()) {
            case "schema" -> schemaName != null ? "schema" : (databaseName != null ? "database" : "connection");
            case "database" -> databaseName != null ? "database" : "connection";
            case "connection" -> "connection";
            default -> schemaName != null ? "schema" : (databaseName != null ? "database" : "connection");
        };
        SessionDataContextRecord refreshed = new SessionDataContextRecord(
            current.sessionId(),
            current.connectionId(),
            connection.name(),
            databaseName,
            schemaName,
            selectedLevel,
            now
        );
        contexts.upsert(refreshed);
        return refreshed;
    }

    private boolean containsIgnoreCase(java.util.Set<String> values, String expected) {
        if (expected == null) return false;
        return values.stream().anyMatch(v -> v != null && v.equalsIgnoreCase(expected));
    }
}
