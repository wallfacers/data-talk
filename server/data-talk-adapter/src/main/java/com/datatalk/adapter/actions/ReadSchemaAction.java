package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.session.SessionDataContextService;
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
    description = "action.read_schema.description",
    requiresConnection = true,
    timeoutMs = 10_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.METADATA }
)
public class ReadSchemaAction implements ActionHandler<Map, Map> {

    private final ConnectionRepository connRepo;
    private final ConnectionService conn;
    private final SessionDataContextService sessionContexts;
    private final Translator translator;

    public ReadSchemaAction(
        ConnectionRepository connRepo,
        ConnectionService conn,
        SessionDataContextService sessionContexts,
        Translator translator
    ) {
        this.connRepo = connRepo;
        this.conn = conn;
        this.sessionContexts = sessionContexts;
        this.translator = translator;
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "properties", Map.of(
                "connectionId", Map.of("type", "string"),
                "database", Map.of("type", "string"),
                "schema", Map.of("type", "string"),
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
        SessionDataContextRecord sessionContext = sessionContexts.get(ctx.sessionId());
        String connectionId = firstNonBlank(
            nullableString(input, "connectionId"),
            sessionContext.connectionId(),
            ctx.connectionId()
        );
        if (!hasText(connectionId)) {
            throw new IllegalArgumentException(translator.get("error.connection.active_required"));
        }
        ConnectionRecord base = connRepo.findById(connectionId)
            .orElseThrow(() -> new IllegalArgumentException(translator.get("error.connection.unknown", connectionId)));
        boolean inheritsSessionScope = connectionId.equals(sessionContext.connectionId());
        String database = firstNonBlank(
            nullableString(input, "database"),
            inheritsSessionScope ? sessionContext.databaseName() : null,
            base.databaseName()
        );
        String schema = firstNonBlank(
            nullableString(input, "schema"),
            inheritsSessionScope ? sessionContext.schemaName() : null
        );
        ConnectionRecord cr = withDatabase(base, database);
        String password = conn.decryptPassword(connectionId);

        List<Map<String, Object>> tables = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(JdbcUrlBuilder.build(cr), cr.username(), password)) {
            applySchema(c, cr.kind(), schema);
            MetadataScope scope = metadataScope(cr.kind(), database, schema);
            var meta = c.getMetaData();
            try (ResultSet tbl = meta.getTables(scope.catalog(), scope.schema(), "%", new String[]{"TABLE"})) {
                while (tbl.next()) {
                    String name = tbl.getString("TABLE_NAME");
                    List<Map<String, Object>> cols = new ArrayList<>();
                    try (ResultSet colRs = meta.getColumns(scope.catalog(), scope.schema(), name, "%")) {
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
            return CompletableFuture.failedStage(new RuntimeException(translator.get("error.schema.read_failed"), e));
        }
        return CompletableFuture.completedFuture(Map.of("schema", tables));
    }

    static MetadataScope metadataScope(String kind, String database, String schema) {
        if ("mysql".equalsIgnoreCase(kind)) {
            return new MetadataScope(hasText(database) ? database : null, null);
        }
        return new MetadataScope(null, schemaPattern(schema));
    }

    private static void applySchema(Connection connection, String kind, String schema) throws java.sql.SQLException {
        if (("postgres".equalsIgnoreCase(kind)
            || "postgresql".equalsIgnoreCase(kind)
            || "h2".equalsIgnoreCase(kind))
            && hasText(schema)) {
            connection.setSchema(schema);
        }
    }

    private static ConnectionRecord withDatabase(ConnectionRecord connection, String database) {
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

    private static String schemaPattern(String schema) {
        return hasText(schema) ? schema : null;
    }

    private static String nullableString(Map input, String key) {
        Object value = input.get(key);
        return value == null ? null : String.valueOf(value);
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

    record MetadataScope(String catalog, String schema) {}
}
