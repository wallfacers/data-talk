package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionKind;
import com.datatalk.application.connection.ConnectionService;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.action.RiskLevel;
import com.datatalk.dto.ConnectionDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.create_connection",
    executor = Executor.SERVER,
    description = "action.create_connection.description",
    timeoutMs = 3_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.MUTATION }
)
public class CreateConnectionAction implements ActionHandler<Map, Map> {

    private final ConnectionService connections;

    public CreateConnectionAction(ConnectionService connections) {
        this.connections = connections;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.ofEntries(
            Map.entry("type", "object"),
            Map.entry("required", List.of("name", "kind")),
            Map.entry("properties", Map.of(
                "name", Map.of("type", "string"),
                "kind", Map.of("type", "string"),
                "host", Map.of("type", "string"),
                "port", Map.of("type", "integer"),
                "databaseName", Map.of("type", "string"),
                "username", Map.of("type", "string"),
                "password", Map.of("type", "string"),
                "connectTimeout", Map.of("type", "integer")
            )),
            Map.entry("allOf", List.of(nonSqliteRequiresServerFieldsSchema()))
        );
    }

    private static Map<String, Object> nonSqliteRequiresServerFieldsSchema() {
        return Map.of(
            "if", Map.of(
                "properties", Map.of("kind", Map.of("const", ConnectionKind.SQLITE)),
                "required", List.of("kind")
            ),
            "else", Map.of("required", List.of("host", "port", "username", "password"))
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("id", "connection"),
            "properties", Map.of(
                "id", Map.of("type", "string"),
                "connection", Map.of("type", "object")
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
        var normalized = normalizeInput(input);
        String id = connections.create(
            normalized.name(),
            normalized.kind(),
            normalized.host(),
            normalized.port(),
            normalized.databaseName(),
            normalized.username(),
            normalized.password(),
            normalized.connectTimeout()
        );
        var out = new LinkedHashMap<String, Object>();
        out.put("id", id);
        out.put("connection", toMap(connections.get(id)));
        return CompletableFuture.completedFuture(out);
    }

    private static NormalizedConnectionInput normalizeInput(Map input) {
        String kind = string(input, "kind");
        boolean sqlite = ConnectionKind.SQLITE.equalsIgnoreCase(kind);
        String password = nullableString(input, "password");
        return new NormalizedConnectionInput(
            string(input, "name"),
            kind,
            sqlite ? "" : string(input, "host"),
            sqlite ? 0 : number(input, "port"),
            nullableString(input, "databaseName"),
            sqlite ? "" : string(input, "username"),
            sqlite ? (password == null ? "" : password) : string(input, "password"),
            nullableInteger(input, "connectTimeout")
        );
    }

    private static Map<String, Object> toMap(ConnectionDto c) {
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
        return out;
    }

    private static String string(Map input, String key) {
        return String.valueOf(input.get(key));
    }

    private static String nullableString(Map input, String key) {
        Object value = input.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static int number(Map input, String key) {
        Object value = input.get(key);
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    private static Integer nullableInteger(Map input, String key) {
        Object value = input.get(key);
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    private record NormalizedConnectionInput(
        String name,
        String kind,
        String host,
        int port,
        String databaseName,
        String username,
        String password,
        Integer connectTimeout
    ) {}
}
