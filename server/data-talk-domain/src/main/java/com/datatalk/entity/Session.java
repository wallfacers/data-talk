package com.datatalk.entity;

import java.time.Instant;

/**
 * 会话实体
 */
public record Session(
        String id,
        String projectId,
        String title,
        Instant createdAt
) {

    public Session {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("projectId must not be blank");
    }

    public static Session of(String id, String projectId, String title) {
        return new Session(id, projectId, title, Instant.now());
    }
}
