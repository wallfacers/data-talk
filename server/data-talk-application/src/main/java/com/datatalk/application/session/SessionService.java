package com.datatalk.application.session;

import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    private final SessionRepository repo;
    private final Clock clock;

    public SessionService(SessionRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    public SessionRecord create(String connectionId, String title) {
        long now = clock.millis();
        String id = UUID.randomUUID().toString();
        String safeTitle = (title == null || title.isBlank()) ? "新会话" : title;
        SessionRecord rec = new SessionRecord(id, connectionId, safeTitle, false, null, now, now);
        repo.upsert(rec);
        return rec;
    }

    public List<SessionRecord> list(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) return repo.listAll();
        return repo.listByConnection(connectionId);
    }

    public Optional<SessionRecord> find(String id) {
        return repo.findById(id);
    }

    public SessionRecord rename(String id, String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        SessionRecord existing = repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("session not found: " + id));
        long now = clock.millis();
        repo.updateTitle(id, title, now);
        return new SessionRecord(existing.id(), existing.connectionId(), title,
            existing.hasEverSent(), existing.openCodeSid(), existing.createdAt(), now);
    }

    public void delete(String id) {
        if (repo.findById(id).isEmpty()) {
            throw new NoSuchElementException("session not found: " + id);
        }
        repo.deleteMessagesBySession(id);
        repo.deleteById(id);
    }
}
