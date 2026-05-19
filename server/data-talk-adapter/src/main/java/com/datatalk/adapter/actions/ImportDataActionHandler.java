package com.datatalk.adapter.actions;

import com.datatalk.application.importexport.DataImportService;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.domain.action.*;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.import_data",
    executor = Executor.SERVER,
    description = "action.import_data.description",
    riskLevel = { RiskLevel.L2 },
    category = { Category.MUTATION },
    timeoutMs = 120_000
)
public class ImportDataActionHandler implements ActionHandler<Map, Map> {

    private final DataImportService importService;
    private final SessionDataContextService sessionContexts;
    private final ConnectionRepository connRepo;

    public ImportDataActionHandler(DataImportService importService,
                                   SessionDataContextService sessionContexts,
                                   ConnectionRepository connRepo) {
        this.importService = importService;
        this.sessionContexts = sessionContexts;
        this.connRepo = connRepo;
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> sourceProps = new LinkedHashMap<>();
        sourceProps.put("type", Map.of("type", "string", "enum", List.of("file", "query"),
            "description", "Source type"));
        sourceProps.put("fileId", Map.of("type", "string",
            "description", "Uploaded file ID (when type=file)"));
        sourceProps.put("connectionId", Map.of("type", "string",
            "description", "Source connection ID (when type=query)"));
        sourceProps.put("sql", Map.of("type", "string",
            "description", "SQL query (when type=query)"));

        Map<String, Object> targetProps = new LinkedHashMap<>();
        targetProps.put("connectionId", Map.of("type", "string"));
        targetProps.put("tableName", Map.of("type", "string"));
        targetProps.put("database", Map.of("type", "string",
            "description", "Target database name; overrides session_data_context. "
                + "Required for server-level connections without a default database (BUG-0072)."));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("source", Map.of("type", "object",
            "description", "Data source: file reference or database query",
            "properties", sourceProps));
        properties.put("target", Map.of("type", "object",
            "required", List.of("connectionId", "tableName"),
            "properties", targetProps));
        properties.put("createTable", Map.of("type", "boolean", "default", true));
        properties.put("columnMappings", Map.of("type", "object",
            "description", "Optional rename map: source column name -> target table column name, "
                + "e.g. {\"oldName\":\"newName\"}. Use when file headers don't match the target schema."));
        properties.put("columnTypes", Map.of("type", "object",
            "description", "Optional per-column DDL type override, "
                + "e.g. {\"id\":\"BIGINT\",\"created_at\":\"DATETIME(6)\"}. "
                + "Defaults are inferred from sample values (integer->BIGINT, decimal->DOUBLE, "
                + "otherwise VARCHAR(255)); set only when the user pins specific types or "
                + "inference is wrong (createTable=true only)."));

        return Map.of("type", "object",
            "required", List.of("source", "target"),
            "properties", properties);
    }

    @Override
    public Map<String, Object> outputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("rowsImported", Map.of("type", "integer"));
        properties.put("tableName", Map.of("type", "string"));
        properties.put("columns", Map.of("type", "array",
            "items", Map.of("type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string"),
                    "type", Map.of("type", "string")))));
        properties.put("warnings", Map.of("type", "array", "items", Map.of("type", "string")));
        properties.put("sampleRows", Map.of("type", "array",
            "description", "First 3 rows for AI to report"));
        properties.put("importId", Map.of("type", "string"));
        return Map.of("type", "object", "properties", properties);
    }

    @Override
    public List<OntologyEffect> sideEffects() {
        return List.of(OntologyEffect.CREATE_ARTIFACT);
    }

    @Override
    public Class<Map> inputType() {
        return Map.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        return CompletableFuture.supplyAsync(() -> {
            // 1. Parse source from input map
            Object sourceObj = input.get("source");
            if (sourceObj == null) {
                return errorResult("source is required");
            }
            Map<String, Object> source = (Map<String, Object>) sourceObj;
            String sourceType = (String) source.get("type");

            // 2. Parse target from input map
            Object targetObj = input.get("target");
            if (targetObj == null) {
                return errorResult("target is required");
            }
            Map<String, Object> target = (Map<String, Object>) targetObj;
            String targetConnId = (String) target.get("connectionId");
            String tableName = (String) target.get("tableName");
            String requestedDatabase = (String) target.get("database");

            if (targetConnId == null || targetConnId.isBlank()) {
                return errorResult("target.connectionId is required");
            }
            if (tableName == null || tableName.isBlank()) {
                return errorResult("target.tableName is required");
            }

            String targetDatabase = resolveDatabase(ctx, targetConnId, requestedDatabase);

            // 3. Parse optional params
            boolean createTable = boolVal(input.get("createTable"), true);
            Map<String, String> columnMappings = (Map<String, String>) input.get("columnMappings");
            Map<String, String> columnTypes = (Map<String, String>) input.get("columnTypes");

            // 4. Dispatch based on source type
            DataImportService.ImportResult result;
            if ("file".equals(sourceType)) {
                String fileId = (String) source.get("fileId");
                if (fileId == null || fileId.isBlank()) {
                    return errorResult("source.fileId is required when source type is file");
                }
                result = importService.importFromFile(fileId, targetConnId, targetDatabase,
                    tableName, createTable, columnMappings, columnTypes);
            } else if ("query".equals(sourceType)) {
                String sourceConnId = (String) source.get("connectionId");
                String sql = (String) source.get("sql");
                if (sourceConnId == null || sourceConnId.isBlank()) {
                    return errorResult("source.connectionId is required when source type is query");
                }
                if (sql == null || sql.isBlank()) {
                    return errorResult("source.sql is required when source type is query");
                }
                String requestedSourceDatabase = (String) source.get("database");
                String sourceDatabase = resolveDatabase(ctx, sourceConnId, requestedSourceDatabase);
                result = importService.importFromQuery(sourceConnId, sourceDatabase, sql,
                    targetConnId, targetDatabase, tableName, createTable);
            } else {
                return errorResult("Invalid source type: " + sourceType);
            }

            // 5. Build output map
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("rowsImported", result.rowsImported());
            output.put("tableName", result.tableName());
            output.put("columns", result.columns().stream()
                .map(c -> Map.of("name", c.name(), "type", (Object) c.ddlType()))
                .toList());
            output.put("warnings", result.warnings());
            output.put("sampleRows", result.sampleRows().stream().limit(3).toList());
            output.put("importId", result.importId());
            return output;
        });
    }

    /**
     * Three-tier database resolution mirroring {@code ExecuteSqlAction.resolveContext}:
     * explicit input > session_data_context > connection default. Closes BUG-0072 by ensuring
     * server-level connections (databaseName=null) inherit the database the user set via
     * {@code datatalk_set_data_context}, rather than producing a URL with no database segment
     * that fails INSERT with "No database selected".
     */
    private String resolveDatabase(ActionContext ctx, String connectionId, String requestedDatabase) {
        if (requestedDatabase != null && !requestedDatabase.isBlank()) {
            return requestedDatabase;
        }
        if (ctx != null && ctx.sessionId() != null) {
            SessionDataContextRecord sessionContext = sessionContexts.get(ctx.sessionId());
            if (sessionContext != null
                && connectionId.equals(sessionContext.connectionId())
                && sessionContext.databaseName() != null
                && !sessionContext.databaseName().isBlank()) {
                return sessionContext.databaseName();
            }
        }
        return connRepo.findById(connectionId)
            .map(ConnectionRecord::databaseName)
            .orElse(null);
    }

    private static Map<String, Object> errorResult(String message) {
        return Map.of("error", message);
    }

    private static boolean boolVal(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return fallback;
    }
}
