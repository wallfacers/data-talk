package com.datatalk.domain.stage;

import java.util.Objects;

/**
 * Core tab metadata record. Payload and content are stored separately
 * in {@link StageTabContent} for selective loading.
 */
public record StageTab(
    String id,
    String type,
    StageTabScope scope,
    String title,
    String connectionId,
    String databaseName,
    String schemaName,
    String originSessionId,
    int payloadVersion,
    boolean pinned,
    boolean archived,
    Long archivedAt,
    long createdAt,
    long lastTouchedAt
) {
    public StageTab {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(title, "title must not be null");
        if (payloadVersion < 1) {
            throw new IllegalArgumentException("payloadVersion must be >= 1, got " + payloadVersion);
        }
        if (scope == StageTabScope.SESSION && (originSessionId == null || originSessionId.isBlank())) {
            throw new IllegalArgumentException("SESSION scope requires originSessionId");
        }
    }
}
