package com.datatalk.domain.fileartifact;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * A physical file produced by the AI inside an OpenCode session subdir,
 * or promoted to a connection-scoped library.
 */
public record FileArtifact(
        String id,
        FileArtifactScope scope,
        FileArtifactStatus status,
        FileArtifactKind kind,
        String sessionId,
        String connectionId,
        String filename,
        String physicalPath,
        long sizeBytes,
        String mimeType,
        String title,
        String summary,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt,
        Map<String, Object> metadata,
        boolean external
) {
    public Optional<String> sessionIdOpt() {
        return Optional.ofNullable(sessionId);
    }

    public Optional<String> connectionIdOpt() {
        return Optional.ofNullable(connectionId);
    }

    public Optional<String> mimeTypeOpt() {
        return Optional.ofNullable(mimeType);
    }

    public Optional<String> titleOpt() {
        return Optional.ofNullable(title);
    }

    public Optional<String> summaryOpt() {
        return Optional.ofNullable(summary);
    }

    public Optional<Instant> archivedAtOpt() {
        return Optional.ofNullable(archivedAt);
    }
}
