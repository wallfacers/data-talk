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
}
