package com.datatalk.application.fileartifact;

import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
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
import static org.mockito.Mockito.*;

class FileArtifactReconcilerExternalDirsTest {

    @Test
    void reconcileExternalDirs_deletes_files_with_no_db_row(@TempDir Path tmp) throws IOException {
        Path orphan = Files.writeString(tmp.resolve("orphan.dashboard.json"), "{}");
        Path tracked = Files.writeString(tmp.resolve("tracked.dashboard.json"), "{}");

        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findExternalRowsByDir(tmp.toString())).thenReturn(List.of(
                externalRow("t1", tracked.toString())
        ));
        when(repo.findByPhysicalPath(tracked.toString())).thenReturn(Optional.of(externalRow("t1", tracked.toString())));
        when(repo.findByPhysicalPath(orphan.toString())).thenReturn(Optional.empty());

        FileArtifactReconciler r = newReconciler(repo, tmp);
        r.reconcileExternalDirs(List.of(tmp));

        assertThat(Files.exists(orphan)).isFalse();
        assertThat(Files.exists(tracked)).isTrue();
        verify(repo, never()).deleteById("t1");
    }

    @Test
    void reconcileExternalDirs_deletes_db_rows_with_missing_physical(@TempDir Path tmp) {
        Path missing = tmp.resolve("ghost.dashboard.json");
        FileArtifact row = externalRow("g1", missing.toString());

        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findExternalRowsByDir(tmp.toString())).thenReturn(List.of(row));

        FileArtifactReconciler r = newReconciler(repo, tmp);
        r.reconcileExternalDirs(List.of(tmp));

        verify(repo).deleteById("g1");
    }

    private FileArtifact externalRow(String id, String path) {
        Instant now = Instant.now();
        return new FileArtifact(
                id, FileArtifactScope.WORKSPACE, FileArtifactStatus.ARCHIVED,
                FileArtifactKind.DASHBOARD, null, null,
                Path.of(path).getFileName().toString(), path,
                10L, null, null, null, now, now, now, Map.of(), true);
    }

    private FileArtifactReconciler newReconciler(FileArtifactRepository repo, Path tmp) {
        SessionWorkdirService workdir = mock(SessionWorkdirService.class);
        when(workdir.root()).thenReturn(new SessionWorkdirRoot(tmp.getParent(), tmp.getParent().resolve("opencode")));
        return new FileArtifactReconciler(
                repo,
                mock(FileArtifactService.class),
                workdir,
                mock(SessionBusRegistry.class),
                mock(SessionRepository.class));
    }
}
