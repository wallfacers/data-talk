package com.datatalk.adapter.actions;

import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.action.RiskLevel;
import com.datatalk.dto.SessionDataContextUpdateRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.set_data_context",
    executor = Executor.SERVER,
    description = "action.set_data_context.description",
    timeoutMs = 3_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.MUTATION }
)
public class SetDataContextAction implements ActionHandler<Map, Map> {

    private final SessionDataContextService sessionContexts;

    public SetDataContextAction(SessionDataContextService sessionContexts) {
        this.sessionContexts = sessionContexts;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "connectionId", Map.of("type", "string"),
                "database", Map.of("type", "string"),
                "schema", Map.of("type", "string"),
                "selectedLevel", Map.of("type", "string", "enum", List.of("connection", "database", "schema"))
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return new GetDataContextAction(sessionContexts).outputSchema();
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
        SessionDataContextUpdateRequest request = new SessionDataContextUpdateRequest(
            nullableString(input, "connectionId"),
            nullableString(input, "database"),
            nullableString(input, "schema"),
            nullableString(input, "selectedLevel")
        );
        return CompletableFuture.completedFuture(
            GetDataContextAction.toMap(sessionContexts.set(ctx.sessionId(), request))
        );
    }

    private static String nullableString(Map input, String key) {
        Object value = input.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
