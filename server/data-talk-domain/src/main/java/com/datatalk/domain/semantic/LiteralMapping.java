package com.datatalk.domain.semantic;

import java.util.Objects;

public record LiteralMapping(
    String dimension,
    String natural,
    String dbValue
) {
    public LiteralMapping {
        Objects.requireNonNull(dimension, "dimension must not be null");
        Objects.requireNonNull(natural, "natural must not be null");
        Objects.requireNonNull(dbValue, "dbValue must not be null");
    }
}
