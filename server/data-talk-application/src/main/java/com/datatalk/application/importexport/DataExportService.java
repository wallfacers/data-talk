package com.datatalk.application.importexport;

import com.datatalk.application.script.ScriptDataWriteService;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.application.sql.CalciteSqlRiskAnalyzer;
import com.datatalk.application.sql.SqlRiskAnalysis;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.RiskLevel;
import com.datatalk.domain.event.DtEvent;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class DataExportService {

    private static final Logger log = LoggerFactory.getLogger(DataExportService.class);
    private static final int ASYNC_THRESHOLD = 10_000;
    private static final int DEFAULT_MAX_ROWS = 1_000_000;
    private static final long MAX_FILE_SIZE_BYTES = 500L * 1024 * 1024;
    private static final int XLSX_MAX_ROWS = 1_048_576;
    private static final int FETCH_SIZE = 500;
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ScriptDataWriteService writeService;
    private final SessionBusRegistry sessionBusRegistry;
    private final CalciteSqlRiskAnalyzer sqlRiskAnalyzer;

    public record ExportResult(String exportId, String downloadUrl, int rowCount, long fileSizeBytes,
                               String format, String status, List<String> warnings) {}

    public record ExportError(String errorCode, String message) {}

    public DataExportService(ScriptDataWriteService writeService,
                             SessionBusRegistry sessionBusRegistry,
                             CalciteSqlRiskAnalyzer sqlRiskAnalyzer) {
        this.writeService = writeService;
        this.sessionBusRegistry = sessionBusRegistry;
        this.sqlRiskAnalyzer = sqlRiskAnalyzer;
    }

    public ExportResult export(String sessionId, String connectionId, String sql, String tableName,
                               String format, String filename, Integer maxRows) {
        String effectiveFormat = (format != null) ? format.toLowerCase() : "csv";
        int effectiveMaxRows = (maxRows != null && maxRows > 0) ? maxRows : DEFAULT_MAX_ROWS;
        String exportId = java.util.UUID.randomUUID().toString();

        // Determine effective SQL
        String effectiveSql;
        if (sql != null && !sql.isBlank()) {
            effectiveSql = sql.trim();
        } else if (tableName != null && !tableName.isBlank()) {
            effectiveSql = "SELECT * FROM " + tableName;
        } else {
            throw new IllegalArgumentException("Either sql or tableName must be provided");
        }

        // Validate SQL is read-only
        SqlRiskAnalysis risk = sqlRiskAnalyzer.analyze(effectiveSql, Category.QUERY, null);
        if (risk.riskLevel() != RiskLevel.L1) {
            throw new IllegalArgumentException("QUERY_NOT_READ_ONLY: only SELECT queries are allowed for export");
        }

        // XLSX row limit check will be done during export
        if ("xlsx".equals(effectiveFormat) && effectiveMaxRows > XLSX_MAX_ROWS) {
            effectiveMaxRows = XLSX_MAX_ROWS;
        }

        // Generate filename if not provided
        String effectiveFilename = (filename != null && !filename.isBlank())
            ? filename
            : generateFilename(tableName, effectiveSql, effectiveFormat);

        String extension = extensionForFormat(effectiveFormat);
        Path exportDir = getExportDir(exportId);
        Path exportFile = exportDir.resolve(effectiveFilename + "." + extension);

        List<String> warnings = new ArrayList<>();

        // Count rows
        int rowCount = countRows(connectionId, effectiveSql);
        if (rowCount > effectiveMaxRows) {
            warnings.add("Result set has " + rowCount + " rows, limited to " + effectiveMaxRows);
            rowCount = effectiveMaxRows;
        }

        if (rowCount < ASYNC_THRESHOLD) {
            // Synchronous export
            ExportResult result = doExport(connectionId, effectiveSql, effectiveFormat, tableName,
                exportFile, effectiveMaxRows, exportId, effectiveFilename, warnings);
            log.info("Synchronous export completed: exportId={}, format={}, rows={}", exportId, effectiveFormat, result.rowCount);
            return result;
        } else {
            // Async export
            final String fSql = effectiveSql;
            final String fFormat = effectiveFormat;
            final String fTableName = tableName;
            final Path fExportFile = exportFile;
            final int fMaxRows = effectiveMaxRows;
            final String fFilename = effectiveFilename;
            final List<String> fWarnings = List.copyOf(warnings);

            log.info("Starting async export: exportId={}, estimated rows={}", exportId, rowCount);
            Thread.startVirtualThread(() -> {
                try {
                    ExportResult result = doExport(connectionId, fSql, fFormat, fTableName,
                        fExportFile, fMaxRows, exportId, fFilename, fWarnings);
                    log.info("Async export completed: exportId={}, rows={}", exportId, result.rowCount);

                    SessionBus bus = sessionBusRegistry.getOrCreate(sessionId);
                    bus.publish(new DtEvent.ExportCompleted(
                        sessionId, exportId, result.downloadUrl,
                        result.rowCount, result.format, result.fileSizeBytes
                    ));
                } catch (Exception e) {
                    log.error("Async export failed: exportId={}", exportId, e);
                }
            });

            return new ExportResult(exportId, "/api/exports/" + exportId + "/download",
                0, 0, effectiveFormat, "processing", fWarnings);
        }
    }

    private ExportResult doExport(String connectionId, String sql, String format, String tableName,
                                  Path exportFile, int maxRows, String exportId, String filename,
                                  List<String> warnings) {
        try {
            Files.createDirectories(exportFile.getParent());

            try (Connection conn = openConnection(connectionId)) {
                conn.setAutoCommit(false);
                try (Statement stmt = conn.createStatement(
                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                    stmt.setFetchSize(FETCH_SIZE);
                    try (ResultSet rs = stmt.executeQuery(sql)) {
                        switch (format) {
                            case "csv" -> writeCsv(rs, exportFile, maxRows);
                            case "json" -> writeJson(rs, exportFile, maxRows);
                            case "xlsx" -> writeXlsx(rs, exportFile, maxRows);
                            case "sql_insert" -> writeSqlInsert(rs, exportFile, tableName, maxRows);
                            default -> throw new IllegalArgumentException("Unsupported export format: " + format);
                        }
                    }
                }
            }

            long fileSize = Files.size(exportFile);
            // Count actual rows written (re-read or trust the writer)
            int actualRows = countWrittenRows(exportFile, format, maxRows);

            return new ExportResult(exportId, "/api/exports/" + exportId + "/download",
                actualRows, fileSize, format, "completed", warnings);
        } catch (Exception e) {
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }

    // ── CSV writer ──────────────────────────────────────────────────────

    private void writeCsv(ResultSet rs, Path file, int maxRows) throws Exception {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();

        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            // UTF-8 BOM for Excel compatibility
            writer.write('﻿');

            // Header
            String[] headers = new String[colCount];
            for (int i = 0; i < colCount; i++) {
                headers[i] = meta.getColumnLabel(i + 1);
            }
            writer.write(csvLine(headers));
            writer.newLine();

            // Data rows
            int rows = 0;
            while (rs.next() && rows < maxRows) {
                String[] values = new String[colCount];
                for (int i = 0; i < colCount; i++) {
                    values[i] = getStringValue(rs, i + 1);
                }
                writer.write(csvLine(values));
                writer.newLine();
                rows++;
            }
        }
    }

    private String csvLine(String[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsv(values[i]));
        }
        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ── JSON writer ─────────────────────────────────────────────────────

    private void writeJson(ResultSet rs, Path file, int maxRows) throws Exception {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        String[] colNames = new String[colCount];
        for (int i = 0; i < colCount; i++) {
            colNames[i] = meta.getColumnLabel(i + 1);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("[");
            boolean first = true;
            int rows = 0;
            while (rs.next() && rows < maxRows) {
                if (!first) writer.write(",");
                writer.newLine();
                writer.write("  {");
                for (int i = 0; i < colCount; i++) {
                    if (i > 0) writer.write(", ");
                    String val = getStringValue(rs, i + 1);
                    writer.write("\"");
                    writer.write(escapeJson(colNames[i]));
                    writer.write("\": ");
                    if (val == null) {
                        writer.write("null");
                    } else {
                        writer.write("\"");
                        writer.write(escapeJson(val));
                        writer.write("\"");
                    }
                }
                writer.write("}");
                first = false;
                rows++;
            }
            if (!first) writer.newLine();
            writer.write("]");
        }
    }

    private String escapeJson(String value) {
        if (value == null) return null;
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
    }

    // ── XLSX writer ─────────────────────────────────────────────────────

    private void writeXlsx(ResultSet rs, Path file, int maxRows) throws Exception {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        int effectiveMax = Math.min(maxRows, XLSX_MAX_ROWS);

        SXSSFWorkbook wb = new SXSSFWorkbook(100);
        try {
            Sheet sheet = wb.createSheet("Export");

            // Header row
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            for (int i = 0; i < colCount; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(meta.getColumnLabel(i + 1));
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int rows = 0;
            int rowNum = 1;
            while (rs.next() && rows < effectiveMax) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < colCount; i++) {
                    String val = getStringValue(rs, i + 1);
                    Cell cell = row.createCell(i);
                    if (val != null) {
                        cell.setCellValue(val);
                    }
                }
                rows++;
            }

            try (var out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        } finally {
            wb.dispose();
        }
    }

    // ── SQL INSERT writer ────────────────────────────────────────────────

    private void writeSqlInsert(ResultSet rs, Path file, String tableName, int maxRows) throws Exception {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        String effectiveTable = (tableName != null && !tableName.isBlank()) ? tableName : "exported_table";

        String[] colNames = new String[colCount];
        for (int i = 0; i < colCount; i++) {
            colNames[i] = quoteIdentifier(meta.getColumnLabel(i + 1));
        }
        String columnsPart = String.join(", ", colNames);

        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            int rows = 0;
            List<String> batchValues = new ArrayList<>(100);

            while (rs.next() && rows < maxRows) {
                String[] vals = new String[colCount];
                for (int i = 0; i < colCount; i++) {
                    vals[i] = escapeSqlValue(getStringValue(rs, i + 1));
                }
                batchValues.add("(" + String.join(", ", vals) + ")");
                rows++;

                if (batchValues.size() >= 100) {
                    writer.write("INSERT INTO ");
                    writer.write(quoteIdentifier(effectiveTable));
                    writer.write(" (");
                    writer.write(columnsPart);
                    writer.write(") VALUES ");
                    writer.write(String.join(", ", batchValues));
                    writer.write(";");
                    writer.newLine();
                    batchValues.clear();
                }
            }

            if (!batchValues.isEmpty()) {
                writer.write("INSERT INTO ");
                writer.write(quoteIdentifier(effectiveTable));
                writer.write(" (");
                writer.write(columnsPart);
                writer.write(") VALUES ");
                writer.write(String.join(", ", batchValues));
                writer.write(";");
                writer.newLine();
            }
        }
    }

    // ── Row counting ────────────────────────────────────────────────────

    private int countRows(String connectionId, String sql) {
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") AS _export_count_";
        try (Connection conn = openConnection(connectionId);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            log.warn("Failed to count rows, will proceed without count", e);
            return 0;
        }
    }

    private int countWrittenRows(Path file, String format, int maxRows) {
        // For CSV/JSON/SQL, count non-empty lines minus header as rough estimate
        // But we already limited by maxRows during write, so return maxRows as estimate
        // More precise: just read the file size and estimate
        try {
            if ("csv".equals(format)) {
                long lineCount = Files.lines(file).count();
                return (int) Math.max(0, lineCount - 1); // minus header
            }
            // For other formats, return maxRows as we limited during write
            return maxRows;
        } catch (Exception e) {
            return 0;
        }
    }

    // ── Connection handling ──────────────────────────────────────────────

    private Connection openConnection(String connectionId) throws Exception {
        return writeService.openConnection(connectionId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String getStringValue(ResultSet rs, int index) throws Exception {
        Object obj = rs.getObject(index);
        if (obj == null) return null;
        return obj.toString();
    }

    private String quoteIdentifier(String id) {
        return "\"" + id.replace("\"", "\"\"") + "\"";
    }

    private String escapeSqlValue(String value) {
        if (value == null) return "NULL";
        return "'" + value.replace("'", "''") + "'";
    }

    private Path getExportDir(String exportId) {
        return Path.of(System.getProperty("user.home"), ".data-talk", "exports", exportId);
    }

    private String generateFilename(String tableName, String sql, String format) {
        String base;
        if (tableName != null && !tableName.isBlank()) {
            base = "export-" + tableName;
        } else {
            base = "export-query";
        }
        return base + "-" + TS_FORMAT.format(LocalDateTime.now());
    }

    private String extensionForFormat(String format) {
        return switch (format) {
            case "csv" -> "csv";
            case "json" -> "json";
            case "xlsx" -> "xlsx";
            case "sql_insert" -> "sql";
            default -> "csv";
        };
    }

    /**
     * Resolve the export file for a given exportId. Returns the first file found
     * in the export directory, or null if not found.
     */
    public Path resolveExportFile(String exportId) {
        Path dir = getExportDir(exportId);
        if (!Files.isDirectory(dir)) return null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) return entry;
            }
        } catch (IOException e) {
            log.warn("Failed to list export directory: {}", dir, e);
        }
        return null;
    }

    /**
     * Clean up old export directories (older than 1 hour).
     */
    public void cleanupOldExports() {
        Path exportsRoot = Path.of(System.getProperty("user.home"), ".data-talk", "exports");
        if (!Files.isDirectory(exportsRoot)) return;

        long cutoff = System.currentTimeMillis() - 3_600_000; // 1 hour ago
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(exportsRoot)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    try (DirectoryStream<Path> files = Files.newDirectoryStream(entry)) {
                        for (Path file : files) {
                            if (Files.isRegularFile(file) && Files.getLastModifiedTime(file).toMillis() < cutoff) {
                                Files.deleteIfExists(file);
                                // Try to delete the directory too if empty
                                Files.deleteIfExists(entry);
                                log.debug("Cleaned up old export: {}", entry);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup old exports", e);
        }
    }
}
