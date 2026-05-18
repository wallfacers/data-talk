package com.datatalk.adapter.actions;

import com.datatalk.application.importexport.DataExportService;
import com.datatalk.domain.action.*;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.export_data",
    executor = Executor.SERVER,
    description = "action.export_data.description",
    riskLevel = { RiskLevel.L1 },
    category = { Category.QUERY },
    timeoutMs = 120_000
)
public class ExportDataActionHandler implements ActionHandler<Map, Map> {

    private final DataExportService exportService;

    public ExportDataActionHandler(DataExportService exportService) {
        this.exportService = exportService;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("source", "format"),
            "properties", Map.of(
                "source", Map.of(
                    "type", "object",
                    "description", "Data source: SQL query or table name",
                    "properties", Map.of(
                        "connectionId", Map.of("type", "string", "description", "Database connection ID"),
                        "sql", Map.of("type", "string", "description", "SQL query to export"),
                        "tableName", Map.of("type", "string", "description", "Table name (generates SELECT *)")
                    )
                ),
                "format", Map.of("type", "string", "enum", List.of("csv", "json", "xlsx", "sql_insert"),
                    "description", "Export format"),
                "options", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "filename", Map.of("type", "string", "description", "Custom filename (without extension)"),
                        "maxRows", Map.of("type", "integer", "description", "Max rows to export", "default", 1000000)
                    )
                )
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "exportId", Map.of("type", "string"),
                "downloadUrl", Map.of("type", "string", "description", "Download URL (null if still processing)"),
                "rowCount", Map.of("type", "integer"),
                "fileSize", Map.of("type", "integer", "description", "File size in bytes"),
                "format", Map.of("type", "string"),
                "status", Map.of("type", "string", "enum", List.of("completed", "processing")),
                "warnings", Map.of("type", "array", "items", Map.of("type", "string"))
            )
        );
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
            // 1. Parse source
            Map<String, Object> source = (Map<String, Object>) input.get("source");
            String connectionId = source != null ? (String) source.get("connectionId") : null;
            if (connectionId == null || connectionId.isBlank()) {
                connectionId = ctx.connectionId();
            }
            String sql = source != null ? (String) source.get("sql") : null;
            String tableName = source != null ? (String) source.get("tableName") : null;

            // 2. Parse format
            String format = (String) input.get("format");
            if (format == null || format.isBlank()) {
                return errorResult("format is required");
            }

            // 3. Parse options
            Map<String, Object> options = (Map<String, Object>) input.get("options");
            String filename = options != null ? (String) options.get("filename") : null;
            Integer maxRows = null;
            if (options != null && options.get("maxRows") != null) {
                maxRows = ((Number) options.get("maxRows")).intValue();
            }

            // 4. Call export service
            DataExportService.ExportResult result;
            try {
                result = exportService.export(ctx.sessionId(), connectionId, sql, tableName, format, filename, maxRows);
            } catch (Exception e) {
                return errorResult("Export failed: " + e.getMessage());
            }

            // 5. Build output map
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("exportId", result.exportId());
            output.put("downloadUrl", result.downloadUrl());
            output.put("rowCount", result.rowCount());
            output.put("fileSize", result.fileSizeBytes());
            output.put("format", result.format());
            output.put("status", result.status());
            output.put("warnings", result.warnings());
            return output;
        });
    }

    private static Map<String, Object> errorResult(String message) {
        return Map.of("error", message);
    }
}
