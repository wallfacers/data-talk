package com.datatalk.application.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileAnalysisServiceTest {

    private final FileAnalysisService service = new FileAnalysisService(new ObjectMapper());

    @TempDir
    Path tempDir;

    @Test
    void detectMime_png() {
        assertThat(service.detectMime(Path.of("x"), "photo.png")).isEqualTo("image/png");
    }

    @Test
    void detectMime_jpg() {
        assertThat(service.detectMime(Path.of("x"), "photo.jpg")).isEqualTo("image/jpeg");
    }

    @Test
    void detectMime_jpeg() {
        assertThat(service.detectMime(Path.of("x"), "photo.jpeg")).isEqualTo("image/jpeg");
    }

    @Test
    void detectMime_gif() {
        assertThat(service.detectMime(Path.of("x"), "anim.gif")).isEqualTo("image/gif");
    }

    @Test
    void detectMime_webp() {
        assertThat(service.detectMime(Path.of("x"), "img.webp")).isEqualTo("image/webp");
    }

    @Test
    void detectMime_bmp() {
        assertThat(service.detectMime(Path.of("x"), "img.bmp")).isEqualTo("image/bmp");
    }

    @Test
    void detectMime_svg_returnsNull() {
        assertThat(service.detectMime(Path.of("x"), "logo.svg")).isNull();
    }

    @Test
    void analyzeImage_png_returnsDimensions() throws IOException {
        // Create a small 10x5 PNG
        Path file = createTestImage("png", 10, 5);
        FileAnalysisResult result = service.analyze(file, null, "test.png");

        assertThat(result.type()).isEqualTo("IMAGE");
        assertThat(result.fullContent()).isFalse();
        assertThat(result.content()).isNull();
        assertThat(result.summary()).containsEntry("width", 10);
        assertThat(result.summary()).containsEntry("height", 5);
        assertThat(result.summary()).containsEntry("format", "png");
        assertThat(result.summary()).containsEntry("sizeBytes", Files.size(file));
    }

    @Test
    void analyzeImage_jpeg_returnsDimensions() throws IOException {
        Path file = createTestImage("jpg", 20, 30);
        FileAnalysisResult result = service.analyze(file, null, "photo.jpg");

        assertThat(result.type()).isEqualTo("IMAGE");
        assertThat(result.summary()).containsEntry("width", 20);
        assertThat(result.summary()).containsEntry("height", 30);
        assertThat(result.summary()).containsEntry("format", "jpeg");
    }

    @Test
    void analyzeImage_bmp_returnsDimensions() throws IOException {
        Path file = createTestImage("bmp", 8, 8);
        FileAnalysisResult result = service.analyze(file, null, "img.bmp");

        assertThat(result.type()).isEqualTo("IMAGE");
        assertThat(result.summary()).containsEntry("width", 8);
        assertThat(result.summary()).containsEntry("height", 8);
        assertThat(result.summary()).containsEntry("format", "bmp");
    }

    @Test
    void analyze_emptyFile_throws() {
        Path empty = tempDir.resolve("empty.png");
        assertThatThrownBy(() -> {
            Files.createFile(empty);
            service.analyze(empty, null, "empty.png");
        }).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Empty file");
    }

    @Test
    void analyze_csv_stillWorks() throws IOException {
        Path csv = tempDir.resolve("data.csv");
        Files.writeString(csv, "name,age\nAlice,30\nBob,25\n");
        FileAnalysisResult result = service.analyze(csv, null, "data.csv");

        assertThat(result.type()).isEqualTo("CSV");
        assertThat(result.summary()).containsKey("headers");
    }

    private Path createTestImage(String format, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, format, bos);
        Path file = tempDir.resolve("test." + format);
        Files.write(file, bos.toByteArray());
        return file;
    }
}
