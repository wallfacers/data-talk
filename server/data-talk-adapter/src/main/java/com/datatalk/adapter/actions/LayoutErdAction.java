package com.datatalk.adapter.actions;

import com.datatalk.application.channel.IdGenerator;
import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.persistence.*;
import com.datatalk.domain.action.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.layout_erd",
    executor = Executor.SERVER,
    description = "action.layout_erd.description",
    produces = {"datatalk.artifact"},
    requiresConnection = true,
    timeoutMs = 15_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.MISC }
)
public class LayoutErdAction implements ActionHandler<Map, Map> {

    private final ConnectionRepository connRepo;
    private final ConnectionService conn;
    private final ArtifactRepository artifacts;
    private final ObjectMapper om;
    private final Clock clock;
    private final IdGenerator ids;

    public LayoutErdAction(ConnectionRepository connRepo, ConnectionService conn,
                           ArtifactRepository artifacts, ObjectMapper om, Clock clock,
                           IdGenerator ids) {
        this.connRepo = connRepo; this.conn = conn;
        this.artifacts = artifacts; this.om = om; this.clock = clock;
        this.ids = ids;
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("connectionId", "tables"),
            "properties", Map.of(
                "connectionId", Map.of("type", "string"),
                "tables", Map.of("type", "array", "items", Map.of("type", "string")),
                "layoutAlgo", Map.of("type", "string")
            ));
    }

    @Override public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "required", List.of("artifactId", "version", "nodes", "edges"),
            "properties", Map.of(
                "artifactId", Map.of("type", "string"),
                "version", Map.of("type", "integer"),
                "nodes", Map.of("type", "array"),
                "edges", Map.of("type", "array")));
    }

    @Override public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.CREATE_ARTIFACT); }
    @Override public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String connectionId = String.valueOf(input.get("connectionId"));
        List<String> tables = (List<String>) input.get("tables");
        ConnectionRecord cr = connRepo.findById(connectionId).orElseThrow();
        String pw = conn.decryptPassword(connectionId);

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        try (Connection c = DriverManager.getConnection(JdbcUrlBuilder.build(cr), cr.username(), pw)) {
            var meta = c.getMetaData();
            int i = 0;
            for (String table : tables) {
                List<Map<String, Object>> cols = new ArrayList<>();
                try (ResultSet rs = meta.getColumns(null, null, table, "%")) {
                    while (rs.next()) cols.add(Map.of(
                        "name", rs.getString("COLUMN_NAME"),
                        "type", rs.getString("TYPE_NAME")
                    ));
                }
                int col = i % 4, row = i / 4;
                nodes.add(Map.of(
                    "id", table,
                    "position", Map.of("x", col * 240, "y", row * 180),
                    "columns", cols
                ));
                try (ResultSet rs = meta.getImportedKeys(null, null, table)) {
                    while (rs.next()) {
                        edges.add(Map.of(
                            "from", rs.getString("PKTABLE_NAME"),
                            "to", table,
                            "fromCol", rs.getString("PKCOLUMN_NAME"),
                            "toCol", rs.getString("FKCOLUMN_NAME")
                        ));
                    }
                }
                i++;
            }
        } catch (Exception e) {
            return CompletableFuture.failedStage(e);
        }

        String artifactId = ids.nextArtifactId();
        String payloadJson;
        try {
            payloadJson = om.writeValueAsString(Map.of("nodes", nodes, "edges", edges));
        } catch (Exception e) { return CompletableFuture.failedStage(e); }
        artifacts.insert(new ArtifactRecord(
            artifactId, 1, ctx.sessionId(), "erd", ctx.callId(),
            PayloadRef.INLINE_PREFIX + payloadJson, payloadJson.length(),
            null, null, false, clock.millis()));

        return CompletableFuture.completedFuture(Map.of(
            "artifactId", artifactId,
            "version", 1,
            "nodes", nodes,
            "edges", edges
        ));
    }
}
