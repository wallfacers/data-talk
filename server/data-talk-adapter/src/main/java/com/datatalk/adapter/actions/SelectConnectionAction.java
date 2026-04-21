package com.datatalk.adapter.actions;

import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.dto.SessionDataContextUpdateRequest;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.action.RiskLevel;
import com.datatalk.dto.SessionDataContextDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.select_connection",
    executor = Executor.SERVER,
    description = "action.select_connection.description",
    timeoutMs = 3_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.METADATA }
)
public class SelectConnectionAction implements ActionHandler<Map, Map> {

    private final SessionDataContextService sessionContexts;

    public SelectConnectionAction(SessionDataContextService sessionContexts) {
        this.sessionContexts = sessionContexts;
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
            "required", List.of("connectionId", "connectionNameSnapshot"),
            "properties", Map.of(
                "connectionId", Map.of("type", "string"),
                "connectionNameSnapshot", Map.of("type", "string"),
                "database", Map.of("type", "string"),
                "schema", Map.of("type", "string")
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
        String connectionId = String.valueOf(input.get("connectionId"));
        SessionDataContextDto dto = toDto(sessionContexts.set(
            ctx.sessionId(),
            new SessionDataContextUpdateRequest(connectionId, null, null, "connection")
        ));
        var out = new LinkedHashMap<String, Object>();
        out.put("sessionId", dto.sessionId());
        out.put("connectionId", dto.connectionId());
        out.put("connectionNameSnapshot", dto.connectionNameSnapshot());
        out.put("database", dto.database());
        out.put("schema", dto.schema());
        out.put("selectedLevel", dto.selectedLevel());
        return CompletableFuture.completedFuture(out);
    }

    private static SessionDataContextDto toDto(com.datatalk.application.persistence.SessionDataContextRecord record) {
        return new SessionDataContextDto(
            record.sessionId(),
            record.connectionId(),
            record.connectionNameSnapshot(),
            record.databaseName(),
            record.schemaName(),
            record.selectedLevel(),
            record.updatedAt()
        );
    }
}
