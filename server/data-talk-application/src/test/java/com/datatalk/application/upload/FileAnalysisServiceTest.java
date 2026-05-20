package com.datatalk.application.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileAnalysisServiceTest {

    private final FileAnalysisService service = new FileAnalysisService(new ObjectMapper());

    @TempDir
    Path tempDir;

    // ── MIME detection ────────────────────────────────────────────────

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

    // ── extension-less files: content sniffing ────────────────────────

    @Test
    void detectMime_noExtension_sqlContent_returnsSqlMime() throws IOException {
        Path file = writeTemp("create_test", "CREATE TABLE test (id INT);");
        assertThat(service.detectMime(file, "create_test")).isEqualTo("text/x-sql");
    }

    @Test
    void detectMime_noExtension_jsonContent_returnsJsonMime() throws IOException {
        Path file = writeTemp("config", "{\"name\": \"datatalk\", \"version\": 1}");
        assertThat(service.detectMime(file, "config")).isEqualTo("application/json");
    }

    @Test
    void detectMime_noExtension_plainText_returnsTextMime() throws IOException {
        Path file = writeTemp("notes", "just some readable notes\nsecond line");
        assertThat(service.detectMime(file, "notes")).isEqualTo("text/plain");
    }

    @Test
    void detectMime_noExtension_binaryContent_returnsNull() throws IOException {
        Path file = tempDir.resolve("blob");
        Files.write(file, new byte[] {0x00, 0x01, 0x02, (byte) 0xFF, 0x00, 0x10});
        assertThat(service.detectMime(file, "blob")).isNull();
    }

    @Test
    void detectMime_trailingDot_sniffsContent() throws IOException {
        Path file = writeTemp("dump.", "SELECT * FROM users;");
        assertThat(service.detectMime(file, "dump.")).isEqualTo("text/x-sql");
    }

    @Test
    void analyze_noExtension_sqlContent_returnsSqlAnalysis() throws IOException {
        Path file = writeTemp("create_test", "CREATE TABLE test (id INT);");
        FileAnalysisResult result = service.analyze(file, null, "create_test");
        assertThat(result.type()).isEqualTo("SQL");
        assertThat(result.summary()).containsKey("statementTypes");
    }

    private Path writeTemp(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    // ── Image analysis ────────────────────────────────────────────────

    @Test
    void analyzeImage_png_returnsDimensions() throws IOException {
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

    // ── Validation ────────────────────────────────────────────────────

    @Test
    void analyze_emptyFile_throws() {
        Path empty = tempDir.resolve("empty.png");
        assertThatThrownBy(() -> {
            Files.createFile(empty);
            service.analyze(empty, null, "empty.png");
        }).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Empty file");
    }

    // ── CSV analysis (small + streaming) ──────────────────────────────

    @Test
    void analyze_csv_smallFile_returnsFullContent() throws IOException {
        Path csv = tempDir.resolve("data.csv");
        Files.writeString(csv, "name,age\nAlice,30\nBob,25\n");

        FileAnalysisResult result = service.analyze(csv, null, "data.csv");

        assertThat(result.type()).isEqualTo("CSV");
        assertThat(result.fullContent()).isTrue();
        assertThat(result.content()).isNotNull();
        assertThat(result.summary()).containsKey("headers");

        @SuppressWarnings("unchecked")
        List<String> headers = (List<String>) result.summary().get("headers");
        assertThat(headers).containsExactly("name", "age");
    }

    @Test
    void analyze_csv_largeFile_streamingAnalysis() throws IOException {
        Path csv = tempDir.resolve("large.csv");

        // Create a file larger than 4KB
        StringBuilder sb = new StringBuilder("id,value,description\n");
        for (int i = 0; i < 500; i++) {
            sb.append(i).append(",val").append(i).append(",description text for row ").append(i).append("\n");
        }
        Files.writeString(csv, sb.toString());

        FileAnalysisResult result = service.analyze(csv, null, "large.csv");

        assertThat(result.type()).isEqualTo("CSV");
        assertThat(result.fullContent()).isFalse();
        assertThat(result.content()).isNull();

        @SuppressWarnings("unchecked")
        List<String> headers = (List<String>) result.summary().get("headers");
        assertThat(headers).containsExactly("id", "value", "description");

        @SuppressWarnings("unchecked")
        List<?> sampleRows = (List<?>) result.summary().get("sampleRows");
        assertThat(sampleRows).hasSize(5); // CSV_SAMPLE_ROWS

        int estimatedRows = (int) result.summary().get("estimatedRows");
        assertThat(estimatedRows).isGreaterThan(0);
    }

    @Test
    void analyze_csv_withBom() throws IOException {
        Path csv = tempDir.resolve("bom.csv");
        // Write UTF-8 BOM + content
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        bos.write("name,age\nAlice,30\n".getBytes());
        Files.write(csv, bos.toByteArray());

        FileAnalysisResult result = service.analyze(csv, null, "bom.csv");

        assertThat(result.type()).isEqualTo("CSV");
        assertThat(result.summary()).containsEntry("encoding", "UTF-8-BOM");

        @SuppressWarnings("unchecked")
        List<String> headers = (List<String>) result.summary().get("headers");
        assertThat(headers).containsExactly("name", "age");
    }

    @Test
    void analyze_csv_singleRowFile() throws IOException {
        Path csv = tempDir.resolve("header_only.csv");
        Files.writeString(csv, "col1,col2,col3\n");

        FileAnalysisResult result = service.analyze(csv, null, "header_only.csv");

        assertThat(result.type()).isEqualTo("CSV");
        @SuppressWarnings("unchecked")
        List<String> headers = (List<String>) result.summary().get("headers");
        assertThat(headers).containsExactly("col1", "col2", "col3");

        @SuppressWarnings("unchecked")
        List<?> sampleRows = (List<?>) result.summary().get("sampleRows");
        assertThat(sampleRows).isEmpty();
    }

    @Test
    void analyze_csv_typeInference() throws IOException {
        Path csv = tempDir.resolve("typed.csv");
        Files.writeString(csv, "name,age,score,active\nAlice,30,95.5,yes\nBob,25,87.3,no\n");

        FileAnalysisResult result = service.analyze(csv, null, "typed.csv");

        @SuppressWarnings("unchecked")
        Map<String, String> types = (Map<String, String>) result.summary().get("detectedTypes");
        assertThat(types.get("age")).isEqualTo("INTEGER");
        assertThat(types.get("score")).isEqualTo("REAL");
        assertThat(types.get("name")).isEqualTo("TEXT");
        assertThat(types.get("active")).isEqualTo("TEXT");
    }

    // ── JSON analysis (small + streaming) ──────────────────────────────

    @Test
    void analyze_json_smallFile_arrayOfObjects() throws IOException {
        Path json = tempDir.resolve("data.json");
        Files.writeString(json, "[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}]");

        FileAnalysisResult result = service.analyze(json, null, "data.json");

        assertThat(result.type()).isEqualTo("JSON");
        assertThat(result.fullContent()).isTrue();
        assertThat(result.content()).isNotNull();
        assertThat(result.summary()).containsEntry("structure", "array_of_objects");
        assertThat(result.summary()).containsEntry("arrayLength", 2);
    }

    @Test
    void analyze_json_largeFile_arrayOfObjects_streaming() throws IOException {
        Path json = tempDir.resolve("large.json");

        // Create a JSON array of objects larger than 4KB
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < 200; i++) {
            sb.append("  {\"id\":").append(i)
              .append(",\"name\":\"User").append(i)
              .append("\",\"email\":\"user").append(i).append("@test.com\"}");
            if (i < 199) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        Files.writeString(json, sb.toString());

        FileAnalysisResult result = service.analyze(json, null, "large.json");

        assertThat(result.type()).isEqualTo("JSON");
        assertThat(result.fullContent()).isFalse();
        assertThat(result.content()).isNull();
        assertThat(result.summary()).containsEntry("structure", "array_of_objects");

        @SuppressWarnings("unchecked")
        List<String> preview = (List<String>) result.summary().get("preview");
        assertThat(preview).hasSize(2);
        assertThat(preview.get(0)).contains("\"id\":0");

        int arrayLength = (int) result.summary().get("arrayLength");
        assertThat(arrayLength).isGreaterThan(0);
    }

    @Test
    void analyze_json_smallObject() throws IOException {
        Path json = tempDir.resolve("obj.json");
        Files.writeString(json, "{\"key\":\"value\",\"count\":42}");

        FileAnalysisResult result = service.analyze(json, null, "obj.json");

        assertThat(result.type()).isEqualTo("JSON");
        assertThat(result.summary()).containsEntry("structure", "object");
    }

    // ── helpers ────────────────────────────────────────────────────────

    private Path createTestImage(String format, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, format, bos);
        Path file = tempDir.resolve("test." + format);
        Files.write(file, bos.toByteArray());
        return file;
    }
}
