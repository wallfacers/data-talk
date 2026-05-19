package com.datatalk.domain.fileartifact;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * AI / user-declared category of a {@link FileArtifact}.
 */
public enum FileArtifactKind {
    REPORT,
    REPORT_ASSET,
    REPORT_DATA_CSV,
    ER_DIAGRAM,
    SQL_SCRIPT,
    DATASET,
    DASHBOARD,
    OTHER;

    @JsonValue
    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FileArtifactKind fromDb(String dbValue) {
        return valueOf(dbValue.toUpperCase(Locale.ROOT));
    }
}
