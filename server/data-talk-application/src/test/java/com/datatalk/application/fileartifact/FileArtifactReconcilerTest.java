package com.datatalk.application.fileartifact;

import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileArtifactReconcilerTest {

    @TempDir
    Path tmp;

    SessionWorkdirRoot root;
    SessionWorkdirService workdir;
    FileArtifactRepository repo;
    FileArtifactService artifactService;
    SessionBusRegistry buses;
    SessionBus bus;
    SessionRepository sessions;
    FileArtifactReconciler reconciler;

    @BeforeEach
    void setUp() throws Exception {
        root = new SessionWorkdirRoot(tmp, tmp.resolve("opencode"));
        workdir = new SessionWorkdirService(root, new ObjectMapper());
        Files.createDirectories(root.sessionsRoot());
        Files.createDirectories(root.workspacesRoot());
        repo = mock(FileArtifactRepository.class);
        artifactService = mock(FileArtifactService.class);
        buses = mock(SessionBusRegistry.class);
        bus = mock(SessionBus.class);
        when(buses.getOrCreate(any())).thenReturn(bus);
        sessions = mock(SessionRepository.class);
        when(sessions.listAll()).thenReturn(List.of(session("ses_x")));
        reconciler = new FileArtifactReconciler(repo, artifactService, workdir, buses, sessions);
    }

    @Test
    void orphanFileUnderSessionDirIsAdopted() throws Exception {
        Path session = root.sessionDir("ses_x");
        Files.createDirectories(session);
        Path file = session.resolve("foo.csv");
        Files.writeString(file, "id,val\n1,2\n");
        when(repo.findAllSessionScoped()).thenReturn(List.of());
        when(repo.findAllWorkspaceScopedArchived()).thenReturn(List.of());
        when(artifactService.recordDetected(eq("ses_x"), eq(file), any())).thenReturn(Optional.of(artifact("a1")));

        reconciler.runFullReconcile();

        verify(artifactService).recordDetected(eq("ses_x"), eq(file), any());
    }

    @Test
    void orphanFileUnderUnknownSessionDirIsSkipped() throws Exception {
        Path deletedSession = root.sessionDir("ses_deleted");
        Files.createDirectories(deletedSession);
        Files.writeString(deletedSession.resolve("orphan.md"), "# stale\n");
        when(repo.findAllSessionScoped()).thenReturn(List.of());
        when(repo.findAllWorkspaceScopedArchived()).thenReturn(List.of());

        reconciler.runFullReconcile();

        verify(artifactService, never()).recordDetected(any(), any(), any());
        verify(buses, never()).getOrCreate(eq("ses_deleted"));
    }

    @Test
    void canonicalizesSessionWalkPathsBeforeComparingRows() throws Exception {
        Path realDataRoot = tmp.resolve("real-data");
        Files.createDirectories(realDataRoot);
        Path linkedDataRoot = tmp.resolve("linked-data");
        createSymlinkOrSkip(linkedDataRoot, realDataRoot);

        SessionWorkdirRoot linkedRoot = new SessionWorkdirRoot(linkedDataRoot, linkedDataRoot.resolve("opencode"));
        SessionWorkdirService linkedWorkdir = new SessionWorkdirService(linkedRoot, new ObjectMapper());
        Files.createDirectories(linkedRoot.sessionsRoot().resolve("ses_x"));
        Path canonicalFile = linkedRoot.sessionsRoot().toRealPath().resolve("ses_x").resolve("foo.csv");
        Files.writeString(canonicalFile, "id,val\n1,2\n");

        FileArtifactRepository linkedRepo = mock(FileArtifactRepository.class);
        FileArtifactService linkedArtifactService = mock(FileArtifactService.class);
        SessionRepository linkedSessions = mock(SessionRepository.class);
        when(linkedSessions.listAll()).thenReturn(List.of(session("ses_x")));
        when(linkedRepo.findAllSessionScoped())
                .thenReturn(List.of(artifactAt("a1", FileArtifactStatus.TEMPORARY, canonicalFile)));
        when(linkedRepo.findAllWorkspaceScopedArchived()).thenReturn(List.of());
        FileArtifactReconciler linkedReconciler =
                new FileArtifactReconciler(linkedRepo, linkedArtifactService, linkedWorkdir, buses, linkedSessions);

        linkedReconciler.runFullReconcile();

        verify(linkedArtifactService, never()).recordDetected(any(), any(), any());
    }

    @Test
    void missingTemporaryRowIsDeletedSilently() {
        FileArtifact row = artifactAt("a1", FileArtifactStatus.TEMPORARY,
                root.sessionDir("ses_x").resolve("missing.csv"));
        when(repo.findAllSessionScoped()).thenReturn(List.of(row));
        when(repo.findAllWorkspaceScopedArchived()).thenReturn(List.of());

        reconciler.runFullReconcile();

        verify(repo).deleteById("a1");
        verify(bus, never()).publish(any());
    }

    @Test
    void missingCandidateRowPublishesDiscarded() {
        FileArtifact row = artifactAt("a1", FileArtifactStatus.CANDIDATE,
                root.sessionDir("ses_x").resolve("missing.md"));
        when(repo.findAllSessionScoped()).thenReturn(List.of(row));
        when(repo.findAllWorkspaceScopedArchived()).thenReturn(List.of());

        reconciler.runFullReconcile();

        verify(repo).deleteById("a1");
        verify(bus).publish(any(DtEvent.FileArtifactDiscarded.class));
    }

    @Test
    void missingCandidateRowForUnknownSessionDoesNotPublishDiscarded() {
        FileArtifact row = new FileArtifact(
                "a1",
                FileArtifactScope.SESSION,
                FileArtifactStatus.CANDIDATE,
                FileArtifactKind.REPORT,
                "ses_deleted",
                null,
                "missing.md",
                root.sessionDir("ses_deleted").resolve("missing.md").toAbsolutePath().normalize().toString(),
                1L,
                "text/markdown",
                null,
                null,
                Instant.now(),
                Instant.now(),
                null,
                Map.of(), false);
        when(repo.findAllSessionScoped()).thenReturn(List.of(row));
        when(repo.findAllWorkspaceScopedArchived()).thenReturn(List.of());

        reconciler.runFullReconcile();

        verify(repo).deleteById("a1");
        verify(buses, never()).getOrCreate(eq("ses_deleted"));
        verify(bus, never()).publish(any());
    }

    @Test
    void missingArchivedWorkspaceRowIsDeletedAndPublishesWhenSessionKnown() {
        FileArtifact row = new FileArtifact(
                "w1",
                FileArtifactScope.WORKSPACE,
                FileArtifactStatus.ARCHIVED,
                FileArtifactKind.REPORT,
                "ses_x",
                "conn_x",
                "report.md",
                root.workspaceDir("conn_x").resolve("report.md").toAbsolutePath().normalize().toString(),
                1L,
                "text/markdown",
                null,
                null,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                Map.of(), false);
        when(repo.findAllSessionScoped()).thenReturn(List.of());
        when(repo.findAllWorkspaceScopedArchived()).thenReturn(List.of(row));

        reconciler.runFullReconcile();

        verify(repo).deleteById("w1");
        verify(bus).publish(any(DtEvent.FileArtifactDiscarded.class));
    }

    @Test
    void hiddenTempAndUnderscoreSessionDirsAreSkipped() throws Exception {
        Path session = root.sessionDir("ses_x");
        Files.createDirectories(session);
        Files.writeString(session.resolve(".hidden.md"), "x");
        Files.writeString(session.resolve("file.tmp"), "x");
        Files.createDirectories(root.sessionsRoot().resolve("_system"));
        Files.writeString(root.sessionsRoot().resolve("_system").resolve("ignored.md"), "x");
        when(repo.findAllSessionScoped()).thenReturn(List.of());
        when(repo.findAllWorkspaceScopedArchived()).thenReturn(List.of());

        reconciler.runFullReconcile();

        verify(artifactService, never()).recordDetected(any(), any(), any());
    }

    @Test
    void fileDirectlyUnderSessionsRootIsSkipped() throws Exception {
        Files.writeString(root.sessionsRoot().resolve("orphan.md"), "x");
        when(repo.findAllSessionScoped()).thenReturn(List.of());
        when(repo.findAllWorkspaceScopedArchived()).thenReturn(List.of());

        reconciler.runFullReconcile();

        verify(artifactService, never()).recordDetected(any(), any(), any());
    }

    private static FileArtifact artifact(String id) {
        return artifactAt(id, FileArtifactStatus.TEMPORARY, Path.of("/abs/x.md"));
    }

    private static FileArtifact artifactAt(String id, FileArtifactStatus status, Path path) {
        Instant now = Instant.now();
        return new FileArtifact(
                id,
                FileArtifactScope.SESSION,
                status,
                FileArtifactKind.OTHER,
                "ses_x",
                null,
                path.getFileName().toString(),
                path.toAbsolutePath().normalize().toString(),
                1L,
                "text/markdown",
                null,
                null,
                now,
                now,
                null,
                Map.of(), false);
    }

    private static SessionRecord session(String id) {
        return new SessionRecord(id, null, id, false, null, 1L, 1L, false);
    }

    @Test
    void reconcileTrash_removes_fs_orphans_without_db_row() throws IOException {
        Path trashDir = workdir.root().trashRoot();
        Files.createDirectories(trashDir);
        Path orphan = trashDir.resolve("conn_x__fa_99__orphan.md");
        Files.writeString(orphan, "orphan");
        when(repo.findAllSessionScoped()).thenReturn(List.of());

        reconciler.reconcileTrash();

        assertThat(Files.exists(orphan)).isFalse();
    }

    @Test
    void reconcileWorkspacesTree_skips_null_connection_id_archived_rows() {
        FileArtifact orphan = new FileArtifact(
                "fa_orphan", com.datatalk.domain.fileartifact.FileArtifactScope.WORKSPACE,
                FileArtifactStatus.ARCHIVED, com.datatalk.domain.fileartifact.FileArtifactKind.OTHER,
                null, null, "report.md", "/nonexistent/report.md",
                100L, null, null, null, Instant.now(), Instant.now(), Instant.now(), Map.of(), false);
        when(repo.findAllWorkspaceScopedArchived()).thenReturn(List.of(orphan));
        when(repo.findAllSessionScoped()).thenReturn(List.of());

        reconciler.runFullReconcile();

        verify(repo, never()).deleteById("fa_orphan");
    }

    private static void createSymlinkOrSkip(Path link, Path target) throws Exception {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
            assumeTrue(false, "symbolic links are not available: " + e);
        }
    }
}
