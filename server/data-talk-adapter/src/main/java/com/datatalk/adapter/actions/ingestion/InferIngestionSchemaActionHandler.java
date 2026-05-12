package com.datatalk.adapter.actions.ingestion;

import com.datatalk.application.ingestion.IngestionSchemaInferrer;
import com.datatalk.domain.action.*;
import com.datatalk.domain.ingestion.InferredType;
import com.datatalk.domain.ingestion.IngestionMapping;
import com.datatalk.domain.ingestion.MappingColumn;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@Component
@DataTalkAction(
    id = "datatalk.infer_ingestion_schema",
    executor = Executor.SERVER,
    description = "action.infer_ingestion_schema.description",
    timeoutMs = 60_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.METADATA }
)
public class InferIngestionSchemaActionHandler implements ActionHandler<Map, Map> {

    private final IngestionSchemaInferrer inferrer;

    public InferIngestionSchemaActionHandler(IngestionSchemaInferrer inferrer) {
        this.inferrer = inferrer;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("jobId"),
            "properties", Map.of(
                "jobId", Map.of("type", "string", "description", "Ingestion job ID with a fetched payload"),
                "dialect", Map.of("type", "string", "description", "Target SQL dialect for DDL (unused, reserved for future)"),
                "sampleSize", Map.of("type", "integer", "description", "Max rows to sample for schema inference", "default", 100)
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("mappingId", "columns", "rowsAnalyzed"),
            "properties", Map.of(
                "mappingId", Map.of("type", "string"),
                "columns", Map.of("type", "array"),
                "suggestedDdl", Map.of("type", "string"),
                "rowsAnalyzed", Map.of("type", "integer"),
                "status", Map.of("type", "string"),
                "error", Map.of("type", "object"),
                "userHint", Map.of("type", "string")
            )
        );
    }

    @Override
    public List<OntologyEffect> sideEffects() {
        return List.of(OntologyEffect.PATCH_ARTIFACT);
    }

    @Override
    public Class<Map> inputType() {
        return Map.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        try {
            String jobId = str(input, "jobId");
            int sampleSize = input.get("sampleSize") != null
                ? ((Number) input.get("sampleSize")).intValue() : 100;

            IngestionMapping mapping = inferrer.infer(jobId, sampleSize, ctx.sessionId());

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("mappingId", mapping.mappingId());
            out.put("columns", mapping.columns().stream()
                .map(this::columnToMap)
                .collect(Collectors.toList()));
            out.put("suggestedDdl", buildSuggestedDdl(mapping));
            out.put("rowsAnalyzed", sampleSize);
            out.put("status", "inferred");
            out.put("error", null);
            out.put("userHint", null);
            return CompletableFuture.completedFuture(out);

        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                errorNode("INGESTION_JOB_NOT_FOUND", e.getMessage(),
                    "Verify the jobId is correct and the payload has been fetched."));
        } catch (UnsupportedOperationException e) {
            return CompletableFuture.completedFuture(
                errorNode("INGESTION_FORMAT_UNSUPPORTED", e.getMessage(),
                    "This payload format is not yet supported for schema inference."));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                errorNode("INGESTION_INFER_FAILED", e.getMessage(),
                    "Schema inference failed. Check the payload file is valid."));
        }
    }

    private Map<String, Object> columnToMap(MappingColumn col) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sourcePath", col.sourcePath());
        m.put("targetName", col.targetName());
        m.put("type", col.type().dbValue());
        m.put("skip", col.skip());
        m.put("sampleValues", col.sampleValues());
        m.put("nullable", col.nullable());
        return m;
    }

    /**
     * Build a simple vanilla CREATE TABLE DDL. Identifiers are quoted with
     * double-quotes (standard SQL). Types are mapped from {@link InferredType}
     * to portable SQL types.
     */
    private String buildSuggestedDdl(IngestionMapping mapping) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE \"ingested_data\" (\n");
        List<MappingColumn> cols = mapping.columns();
        for (int i = 0; i < cols.size(); i++) {
            MappingColumn col = cols.get(i);
            if (col.skip()) continue;
            ddl.append("  \"").append(col.targetName()).append("\" ").append(mapType(col.type()));
            if (i < cols.size() - 1) ddl.append(",");
            ddl.append("\n");
        }
        ddl.append(");");
        return ddl.toString();
    }

    private String mapType(InferredType type) {
        return switch (type) {
            case BOOLEAN -> "BOOLEAN";
            case INTEGER_32 -> "INTEGER";
            case INTEGER_64 -> "BIGINT";
            case DECIMAL -> "DECIMAL(18,4)";
            case DATE -> "DATE";
            case TIMESTAMP -> "TIMESTAMP";
            case STRING_64 -> "VARCHAR(64)";
            case STRING_256 -> "VARCHAR(256)";
            case STRING_500 -> "VARCHAR(500)";
            case STRING_LONG -> "TEXT";
            case JSON -> "TEXT";
        };
    }

    private Map<String, Object> errorNode(String code, String reason, String userHint) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "failed");
        out.put("mappingId", null);
        out.put("columns", List.of());
        out.put("suggestedDdl", null);
        out.put("rowsAnalyzed", 0);
        Map<String, String> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("reason", reason);
        out.put("error", error);
        out.put("userHint", userHint);
        return out;
    }

    private static String str(Map input, String key) {
        Object v = input.get(key);
        if (v == null) throw new IllegalArgumentException("missing required field: " + key);
        return String.valueOf(v);
    }
}
