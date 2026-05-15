package com.datatalk.domain.semantic;

import java.util.Objects;

public record Measure(
    String name,
    String entity,
    String agg,
    String expr,
    String filter,
    String labelZh,
    String labelEn,
    String description
) {
    public Measure {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(entity, "entity must not be null");
        Objects.requireNonNull(agg, "agg must not be null");
        Objects.requireNonNull(expr, "expr must not be null");
        Objects.requireNonNull(labelZh, "label_zh must not be null");
        Objects.requireNonNull(labelEn, "label_en must not be null");
    }
}
