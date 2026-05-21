package com.datatalk.application.fileartifact;

import com.datatalk.application.upload.UploadedFileRepository;
import com.datatalk.domain.upload.UploadedFile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Provides content preview for resources in the 5 resource directories:
 * dashboards, reports, exports, semantic models, and uploads.
 *
 * <p>All file-system operations go through {@link SessionWorkdirRoot} so the
 * preview paths stay consistent with the configured {@code datatalk.workdir.data-talk-root}.
 */
@Service
public class ResourcePreviewService {

    private static final Logger log = LoggerFactory.getLogger(ResourcePreviewService.class);
    private static final int MAX_PREVIEW_ROWS = 100;
    private static final long MAX_TEXT_CONTENT_BYTES = 1_048_576L; // 1 MB
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() {};

    private final SessionWorkdirRoot workdirRoot;
    private final UploadedFileRepository uploadedFileRepo;
    private final ObjectMapper objectMapper;

    public ResourcePreviewService(SessionWorkdirRoot workdirRoot,
                                  UploadedFileRepository uploadedFileRepo,
                                  ObjectMapper objectMapper) {
        this.workdirRoot = workdirRoot;
        this.uploadedFileRepo = uploadedFileRepo;
        this.objectMapper = objectMapper;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Dashboard
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Reads the dashboard HTML file from {@code dashboards/<id>.html} and
     * returns its full content.
     *
     * @param id the dashboard identifier
     * @return the HTML content
     * @throws IllegalArgumentException if the dashboard file does not exist
     */
    public String previewDashboard(String id) {
        Path file = workdirRoot.dashboardsRoot().resolve(id + ".html");
        return readTextFile(file, "dashboard", id);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Report
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Reads the HTML report from {@code reports/<reportId>/report.html} and
     * returns its full content.
     *
     * @param reportId the report identifier
     * @return the HTML content
     * @throws IllegalArgumentException if the report file does not exist
     */
    public String previewReport(String reportId) {
        Path file = workdirRoot.reportDir(reportId).resolve("report.html");
        return readTextFile(file, "report", reportId);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Export
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Previews a data export file. Detects the format from the file extension
     * and returns a structured preview with columns and rows.
     *
     * @param exportId the export identifier (subdirectory under {@code exports/})
     * @return a structured preview of the exported data
     * @throws IllegalArgumentException if the export directory or file is not found
     */
    public ExportPreview previewExport(String exportId) {
        Path exportDir = workdirRoot.dataTalkRoot().resolve("exports").resolve(exportId);
        Path file = findFirstFile(exportDir);
        if (file == null) {
            throw new IllegalArgumentException("Export not found: " + exportId);
        }
        String format = detectFormat(file.getFileName().toString());
        return switch (format) {
            case "csv" -> previewCsv(file, format);
            case "json" -> previewJson(file, format);
            case "xlsx" -> previewXlsx(file, format);
            case "sql" -> previewSql(file, format);
            default -> throw new IllegalArgumentException("Unsupported export format: " + format);
        };
    }

    // ══════════════════════════════════════════════════════════════════════
    // Semantic Model
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Reads a semantic model YAML file from
     * {@code semantic/<connectionId>/<domain>.model.yaml} and returns the
     * raw YAML content.
     *
     * @param domain       the semantic domain name
     * @param connectionId the connection identifier
     * @return the YAML content as a string
     * @throws IllegalArgumentException if the model file does not exist
     */
    public String previewSemantic(String domain, String connectionId) {
        Path file = workdirRoot.dataTalkRoot()
                .resolve("semantic")
                .resolve(connectionId)
                .resolve(domain + ".model.yaml");
        return readTextFile(file, "semantic model", domain);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Upload
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Previews an uploaded file. Behaviour varies by MIME type:
     * <ul>
     *   <li>{@code text/*} and {@code application/json}: returns the file
     *       content as a string, limited to 1 MB</li>
     *   <li>{@code image/*}: returns the physical file path so the controller
     *       can serve the binary</li>
     *   <li>Everything else: returns metadata only</li>
     * </ul>
     *
     * @param fileId the uploaded file identifier (PK in {@code uploaded_file})
     * @return a structured preview result
     * @throws IllegalArgumentException if the upload record is not found
     */
    public UploadPreview previewUpload(String fileId) {
        UploadedFile uploaded = uploadedFileRepo.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Uploaded file not found: " + fileId));

        String mimeType = uploaded.mimeType() != null ? uploaded.mimeType() : "application/octet-stream";
        Path physicalPath = Path.of(uploaded.physicalPath());

        if (!Files.isRegularFile(physicalPath)) {
            return new UploadPreview(mimeType, uploaded.sizeBytes(), false, null,
                    "File not found on disk");
        }

        if (isTextMime(mimeType)) {
            String content = readTextFileLimited(physicalPath, MAX_TEXT_CONTENT_BYTES);
            boolean truncated = uploaded.sizeBytes() > MAX_TEXT_CONTENT_BYTES;
            return new UploadPreview(mimeType, uploaded.sizeBytes(), true, content,
                    truncated ? "Content truncated to 1 MB" : null);
        }

        if (isImageMime(mimeType)) {
            return new UploadPreview(mimeType, uploaded.sizeBytes(), true, null,
                    physicalPath.toString());
        }

        return new UploadPreview(mimeType, uploaded.sizeBytes(), false, null,
                "Preview not available for " + mimeType);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Preview result types
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Structured preview of a tabular data export.
     *
     * @param format      the detected format (csv, json, xlsx, sql)
     * @param columns     column names extracted from the header or the first JSON object
     * @param rows        preview rows (up to {@value #MAX_PREVIEW_ROWS})
     * @param totalRows   total number of data rows in the file
     * @param previewRows actual number of preview rows returned
     */
    public record ExportPreview(String format, List<String> columns, List<List<Object>> rows,
                                long totalRows, int previewRows) {}

    /**
     * Preview result for an uploaded file.
     *
     * @param mimeType    the declared MIME type
     * @param sizeBytes   the original file size in bytes
     * @param previewable whether a preview could be produced
     * @param content     the text content (for text/* or application/json), may be truncated to 1 MB
     * @param message     additional context — file path for images, truncation notice, or error reason
     */
    public record UploadPreview(String mimeType, long sizeBytes, boolean previewable,
                                String content, String message) {}

    // ══════════════════════════════════════════════════════════════════════
    // File I/O helpers
    // ══════════════════════════════════════════════════════════════════════

    private String readTextFile(Path file, String resourceType, String id) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException(
                    "Resource not found: %s '%s' at %s".formatted(resourceType, id, file));
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read %s '%s'".formatted(resourceType, id), e);
        }
    }

    /**
     * Reads up to {@code maxBytes} from the file. When the file exceeds the
     * limit only the first {@code maxBytes} bytes are returned as a string.
     */
    private String readTextFileLimited(Path file, long maxBytes) {
        try {
            long size = Files.size(file);
            if (size <= maxBytes) {
                return Files.readString(file, StandardCharsets.UTF_8);
            }
            byte[] buffer = new byte[(int) maxBytes];
            int totalRead = 0;
            try (InputStream in = Files.newInputStream(file)) {
                while (totalRead < maxBytes) {
                    int n = in.read(buffer, totalRead, (int) (maxBytes - totalRead));
                    if (n < 0) break;
                    totalRead += n;
                }
            }
            return new String(buffer, 0, totalRead, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file: " + file, e);
        }
    }

    /** Returns the first regular file found in the directory, or {@code null}. */
    private Path findFirstFile(Path dir) {
        if (!Files.isDirectory(dir)) return null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) return entry;
            }
        } catch (IOException e) {
            log.warn("Failed to list directory: {}", dir, e);
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Format & MIME detection
    // ══════════════════════════════════════════════════════════════════════

    private String detectFormat(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) return "csv";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".xlsx")) return "xlsx";
        if (lower.endsWith(".sql")) return "sql";
        int dot = lower.lastIndexOf('.');
        return dot >= 0 ? lower.substring(dot + 1) : "unknown";
    }

    private static boolean isTextMime(String mimeType) {
        return mimeType.startsWith("text/") || "application/json".equals(mimeType);
    }

    private static boolean isImageMime(String mimeType) {
        return mimeType.startsWith("image/");
    }

    // ══════════════════════════════════════════════════════════════════════
    // CSV preview
    // ══════════════════════════════════════════════════════════════════════

    private ExportPreview previewCsv(Path file, String format) {
        List<String> columns = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            // Skip UTF-8 BOM if present
            reader.mark(1);
            int firstChar = reader.read();
            if (firstChar != 0xFEFF) {
                reader.reset();
            }

            // Header line
            String headerLine = reader.readLine();
            if (headerLine != null && !headerLine.isBlank()) {
                columns.addAll(parseCsvLine(headerLine));
            }

            // Data rows (limited to MAX_PREVIEW_ROWS)
            String line;
            while ((line = reader.readLine()) != null && rows.size() < MAX_PREVIEW_ROWS) {
                if (line.isBlank()) continue;
                List<String> fields = parseCsvLine(line);
                rows.add(new ArrayList<>(fields));
            }

            // Count remaining rows for the total
            long remainingRows = 0;
            while (reader.readLine() != null) {
                remainingRows++;
            }

            long totalRows = rows.size() + remainingRows;
            return new ExportPreview(format, columns, rows, totalRows, rows.size());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read CSV export: " + file, e);
        }
    }

    /**
     * Parses a single CSV line into fields. Supports double-quoted fields
     * with embedded commas, newlines, and escaped double-quotes ("").
     */
    static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        int len = line.length();

        for (int i = 0; i < len; i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++; // skip escaped quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields;
    }

    // ══════════════════════════════════════════════════════════════════════
    // JSON preview
    // ══════════════════════════════════════════════════════════════════════

    private ExportPreview previewJson(Path file, String format) {
        try {
            List<Map<String, Object>> allObjects = objectMapper.readValue(
                    file.toFile(), MAP_LIST_TYPE);

            if (allObjects.isEmpty()) {
                return new ExportPreview(format, List.of(), List.of(), 0, 0);
            }

            // Extract column names from the first object, preserving key order
            // (LinkedHashMap preserves insertion order via Jackson default)
            List<String> columns = new ArrayList<>(allObjects.getFirst().keySet());

            // Take preview rows
            int previewCount = Math.min(allObjects.size(), MAX_PREVIEW_ROWS);
            List<List<Object>> rows = new ArrayList<>(previewCount);
            for (int i = 0; i < previewCount; i++) {
                Map<String, Object> obj = allObjects.get(i);
                List<Object> row = new ArrayList<>(columns.size());
                for (String col : columns) {
                    row.add(obj.getOrDefault(col, null));
                }
                rows.add(row);
            }

            return new ExportPreview(format, columns, rows, allObjects.size(), rows.size());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read JSON export: " + file, e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // XLSX preview
    // ══════════════════════════════════════════════════════════════════════

    private ExportPreview previewXlsx(Path file, String format) {
        List<String> columns = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();

        try (InputStream in = Files.newInputStream(file);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return new ExportPreview(format, List.of(), List.of(), 0, 0);
            }

            DataFormatter dataFormatter = new DataFormatter();

            // Header row (row index 0)
            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    Cell cell = headerRow.getCell(i);
                    columns.add(cell != null ? dataFormatter.formatCellValue(cell) : "");
                }
            }

            // Data rows
            int lastRowNum = sheet.getLastRowNum();
            for (int r = 1; r <= lastRowNum && rows.size() < MAX_PREVIEW_ROWS; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                List<Object> rowData = new ArrayList<>(columns.size());
                for (int c = 0; c < columns.size(); c++) {
                    Cell cell = row.getCell(c);
                    if (cell == null) {
                        rowData.add(null);
                    } else {
                        rowData.add(dataFormatter.formatCellValue(cell));
                    }
                }
                rows.add(rowData);
            }

            long totalRows = Math.max(0, lastRowNum); // row 0 is the header
            return new ExportPreview(format, columns, rows, totalRows, rows.size());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read XLSX export: " + file, e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SQL preview
    // ══════════════════════════════════════════════════════════════════════

    private ExportPreview previewSql(Path file, String format) {
        List<List<Object>> rows = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null && rows.size() < MAX_PREVIEW_ROWS) {
                rows.add(List.of(line));
            }

            // Count total lines
            long totalLines = rows.size();
            while (reader.readLine() != null) {
                totalLines++;
            }

            return new ExportPreview(format, List.of("line"), rows, totalLines, rows.size());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read SQL export: " + file, e);
        }
    }
}
