package com.datatalk.domain.ingestion;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum InferredType {
    BOOLEAN,
    INTEGER_32,
    INTEGER_64,
    DECIMAL,
    DATE,
    TIMESTAMP,
    STRING_64,
    STRING_256,
    STRING_500,
    STRING_LONG,
    JSON;

    @JsonValue
    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
