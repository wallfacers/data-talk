package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.action.RiskLevel;
import com.datatalk.dto.ConnectionDto;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.list_connections",
    executor = Executor.SERVER,
    description = "action.list_connections.description",
    timeoutMs = 3_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.METADATA }
)
public class ListConnectionsAction implements ActionHandler<Map, Map> {

    private final ConnectionService connections;
    private final SessionDataContextService sessionContexts;

    public ListConnectionsAction(ConnectionService connections, SessionDataContextService sessionContexts) {
        this.connections = connections;
        this.sessionContexts = sessionContexts;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object");
    }

    @Override
    public Map<String, Object> outputSchema() {
        Map<String, Object> connectionItemProperties = new LinkedHashMap<>();
        connectionItemProperties.put("id", Map.of("type", "string"));
        connectionItemProperties.put("name", Map.of("type", "string"));
        connectionItemProperties.put("kind", Map.of("type", "string"));
        connectionItemProperties.put("isActiveInSession", Map.of("type", "boolean"));

        Map<String, Object> connectionItemSchema = new LinkedHashMap<>();
        connectionItemSchema.put("type", "object");
        connectionItemSchema.put("required", List.of("id", "isActiveInSession"));
        connectionItemSchema.put("properties", connectionItemProperties);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("activeSessionConnectionId", Map.of("type", List.of("string", "null")));
        properties.put("connections", Map.of(
            "type", "array",
            "items", connectionItemSchema
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("connections", "activeSessionConnectionId"));
        schema.put("properties", properties);
        return schema;
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
        String activeId = resolveActiveConnectionId(ctx);
        var out = new LinkedHashMap<String, Object>();
        out.put("activeSessionConnectionId", activeId);
        out.put("connections", connections.list().stream()
            .map(c -> toMap(c, activeId))
            .toList());
        return CompletableFuture.completedFuture(out);
    }

    private String resolveActiveConnectionId(ActionContext ctx) {
        if (ctx == null || ctx.sessionId() == null || ctx.sessionId().isBlank()) {
            return null;
        }
        try {
            var record = sessionContexts.get(ctx.sessionId());
            return record == null ? null : record.connectionId();
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    private static Map<String, Object> toMap(ConnectionDto c, String activeId) {
        var out = new LinkedHashMap<String, Object>();
        out.put("id", c.id());
        out.put("name", c.name());
        out.put("kind", c.kind());
        out.put("host", c.host());
        out.put("port", c.port());
        out.put("databaseName", c.databaseName());
        out.put("username", c.username());
        out.put("createdAt", c.createdAt());
        out.put("connectTimeout", c.connectTimeout());
        out.put("lastTestStatus", c.lastTestStatus());
        out.put("lastTestAt", c.lastTestAt());
        out.put("isActiveInSession", Objects.equals(c.id(), activeId));
        return out;
    }
}
