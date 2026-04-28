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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    private static final int DEFAULT_DISCOVERY_LIMIT = 50;
    private static final int MAX_DISCOVERY_LIMIT = 100;
    private static final int MAX_DESCRIBE_TABLES = 20;
    private static final int MAX_COLUMNS_PER_TABLE = 200;

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
                "mode", Map.of("type", "string", "enum", List.of("discover", "describe")),
                "pattern", Map.of("type", "string"),
                "limit", Map.of("type", "integer", "minimum", 1, "maximum", MAX_DISCOVERY_LIMIT),
                "cursor", Map.of("oneOf", List.of(
                    Map.of("type", "string"),
                    Map.of("type", "integer", "minimum", 0)
                )),
                "searchColumns", Map.of("type", "boolean"),
                "tables", Map.of("type", "array", "items", Map.of("type", "string"))
            ));
    }

    @Override public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "required", List.of("schema"),
            "properties", Map.of(
                "schema", Map.of("type", "array"),
                "mode", Map.of("type", "string"),
                "returnedCount", Map.of("type", "integer"),
                "totalCount", Map.of("type", "integer"),
                "truncated", Map.of("type", "boolean"),
                "nextCursor", Map.of("type", "string")
            ));
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
        Set<String> requestedTables = requestedTables(input.get("tables"));
        String mode = resolveMode(nullableString(input, "mode"), requestedTables);
        if ("describe".equals(mode) && requestedTables.size() > MAX_DESCRIBE_TABLES) {
            return CompletableFuture.failedStage(new IllegalArgumentException(
                "datatalk_read_schema describe mode accepts at most 20 tables per call"
            ));
        }

        List<Map<String, Object>> tables = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(JdbcUrlBuilder.build(cr), cr.username(), password)) {
            applySchema(c, cr.kind(), schema);
            MetadataScope scope = metadataScope(cr.kind(), database, schema);
            var meta = c.getMetaData();
            try (ResultSet tbl = meta.getTables(scope.catalog(), scope.schema(), "%", new String[]{"TABLE"})) {
                while (tbl.next()) {
                    String name = tbl.getString("TABLE_NAME");
                    if ("describe".equals(mode) && !requestedTables.contains(normalizeTableName(name))) {
                        continue;
                    }
                    if ("discover".equals(mode)) {
                        if (!matchesDiscoveryPattern(meta, scope, name, input)) {
                            continue;
                        }
                        tables.add(Map.of("name", name));
                        continue;
                    }
                    List<Map<String, Object>> cols = new ArrayList<>();
                    boolean columnsTruncated = false;
                    try (ResultSet colRs = meta.getColumns(scope.catalog(), scope.schema(), name, "%")) {
                        while (colRs.next()) {
                            if (cols.size() >= MAX_COLUMNS_PER_TABLE) {
                                columnsTruncated = true;
                                break;
                            }
                            cols.add(Map.of(
                                "name", colRs.getString("COLUMN_NAME"),
                                "type", colRs.getString("TYPE_NAME"),
                                "nullable", "YES".equals(colRs.getString("IS_NULLABLE"))
                            ));
                        }
                    }
                    Map<String, Object> table = new LinkedHashMap<>();
                    table.put("name", name);
                    table.put("columns", cols);
                    if (columnsTruncated) {
                        table.put("columnsTruncated", true);
                    }
                    tables.add(table);
                }
            }
        } catch (Exception e) {
            return CompletableFuture.failedStage(new RuntimeException(translator.get("error.schema.read_failed"), e));
        }
        if ("discover".equals(mode)) {
            return CompletableFuture.completedFuture(discoveryPage(tables, input));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schema", tables);
        out.put("mode", "describe");
        out.put("returnedCount", tables.size());
        out.put("totalCount", tables.size());
        out.put("truncated", false);
        return CompletableFuture.completedFuture(out);
    }

    private static Map<String, Object> discoveryPage(List<Map<String, Object>> allTables, Map input) {
        int limit = limit(input.get("limit"));
        int cursor = cursor(input.get("cursor"));
        int totalCount = allTables.size();
        int from = Math.min(cursor, totalCount);
        int to = Math.min(from + limit, totalCount);
        List<Map<String, Object>> page = allTables.subList(from, to);
        boolean truncated = to < totalCount;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schema", List.copyOf(page));
        out.put("mode", "discover");
        out.put("returnedCount", page.size());
        out.put("totalCount", totalCount);
        out.put("truncated", truncated);
        if (truncated) {
            out.put("nextCursor", String.valueOf(to));
        }
        return out;
    }

    private static boolean matchesDiscoveryPattern(java.sql.DatabaseMetaData meta, MetadataScope scope, String tableName, Map input)
        throws java.sql.SQLException {
        String pattern = nullableString(input, "pattern");
        if (!hasText(pattern)) {
            return true;
        }
        String normalizedPattern = pattern.toLowerCase(Locale.ROOT);
        if (normalizeTableName(tableName).contains(normalizedPattern)) {
            return true;
        }
        if (!Boolean.TRUE.equals(input.get("searchColumns"))) {
            return false;
        }
        try (ResultSet colRs = meta.getColumns(scope.catalog(), scope.schema(), tableName, "%")) {
            while (colRs.next()) {
                String columnName = colRs.getString("COLUMN_NAME");
                if (columnName != null && columnName.toLowerCase(Locale.ROOT).contains(normalizedPattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> requestedTables(Object value) {
        if (!(value instanceof Iterable<?> rawTables)) {
            return Set.of();
        }
        Set<String> tables = new LinkedHashSet<>();
        for (Object rawTable : rawTables) {
            if (rawTable instanceof String table && hasText(table)) {
                tables.add(normalizeTableName(table));
            }
        }
        return tables;
    }

    private static String resolveMode(String requestedMode, Set<String> requestedTables) {
        if ("discover".equalsIgnoreCase(requestedMode)) {
            return "discover";
        }
        if ("describe".equalsIgnoreCase(requestedMode)) {
            return "describe";
        }
        return requestedTables.isEmpty() ? "discover" : "describe";
    }

    private static int limit(Object value) {
        int parsed = intValue(value, DEFAULT_DISCOVERY_LIMIT);
        if (parsed < 1) {
            return DEFAULT_DISCOVERY_LIMIT;
        }
        return Math.min(parsed, MAX_DISCOVERY_LIMIT);
    }

    private static int cursor(Object value) {
        return Math.max(0, intValue(value, 0));
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && hasText(text)) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static MetadataScope metadataScope(String kind, String database, String schema) {
        if ("mysql".equalsIgnoreCase(kind)) {
            return new MetadataScope(hasText(database) ? database : null, null);
        }
        return new MetadataScope(null, schemaPattern(schema));
    }

    private static String normalizeTableName(String tableName) {
        return tableName.toLowerCase(Locale.ROOT);
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
