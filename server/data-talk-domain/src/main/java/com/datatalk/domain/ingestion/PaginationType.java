package com.datatalk.domain.ingestion;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum PaginationType {
    NONE,
    PAGE,
    OFFSET,
    CURSOR;

    @JsonValue
    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
