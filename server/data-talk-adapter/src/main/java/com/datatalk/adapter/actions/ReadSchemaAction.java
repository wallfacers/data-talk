package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.domain.action.*;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.read_schema",
    executor = Executor.OPENCODE,
    description = "Return table + column metadata for the given connection. Read-only context tool.",
    requiresConnection = true,
    timeoutMs = 10_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.METADATA }
)
public class ReadSchemaAction implements ActionHandler<Map, Map> {

    private final ConnectionRepository connRepo;
    private final ConnectionService conn;

    public ReadSchemaAction(ConnectionRepository connRepo, ConnectionService conn) {
        this.connRepo = connRepo;
        this.conn = conn;
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("connectionId"),
            "properties", Map.of(
                "connectionId", Map.of("type", "string"),
                "tables", Map.of("type", "array", "items", Map.of("type", "string"))
            ));
    }

    @Override public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "required", List.of("schema"),
            "properties", Map.of("schema", Map.of("type", "array")));
    }

    @Override public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.NONE); }
    @Override public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String connectionId = String.valueOf(input.get("connectionId"));
        ConnectionRecord cr = connRepo.findById(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("unknown connection " + connectionId));
        String password = conn.decryptPassword(connectionId);

        List<Map<String, Object>> tables = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(JdbcUrlBuilder.build(cr), cr.username(), password)) {
            var meta = c.getMetaData();
            try (ResultSet tbl = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tbl.next()) {
                    String name = tbl.getString("TABLE_NAME");
                    List<Map<String, Object>> cols = new ArrayList<>();
                    try (ResultSet colRs = meta.getColumns(null, null, name, "%")) {
                        while (colRs.next()) {
                            cols.add(Map.of(
                                "name", colRs.getString("COLUMN_NAME"),
                                "type", colRs.getString("TYPE_NAME"),
                                "nullable", "YES".equals(colRs.getString("IS_NULLABLE"))
                            ));
                        }
                    }
                    tables.add(Map.of("name", name, "columns", cols));
                }
            }
        } catch (Exception e) {
            return CompletableFuture.failedStage(new RuntimeException("schema read failed", e));
        }
        return CompletableFuture.completedFuture(Map.of("schema", tables));
    }
}
