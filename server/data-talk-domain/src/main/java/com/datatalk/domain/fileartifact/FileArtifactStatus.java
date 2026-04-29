package com.datatalk.domain.fileartifact;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Lifecycle states for {@link FileArtifact}.
 */
public enum FileArtifactStatus {
    TEMPORARY,
    CANDIDATE,
    ARCHIVED,
    DISCARDED;

    @JsonValue
    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FileArtifactStatus fromDb(String dbValue) {
        return valueOf(dbValue.toUpperCase(Locale.ROOT));
    }
}
