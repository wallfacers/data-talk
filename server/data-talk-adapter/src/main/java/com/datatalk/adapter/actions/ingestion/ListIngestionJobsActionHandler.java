package com.datatalk.adapter.actions.ingestion;

import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.domain.action.*;
import com.datatalk.domain.ingestion.IngestionJob;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.list_ingestion_jobs",
    executor = Executor.SERVER,
    description = "action.list_ingestion_jobs.description",
    timeoutMs = 10_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.METADATA }
)
public class ListIngestionJobsActionHandler implements ActionHandler<Map, Map> {

    private final IngestionJobRepository jobRepo;

    public ListIngestionJobsActionHandler(IngestionJobRepository jobRepo) {
        this.jobRepo = jobRepo;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "status", Map.of("type", "string"),
                "connectionId", Map.of("type", "string"),
                "limit", Map.of("type", "integer", "default", 50),
                "offset", Map.of("type", "integer", "default", 0)
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of("type", "object", "properties", Map.of(
            "items", Map.of("type", "array"), "total", Map.of("type", "integer")));
    }

    @Override
    public List<OntologyEffect> sideEffects() { return List.of(); }

    @Override
    public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String status = input.get("status") != null ? String.valueOf(input.get("status")) : null;
        String connectionId = input.get("connectionId") != null ? String.valueOf(input.get("connectionId")) : null;
        int limit = input.get("limit") != null ? ((Number) input.get("limit")).intValue() : 50;
        int offset = input.get("offset") != null ? ((Number) input.get("offset")).intValue() : 0;

        List<Map<String, Object>> items = jobRepo.list(connectionId, status, null, limit, offset)
            .stream().map(this::jobToMap).toList();
        int total = jobRepo.count(connectionId, status, null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("total", total);
        return CompletableFuture.completedFuture(out);
    }

    private Map<String, Object> jobToMap(IngestionJob j) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", j.id());
        map.put("sourceUrl", j.sourceUrl());
        map.put("status", j.status());
        map.put("payloadFormat", j.payloadFormat() != null ? j.payloadFormat().name() : null);
        map.put("connectionId", j.connectionId());
        map.put("targetSchema", j.targetSchema());
        map.put("targetTable", j.targetTable());
        map.put("rowCount", j.rowCount());
        map.put("createdAt", j.createdAt());
        return map;
    }
}
