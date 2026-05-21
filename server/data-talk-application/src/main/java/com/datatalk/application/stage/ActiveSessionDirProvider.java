package com.datatalk.application.stage;

import java.util.Optional;

/**
 * Resolves the currently-active session id for AGENTS.md active directory rendering.
 */
public interface ActiveSessionDirProvider {
    Optional<String> currentSessionId();
}
