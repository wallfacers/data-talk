package com.datatalk.adapter.actions;

import com.datatalk.application.session.ConnectionTargetDiscoveryService;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.action.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.list_connection_targets",
    executor = Executor.SERVER,
    description = "action.list_connection_targets.description",
    timeoutMs = 5_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.METADATA }
)
public class ListConnectionTargetsAction implements ActionHandler<Map, Map> {

    private final ConnectionTargetDiscoveryService discovery;
    private final SessionDataContextService sessionContexts;

    public ListConnectionTargetsAction(
        ConnectionTargetDiscoveryService discovery,
        SessionDataContextService sessionContexts
    ) {
        this.discovery = discovery;
        this.sessionContexts = sessionContexts;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of("connectionId", Map.of("type", "string"))
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("connectionId", "connectionName", "databases", "schemas"),
            "properties", Map.of(
                "connectionId", Map.of("type", "string"),
                "connectionName", Map.of("type", "string"),
                "databases", Map.of("type", "array"),
                "schemas", Map.of("type", "array")
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
        String connectionId = nullableString(input, "connectionId");
        if (connectionId == null || connectionId.isBlank()) {
            connectionId = sessionContexts.get(ctx.sessionId()).connectionId();
        }
        if (connectionId == null || connectionId.isBlank()) {
            throw new IllegalArgumentException("no active connection in current session");
        }
        var discovered = discovery.discover(connectionId);
        var out = new LinkedHashMap<String, Object>();
        out.put("connectionId", discovered.connectionId());
        out.put("connectionName", discovered.connectionName());
        out.put("databases", discovered.databaseNames().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
        out.put("schemas", discovered.schemaNames().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
        return CompletableFuture.completedFuture(out);
    }

    private static String nullableString(Map input, String key) {
        Object value = input.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
