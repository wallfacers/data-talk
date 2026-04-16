package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.*;
import com.datatalk.application.sql.SqlStatementGuard;
import com.datatalk.domain.action.*;
import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.execute_sql",
    executor = Executor.SERVER,
    description = "Run a SELECT query on the given connection and persist the result as a table Artifact.",
    produces = {"datatalk.artifact"},
    requiresConnection = true,
    timeoutMs = 30_000
)
public class ExecuteSqlAction implements ActionHandler<Map, Map> {

    private static final int INLINE_LIMIT_BYTES = 256 * 1024;
    private static final int PREVIEW_ROWS = 100;

    private final ConnectionRepository connRepo;
    private final ConnectionService connSvc;
    private final SqlStatementGuard guard;
    private final ArtifactRepository artifacts;
    private final QueryResultRepository queryResults;
    private final ObjectMapper om;
    private final Clock clock;

    public ExecuteSqlAction(ConnectionRepository connRepo, ConnectionService connSvc,
                            SqlStatementGuard guard, ArtifactRepository artifacts,
                            QueryResultRepository queryResults, ObjectMapper om, Clock clock) {
        this.connRepo = connRepo;
        this.connSvc = connSvc;
        this.guard = guard;
        this.artifacts = artifacts;
        this.queryResults = queryResults;
        this.om = om;
        this.clock = clock;
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("connectionId", "sql"),
            "properties", Map.of(
                "connectionId", Map.of("type", "string"),
                "sql",          Map.of("type", "string"),
                "pageSize",     Map.of("type", "integer", "minimum", 1, "maximum", 10_000)
            ));
    }

    @Override public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "required", List.of("artifactId", "version", "columns", "preview", "rowCount", "durationMs"),
            "properties", Map.of(
                "artifactId",  Map.of("type", "string"),
                "version",     Map.of("type", "integer"),
                "handle",      Map.of("type", "string"),
                "columns",     Map.of("type", "array"),
                "preview",     Map.of("type", "array"),
                "rowCount",    Map.of("type", "integer"),
                "durationMs",  Map.of("type", "integer")
            ));
    }

    @Override public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.CREATE_ARTIFACT); }
    @Override public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        return CompletableFuture.supplyAsync(() -> execute(ctx, input));
    }

    private Map<String, Object> execute(ActionContext ctx, Map<String, Object> input) {
        String sql = String.valueOf(input.get("sql"));
        guard.assertSelectOnly(sql);

        String connectionId = String.valueOf(input.get("connectionId"));
        ConnectionRecord cr = connRepo.findById(connectionId)
            .orElseThrow(() -> new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING,
                "unknown connection: " + connectionId, false));

        long started = clock.millis();
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        try (Connection c = DriverManager.getConnection(jdbcUrl(cr), cr.username(),
                connSvc.decryptPassword(connectionId));
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(30);
            try (ResultSet rs = ps.executeQuery()) {
                var md = rs.getMetaData();
                for (int i = 1; i <= md.getColumnCount(); i++) columns.add(md.getColumnLabel(i));
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) row.put(columns.get(i - 1), rs.getObject(i));
                    rows.add(row);
                }
            }
        } catch (SQLTimeoutException e) {
            throw new DataTalkException(DataTalkErrorCodes.SQL_TIMEOUT, "query timeout", false);
        } catch (SQLException e) {
            throw new DataTalkException(DataTalkErrorCodes.SQL_SYNTAX_ERROR, e.getMessage(), true);
        }

        long duration = clock.millis() - started;
        String artifactId = "art-" + UUID.randomUUID();
        int version = 1;
        long createdAt = clock.millis();

        String rowsJson;
        try { rowsJson = om.writeValueAsString(rows); }
        catch (Exception e) { throw new RuntimeException(e); }

        String payloadRef;
        int payloadSize = rowsJson.getBytes().length;
        if (payloadSize <= INLINE_LIMIT_BYTES) {
            payloadRef = "INLINE:" + rowsJson;
        } else {
            String handle = "qr-" + UUID.randomUUID();
            try {
                queryResults.insert(handle, ctx.sessionId(),
                    om.writeValueAsString(columns),
                    String.join("\n", rows.stream().map(r -> {
                        try { return om.writeValueAsString(r); } catch (Exception e) { throw new RuntimeException(e); }
                    }).toList()),
                    rows.size(), createdAt, createdAt + 7L * 24 * 3600 * 1000);
            } catch (Exception e) { throw new RuntimeException(e); }
            payloadRef = "HANDLE:" + handle;
        }

        artifacts.insert(new ArtifactRecord(
            artifactId, version, ctx.sessionId(), "table", ctx.callId(),
            payloadRef, payloadSize, null, null, false, createdAt));

        List<Map<String, Object>> preview = rows.size() > PREVIEW_ROWS
            ? rows.subList(0, PREVIEW_ROWS) : rows;

        return Map.of(
            "artifactId", artifactId,
            "version", version,
            "handle", payloadRef.startsWith("HANDLE:") ? payloadRef.substring(7) : "",
            "columns", columns,
            "preview", preview,
            "rowCount", rows.size(),
            "durationMs", (int) duration
        );
    }

    private static String jdbcUrl(ConnectionRecord c) {
        return switch (c.kind()) {
            case "postgresql" -> "jdbc:postgresql://" + c.host() + ":" + c.port() + "/" + c.databaseName();
            case "mysql"      -> "jdbc:mysql://" + c.host() + ":" + c.port() + "/" + c.databaseName();
            default           -> throw new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING,
                "unsupported kind: " + c.kind(), false);
        };
    }
}
