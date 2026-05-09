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
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileArtifactServiceRegisterExternalTest {

    @Test
    void registerExternal_inserts_row_with_external_true_and_reads_size_from_disk(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("d1.dashboard.json");
        Files.writeString(file, "{\"a\":1}");

        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("d1")).thenReturn(Optional.empty());
        FileArtifactService svc = newServiceWith(repo);

        FileArtifact result = svc.registerExternal(
                "d1", FileArtifactKind.DASHBOARD, FileArtifactScope.WORKSPACE,
                null, null, file,
                "Title", "Sum", Map.of("k", "v"));

        ArgumentCaptor<FileArtifact> cap = ArgumentCaptor.forClass(FileArtifact.class);
        verify(repo).insert(cap.capture());
        FileArtifact inserted = cap.getValue();
        assertThat(inserted.id()).isEqualTo("d1");
        assertThat(inserted.external()).isTrue();
        assertThat(inserted.scope()).isEqualTo(FileArtifactScope.WORKSPACE);
        assertThat(inserted.status()).isEqualTo(FileArtifactStatus.ARCHIVED);
        assertThat(inserted.kind()).isEqualTo(FileArtifactKind.DASHBOARD);
        assertThat(inserted.physicalPath()).isEqualTo(file.toAbsolutePath().normalize().toString());
        assertThat(inserted.sizeBytes()).isEqualTo(Files.size(file));
        assertThat(inserted.title()).isEqualTo("Title");
        assertThat(inserted.metadata()).containsEntry("k", "v");
        assertThat(result).isEqualTo(inserted);
    }

    @Test
    void registerExternal_throws_if_file_missing(@TempDir Path tmp) {
        Path missing = tmp.resolve("nope.json");
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        FileArtifactService svc = newServiceWith(repo);

        assertThatThrownBy(() -> svc.registerExternal(
                "x", FileArtifactKind.DASHBOARD, FileArtifactScope.WORKSPACE,
                null, null, missing, null, null, Map.of()))
            .isInstanceOf(IOException.class);
        verify(repo, never()).insert(any());
    }

    @Test
    void registerExternal_throws_conflict_if_id_exists(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("d2.json");
        Files.writeString(file, "{}");
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("d2")).thenReturn(Optional.of(mock(FileArtifact.class)));
        FileArtifactService svc = newServiceWith(repo);

        assertThatThrownBy(() -> svc.registerExternal(
                "d2", FileArtifactKind.DASHBOARD, FileArtifactScope.WORKSPACE,
                null, null, file, null, null, Map.of()))
            .isInstanceOf(FileArtifactConflictException.class);
        verify(repo, never()).insert(any());
    }

    @Test
    void registerExternal_rejects_relative_path(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("d3.json");
        Files.writeString(file, "{}");
        Path relative = tmp.relativize(file);
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("d3")).thenReturn(Optional.empty());
        FileArtifactService svc = newServiceWith(repo);

        assertThatThrownBy(() -> svc.registerExternal(
                "d3", FileArtifactKind.DASHBOARD, FileArtifactScope.WORKSPACE,
                null, null, relative, null, null, Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("absolutePath must be absolute");
        verify(repo, never()).insert(any());
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
