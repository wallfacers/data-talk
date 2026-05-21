package com.datatalk.adapter.actions;

import com.datatalk.application.upload.UploadedFileRepository;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.upload.UploadedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FileReadActionHandlerTest {

    private final UploadedFileRepository repo = mock(UploadedFileRepository.class);
    private final FileReadActionHandler handler = new FileReadActionHandler(repo);
    private final ActionContext ctx = new ActionContext("s1", "call", null, null);

    @TempDir
    Path tempDir;

    // ---------- Existing image-branch coverage (now exercising the small-file path) ----------

    @Test
    void smallImageFile_returnsBase64DataUri_withBelowThresholdSkip() throws Exception {
        // Small PNG (< 50KB) — hits ImageCompressor below_threshold path, which keeps
        // the original mime and bytes in the data URI.
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
        // New observability fields
        assertThat(result).containsEntry("compressionApplied", false);
        assertThat(result).containsEntry("compressionSkipReason", "below_threshold");
        assertThat(result).containsEntry("compressedMimeType", "image/png");
        assertThat(result).containsEntry("originalBytes", (long) content.length);
        assertThat(result).containsEntry("compressedBytes", content.length);
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
        // Small synthetic JPEG bytes also skip via below_threshold
        assertThat(result).containsEntry("compressionApplied", false);
    }

    // ---------- New: image compression scenarios ----------

    @Test
    void largePng_isCompressedToJpegDataUri() throws Exception {
        // 4K resolution so the resize maxEdge=2048 actually shrinks the dimensions
        // and JPEG q=0.85 of the resulting mosaic stays well below the source PNG.
        Path png = writeBusyPng(3840, 2160, "screenshot.png");
        long originalBytes = Files.size(png);
        assertThat(originalBytes).isGreaterThan(50L * 1024L);

        UploadedFile uf = new UploadedFile("img-large", "s1", "screenshot.png", "image/png",
                originalBytes, png.toAbsolutePath().toString(), Map.of(), Instant.now());
        when(repo.findById("img-large")).thenReturn(Optional.of(uf));

        Map result = handler.handle(ctx, Map.of("fileId", "img-large")).toCompletableFuture().join();

        String dataUri = (String) result.get("content");
        assertThat(dataUri).startsWith("data:image/jpeg;base64,");
        assertThat(result).containsEntry("compressionApplied", true);
        assertThat(result).containsEntry("compressedMimeType", "image/jpeg");
        assertThat(result).doesNotContainKey("compressionSkipReason");
        // compressedBytes < originalBytes for a busy 1920×1080 PNG re-encoded to JPEG q=0.85
        int compressedBytes = (int) result.get("compressedBytes");
        assertThat(compressedBytes).isLessThan((int) originalBytes);
        assertThat(result).containsEntry("originalBytes", originalBytes);
        assertThat(result).containsEntry("bytesRead", compressedBytes);
    }

    @Test
    void gifImage_isPassthroughWithSkipReason() throws Exception {
        Path gif = writeBusyGif(800, 600, "anim.gif");
        long originalBytes = Files.size(gif);

        UploadedFile uf = new UploadedFile("img-gif", "s1", "anim.gif", "image/gif",
                originalBytes, gif.toAbsolutePath().toString(), Map.of(), Instant.now());
        when(repo.findById("img-gif")).thenReturn(Optional.of(uf));

        Map result = handler.handle(ctx, Map.of("fileId", "img-gif")).toCompletableFuture().join();

        String dataUri = (String) result.get("content");
        assertThat(dataUri).startsWith("data:image/gif;base64,");
        assertThat(result).containsEntry("compressionApplied", false);
        assertThat(result).containsEntry("compressionSkipReason", "animated_passthrough");
        assertThat(result).containsEntry("compressedMimeType", "image/gif");
        // Passthrough preserves byte count
        assertThat(result).containsEntry("originalBytes", originalBytes);
        assertThat(result).containsEntry("compressedBytes", (int) originalBytes);
    }

    @Test
    void smallPng_belowThreshold_preservesOriginalMime() throws Exception {
        // 100×100 solid PNG is well under 50KB → skip with below_threshold; data URI
        // must keep the original image/png mime, not be rewritten to image/jpeg.
        Path png = writeSolidPng(100, 100, "tiny.png");
        long originalBytes = Files.size(png);
        assertThat(originalBytes).isLessThanOrEqualTo(50L * 1024L);

        UploadedFile uf = new UploadedFile("img-small", "s1", "tiny.png", "image/png",
                originalBytes, png.toAbsolutePath().toString(), Map.of(), Instant.now());
        when(repo.findById("img-small")).thenReturn(Optional.of(uf));

        Map result = handler.handle(ctx, Map.of("fileId", "img-small")).toCompletableFuture().join();

        String dataUri = (String) result.get("content");
        assertThat(dataUri).startsWith("data:image/png;base64,");
        assertThat(result).containsEntry("compressionApplied", false);
        assertThat(result).containsEntry("compressionSkipReason", "below_threshold");
        assertThat(result).containsEntry("compressedMimeType", "image/png");
    }

    // ---------- Existing text path coverage — MUST NOT carry image observability keys ----------

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
        // Spec: non-image paths MUST NOT carry image observability fields.
        assertThat(result).doesNotContainKey("originalBytes");
        assertThat(result).doesNotContainKey("compressedBytes");
        assertThat(result).doesNotContainKey("compressionApplied");
        assertThat(result).doesNotContainKey("compressedMimeType");
        assertThat(result).doesNotContainKey("compressionSkipReason");
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
        assertThat(result).doesNotContainKey("compressionApplied");
    }

    @Test
    void csvFile_doesNotEmitImageFields() throws Exception {
        // CSV-shaped text content — confirms broader text branch coverage and
        // doubles as a guard against accidental cross-branch leakage.
        String csv = "id,name,age\n1,Alice,30\n2,Bob,25\n";
        Path file = tempDir.resolve("data.csv");
        Files.writeString(file, csv);

        UploadedFile uf = new UploadedFile("f-csv", "s1", "data.csv", "text/csv",
                csv.length(), file.toAbsolutePath().toString(), Map.of(), Instant.now());
        when(repo.findById("f-csv")).thenReturn(Optional.of(uf));

        Map result = handler.handle(ctx, Map.of("fileId", "f-csv", "offset", 0, "limit", 4096))
                .toCompletableFuture().join();

        assertThat(result.get("content")).isEqualTo(csv);
        assertThat(result).doesNotContainKey("originalBytes");
        assertThat(result).doesNotContainKey("compressedBytes");
        assertThat(result).doesNotContainKey("compressionApplied");
        assertThat(result).doesNotContainKey("compressedMimeType");
        assertThat(result).doesNotContainKey("compressionSkipReason");
    }

    @Test
    void missingFile_returnsError() {
        when(repo.findById("missing")).thenReturn(Optional.empty());

        Map result = handler.handle(ctx, Map.of("fileId", "missing"))
                .toCompletableFuture().join();

        assertThat(result).containsKey("error");
    }

    // ---------- fixture helpers ----------

    private Path writeBusyPng(int width, int height, String name) throws IOException {
        BufferedImage img = paintBusy(width, height);
        Path out = tempDir.resolve(name);
        ImageIO.write(img, "png", out.toFile());
        return out;
    }

    private Path writeBusyGif(int width, int height, String name) throws IOException {
        BufferedImage img = paintBusy(width, height);
        Path out = tempDir.resolve(name);
        ImageIO.write(img, "gif", out.toFile());
        return out;
    }

    private Path writeSolidPng(int width, int height, String name) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        Path out = tempDir.resolve(name);
        ImageIO.write(img, "png", out.toFile());
        return out;
    }

    /**
     * 64×64 mosaic with a few overlaid text lines. See ImageCompressorTest.paintBusy
     * for the design rationale (small mosaic blocks make JPEG q=0.85 inflate).
     */
    private static BufferedImage paintBusy(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            Random rnd = new Random(width * 31L + height);
            int block = 64;
            for (int y = 0; y < height; y += block) {
                for (int x = 0; x < width; x += block) {
                    g.setColor(new Color(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)));
                    g.fillRect(x, y, block, block);
                }
            }
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            int lines = Math.min(8, height / 40);
            for (int line = 0; line < lines; line++) {
                g.drawString("DataTalk fixture " + width + "x" + height + " line " + line,
                        50, 50 + line * 40);
            }
        } finally {
            g.dispose();
        }
        return img;
    }
}
