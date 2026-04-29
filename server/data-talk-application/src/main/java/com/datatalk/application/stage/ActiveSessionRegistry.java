package com.datatalk.application.stage;

import com.datatalk.application.fileartifact.SessionWorkdirService;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-local best effort view of the session most recently activated by the server.
 */
@Component
public class ActiveSessionRegistry implements ActiveSessionDirProvider {

    private final AtomicReference<String> currentSessionId = new AtomicReference<>();

    public void markActive(String sessionId) {
        currentSessionId.set(SessionWorkdirService.requireSafeSessionId(sessionId));
    }

    public void clearIfActive(String sessionId) {
        String safeSessionId = SessionWorkdirService.requireSafeSessionId(sessionId);
        currentSessionId.compareAndSet(safeSessionId, null);
    }

    @Override
    public Optional<String> currentSessionId() {
        return Optional.ofNullable(currentSessionId.get());
    }
}
