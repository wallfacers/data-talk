package com.datatalk.domain.fileartifact;

import java.util.Locale;

/**
 * Ownership dimension of a {@link FileArtifact}.
 */
public enum FileArtifactScope {
    SESSION,
    WORKSPACE;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FileArtifactScope fromDb(String dbValue) {
        return valueOf(dbValue.toUpperCase(Locale.ROOT));
    }
}
