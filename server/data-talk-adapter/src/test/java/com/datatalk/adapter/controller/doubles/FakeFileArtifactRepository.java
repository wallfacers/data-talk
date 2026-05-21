package com.datatalk.adapter.controller.doubles;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.fileartifact.FileArtifactRepository.ConnectionResourceCounts;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class FakeFileArtifactRepository implements FileArtifactRepository {

    private final Map<String, FileArtifact> store = new HashMap<>();

    @Override public void insert(FileArtifact artifact) { store.put(artifact.id(), artifact); }
    @Override public Optional<FileArtifact> findById(String id) { return Optional.ofNullable(store.get(id)); }

    @Override
    public Optional<FileArtifact> findByPhysicalPath(String physicalPath) {
        return store.values().stream().filter(a -> a.physicalPath().equals(physicalPath)).findFirst();
    }

    @Override public List<FileArtifact> findBySession(String sessionId) {
        return store.values().stream().filter(a -> sessionId.equals(a.sessionId())).collect(Collectors.toList());
    }
    @Override public List<FileArtifact> findArchivedByConnection(String connectionId) {
        return store.values().stream().filter(a -> connectionId.equals(a.connectionId())).collect(Collectors.toList());
    }
    @Override public List<FileArtifact> findCandidatesBySession(String sessionId) {
        return store.values().stream().filter(a -> sessionId.equals(a.sessionId()) && a.status() == FileArtifactStatus.CANDIDATE).collect(Collectors.toList());
    }
    @Override public List<FileArtifact> findAllSessionScoped() {
        return store.values().stream().collect(Collectors.toList());
    }
    @Override public List<FileArtifact> findAllWorkspaceScopedArchived() {
        return store.values().stream().filter(a -> a.status() == FileArtifactStatus.ARCHIVED).collect(Collectors.toList());
    }
    @Override public void updateStatus(String id, FileArtifactStatus newStatus) {
        FileArtifact a = store.get(id); if (a != null) { store.put(id, replace(a, newStatus)); }
    }
    @Override public void updateLocation(String id, FileArtifactStatus newStatus, String newScope, String newPhysicalPath, String newConnectionId) {
        throw new UnsupportedOperationException();
    }
    @Override public void markArchived(String id, String connectionId, String newPhysicalPath) {
        throw new UnsupportedOperationException();
    }
    @Override public void deleteTransientByForSession(String sessionId) {
        throw new UnsupportedOperationException();
    }
    @Override public void detachArchivedFromSession(String sessionId) {
        throw new UnsupportedOperationException();
    }
    @Override public void deleteById(String id) { store.remove(id); }
    @Override public void updateMetadata(String id, long sizeBytes, long updatedAtMillis) {
        FileArtifact a = store.get(id);
        if (a != null) {
            store.put(id, new FileArtifact(a.id(), a.scope(), a.status(), a.kind(), a.sessionId(), a.connectionId(),
                    a.filename(), a.physicalPath(), sizeBytes, a.mimeType(), a.title(), a.summary(),
                    a.createdAt(), java.time.Instant.ofEpochMilli(updatedAtMillis), a.archivedAt(), a.metadata(), a.external()));
        }
    }
    @Override public int countCandidatesBySession(String sessionId) { return 0; }
    @Override public ConnectionResourceCounts countResourcesByConnection(String connectionId, java.util.List<String> sessionIds) {
        return new ConnectionResourceCounts(0, 0, 0, 0);
    }
    @Override public void deleteTransientByForConnection(java.util.List<String> sessionIds) {}
    @Override public void detachArchivedFromConnection(String connectionId, String connectionName, long deletedAtMillis) {}
    @Override public List<FileArtifact> findOrphanedArchived(int limit) {
        return store.values().stream().filter(a -> a.connectionId() == null).limit(limit).collect(Collectors.toList());
    }
    @Override public void reattachArchived(String fileArtifactId, String newConnectionId, String newPhysicalPath, long updatedAtMillis) {
        throw new UnsupportedOperationException();
    }
    @Override public void deleteDiscardedById(String id) { store.remove(id); }
    @Override public int countOrphanedArchived() { return 0; }
    @Override public List<FileArtifact> findExternalRowsByDir(String dirAbsolute) {
        return store.values().stream().filter(a -> a.external() && a.physicalPath().startsWith(dirAbsolute)).collect(Collectors.toList());
    }

    private static FileArtifact replace(FileArtifact a, FileArtifactStatus status) {
        return new FileArtifact(a.id(), a.scope(), status, a.kind(), a.sessionId(), a.connectionId(),
                a.filename(), a.physicalPath(), a.sizeBytes(), a.mimeType(), a.title(), a.summary(),
                a.createdAt(), a.updatedAt(), a.archivedAt(), a.metadata(), a.external());
    }
}
