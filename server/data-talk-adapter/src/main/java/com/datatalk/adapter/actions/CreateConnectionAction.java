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
            Map.entry("properties", Map.ofEntries(
                Map.entry("name", Map.of("type", "string")),
                Map.entry("kind", Map.of("type", "string")),
                Map.entry("host", Map.of("type", "string")),
                Map.entry("port", Map.of("type", "integer")),
                Map.entry("databaseName", Map.of("type", "string")),
                Map.entry("username", Map.of("type", "string")),
                Map.entry("password", Map.of("type", "string")),
                Map.entry("connectTimeout", Map.of("type", "integer")),
                Map.entry("oracleServiceType", Map.of("type", "string")),
                Map.entry("sqlserverEncrypt", Map.of("type", "boolean")),
                Map.entry("sqlserverTrustServerCertificate", Map.of("type", "boolean")),
                Map.entry("sqlserverInstanceName", Map.of("type", "string")),
                Map.entry("readOnly", Map.of("type", "boolean")),
                Map.entry("compatibilityMode", Map.of("type", "string")),
                Map.entry("oceanbaseTenant", Map.of("type", "string")),
                Map.entry("oceanbaseCluster", Map.of("type", "string"))
            )),
            Map.entry("allOf", List.of(embeddedKindSchema()))
        );
    }

    // SQLite and DuckDB are embedded databases that don't need host/port/username/password
    private static Map<String, Object> embeddedKindSchema() {
        return Map.of(
            "if", Map.of(
                "properties", Map.of("kind", Map.of("enum", List.of(ConnectionKind.SQLITE, ConnectionKind.DUCKDB))),
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
            normalized.connectTimeout(),
            normalized.oracleServiceType(),
            normalized.sqlserverEncrypt(),
            normalized.sqlserverTrustServerCertificate(),
            normalized.sqlserverInstanceName(),
            nullableBoolean(input, "readOnly"),
            nullableString(input, "compatibilityMode"),
            nullableString(input, "oceanbaseTenant"),
            nullableString(input, "oceanbaseCluster")
        );
        var out = new LinkedHashMap<String, Object>();
        out.put("id", id);
        out.put("connection", toMap(connections.get(id)));
        return CompletableFuture.completedFuture(out);
    }

    private static NormalizedConnectionInput normalizeInput(Map input) {
        String kind = string(input, "kind");
        boolean embedded = ConnectionKind.SQLITE.equalsIgnoreCase(kind) || ConnectionKind.DUCKDB.equalsIgnoreCase(kind);
        String password = nullableString(input, "password");
        return new NormalizedConnectionInput(
            string(input, "name"),
            kind,
            embedded ? "" : string(input, "host"),
            embedded ? 0 : number(input, "port"),
            nullableString(input, "databaseName"),
            embedded ? "" : string(input, "username"),
            embedded ? (password == null ? "" : password) : string(input, "password"),
            nullableInteger(input, "connectTimeout"),
            nullableString(input, "oracleServiceType"),
            nullableBoolean(input, "sqlserverEncrypt"),
            nullableBoolean(input, "sqlserverTrustServerCertificate"),
            nullableString(input, "sqlserverInstanceName"),
            nullableBoolean(input, "readOnly"),
            nullableString(input, "compatibilityMode"),
            nullableString(input, "oceanbaseTenant"),
            nullableString(input, "oceanbaseCluster")
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

    private static Boolean nullableBoolean(Map input, String key) {
        Object value = input.get(key);
        if (value == null) return null;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private record NormalizedConnectionInput(
        String name,
        String kind,
        String host,
        int port,
        String databaseName,
        String username,
        String password,
        Integer connectTimeout,
        String oracleServiceType,
        Boolean sqlserverEncrypt,
        Boolean sqlserverTrustServerCertificate,
        String sqlserverInstanceName,
        Boolean readOnly,
        String compatibilityMode,
        String oceanbaseTenant,
        String oceanbaseCluster
    ) {}
}
