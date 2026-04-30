package com.datatalk.application.fileartifact;

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        reconciler = new FileArtifactReconciler(repo, artifactService, workdir, buses);
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
                Map.of());
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
                Map.of());
    }
}
