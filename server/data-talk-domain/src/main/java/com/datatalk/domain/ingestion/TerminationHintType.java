package com.datatalk.domain.ingestion;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum TerminationHintType {
    EMPTY_ARRAY,
    JSON_PATH_COUNT_ZERO,
    HTTP_STATUS_404;

    @JsonValue
    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
