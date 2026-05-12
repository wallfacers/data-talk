package com.datatalk.adapter.actions.ingestion;

import com.datatalk.application.ingestion.IngestionExecutor;
import com.datatalk.domain.action.*;
import com.datatalk.domain.ingestion.IngestionDialectUnsupportedException;
import com.datatalk.domain.ingestion.IngestionTokenInvalidException;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.create_ingestion_table",
    executor = Executor.SERVER,
    description = "action.create_ingestion_table.description",
    timeoutMs = 60_000,
    riskLevel = { RiskLevel.L2 },
    category = { Category.DDL }
)
public class CreateIngestionTableActionHandler implements ActionHandler<Map, Map> {

    private final IngestionExecutor executor;

    public CreateIngestionTableActionHandler(IngestionExecutor executor) {
        this.executor = executor;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("jobId", "connectionId", "schema", "table", "mappingHash", "tokenId"),
            "properties", Map.of(
                "jobId", Map.of("type", "string"),
                "connectionId", Map.of("type", "string"),
                "schema", Map.of("type", "string"),
                "table", Map.of("type", "string"),
                "mappingHash", Map.of("type", "string"),
                "tokenId", Map.of("type", "string")
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "jobId", Map.of("type", "string"),
                "targetTable", Map.of("type", "string"),
                "ddl", Map.of("type", "string"),
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
    public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        try {
            String jobId = str(input, "jobId");
            String connectionId = str(input, "connectionId");
            String schema = str(input, "schema");
            String table = str(input, "table");
            String mappingHash = str(input, "mappingHash");
            String tokenId = str(input, "tokenId");

            var result = executor.createTable(jobId, connectionId, schema, table, mappingHash, tokenId, ctx.sessionId());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("jobId", result.jobId());
            out.put("targetTable", result.targetTable());
            out.put("ddl", result.ddl());
            out.put("status", "table_created");
            // BUG-0031: skip null `error` / `userHint` on success — JSON-Schema
            // declares `type: object` / `type: string`, which rejects null.
            return CompletableFuture.completedFuture(out);
        } catch (IngestionTokenInvalidException e) {
            return CompletableFuture.completedFuture(
                errorNode("INGESTION_TOKEN_INVALID", e.getMessage(),
                    "Confirmation token expired or invalidated. Reopen the ingestion_job Tab and click [Confirm and Ingest] again."));
        } catch (IngestionDialectUnsupportedException e) {
            return CompletableFuture.completedFuture(
                errorNode("INGESTION_DIALECT_UNSUPPORTED", e.getMessage(),
                    "Target connection dialect " + e.kind() + " is not supported. Switch to mysql/postgresql/h2/sqlite."));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                errorNode("INGESTION_CREATE_TABLE_FAILED", e.getMessage(),
                    "CREATE TABLE failed. Check the connection is valid and the table doesn't already exist."));
        }
    }

    private Map<String, Object> errorNode(String code, String reason, String userHint) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "failed");
        // BUG-0031: mirror the HttpRequestActionHandler convention — surface
        // errorCode at the top level so MCP callers / E2E tests can branch
        // on `res.result.errorCode` without unwrapping the nested error.
        out.put("errorCode", code);
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
