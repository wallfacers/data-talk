package com.datatalk.domain.semantic;

import java.util.Objects;

public record Metric(
    String name,
    String type,
    String numerator,
    String denominator,
    String base,
    String timeOffset,
    String labelZh,
    String labelEn,
    String description
) {
    public Metric {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(labelZh, "label_zh must not be null");
        Objects.requireNonNull(labelEn, "label_en must not be null");
    }
}
