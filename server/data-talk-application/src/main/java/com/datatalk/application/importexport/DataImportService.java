package com.datatalk.application.importexport;

import com.datatalk.application.dialect.IdentifierQuoter;
import com.datatalk.application.script.ScriptDataWriteService;
import com.datatalk.application.upload.UploadedFileRepository;
import com.datatalk.domain.upload.UploadedFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DataImportService {

    private static final Logger log = LoggerFactory.getLogger(DataImportService.class);
    private static final int BATCH_SIZE = 1000;

    private final UploadedFileRepository fileRepo;
    private final ScriptDataWriteService writeService;
    private final ObjectMapper objectMapper;

    public DataImportService(UploadedFileRepository fileRepo,
                             ScriptDataWriteService writeService,
                             ObjectMapper objectMapper) {
        this.fileRepo = fileRepo;
        this.writeService = writeService;
        this.objectMapper = objectMapper;
    }

    public record ImportResult(int rowsImported, String tableName, List<ColumnInfo> columns,
                               List<String> warnings, List<Map<String, Object>> sampleRows,
                               String importId) {}

    public record ColumnInfo(String name, String ddlType) {}

    // ── file import ───────────────────────────────────────────────────

    public ImportResult importFromFile(String fileId, String connectionId, String tableName,
                                       boolean createTable,
                                       Map<String, String> columnMappings,
                                       Map<String, String> columnTypes) {
        UploadedFile file = fileRepo.findById(fileId)
            .orElseThrow(() -> new RuntimeException(
                "FILE_NOT_FOUND: File not found for id: " + fileId));

        String filename = file.filename().toLowerCase();
        String fileType = detectFileType(filename);
        if (fileType == null) {
            throw new RuntimeException(
                "UNSUPPORTED_FILE_TYPE: Unsupported file type for import: " + file.filename());
        }

        Path filePath = Path.of(file.physicalPath());
        String importId = UUID.randomUUID().toString();
        log.info("Starting file import [{}] type={} table={} file={}", importId, fileType, tableName, file.filename());

        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> sampleRows;
        List<String> columns;
        List<String> ddlTypes;

        // Batch accumulator - writes directly to DB via batch insert
        List<Map<String, Object>> allRows = new ArrayList<>();

        try {
            switch (fileType) {
                case "csv" -> {
                    CsvStreamReader csvReader = new CsvStreamReader();
                    CsvStreamReader.StreamReadResult csvResult = csvReader.stream(
                        filePath, BATCH_SIZE, allRows::addAll, columnMappings, columnTypes);
                    sampleRows = csvResult.sampleRows();
                    columns = csvResult.columns();
                    ddlTypes = csvReader.inferDdlTypes(columns, sampleRows, columnTypes);
                    if (csvResult.totalRows() != allRows.size()) {
                        warnings.add("Partial read: expected " + csvResult.totalRows()
                            + " rows but collected " + allRows.size());
                    }
                }
                case "json" -> {
                    JsonStreamReader jsonReader = new JsonStreamReader(objectMapper);
                    JsonStreamReader.StreamReadResult jsonResult = jsonReader.stream(
                        filePath.toFile(), BATCH_SIZE, allRows::addAll, columnMappings, columnTypes);
                    sampleRows = jsonResult.sampleRows();
                    columns = jsonResult.columns();
                    ddlTypes = jsonReader.inferDdlTypes(columns, sampleRows, columnTypes);
                    if (jsonResult.totalRows() != allRows.size()) {
                        warnings.add("Partial read: expected " + jsonResult.totalRows()
                            + " rows but collected " + allRows.size());
                    }
                }
                case "xlsx" -> {
                    ExcelSaxStreamReader excelReader = new ExcelSaxStreamReader();
                    ExcelSaxStreamReader.StreamReadResult excelResult = excelReader.stream(
                        filePath, BATCH_SIZE, allRows::addAll, columnMappings, columnTypes);
                    sampleRows = excelResult.sampleRows();
                    columns = excelResult.columns();
                    ddlTypes = excelReader.inferDdlTypes(columns, sampleRows, columnTypes);
                    if (excelResult.totalRows() != allRows.size()) {
                        warnings.add("Partial read: expected " + excelResult.totalRows()
                            + " rows but collected " + allRows.size());
                    }
                }
                case "sql" -> {
                    SqlStreamReader sqlReader = new SqlStreamReader();
                    SqlStreamReader.StreamReadResult sqlResult = sqlReader.stream(
                        filePath, BATCH_SIZE, allRows::addAll, columnMappings, columnTypes);
                    sampleRows = sqlResult.sampleRows();
                    columns = sqlResult.columns();

                    // Execute DDL prefix (DROP TABLE / CREATE TABLE) if present
                    String ddlPrefix = sqlReader.getDdlPrefix();
                    if (ddlPrefix != null && !ddlPrefix.isEmpty()) {
                        // Validate DDL target table matches parameter tableName
                        String ddlTable = sqlReader.getDdlTargetTable();
                        if (ddlTable != null) {
                            String paramTable = tableName.toLowerCase();
                            String ddlTableNorm = ddlTable.toLowerCase();
                            if (!ddlTableNorm.equals(paramTable)) {
                                throw new RuntimeException(
                                    "TABLE_NAME_MISMATCH: DDL targets " + ddlTable
                                    + " but tableName=" + tableName);
                            }
                        }

                        // Execute DDL via JDBC
                        try (Connection ddlConn = writeService.openConnection(connectionId)) {
                            for (String ddlStmt : ddlPrefix.split(";\\s*")) {
                                if (!ddlStmt.isBlank()) {
                                    try {
                                        ddlConn.createStatement().execute(ddlStmt);
                                        log.info("Executed DDL: {}", ddlStmt.substring(0, Math.min(80, ddlStmt.length())));
                                    } catch (Exception ddlEx) {
                                        warnings.add("DDL execution warning: " + ddlEx.getMessage());
                                        log.warn("DDL execution failed (continuing): {}", ddlEx.getMessage());
                                    }
                                }
                            }
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            warnings.add("DDL connection warning: " + e.getMessage());
                            log.warn("DDL connection failed (continuing): {}", e.getMessage());
                        }
                    }

                    if (sqlResult.totalRows() == 0) {
                        throw new RuntimeException(
                            "SQL_PARSE_FAILED: No parseable INSERT statements found in file");
                    }

                    // Validate table name consistency
                    Set<String> sqlTables = sqlReader.extractTargetTables(filePath);
                    if (sqlTables.size() > 1) {
                        throw new RuntimeException(
                            "MULTI_TABLE_NOT_SUPPORTED: SQL file targets multiple tables "
                            + sqlTables + "; use query editor instead");
                    }
                    if (!sqlTables.isEmpty()) {
                        String sqlTable = sqlTables.iterator().next().toLowerCase();
                        String paramTable = tableName.toLowerCase();
                        if (!sqlTable.equals(paramTable)) {
                            throw new RuntimeException(
                                "TABLE_NAME_MISMATCH: SQL file targets " + sqlTables.iterator().next()
                                + " but tableName=" + tableName);
                        }
                    }

                    ddlTypes = sqlReader.inferDdlTypesFromTokens(columns, sampleRows, columnTypes);
                    if (sqlResult.totalRows() != allRows.size()) {
                        warnings.add("Partial read: expected " + sqlResult.totalRows()
                            + " rows but collected " + allRows.size());
                    }
                }
                default -> throw new RuntimeException(
                    "UNSUPPORTED_FILE_TYPE: Unsupported file type: " + fileType);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse file: " + e.getMessage(), e);
        }

        if (allRows.isEmpty()) {
            log.info("File import [{}] completed: 0 rows (empty file)", importId);
            List<ColumnInfo> colInfos = new ArrayList<>();
            for (int i = 0; i < columns.size(); i++) {
                colInfos.add(new ColumnInfo(columns.get(i),
                    i < ddlTypes.size() ? ddlTypes.get(i) : "VARCHAR(255)"));
            }
            return new ImportResult(0, tableName, colInfos, warnings, List.of(), importId);
        }

        // Write to target database
        int rowsImported = writeRowsToTable(connectionId, tableName, columns, ddlTypes,
                                             allRows, createTable, columnTypes, warnings);

        List<ColumnInfo> colInfos = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            colInfos.add(new ColumnInfo(columns.get(i),
                i < ddlTypes.size() ? ddlTypes.get(i) : "VARCHAR(255)"));
        }

        log.info("File import [{}] completed: {} rows imported into {}", importId, rowsImported, tableName);
        return new ImportResult(rowsImported, tableName, colInfos, warnings, sampleRows, importId);
    }

    // ── cross-DB copy ─────────────────────────────────────────────────

    public ImportResult importFromQuery(String sourceConnectionId, String sql,
                                         String targetConnectionId, String tableName,
                                         boolean createTable) {
        String importId = UUID.randomUUID().toString();
        log.info("Starting cross-DB copy [{}] from conn={} to conn={} table={}",
                 importId, sourceConnectionId, targetConnectionId, tableName);

        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> sampleRows = new ArrayList<>();

        try (Connection srcConn = writeService.openConnection(sourceConnectionId)) {
            srcConn.setAutoCommit(false);
            try (Statement stmt = srcConn.createStatement(
                     ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                stmt.setFetchSize(500);
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    // Stream the entire ResultSet via writeStream,
                    // which handles cursor-based batch insertion.
                    var streamResult = writeService.writeStream(
                        targetConnectionId, tableName, rs, createTable, null);

                    // Collect columns from the stream result
                    List<ColumnInfo> resultColumns = streamResult.columns().stream()
                        .map(c -> new ColumnInfo(c.name(), c.ddlType()))
                        .toList();

                    log.info("Cross-DB copy [{}] completed: {} rows imported into {}",
                             importId, streamResult.rowsInserted(), tableName);
                    return new ImportResult(streamResult.rowsInserted(), tableName,
                                           resultColumns, warnings, sampleRows, importId);
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("connection") || msg.contains("Connection")
                || msg.contains("connect"))) {
                throw new RuntimeException(
                    "SOURCE_CONNECTION_FAILED: Failed to connect to source database: " + msg, e);
            }
            throw new RuntimeException(
                "SOURCE_QUERY_FAILED: Failed to execute source query: " + msg, e);
        }
    }

    // ── helpers ────────────────────────────────────────────────────────

    private int writeRowsToTable(String connectionId, String tableName, List<String> columns,
                                  List<String> ddlTypes, List<Map<String, Object>> rows,
                                  boolean createTable, Map<String, String> columnTypes,
                                  List<String> warnings) {
        String kind = writeService.resolveKind(connectionId);
        try (Connection c = writeService.openConnection(connectionId)) {
            c.setAutoCommit(false);

            if (createTable && !tableExists(c, tableName)) {
                String ddl = buildCreateTableSql(tableName, columns, ddlTypes, kind);
                log.info("DDL: {}", ddl);
                c.createStatement().execute(ddl);
                log.info("Created table {} with {} columns", tableName, columns.size());
            }

            String insertSql = buildInsertSql(tableName, columns, kind);
            try (PreparedStatement ps = c.prepareStatement(insertSql)) {
                int batchCount = 0;
                int totalInserted = 0;

                for (Map<String, Object> row : rows) {
                    for (int i = 0; i < columns.size(); i++) {
                        ps.setObject(i + 1, row.get(columns.get(i)));
                    }
                    ps.addBatch();
                    batchCount++;

                    if (batchCount >= BATCH_SIZE) {
                        int[] counts = ps.executeBatch();
                        totalInserted += Arrays.stream(counts).sum();
                        c.commit();
                        ps.clearBatch();
                        batchCount = 0;
                    }
                }

                if (batchCount > 0) {
                    int[] counts = ps.executeBatch();
                    totalInserted += Arrays.stream(counts).sum();
                    c.commit();
                }

                return totalInserted;
            }
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to write data to " + tableName + ": " + e.getMessage(), e);
        }
    }

    private String detectFileType(String filename) {
        if (filename.endsWith(".csv")) return "csv";
        if (filename.endsWith(".json")) return "json";
        if (filename.endsWith(".xlsx")) return "xlsx";
        if (filename.endsWith(".sql")) return "sql";
        return null;
    }

    private boolean tableExists(Connection c, String tableName) throws Exception {
        String schema = c.getSchema();
        try (ResultSet rs = c.getMetaData().getTables(c.getCatalog(), schema, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private String buildCreateTableSql(String tableName, List<String> columns,
                                        List<String> ddlTypes, String connectionKind) {
        StringBuilder sb = new StringBuilder("CREATE TABLE ")
            .append(IdentifierQuoter.quote(tableName, connectionKind)).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            String ddlType = i < ddlTypes.size() ? ddlTypes.get(i) : "VARCHAR(255)";
            sb.append(IdentifierQuoter.quote(columns.get(i), connectionKind))
              .append(" ").append(ddlType);
        }
        sb.append(")");
        return sb.toString();
    }

    private String buildInsertSql(String tableName, List<String> columns, String connectionKind) {
        StringBuilder sb = new StringBuilder("INSERT INTO ")
            .append(IdentifierQuoter.quote(tableName, connectionKind)).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(IdentifierQuoter.quote(columns.get(i), connectionKind));
        }
        sb.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("?");
        }
        sb.append(")");
        return sb.toString();
    }
}
