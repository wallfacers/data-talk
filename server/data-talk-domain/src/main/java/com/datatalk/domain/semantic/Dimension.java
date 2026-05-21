package com.datatalk.domain.semantic;

import java.util.Objects;

public record Dimension(
    String name,
    String entity,
    String expr,
    String type,
    String timeGranularity,
    String labelZh,
    String labelEn,
    String description
) {
    public Dimension {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(entity, "entity must not be null");
        Objects.requireNonNull(expr, "expr must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(labelZh, "label_zh must not be null");
        Objects.requireNonNull(labelEn, "label_en must not be null");
    }
}
