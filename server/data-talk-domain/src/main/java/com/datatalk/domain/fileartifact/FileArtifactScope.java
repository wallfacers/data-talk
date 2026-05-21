package com.datatalk.domain.fileartifact;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Ownership dimension of a {@link FileArtifact}.
 */
public enum FileArtifactScope {
    SESSION,
    WORKSPACE;

    @JsonValue
    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FileArtifactScope fromDb(String dbValue) {
        return valueOf(dbValue.toUpperCase(Locale.ROOT));
    }
}
