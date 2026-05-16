package com.datatalk.adapter.actions;

import com.datatalk.application.upload.UploadedFileRepository;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.upload.UploadedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FileReadActionHandlerTest {

    private final UploadedFileRepository repo = mock(UploadedFileRepository.class);
    private final FileReadActionHandler handler = new FileReadActionHandler(repo);
    private final ActionContext ctx = new ActionContext("s1", "call", null, null);

    @TempDir
    Path tempDir;

    @Test
    void imageFile_returnsBase64DataUri() throws Exception {
        // Create a small test file
        byte[] content = "fake-image-data".getBytes();
        Path file = tempDir.resolve("test.png");
        Files.write(file, content);

        UploadedFile uf = new UploadedFile("f1", "s1", "test.png", "image/png",
                content.length, file.toAbsolutePath().toString(), Map.of(), Instant.now());
        when(repo.findById("f1")).thenReturn(Optional.of(uf));

        Map result = handler.handle(ctx, Map.of("fileId", "f1")).toCompletableFuture().join();

        assertThat(result).containsEntry("fileId", "f1");
        assertThat(result).containsEntry("offset", 0);
        String dataUri = (String) result.get("content");
        assertThat(dataUri).startsWith("data:image/png;base64,");

        String encoded = dataUri.substring("data:image/png;base64,".length());
        byte[] decoded = Base64.getDecoder().decode(encoded);
        assertThat(decoded).isEqualTo(content);
        assertThat(result).containsEntry("bytesRead", content.length);
    }

    @Test
    void imageFile_ignoresOffsetAndLimit() throws Exception {
        byte[] content = "binary-image-content-here".getBytes();
        Path file = tempDir.resolve("img.jpg");
        Files.write(file, content);

        UploadedFile uf = new UploadedFile("f2", "s1", "img.jpg", "image/jpeg",
                content.length, file.toAbsolutePath().toString(), Map.of(), Instant.now());
        when(repo.findById("f2")).thenReturn(Optional.of(uf));

        Map result = handler.handle(ctx, Map.of("fileId", "f2", "offset", 10, "limit", 5))
                .toCompletableFuture().join();

        // Should return full content, ignoring offset/limit
        assertThat(result).containsEntry("offset", 0);
        String dataUri = (String) result.get("content");
        assertThat(dataUri).startsWith("data:image/jpeg;base64,");
        assertThat(result).containsEntry("bytesRead", content.length);
    }

    @Test
    void textFile_usesTextReadPath() throws Exception {
        String textContent = "hello world";
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, textContent);

        UploadedFile uf = new UploadedFile("f3", "s1", "test.txt", "text/plain",
                textContent.length(), file.toAbsolutePath().toString(), Map.of(), Instant.now());
        when(repo.findById("f3")).thenReturn(Optional.of(uf));

        Map result = handler.handle(ctx, Map.of("fileId", "f3"))
                .toCompletableFuture().join();

        assertThat(result).containsEntry("fileId", "f3");
        assertThat(result.get("content")).isEqualTo(textContent);
        assertThat(result).containsEntry("bytesRead", textContent.length());
    }

    @Test
    void textFile_respectsOffsetAndLimit() throws Exception {
        String textContent = "0123456789abcdef";
        Path file = tempDir.resolve("test.sql");
        Files.writeString(file, textContent);

        UploadedFile uf = new UploadedFile("f4", "s1", "test.sql", "text/x-sql",
                textContent.length(), file.toAbsolutePath().toString(), Map.of(), Instant.now());
        when(repo.findById("f4")).thenReturn(Optional.of(uf));

        Map result = handler.handle(ctx, Map.of("fileId", "f4", "offset", 4, "limit", 4))
                .toCompletableFuture().join();

        assertThat(result.get("content")).isEqualTo("4567");
        assertThat(result).containsEntry("offset", 4);
        assertThat(result).containsEntry("bytesRead", 4);
    }

    @Test
    void missingFile_returnsError() {
        when(repo.findById("missing")).thenReturn(Optional.empty());

        Map result = handler.handle(ctx, Map.of("fileId", "missing"))
                .toCompletableFuture().join();

        assertThat(result).containsKey("error");
    }
}
