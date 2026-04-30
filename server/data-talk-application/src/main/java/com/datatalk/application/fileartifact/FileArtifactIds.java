package com.datatalk.application.fileartifact;

import java.util.UUID;

/**
 * Id generation for file_artifact rows.
 *
 * <p>The spec names ULIDs, but this module currently has no ULID dependency.
 * UUID keeps Part 2 dependency-neutral while preserving the stable prefix.
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
