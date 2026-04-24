package com.datatalk.adapter.actions;

import com.datatalk.application.channel.IdGenerator;
import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.persistence.*;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.application.sql.JdbcResultValueNormalizer;
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
import java.util.stream.Collectors;

@Component
@DataTalkAction(
    id = "datatalk.execute_sql",
    executor = Executor.SERVER,
    description = "action.execute_sql.description",
    produces = {"datatalk.artifact"},
    requiresConnection = true,
    timeoutMs = 30_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.QUERY }
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
    private final IdGenerator ids;
    private final SessionDataContextService sessionContexts;

    public ExecuteSqlAction(ConnectionRepository connRepo, ConnectionService connSvc,
                            SqlStatementGuard guard, ArtifactRepository artifacts,
                            QueryResultRepository queryResults, ObjectMapper om, Clock clock,
                            IdGenerator ids,
                            SessionDataContextService sessionContexts) {
        this.connRepo = connRepo;
        this.connSvc = connSvc;
        this.guard = guard;
        this.artifacts = artifacts;
        this.queryResults = queryResults;
        this.om = om;
        this.clock = clock;
        this.ids = ids;
        this.sessionContexts = sessionContexts;
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("sql"),
            "properties", Map.of(
                "connectionId", Map.of("type", "string"),
                "database",     Map.of("type", "string"),
                "schema",       Map.of("type", "string"),
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
                "durationMs",  Map.of("type", "integer"),
                "metadata",    Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "riskLevel", Map.of("type", "string"),
                        "riskReason", Map.of("type", "string"),
                        "fallbackUsed", Map.of("type", "boolean")
                    )
                )
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

        var resolved = resolveContext(ctx, input);
        ConnectionRecord cr = withDatabase(resolved.connection(), resolved.database());

        long started = clock.millis();
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        try (Connection c = DriverManager.getConnection(JdbcUrlBuilder.build(cr), cr.username(),
                connSvc.decryptPassword(cr.id()));
             PreparedStatement ps = c.prepareStatement(sql)) {
            applyExecutionContext(c, cr.kind(), resolved.schema());
            ps.setQueryTimeout(30);
            try (ResultSet rs = ps.executeQuery()) {
                var md = rs.getMetaData();
                for (int i = 1; i <= md.getColumnCount(); i++) columns.add(md.getColumnLabel(i));
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        row.put(columns.get(i - 1), JdbcResultValueNormalizer.normalize(rs.getObject(i)));
                    }
                    rows.add(row);
                }
            }
        } catch (SQLTimeoutException e) {
            throw new DataTalkException(DataTalkErrorCodes.SQL_TIMEOUT, "query timeout", false);
        } catch (SQLException e) {
            throw new DataTalkException(DataTalkErrorCodes.SQL_SYNTAX_ERROR, e.getMessage(), true);
        }

        long duration = clock.millis() - started;
        String artifactId = ids.nextArtifactId();
        int version = 1;

        String rowsNdjson = rows.stream()
            .map(r -> jsonToString(r))
            .collect(Collectors.joining("\n"));
        int payloadSize = rowsNdjson.getBytes().length;

        String payloadRef;
        String handle = "";
        if (payloadSize <= INLINE_LIMIT_BYTES) {
            payloadRef = PayloadRef.INLINE_PREFIX + "[" + rowsNdjson.replace("\n", ",") + "]";
        } else {
            handle = ids.nextQueryHandleId();
            try {
                queryResults.insert(handle, ctx.sessionId(),
                    jsonToString(columns), rowsNdjson,
                    rows.size(), started, started + 7L * 24 * 3600 * 1000);
            } catch (Exception e) { throw new RuntimeException(e); }
            payloadRef = PayloadRef.HANDLE_PREFIX + handle;
        }

        artifacts.insert(new ArtifactRecord(
            artifactId, version, ctx.sessionId(), "table", ctx.callId(),
            payloadRef, payloadSize, null, null, false, started, null, null));

        List<Map<String, Object>> preview = rows.size() > PREVIEW_ROWS
            ? rows.subList(0, PREVIEW_ROWS) : rows;

        return Map.of(
            "artifactId", artifactId,
            "version", version,
            "handle", handle,
            "columns", columns,
            "preview", preview,
            "rowCount", rows.size(),
            "durationMs", (int) duration,
            "metadata", buildMetadata(ctx)
        );
    }

    private ResolvedSqlContext resolveContext(ActionContext ctx, Map<String, Object> input) {
        String requestedConnectionId = nullableString(input, "connectionId");
        String requestedDatabase = nullableString(input, "database");
        String requestedSchema = nullableString(input, "schema");
        SessionDataContextRecord sessionContext = sessionContexts.get(ctx.sessionId());

        String connectionId = firstNonBlank(
            requestedConnectionId,
            sessionContext.connectionId(),
            ctx.connectionId()
        );
        if (!hasText(connectionId)) {
            throw new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING, "no active connection", false);
        }

        ConnectionRecord connection = connRepo.findById(connectionId)
            .orElseThrow(() -> new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING,
                "unknown connection: " + connectionId, false));
        boolean inheritsSessionScope = connectionId.equals(sessionContext.connectionId());
        return new ResolvedSqlContext(
            connection,
            firstNonBlank(
                requestedDatabase,
                inheritsSessionScope ? sessionContext.databaseName() : null,
                connection.databaseName()
            ),
            firstNonBlank(
                requestedSchema,
                inheritsSessionScope ? sessionContext.schemaName() : null
            )
        );
    }

    private void applyExecutionContext(Connection connection, String kind, String schema) throws SQLException {
        if (("postgres".equalsIgnoreCase(kind)
            || "postgresql".equalsIgnoreCase(kind)
            || "h2".equalsIgnoreCase(kind))
            && hasText(schema)) {
            connection.setSchema(schema);
        }
    }

    private ConnectionRecord withDatabase(ConnectionRecord connection, String database) {
        return new ConnectionRecord(
            connection.id(),
            connection.name(),
            connection.kind(),
            connection.host(),
            connection.port(),
            database,
            connection.username(),
            connection.passwordEnc(),
            connection.schemaDigest(),
            connection.createdAt(),
            connection.connectTimeout(),
            connection.lastTestStatus(),
            connection.lastTestAt()
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String nullableString(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private record ResolvedSqlContext(
        ConnectionRecord connection,
        String database,
        String schema
    ) {}

    private Map<String, Object> buildMetadata(ActionContext ctx) {
        if (ctx.metadata() == null || ctx.metadata().sqlRisk() == null) {
            return Map.of();
        }
        var risk = ctx.metadata().sqlRisk();
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (risk.riskLevel() != null) {
            metadata.put("riskLevel", risk.riskLevel().name());
        }
        if (risk.reason() != null && !risk.reason().isBlank()) {
            metadata.put("riskReason", risk.reason());
        }
        metadata.put("fallbackUsed", risk.fallbackUsed());
        return metadata;
    }

    private String jsonToString(Object obj) {
        try { return om.writeValueAsString(obj); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
