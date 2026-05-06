package com.datatalk.application.fileartifact;

import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactStatus;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link FileArtifact}.
 *
 * <p>Infrastructure owns the JDBC implementation; application code depends only on this port.
 */
public interface FileArtifactRepository {

    void insert(FileArtifact artifact);

    Optional<FileArtifact> findById(String id);

    Optional<FileArtifact> findByPhysicalPath(String physicalPath);

    List<FileArtifact> findBySession(String sessionId);

    List<FileArtifact> findArchivedByConnection(String connectionId);

    List<FileArtifact> findCandidatesBySession(String sessionId);

    List<FileArtifact> findAllSessionScoped();

    List<FileArtifact> findAllWorkspaceScopedArchived();

    void updateStatus(String id, FileArtifactStatus newStatus);

    void updateLocation(
            String id,
            FileArtifactStatus newStatus,
            String newScope,
            String newPhysicalPath,
            String newConnectionId);

    void markArchived(String id, String connectionId, String newPhysicalPath);

    void deleteTransientByForSession(String sessionId);

    void detachArchivedFromSession(String sessionId);

    void deleteById(String id);

    void updateMetadata(String id, long sizeBytes, long updatedAtMillis);

    int countCandidatesBySession(String sessionId);

    /**
     * Aggregate counts of file_artifact rows that belong to a connection
     * (either via session_id of a child session or directly via connection_id).
     * Used by Phase 1 of connection DELETE.
     */
    ConnectionResourceCounts countResourcesByConnection(String connectionId, java.util.List<String> sessionIds);

    /**
     * Bulk delete temporary + candidate rows for every session under a
     * connection. Equivalent to calling {@link #deleteTransientByForSession}
     * for each session_id.
     */
    void deleteTransientByForConnection(java.util.List<String> sessionIds);

    /**
     * Detach archived rows from a deleted connection. For every row matching
     * connection_id, set connection_id = NULL and stamp metadata_json with
     * the orphan provenance fields (spec §A.4 / §B.3.1):
     *   orphanedFromConnection   = original connection.name (human-readable)
     *   orphanedFromConnectionId = original connection.id
     *   orphanedAt               = epoch millis at deletion time
     *
     * <p>Application layer must call this BEFORE the connection row itself
     * is removed (so the name is still discoverable). Same transaction as
     * the connection row delete.
     */
    void detachArchivedFromConnection(String connectionId, String connectionName, long deletedAtMillis);

    record ConnectionResourceCounts(int sessions, int candidates, int temporary, int archived) {
    }
}
