package com.datatalk.application.session;

import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
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
}
