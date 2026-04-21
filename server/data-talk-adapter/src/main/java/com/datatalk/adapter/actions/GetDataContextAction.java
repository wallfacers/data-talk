package com.datatalk.adapter.actions;

import com.datatalk.application.persistence.SessionDataContextRecord;
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
    id = "datatalk.get_data_context",
    executor = Executor.SERVER,
    description = "action.get_data_context.description",
    timeoutMs = 3_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.METADATA }
)
public class GetDataContextAction implements ActionHandler<Map, Map> {

    private final SessionDataContextService sessionContexts;

    public GetDataContextAction(SessionDataContextService sessionContexts) {
        this.sessionContexts = sessionContexts;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object");
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("sessionId"),
            "properties", Map.of(
                "sessionId", Map.of("type", "string"),
                "connectionId", Map.of("type", "string"),
                "connectionNameSnapshot", Map.of("type", "string"),
                "database", Map.of("type", "string"),
                "schema", Map.of("type", "string"),
                "selectedLevel", Map.of("type", "string"),
                "updatedAt", Map.of("type", "integer")
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
        return CompletableFuture.completedFuture(toMap(sessionContexts.get(ctx.sessionId())));
    }

    static Map<String, Object> toMap(SessionDataContextRecord record) {
        var out = new LinkedHashMap<String, Object>();
        out.put("sessionId", record.sessionId());
        out.put("connectionId", record.connectionId());
        out.put("connectionNameSnapshot", record.connectionNameSnapshot());
        out.put("database", record.databaseName());
        out.put("schema", record.schemaName());
        out.put("selectedLevel", record.selectedLevel());
        out.put("updatedAt", record.updatedAt());
        return out;
    }
}
