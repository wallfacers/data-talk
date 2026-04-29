package com.datatalk.application.fileartifact;

import java.nio.file.Path;

/**
 * Layout anchors for file artifact storage.
 */
public record SessionWorkdirRoot(Path dataTalkRoot, Path opencodeCwd) {

    public Path sessionsRoot() {
        return opencodeCwd.resolve("sessions");
    }

    public Path sessionDir(String sessionId) {
        return sessionsRoot().resolve(sessionId);
    }

    public Path workspacesRoot() {
        return dataTalkRoot.resolve("workspaces");
    }

    public Path workspaceDir(String connectionId) {
        return workspacesRoot().resolve(connectionId);
    }

    public Path trashRoot() {
        return dataTalkRoot.resolve("_trash");
    }

    public Path legacyRoot() {
        return dataTalkRoot.resolve("_legacy");
    }
}
