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
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileArtifactServiceReplaceBytesAtomicTest {

    @Test
    void replaceBytesAtomic_writes_new_content_and_updates_metadata(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("d1.dashboard.json");
        Files.writeString(file, "{\"old\":true}");

        FileArtifact row = externalRow("d1", file);
        FileArtifact updatedRow = externalRow("d1", file);
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("d1")).thenReturn(Optional.of(row), Optional.of(updatedRow));
        FileArtifactService svc = newServiceWith(repo);

        byte[] newContent = "{\"new\":true}".getBytes();
        FileArtifact result = svc.replaceBytesAtomic("d1", newContent);

        // File on disk should have new content
        assertThat(Files.readAllBytes(file)).isEqualTo(newContent);
        // Metadata should be updated with new size
        verify(repo).updateMetadata(eq("d1"), eq((long) newContent.length), anyLong());
        assertThat(result).isNotNull();
    }

    @Test
    void replaceBytesAtomic_throws_notFound_when_id_unknown() throws IOException {
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("unknown")).thenReturn(Optional.empty());
        FileArtifactService svc = newServiceWith(repo);

        assertThatThrownBy(() -> svc.replaceBytesAtomic("unknown", "data".getBytes()))
            .isInstanceOf(FileArtifactNotFoundException.class);
        verify(repo, never()).updateMetadata(eq("unknown"), anyLong(), anyLong());
    }

    @Test
    void replaceBytesAtomic_throws_illegal_state_for_managed_row(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("managed.md");
        Files.writeString(file, "content");

        Instant now = Instant.now();
        FileArtifact managedRow = new FileArtifact(
                "m1", FileArtifactScope.SESSION, FileArtifactStatus.ARCHIVED,
                FileArtifactKind.REPORT, "ses_1", null,
                "managed.md", file.toString(),
                100L, "text/markdown", null, null,
                now, now, now, Map.of(), false);
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("m1")).thenReturn(Optional.of(managedRow));
        FileArtifactService svc = newServiceWith(repo);

        assertThatThrownBy(() -> svc.replaceBytesAtomic("m1", "new".getBytes()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("replaceBytesAtomic on managed row not supported");
        verify(repo, never()).updateMetadata(eq("m1"), anyLong(), anyLong());
    }

    @Test
    void replaceBytesAtomic_is_atomic_file_replaced_not_appended(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("d2.dashboard.json");
        Files.writeString(file, "long initial content that is much bigger than replacement");

        FileArtifact row = externalRow("d2", file);
        FileArtifact updatedRow = externalRow("d2", file);
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("d2")).thenReturn(Optional.of(row), Optional.of(updatedRow));
        FileArtifactService svc = newServiceWith(repo);

        byte[] shortContent = "x".getBytes();
        svc.replaceBytesAtomic("d2", shortContent);

        // File should contain exactly the short content, not the old content + new
        assertThat(Files.readAllBytes(file)).isEqualTo(shortContent);
        assertThat(Files.size(file)).isEqualTo(1);
    }

    @Test
    void replaceBytesAtomic_creates_parent_dirs_if_missing(@TempDir Path tmp) throws IOException {
        Path nested = tmp.resolve("sub/dir/d3.dashboard.json");
        // Don't create parent dirs or file — AtomicFileWriter should handle it

        FileArtifact row = externalRow("d3", nested);
        FileArtifact updatedRow = externalRow("d3", nested);
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("d3")).thenReturn(Optional.of(row), Optional.of(updatedRow));
        FileArtifactService svc = newServiceWith(repo);

        byte[] content = "{\"created\":true}".getBytes();
        svc.replaceBytesAtomic("d3", content);

        assertThat(Files.readAllBytes(nested)).isEqualTo(content);
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
