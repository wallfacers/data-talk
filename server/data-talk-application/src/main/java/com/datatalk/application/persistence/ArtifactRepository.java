package com.datatalk.application.persistence;

import java.util.Optional;

/**
 * Reads and writes artifact records.
 */
public interface ArtifactRepository {

    Optional<ArtifactRecord> findById(String artifactId);

    void upsert(ArtifactRecord record);
}
