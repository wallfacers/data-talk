package com.datatalk.application.upload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes uploaded files locally (no AI) and returns structured metadata.
 * <p>
 * Supports SQL, CSV, Excel, JSON, plain-text, and image files (PNG, JPEG, GIF, WebP, BMP).
 * Files smaller than 4 KB include their full content in the result;
 * larger files only return a type-specific summary.
 */
@Service
public class FileAnalysisService {

    private static final long FULL_CONTENT_THRESHOLD = 4096;
    private static final Set<String> SQL_TYPES = Set.of("SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "ALTER", "DROP");
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile(
            "(?:FROM|INTO|TABLE|JOIN)\\s+([\\w.]+)", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    public FileAnalysisService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── public API ──────────────────────────────────────────────────────

    /**
     * Detect MIME type from the file extension, then analyze the file contents.
     *
     * @param file              path to the uploaded file on disk
     * @param detectedMimeType  MIME type detected by the caller (may be null or unreliable)
     * @param originalFilename  the original filename as uploaded by the client
     * @return structured analysis result
     * @throws IllegalArgumentException if the file is empty or has an unsupported type
     */
    public FileAnalysisResult analyze(Path file, String detectedMimeType, String originalFilename) {
        validateFile(file);

        String mime = detectMime(file, originalFilename);
        if (mime == null) {
            throw new IllegalArgumentException("Unsupported file type");
        }

        try {
            return switch (mime) {
                case "text/x-sql" -> analyzeSql(file);
                case "text/csv" -> analyzeCsv(file);
                case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                     "application/vnd.ms-excel" -> analyzeExcel(file);
                case "application/json", "application/jsonl" -> analyzeJson(file);
                case "text/plain", "text/markdown" -> analyzeText(file);
                case "image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp" -> analyzeImage(file, mime);
                default -> analyzeUnknown(file);
            };
        } catch (IOException e) {
            return analyzeUnknown(file);
        }
    }

    /**
     * Detect MIME type from the file extension.
     *
     * @return MIME type string, or null if the extension is not in the allowed list
     */
    public String detectMime(Path file, String originalFilename) {
        String name = originalFilename != null ? originalFilename : file.getFileName().toString();
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx < 0 || dotIdx == name.length() - 1) {
            return null;
        }
        String ext = name.substring(dotIdx + 1).toLowerCase();
        return switch (ext) {
            case "sql" -> "text/x-sql";
            case "csv" -> "text/csv";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls" -> "application/vnd.ms-excel";
            case "json" -> "application/json";
            case "jsonl" -> "application/jsonl";
            case "txt" -> "text/plain";
            case "md" -> "text/markdown";
            case "log" -> "text/plain";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> null;
        };
    }

    // ── validation ──────────────────────────────────────────────────────

    private void validateFile(Path file) {
        try {
            if (Files.size(file) == 0) {
                throw new IllegalArgumentException("Empty file");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read file: " + e.getMessage(), e);
        }
    }

    // ── content helper ──────────────────────────────────────────────────

    private ContentDecision decideContent(Path file) throws IOException {
        long size = Files.size(file);
        if (size < FULL_CONTENT_THRESHOLD) {
            return new ContentDecision(true, Files.readString(file));
        }
        return new ContentDecision(false, null);
    }

    private record ContentDecision(boolean fullContent, String content) {}

    // ── SQL analysis ────────────────────────────────────────────────────

    private FileAnalysisResult analyzeSql(Path file) throws IOException {
        ContentDecision cd = decideContent(file);
        String raw = cd.fullContent ? cd.content : Files.readString(file);

        // Split by semicolons, filter blank
        String[] parts = raw.split(";");
        List<String> statements = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                statements.add(trimmed);
            }
        }

        // Classify each statement
        Map<String, Integer> statementTypes = new LinkedHashMap<>();
        Set<String> targetTables = new LinkedHashSet<>();
        for (String stmt : statements) {
            String type = classifySqlStatement(stmt);
            statementTypes.merge(type, 1, Integer::sum);
            extractTableNames(stmt, targetTables);
        }

        // Risk level
        String riskLevel = "L1";
        if (hasDdl(statementTypes)) {
            riskLevel = "L3";
        } else if (hasDml(statementTypes)) {
            riskLevel = "L2";
        }

        // Preview: first 10 statements
        List<String> preview = statements.stream().limit(10).toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("statementCount", statements.size());
        summary.put("statementTypes", statementTypes);
        summary.put("targetTables", targetTables);
        summary.put("riskLevel", riskLevel);
        summary.put("preview", preview);

        return new FileAnalysisResult("SQL", cd.fullContent, cd.content, summary);
    }

    private String classifySqlStatement(String stmt) {
        String upper = stmt.toUpperCase().stripLeading();
        for (String type : SQL_TYPES) {
            if (upper.startsWith(type)) {
                return type;
            }
        }
        return "OTHER";
    }

    private void extractTableNames(String stmt, Set<String> tables) {
        Matcher m = TABLE_NAME_PATTERN.matcher(stmt);
        while (m.find()) {
            tables.add(m.group(1));
        }
    }

    private boolean hasDdl(Map<String, Integer> types) {
        return types.containsKey("CREATE") || types.containsKey("ALTER") || types.containsKey("DROP");
    }

    private boolean hasDml(Map<String, Integer> types) {
        return types.containsKey("INSERT") || types.containsKey("UPDATE") || types.containsKey("DELETE");
    }

    // ── CSV analysis ────────────────────────────────────────────────────

    private FileAnalysisResult analyzeCsv(Path file) throws IOException {
        ContentDecision cd = decideContent(file);

        byte[] bytes = Files.readAllBytes(file);
        String content = stripBom(bytes);

        List<String> lines = content.lines().toList();
        if (lines.isEmpty()) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("headers", List.of());
            summary.put("estimatedRows", 0);
            summary.put("sampleRows", List.of());
            summary.put("detectedTypes", Map.of());
            summary.put("encoding", detectEncoding(bytes));
            return new FileAnalysisResult("CSV", cd.fullContent, cd.content, summary);
        }

        List<String> headers = parseCsvLine(lines.get(0));
        List<List<String>> sampleRows = new ArrayList<>();
        long sampleBytes = 0;
        for (int i = 1; i < Math.min(6, lines.size()); i++) {
            List<String> row = parseCsvLine(lines.get(i));
            sampleRows.add(row);
            sampleBytes += lines.get(i).getBytes().length;
        }

        // Estimate rows
        int estimatedRows;
        if (!sampleRows.isEmpty() && sampleBytes > 0) {
            long avgBytesPerRow = sampleBytes / sampleRows.size();
            if (avgBytesPerRow > 0) {
                estimatedRows = (int) (Files.size(file) / avgBytesPerRow);
            } else {
                estimatedRows = lines.size() - 1;
            }
        } else {
            estimatedRows = lines.size() - 1;
        }

        // Infer column types from sample rows
        Map<String, String> detectedTypes = inferCsvTypes(headers, sampleRows);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("headers", headers);
        summary.put("estimatedRows", Math.max(0, estimatedRows));
        summary.put("sampleRows", sampleRows);
        summary.put("detectedTypes", detectedTypes);
        summary.put("encoding", detectEncoding(bytes));

        return new FileAnalysisResult("CSV", cd.fullContent, cd.content, summary);
    }

    private String stripBom(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3);
        }
        return new String(bytes);
    }

    private String detectEncoding(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return "UTF-8-BOM";
        }
        return "UTF-8";
    }

    /**
     * Simple CSV line parser that handles basic double-quote quoting.
     */
    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
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
                    fields.add(current.toString().trim());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString().trim());
        return fields;
    }

    private Map<String, String> inferCsvTypes(List<String> headers, List<List<String>> sampleRows) {
        Map<String, String> types = new LinkedHashMap<>();
        for (int col = 0; col < headers.size(); col++) {
            boolean allInteger = true;
            boolean allReal = true;
            for (List<String> row : sampleRows) {
                if (col >= row.size()) continue;
                String val = row.get(col).trim();
                if (val.isEmpty()) continue;
                try {
                    Long.parseLong(val);
                } catch (NumberFormatException e) {
                    allInteger = false;
                }
                try {
                    Double.parseDouble(val);
                } catch (NumberFormatException e) {
                    allReal = false;
                }
            }
            if (allInteger) {
                types.put(headers.get(col), "INTEGER");
            } else if (allReal) {
                types.put(headers.get(col), "REAL");
            } else {
                types.put(headers.get(col), "TEXT");
            }
        }
        return types;
    }

    // ── Excel analysis ──────────────────────────────────────────────────

    private FileAnalysisResult analyzeExcel(Path file) throws IOException {
        try (Workbook wb = WorkbookFactory.create(file.toFile())) {
            List<Map<String, Object>> sheetsInfo = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                Map<String, Object> sheetInfo = new LinkedHashMap<>();
                sheetInfo.put("name", sheet.getSheetName());

                // First row as headers
                Row firstRow = sheet.getRow(0);
                List<String> headers = new ArrayList<>();
                if (firstRow != null) {
                    for (int c = 0; c < firstRow.getLastCellNum(); c++) {
                        Cell cell = firstRow.getCell(c);
                        headers.add(cellToString(cell));
                    }
                }
                sheetInfo.put("headers", headers);
                sheetInfo.put("estimatedRows", Math.max(0, sheet.getLastRowNum()));
                sheetsInfo.add(sheetInfo);
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("sheets", sheetsInfo);

            return new FileAnalysisResult("EXCEL", false, null, summary);
        } catch (IOException e) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("parseError", true);
            return new FileAnalysisResult("UNKNOWN", false, null, summary);
        }
    }

    private String cellToString(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue();
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf(cell.getNumericCellValue());
        if (cell.getCellType() == CellType.BOOLEAN) return String.valueOf(cell.getBooleanCellValue());
        if (cell.getCellType() == CellType.FORMULA) return cell.getCellFormula();
        return "";
    }

    // ── JSON analysis ───────────────────────────────────────────────────

    private FileAnalysisResult analyzeJson(Path file) throws IOException {
        ContentDecision cd = decideContent(file);
        String raw = cd.fullContent ? cd.content : Files.readString(file);

        Map<String, Object> summary = new LinkedHashMap<>();
        try {
            JsonNode root = objectMapper.readTree(raw);

            if (root.isArray()) {
                if (!root.isEmpty() && root.get(0).isObject()) {
                    // Array of objects
                    summary.put("structure", "array_of_objects");
                    Set<String> keys = new LinkedHashSet<>();
                    root.get(0).fieldNames().forEachRemaining(keys::add);
                    summary.put("keys", keys);
                    summary.put("arrayLength", root.size());

                    // Preview: first 2 elements
                    List<String> preview = new ArrayList<>();
                    for (int i = 0; i < Math.min(2, root.size()); i++) {
                        preview.add(objectMapper.writeValueAsString(root.get(i)));
                    }
                    summary.put("preview", preview);
                } else {
                    // Plain array
                    summary.put("structure", "array");
                    summary.put("arrayLength", root.size());

                    List<String> preview = new ArrayList<>();
                    for (int i = 0; i < Math.min(2, root.size()); i++) {
                        preview.add(objectMapper.writeValueAsString(root.get(i)));
                    }
                    summary.put("preview", preview);
                }
                summary.put("nestingDepth", maxDepth(root, 0));
            } else if (root.isObject()) {
                Set<String> keys = new LinkedHashSet<>();
                root.fieldNames().forEachRemaining(keys::add);
                summary.put("structure", "object");
                summary.put("keys", keys);
                summary.put("nestingDepth", maxDepth(root, 0));
            } else {
                summary.put("structure", "scalar");
            }
        } catch (JsonProcessingException e) {
            summary.put("structure", "unparseable");
            summary.put("parseError", true);
        }

        return new FileAnalysisResult("JSON", cd.fullContent, cd.content, summary);
    }

    private int maxDepth(JsonNode node, int current) {
        if (!node.isObject() && !node.isArray()) return current;
        int maxChild = current + 1;
        for (JsonNode child : node) {
            int childDepth = maxDepth(child, current + 1);
            if (childDepth > maxChild) maxChild = childDepth;
        }
        return maxChild;
    }

    // ── Text analysis ───────────────────────────────────────────────────

    private FileAnalysisResult analyzeText(Path file) throws IOException {
        ContentDecision cd = decideContent(file);

        List<String> allLines;
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            allLines = reader.lines().toList();
        }

        List<String> preview = allLines.stream().limit(20).toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("lineCount", allLines.size());
        summary.put("preview", preview);

        return new FileAnalysisResult("TEXT", cd.fullContent, cd.content, summary);
    }

    // ── Image analysis ──────────────────────────────────────────────────

    private FileAnalysisResult analyzeImage(Path file, String mime) throws IOException {
        long sizeBytes = Files.size(file);

        String format = mime.substring(mime.indexOf('/') + 1); // e.g. "png", "jpeg", "gif", "webp", "bmp"

        int width = 0;
        int height = 0;

        // Try ImageIO first — works for PNG, JPEG, GIF, BMP
        byte[] bytes = Files.readAllBytes(file);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image != null) {
            width = image.getWidth();
            height = image.getHeight();
        } else {
            // Fallback: use ImageReader API which handles more formats (including WebP if a reader is registered)
            try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(iis);
                        width = reader.getWidth(0);
                        height = reader.getHeight(0);
                    } finally {
                        reader.dispose();
                    }
                }
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("width", width);
        summary.put("height", height);
        summary.put("format", format);
        summary.put("sizeBytes", sizeBytes);

        return new FileAnalysisResult("IMAGE", false, null, summary);
    }

    // ── Unknown fallback ────────────────────────────────────────────────

    private FileAnalysisResult analyzeUnknown(Path file) {
        Map<String, Object> summary = new LinkedHashMap<>();
        try {
            List<String> lines;
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                lines = reader.lines().limit(20).toList();
            }
            summary.put("preview", lines);
            return new FileAnalysisResult("UNKNOWN", false, null, summary);
        } catch (IOException e) {
            summary.put("preview", List.of());
            return new FileAnalysisResult("UNKNOWN", false, null, summary);
        }
    }
}
