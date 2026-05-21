package com.datatalk.adapter.actions;

import com.datatalk.application.script.ScriptRunService;
import com.datatalk.domain.action.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.script_run",
    executor = Executor.SERVER,
    description = "action.script_run.description",
    timeoutMs = 5_000,
    riskLevel = { RiskLevel.L2 },
    category = { Category.MISC }
)
public class RunScriptActionHandler implements ActionHandler<Map, Map> {

    private final ScriptRunService runService;

    public RunScriptActionHandler(ScriptRunService runService) {
        this.runService = runService;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("scriptContent", "language", "connectionId"),
            "properties", Map.of(
                "scriptContent", Map.of("type", "string", "description", "The script source code"),
                "language", Map.of("type", "string", "enum", List.of("python", "javascript")),
                "connectionId", Map.of("type", "string", "description", "Target database connection ID"),
                "name", Map.of("type", "string", "description", "Optional script name"),
                "createdByKind", Map.of("type", "string", "description", "Who initiated: 'ai' or 'user'")
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("runId", "token"),
            "properties", Map.of(
                "runId", Map.of("type", "string"),
                "token", Map.of("type", "string", "description", "ScriptToken for data write API")
            )
        );
    }

    @Override
    public List<OntologyEffect> sideEffects() {
        return List.of(OntologyEffect.NONE);
    }

    @Override
    public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String scriptContent = (String) input.get("scriptContent");
        String language = (String) input.get("language");
        String connectionId = (String) input.get("connectionId");
        String name = (String) input.get("name");
        String createdByKind = (String) input.getOrDefault("createdByKind", "ai");

        var result = runService.prepareRun(
            scriptContent,
            com.datatalk.domain.script.ScriptLanguage.fromDb(language),
            connectionId,
            name,
            createdByKind,
            ctx.sessionId()
        );

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", result.runId());
        out.put("token", result.token());
        return CompletableFuture.completedFuture(out);
    }
}
