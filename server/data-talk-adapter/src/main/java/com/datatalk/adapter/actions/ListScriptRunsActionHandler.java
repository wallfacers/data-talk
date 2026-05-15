package com.datatalk.adapter.actions;

import com.datatalk.application.script.ScriptRunService;
import com.datatalk.domain.action.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk_script_list",
    executor = Executor.SERVER,
    description = "action.script_list.description",
    timeoutMs = 3_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.METADATA }
)
public class ListScriptRunsActionHandler implements ActionHandler<Map, Map> {

    private final ScriptRunService runService;

    public ListScriptRunsActionHandler(ScriptRunService runService) {
        this.runService = runService;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "connectionId", Map.of("type", "string", "description", "Filter by connection ID"),
                "limit", Map.of("type", "integer", "description", "Max results (default 50)")
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("runs"),
            "properties", Map.of("runs", Map.of("type", "array"))
        );
    }

    @Override
    public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.NONE); }

    @Override
    public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String connectionId = (String) input.get("connectionId");
        int limit = input.containsKey("limit") ? ((Number) input.get("limit")).intValue() : 50;

        var runs = runService.listRuns(connectionId, limit);
        List<Map<String, Object>> runMaps = runs.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id());
            m.put("language", r.language().dbValue());
            m.put("status", r.status().dbValue());
            m.put("exitCode", r.exitCode());
            m.put("connectionId", r.connectionId());
            m.put("targetTable", r.targetTable());
            m.put("rowsWritten", r.rowsWritten());
            m.put("name", r.name());
            m.put("startedAt", r.startedAt() != null ? r.startedAt().toString() : null);
            m.put("finishedAt", r.finishedAt() != null ? r.finishedAt().toString() : null);
            m.put("durationMs", r.durationMs());
            return m;
        }).toList();

        return CompletableFuture.completedFuture(Map.of("runs", runMaps));
    }
}
