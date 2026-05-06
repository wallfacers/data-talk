package com.datatalk.application.session;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.fileartifact.SessionWorkdirService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.stage.ActiveSessionRegistry;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
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
    private final SessionWorkdirService workdirs;
    private final FileArtifactRepository fileArtifacts;
    private final ActiveSessionRegistry activeSessions;

    public SessionService(ConnectionRepository connections, SessionRepository repo, Clock clock,
                          OpenCodeGateway gateway, OpenCodeSessionMap sessionMap,
                          SessionBusRegistry buses, Translator translator,
                          SessionWorkdirService workdirs,
                          FileArtifactRepository fileArtifacts,
                          ActiveSessionRegistry activeSessions) {
        this.connections = connections;
        this.repo = repo;
        this.clock = clock;
        this.gateway = gateway;
        this.sessionMap = sessionMap;
        this.buses = buses;
        this.translator = translator;
        this.workdirs = workdirs;
        this.fileArtifacts = fileArtifacts;
        this.activeSessions = activeSessions;
    }

    public synchronized CreateSessionResult create(String connectionId, String title) {
        String effectiveConnectionId = normalizeConnectionId(connectionId);
        Optional<SessionRecord> existing = repo.findEmpty();
        if (existing.isPresent()) {
            SessionRecord reused = existing.get();
            if (!Objects.equals(reused.connectionId(), effectiveConnectionId)) {
                long now = clock.millis();
                reused = new SessionRecord(
                    reused.id(),
                    effectiveConnectionId,
                    reused.title(),
                    reused.hasEverSent(),
                    reused.openCodeSid(),
                    reused.createdAt(),
                    now,
                    reused.titleLocked()
                );
                repo.upsert(reused);
            }
            workdirs.getOrCreate(reused.id(), reused.connectionId());
            activeSessions.markActive(reused.id());
            return new CreateSessionResult(reused, true);
        }
        long now = clock.millis();
        String id = UUID.randomUUID().toString();
        String effectiveTitle = Strings.defaultIfBlank(title, translator.get("session.default_title"));
        SessionRecord rec = new SessionRecord(id, effectiveConnectionId, effectiveTitle, false, null, now, now, false);
        workdirs.getOrCreate(rec.id(), rec.connectionId());
        repo.upsert(rec);
        activeSessions.markActive(rec.id());
        return new CreateSessionResult(rec, false);
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

    public DeleteOutcome delete(String id, boolean force) {
        SessionRecord rec = repo.findById(id).orElse(null);
        if (rec == null) {
            return new DeleteOutcome.NotFound(id);
        }
        if (!force) {
            int candidateCount = fileArtifacts.countCandidatesBySession(id);
            if (candidateCount > 0) {
                List<FileArtifact> candidates = fileArtifacts.findCandidatesBySession(id);
                return new DeleteOutcome.BlockedByCandidates(id, candidates);
            }
        }
        deleteRecord(rec);
        return new DeleteOutcome.Ok();
    }

    /**
     * Backwards-compatible shim. The {@code deleteAll} path needs an
     * unconditional force-delete; existing callers that ignored candidates
     * keep their behavior. New callers MUST go through {@link #delete(String, boolean)}.
     */
    @Deprecated
    public void delete(String id) {
        DeleteOutcome out = delete(id, true);
        if (out instanceof DeleteOutcome.NotFound) {
            throw new NoSuchElementException(translator.get("error.session.not_found", id));
        }
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
        cleanupFileArtifacts(id);
        repo.deleteById(id);
        cleanupWorkdir(id);
        activeSessions.clearIfActive(id);
        // FK ON DELETE CASCADE handles artifacts, action_invocations, events, query_results.
    }

    private void cleanupFileArtifacts(String sessionId) {
        fileArtifacts.deleteTransientByForSession(sessionId);
        fileArtifacts.detachArchivedFromSession(sessionId);
    }

    private void cleanupWorkdir(String sessionId) {
        try {
            workdirs.delete(sessionId);
        } catch (Exception e) {
            log.warn("[session] session workdir cleanup failed for {}: {}", sessionId, e.toString());
        }
    }
}
