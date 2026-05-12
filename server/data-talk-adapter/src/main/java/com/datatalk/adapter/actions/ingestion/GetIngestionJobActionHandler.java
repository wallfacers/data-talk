package com.datatalk.adapter.actions.ingestion;

import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.domain.action.*;
import com.datatalk.domain.ingestion.IngestionJob;
import com.datatalk.domain.ingestion.IngestionMapping;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.get_ingestion_job",
    executor = Executor.SERVER,
    description = "action.get_ingestion_job.description",
    timeoutMs = 10_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.METADATA }
)
public class GetIngestionJobActionHandler implements ActionHandler<Map, Map> {

    private final IngestionJobRepository jobRepo;

    public GetIngestionJobActionHandler(IngestionJobRepository jobRepo) {
        this.jobRepo = jobRepo;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("jobId"),
            "properties", Map.of("jobId", Map.of("type", "string"))
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of("type", "object", "properties", Map.of(
            "job", Map.of("type", "object"), "error", Map.of("type", "object")));
    }

    @Override
    public List<OntologyEffect> sideEffects() { return List.of(); }

    @Override
    public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String jobId = str(input, "jobId");
        var job = jobRepo.findById(jobId);
        if (job.isEmpty()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "not_found");
            Map<String, String> error = new LinkedHashMap<>();
            error.put("code", "INGESTION_JOB_NOT_FOUND");
            error.put("reason", "job not found: " + jobId);
            out.put("error", error);
            out.put("userHint", "Verify the jobId is correct.");
            return CompletableFuture.completedFuture(out);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("job", jobToMap(job.get()));
        return CompletableFuture.completedFuture(out);
    }

    private Map<String, Object> jobToMap(IngestionJob j) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", j.id());
        map.put("sourceUrl", j.sourceUrl());
        map.put("status", j.status());
        map.put("payloadFormat", j.payloadFormat() != null ? j.payloadFormat().name() : null);
        map.put("payloadArtifactId", j.payloadArtifactId());
        map.put("connectionId", j.connectionId());
        map.put("targetSchema", j.targetSchema());
        map.put("targetTable", j.targetTable());
        map.put("mapping", mappingToMap(j.mapping()));
        map.put("mappingHash", j.mappingHash());
        map.put("rowCount", j.rowCount());
        map.put("rowsInserted", j.rowsInserted());
        map.put("bytesFetched", j.bytesFetched());
        map.put("createdAt", j.createdAt());
        map.put("updatedAt", j.updatedAt());
        map.put("completedAt", j.completedAt());
        map.put("errorMessage", j.errorMessage());
        return map;
    }

    private Map<String, Object> mappingToMap(IngestionMapping mapping) {
        if (mapping == null) return null;
        List<Map<String, Object>> cols = mapping.columns().stream().map(c -> {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("sourcePath", c.sourcePath());
            col.put("targetName", c.targetName());
            col.put("type", c.type() != null ? c.type().name() : null);
            col.put("skip", c.skip());
            col.put("sampleValues", c.sampleValues());
            col.put("nullable", c.nullable());
            return col;
        }).toList();
        return Map.of("mappingId", mapping.mappingId(), "columns", cols);
    }

    private static String str(Map input, String key) {
        Object v = input.get(key);
        if (v == null) throw new IllegalArgumentException("missing required field: " + key);
        return String.valueOf(v);
    }
}
