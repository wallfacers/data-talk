package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.action.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.test_connection",
    executor = Executor.SERVER,
    description = "action.test_connection.description",
    timeoutMs = 10_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.METADATA }
)
public class TestConnectionAction implements ActionHandler<Map, Map> {

    private final ConnectionService connections;

    public TestConnectionAction(ConnectionService connections) {
        this.connections = connections;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("connectionId"),
            "properties", Map.of("connectionId", Map.of("type", "string"))
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("ok", "latencyMs", "reason"),
            "properties", Map.of(
                "ok", Map.of("type", "boolean"),
                "latencyMs", Map.of("type", "integer"),
                "reason", Map.of("type", "string")
            )
        );
    }

    @Override
    public List<OntologyEffect> sideEffects() {
        return List.of(OntologyEffect.NONE);
    }

    @Override
    public Class<Map> inputType() {
        return Map.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        var result = connections.testConnection(String.valueOf(input.get("connectionId")));
        var out = new java.util.LinkedHashMap<String, Object>();
        out.put("ok", result.ok());
        out.put("latencyMs", result.latencyMs());
        out.put("reason", result.reason());
        return CompletableFuture.completedFuture(out);
    }
}
