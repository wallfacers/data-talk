package com.datatalk.application.fileartifact;

import java.util.UUID;

/**
 * Id generation for file_artifact rows.
 */
public final class FileArtifactIds {

    private static final String PREFIX = "file_artifact_";

    private FileArtifactIds() {
    }

    public static String next() {
        return PREFIX + UUID.randomUUID();
    }

    public static String prefix() {
        return PREFIX;
    }
}
