package com.datatalk.application.fileartifact;

import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileArtifactServiceReadBytesTest {

    @Test
    void readBytes_returns_file_content_for_external_row(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("d1.dashboard.json");
        byte[] content = "{\"dashboard\":\"v1\"}".getBytes();
        Files.write(file, content);

        FileArtifact row = externalRow("d1", file);
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("d1")).thenReturn(Optional.of(row));
        FileArtifactService svc = newServiceWith(repo);

        byte[] result = svc.readBytes("d1");
        assertThat(result).isEqualTo(content);
    }

    @Test
    void readBytes_throws_notFound_when_id_unknown() {
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("unknown")).thenReturn(Optional.empty());
        FileArtifactService svc = newServiceWith(repo);

        assertThatThrownBy(() -> svc.readBytes("unknown"))
            .isInstanceOf(FileArtifactNotFoundException.class)
            .hasMessageContaining("unknown");
    }

    @Test
    void readBytes_throws_notFound_when_physical_file_missing(@TempDir Path tmp) {
        Path missing = tmp.resolve("deleted.json");
        // Row exists but file does not
        FileArtifact row = externalRow("d_gone", missing);
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("d_gone")).thenReturn(Optional.of(row));
        FileArtifactService svc = newServiceWith(repo);

        assertThatThrownBy(() -> svc.readBytes("d_gone"))
            .isInstanceOf(FileArtifactNotFoundException.class)
            .hasMessageContaining("d_gone")
            .hasMessageContaining("physical file missing");
    }

    @Test
    void readBytes_returns_content_for_managed_row_via_workdir_root(@TempDir Path tmp) throws IOException {
        // Set up workdir with dataTalkRoot
        Path dataTalkRoot = tmp.resolve("dt-root");
        Files.createDirectories(dataTalkRoot);
        SessionWorkdirRoot workdirRoot = new SessionWorkdirRoot(dataTalkRoot, tmp.resolve("opencode"));

        // Create a physical file under dataTalkRoot
        Path managedFile = dataTalkRoot.resolve("workspaces/conn1/report.md");
        Files.createDirectories(managedFile.getParent());
        byte[] content = "# Report\n".getBytes();
        Files.write(managedFile, content);

        // Row has relative-ish path under dataTalkRoot and external=false
        FileArtifact row = managedRow("m1", "workspaces/conn1/report.md");
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("m1")).thenReturn(Optional.of(row));

        SessionWorkdirService workdir = mock(SessionWorkdirService.class);
        when(workdir.root()).thenReturn(workdirRoot);

        FileArtifactService svc = new FileArtifactService(
                repo, workdir,
                mock(SessionBusRegistry.class), new ObjectMapper(),
                mock(FileArtifactPhysicalMover.class),
                mock(SessionRepository.class),
                mock(ConnectionRepository.class));

        byte[] result = svc.readBytes("m1");
        assertThat(result).isEqualTo(content);
    }

    private static FileArtifact externalRow(String id, Path file) {
        Instant now = Instant.now();
        return new FileArtifact(
                id, FileArtifactScope.WORKSPACE, FileArtifactStatus.ARCHIVED,
                FileArtifactKind.DASHBOARD, null, null,
                file.getFileName().toString(), file.toAbsolutePath().normalize().toString(),
                100L, "application/json", null, null,
                now, now, now, Map.of(), true);
    }

    private static FileArtifact managedRow(String id, String relativePath) {
        Instant now = Instant.now();
        return new FileArtifact(
                id, FileArtifactScope.WORKSPACE, FileArtifactStatus.ARCHIVED,
                FileArtifactKind.REPORT, "ses_1", "conn1",
                "report.md", relativePath,
                100L, "text/markdown", null, null,
                now, now, now, Map.of(), false);
    }

    private FileArtifactService newServiceWith(FileArtifactRepository repo) {
        return new FileArtifactService(
                repo,
                mock(SessionWorkdirService.class),
                mock(SessionBusRegistry.class),
                new ObjectMapper(),
                mock(FileArtifactPhysicalMover.class),
                mock(SessionRepository.class),
                mock(ConnectionRepository.class));
    }
}
