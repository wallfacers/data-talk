package com.datatalk.domain.semantic;

import java.time.Instant;
import java.util.Objects;

public record VerifiedQuery(
    String id,
    String question,
    String sql,
    String modelRef,
    int hitCount,
    Instant lastHit,
    String confirmedBy,
    Instant confirmedAt,
    boolean stale
) {
    public VerifiedQuery {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(question, "question must not be null");
        Objects.requireNonNull(sql, "sql must not be null");
        Objects.requireNonNull(modelRef, "modelRef must not be null");
        if (hitCount < 0) throw new IllegalArgumentException("hitCount must be >= 0, got " + hitCount);
    }
}
