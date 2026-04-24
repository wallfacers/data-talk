package com.datatalk.application.session;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.domain.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final ConnectionRepository connections;
    private final SessionRepository repo;
    private final Clock clock;
    private final OpenCodeGateway gateway;
    private final OpenCodeSessionMap sessionMap;
    private final SessionBusRegistry buses;
    private final Translator translator;
    private final Object createLock = new Object();

    public SessionService(ConnectionRepository connections, SessionRepository repo, Clock clock,
                          OpenCodeGateway gateway, OpenCodeSessionMap sessionMap,
                          SessionBusRegistry buses, Translator translator) {
        this.connections = connections;
        this.repo = repo;
        this.clock = clock;
        this.gateway = gateway;
        this.sessionMap = sessionMap;
        this.buses = buses;
        this.translator = translator;
    }

    public CreateSessionResult create(String connectionId, String title) {
        synchronized (createLock) {
            Optional<SessionRecord> existing = repo.findEmpty();
            if (existing.isPresent()) {
                return new CreateSessionResult(existing.get(), true);
            }
            long now = clock.millis();
            String id = UUID.randomUUID().toString();
            String effectiveTitle = Strings.defaultIfBlank(title, translator.get("session.default_title"));
            String effectiveConnectionId = normalizeConnectionId(connectionId);
            SessionRecord rec = new SessionRecord(id, effectiveConnectionId, effectiveTitle, false, null, now, now, false);
            repo.upsert(rec);
            return new CreateSessionResult(rec, false);
        }
    }

    public List<SessionRecord> list(String connectionId) {
        if (Strings.isBlank(connectionId)) return repo.listAll();
        return repo.listByConnection(connectionId);
    }

    public Optional<SessionRecord> find(String id) {
        return repo.findById(id);
    }

    public SessionRecord rename(String id, String title) {
        if (Strings.isBlank(title)) {
            throw new IllegalArgumentException(translator.get("error.session.title_blank"));
        }
        SessionRecord existing = repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException(translator.get("error.session.not_found", id)));
        long now = clock.millis();
        repo.updateTitleAndLock(id, title, now);
        return new SessionRecord(existing.id(), existing.connectionId(), title,
            existing.hasEverSent(), existing.openCodeSid(), existing.createdAt(), now, true);
    }

    public void delete(String id) {
        SessionRecord rec = repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException(translator.get("error.session.not_found", id)));
        deleteRecord(rec);
    }

    public void deleteAll() {
        List<SessionRecord> sessions = repo.listAll();
        for (SessionRecord session : sessions) {
            deleteRecord(session);
        }
    }

    private String normalizeConnectionId(String connectionId) {
        if (Strings.isBlank(connectionId)) {
            return null;
        }
        if (connections.findById(connectionId).isPresent()) {
            return connectionId;
        }
        log.warn("[session] ignoring stale connectionId during create: {}", connectionId);
        return null;
    }

    private void deleteRecord(SessionRecord rec) {
        String id = rec.id();
        // Order matters: events FK → sessions(id) ON DELETE CASCADE. If we delete
        // the row first, late events on the bus's flusher thread (or new ones
        // pushed by OpenCodeEventLoop) try to INSERT and trip the FK constraint.
        // Stop all sources of new events BEFORE removing the row.
        sessionMap.unbind(id);
        String ocSid = rec.openCodeSid();
        if (ocSid != null && !ocSid.isBlank()) {
            try {
                gateway.deleteOpenCodeSession(ocSid);
            } catch (Exception e) {
                log.warn("[session] OpenCode-side delete failed for {} (ocSid={}): {}",
                    id, ocSid, e.toString());
            }
        }
        buses.close(id);
        repo.deleteById(id);
        // FK ON DELETE CASCADE handles artifacts, action_invocations, events, query_results.
    }
}
