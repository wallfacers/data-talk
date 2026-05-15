package com.datatalk.adapter.actions;

import com.datatalk.application.script.ScriptRunService;
import com.datatalk.domain.action.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.script_stop",
    executor = Executor.SERVER,
    description = "action.script_stop.description",
    timeoutMs = 3_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.MISC }
)
public class StopScriptActionHandler implements ActionHandler<Map, Map> {

    private final ScriptRunService runService;

    public StopScriptActionHandler(ScriptRunService runService) {
        this.runService = runService;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("runId"),
            "properties", Map.of(
                "runId", Map.of("type", "string", "description", "The script run ID to cancel")
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of("type", "object", "properties", Map.of(
            "status", Map.of("type", "string")
        ));
    }

    @Override
    public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.NONE); }

    @Override
    public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String runId = (String) input.get("runId");
        runService.cancelRun(runId);
        return CompletableFuture.completedFuture(Map.of("status", "cancelled"));
    }
}
