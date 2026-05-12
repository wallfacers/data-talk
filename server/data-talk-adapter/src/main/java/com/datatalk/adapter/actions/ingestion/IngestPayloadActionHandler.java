package com.datatalk.adapter.actions.ingestion;

import com.datatalk.application.ingestion.IngestionExecutor;
import com.datatalk.domain.action.*;
import com.datatalk.domain.ingestion.IngestionDialectUnsupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.ingest_payload",
    executor = Executor.SERVER,
    description = "action.ingest_payload.description",
    timeoutMs = 300_000,
    riskLevel = { RiskLevel.L2 },
    category = { Category.MUTATION }
)
public class IngestPayloadActionHandler implements ActionHandler<Map, Map> {

    private static final Logger log = LoggerFactory.getLogger(IngestPayloadActionHandler.class);
    private final IngestionExecutor executor;

    public IngestPayloadActionHandler(IngestionExecutor executor) {
        this.executor = executor;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("jobId"),
            "properties", Map.of(
                "jobId", Map.of("type", "string"),
                "batchSize", Map.of("type", "integer", "default", 1000)
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "jobId", Map.of("type", "string"),
                "status", Map.of("type", "string"),
                "rowsInserted", Map.of("type", "integer"),
                "durationMs", Map.of("type", "integer"),
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
            int batchSize = input.get("batchSize") != null
                ? ((Number) input.get("batchSize")).intValue() : 1000;

            var result = executor.ingestPayload(jobId, batchSize, ctx.sessionId());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("jobId", result.jobId());
            out.put("status", result.status());
            out.put("rowsInserted", result.rowsInserted());
            out.put("durationMs", result.durationMs());
            // BUG-0031: skip null `error` / `userHint` on success path.
            return CompletableFuture.completedFuture(out);
        } catch (IngestionDialectUnsupportedException e) {
            return CompletableFuture.completedFuture(
                errorNode("INGESTION_DIALECT_UNSUPPORTED", e.getMessage(),
                    "Target connection dialect " + e.kind() + " is not supported."));
        } catch (Exception e) {
            // BUG-0031: log the underlying failure — without this we'd silently swallow
            // root causes (e.g. table missing, NULL violations) behind the generic error code.
            log.error("ingest_payload failed", e);
            return CompletableFuture.completedFuture(
                errorNode("INGESTION_PAYLOAD_FAILED", e.getMessage(),
                    "Payload ingestion failed. Check the data format and connection."));
        }
    }

    private Map<String, Object> errorNode(String code, String reason, String userHint) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "failed");
        // BUG-0031: surface errorCode at the top level (see HttpRequestActionHandler).
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
