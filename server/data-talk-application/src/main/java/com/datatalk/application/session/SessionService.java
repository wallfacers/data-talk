package com.datatalk.application.session;

import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.opencode.OpenCodeSessionMap;
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

    private final SessionRepository repo;
    private final Clock clock;
    private final OpenCodeGateway gateway;
    private final OpenCodeSessionMap sessionMap;

    public SessionService(SessionRepository repo, Clock clock,
                          OpenCodeGateway gateway, OpenCodeSessionMap sessionMap) {
        this.repo = repo;
        this.clock = clock;
        this.gateway = gateway;
        this.sessionMap = sessionMap;
    }

    public SessionRecord create(String connectionId, String title) {
        long now = clock.millis();
        String id = UUID.randomUUID().toString();
        String effectiveTitle = Strings.defaultIfBlank(title, "新会话");
        SessionRecord rec = new SessionRecord(id, connectionId, effectiveTitle, false, null, now, now, false);
        repo.upsert(rec);
        return rec;
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
            throw new IllegalArgumentException("title must not be blank");
        }
        SessionRecord existing = repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("session not found: " + id));
        long now = clock.millis();
        repo.updateTitleAndLock(id, title, now);
        return new SessionRecord(existing.id(), existing.connectionId(), title,
            existing.hasEverSent(), existing.openCodeSid(), existing.createdAt(), now, true);
    }

    public void delete(String id) {
        SessionRecord rec = repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("session not found: " + id));
        repo.deleteById(id);
        // FK ON DELETE CASCADE handles messages, artifacts, action_invocations, events, query_results

        // Drop the matching OpenCode session so the embedded server doesn't
        // accumulate orphan sessions on disk. Best-effort: DB delete is the
        // authoritative step from the user's POV; swallow gateway failures so
        // a flaky OpenCode process never blocks a local delete.
        String ocSid = rec.openCodeSid();
        if (ocSid != null && !ocSid.isBlank()) {
            try {
                gateway.deleteOpenCodeSession(ocSid);
            } catch (Exception e) {
                log.warn("[session] OpenCode-side delete failed for {} (ocSid={}): {}",
                    id, ocSid, e.toString());
            }
            sessionMap.unbind(id);
        }
    }
}
