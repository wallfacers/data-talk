package com.datatalk.domain.ingestion;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum PayloadFormat {
    JSON,
    JSONL,
    CSV,
    HTML;

    @JsonValue
    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
