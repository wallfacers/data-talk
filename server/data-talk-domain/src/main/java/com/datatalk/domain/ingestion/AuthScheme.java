package com.datatalk.domain.ingestion;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum AuthScheme {
    NONE,
    BEARER,
    API_KEY_HEADER,
    API_KEY_QUERY,
    BASIC;

    @JsonValue
    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
