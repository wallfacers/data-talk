package com.datatalk.adapter.agents;

import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.stage.ActiveSessionDirProvider;
import com.datatalk.application.stage.ConnectionIdProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SessionConnectionIdProvider implements ConnectionIdProvider {

    private final ActiveSessionDirProvider sessionDirProvider;
    private final SessionRepository sessionRepository;

    public SessionConnectionIdProvider(ActiveSessionDirProvider sessionDirProvider, SessionRepository sessionRepository) {
        this.sessionDirProvider = sessionDirProvider;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public Optional<String> currentConnectionId() {
        return sessionDirProvider.currentSessionId()
            .flatMap(sessionRepository::findById)
            .map(r -> r.connectionId())
            .filter(cid -> cid != null && !cid.isBlank());
    }
}
