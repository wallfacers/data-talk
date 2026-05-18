package com.datatalk.adapter.actions;

import com.datatalk.application.channel.IdGenerator;
import com.datatalk.application.connection.ConnectionKind;
import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.history.SqlExecutionHistoryService;
import com.datatalk.application.history.SqlExecutionRecord;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.*;
import com.datatalk.application.preference.UserPreferencesService;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.application.sql.JdbcResultValueNormalizer;
import com.datatalk.application.sql.CalciteSqlRiskAnalyzer;
import com.datatalk.application.sql.KingbaseUnsupportedReason;
import com.datatalk.application.sql.SqlPendingConfirmationStore;
import com.datatalk.application.sql.SqlRiskAnalysis;
import com.datatalk.application.sql.SqlRiskAnalyzer;
import com.datatalk.domain.action.*;
import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import com.datatalk.domain.preference.UserPreferences;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.time.Clock;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Handles SQL execution from the AI chat path.
 * Only DELETE statements require conversational confirmation (in-chat);
 * all other SQL (SELECT, INSERT, UPDATE, DDL) executes directly.
 * Confirmation flow: DELETE → returns requires_confirmation with confirmationId →
 * user confirms → re-invoked with confirmationId → executes.
 */

@Component
@DataTalkAction(
    id = "datatalk.execute_sql",
    executor = Executor.SERVER,
    description = "action.execute_sql.description",
    produces = {"datatalk.artifact"},
    requiresConnection = true,
    timeoutMs = 30_000,
    riskLevel = { RiskLevel.L1, RiskLevel.L2, RiskLevel.L3 },
    category = { Category.QUERY, Category.MUTATION }
)
public class ExecuteSqlAction implements ActionHandler<Map, Map> {

    private static final Logger log = LoggerFactory.getLogger(ExecuteSqlAction.class);
    private static final int INLINE_LIMIT_BYTES = 256 * 1024;
    private static final int PREVIEW_ROWS = 100;
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 1_000;

    private final ConnectionRepository connRepo;
    private final ConnectionService connSvc;
    private final SqlRiskAnalyzer riskAnalyzer;
    private final ArtifactRepository artifacts;
    private final QueryResultRepository queryResults;
    private final UserPreferencesService userPrefsService;
    private final ObjectMapper om;
    private final Clock clock;
    private final IdGenerator ids;
    private final SessionDataContextService sessionContexts;
    private final Translator translator;
    private final SqlPendingConfirmationStore confirmationStore;
    private final SqlExecutionHistoryService historyService;

    public ExecuteSqlAction(ConnectionRepository connRepo, ConnectionService connSvc,
                            SqlRiskAnalyzer riskAnalyzer, ArtifactRepository artifacts,
                            QueryResultRepository queryResults,
                            UserPreferencesService userPrefsService,
                            ObjectMapper om, Clock clock,
                            IdGenerator ids,
                            SessionDataContextService sessionContexts,
                            Translator translator,
                            SqlPendingConfirmationStore confirmationStore,
                            SqlExecutionHistoryService historyService) {
        this.connRepo = connRepo;
        this.connSvc = connSvc;
        this.riskAnalyzer = riskAnalyzer;
        this.artifacts = artifacts;
        this.queryResults = queryResults;
        this.userPrefsService = userPrefsService;
        this.om = om;
        this.clock = clock;
        this.ids = ids;
        this.sessionContexts = sessionContexts;
        this.translator = translator;
        this.confirmationStore = confirmationStore;
        this.historyService = historyService;
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("sql"),
            "properties", Map.of(
                "connectionId", Map.of("type", "string"),
                "database",     Map.of("type", "string"),
                "schema",       Map.of("type", "string"),
                "sql",          Map.of("type", "string"),
                "pageSize",     Map.of("type", "integer", "minimum", 1, "maximum", MAX_PAGE_SIZE),
                "confirmationId", Map.of("type", "string"),
                "source",       Map.of("type", "string")
            ));
    }

    @Override public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "required", List.of("artifactId", "version", "columns", "preview", "rowCount", "durationMs"),
            "properties", Map.ofEntries(
                Map.entry("artifactId",  Map.of("type", "string")),
                Map.entry("version",     Map.of("type", "integer")),
                Map.entry("handle",      Map.of("type", "string")),
                Map.entry("columns",     Map.of("type", "array")),
                Map.entry("preview",     Map.of("type", "array")),
                Map.entry("rowCount",    Map.of("type", "integer")),
                Map.entry("truncated",   Map.of("type", "boolean")),
                Map.entry("durationMs",  Map.of("type", "integer")),
                Map.entry("status",      Map.of("type", "string")),
                Map.entry("confirmationId", Map.of("type", "string")),
                Map.entry("message",     Map.of("type", "string")),
                Map.entry("sqlPreview",  Map.of("type", "string")),
                Map.entry("affectedObjects", Map.of("type", "array")),
                Map.entry("metadata",    Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "riskLevel", Map.of("type", "string"),
                        "riskReason", Map.of("type", "string"),
                        "fallbackUsed", Map.of("type", "boolean")
                    )
                ))
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
        // Confirmation path: if confirmationId is provided, complete the pending execution
        String confirmationId = nullableString(input, "confirmationId");
        if (hasText(confirmationId)) {
            return executeConfirmation(ctx, confirmationId);
        }

        String sql = String.valueOf(input.get("sql"));
        var resolved = resolveContext(ctx, input);

        // Dameng Channel 2 — dialect_unsupported entry gate (chat path, per spec §8.1)
        if ("dameng".equalsIgnoreCase(resolved.connection().kind())) {
            var unsupportedReason = ((CalciteSqlRiskAnalyzer) riskAnalyzer).detectDamengUnsupported(sql);
            if (unsupportedReason.isPresent()) {
                String i18nKey = switch (unsupportedReason.get()) {
                    case CalciteSqlRiskAnalyzer.DamengUnsupportedReason.PLSQL_BLOCK -> "risk.dialect_unsupported.dameng.plsql_block";
                    case CalciteSqlRiskAnalyzer.DamengUnsupportedReason.PROCEDURE_DDL -> "risk.dialect_unsupported.dameng.procedure_ddl";
                    case CalciteSqlRiskAnalyzer.DamengUnsupportedReason.EXP_IMP_COMMAND -> "risk.dialect_unsupported.dameng.exp_imp_command";
                };
                throw new DataTalkException(DataTalkErrorCodes.DIALECT_UNSUPPORTED,
                    translator.get(i18nKey), false);
            }
        }

        // KingbaseES Channel 2 — dialect_unsupported entry gate (chat path)
        if ("kingbase".equalsIgnoreCase(resolved.connection().kind())) {
            var unsupportedReason = ((CalciteSqlRiskAnalyzer) riskAnalyzer).detectKingbaseUnsupported(sql);
            if (unsupportedReason.isPresent()) {
                String i18nKey = switch (unsupportedReason.get()) {
                    case KingbaseUnsupportedReason.KB_BACKUP_RESTORE_CLI -> "risk.dialect_unsupported.kingbase.kb_backup_restore_cli";
                    case KingbaseUnsupportedReason.ORACLE_PLSQL_BLOCK -> "risk.dialect_unsupported.kingbase.oracle_plsql_block";
                };
                throw new DataTalkException(DataTalkErrorCodes.DIALECT_UNSUPPORTED,
                    translator.get(i18nKey), false);
            }
        }

        // AI chat path: only DELETE requires conversational confirmation.
        // All other SQL (SELECT, INSERT, UPDATE, DDL) executes directly.
        SqlRiskAnalysis risk = riskAnalyzer.analyze(sql, Category.QUERY, resolved.connection().kind());
        if (containsDelete(sql)) {
            String pendingId = confirmationStore.create(new SqlPendingConfirmationStore.PendingConfirmation(
                sql,
                resolved.connection().id(),
                ctx.sessionId(),
                resolved.database(),
                resolved.schema(),
                nullableString(input, "source"),
                risk.affectedObjects()
            ));
            String sqlPreview = sql.length() > 200 ? sql.substring(0, 200) + "..." : sql;
            return Map.of(
                "status", "requires_confirmation",
                "confirmationId", pendingId,
                "message", translator.get("sql.confirmation.delete.message"),
                "sqlPreview", sqlPreview,
                "affectedObjects", risk.affectedObjects()
            );
        }

        // Non-DELETE SQL: execute directly (no risk gate on AI path)
        return executeSql(ctx, input, sql, resolved);
    }

    /**
     * Complete a pending confirmation. Executes the stored SQL directly,
     * skipping risk analysis since the user has already confirmed.
     */
    private Map<String, Object> executeConfirmation(ActionContext ctx, String confirmationId) {
        var pending = confirmationStore.get(confirmationId);
        if (pending.isEmpty()) {
            return Map.of(
                "status", "confirmation_invalid",
                "message", translator.get("sql.confirmation.expired.message")
            );
        }

        SqlPendingConfirmationStore.PendingConfirmation conf = pending.get();
        confirmationStore.remove(confirmationId);

        // Resolve context from the stored confirmation
        ConnectionRecord connection = connRepo.findById(conf.connectionId())
            .orElseThrow(() -> new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING,
                translator.get("error.connection.unknown_connection", conf.connectionId()), false));

        var resolved = new ResolvedSqlContext(
            connection,
            conf.database(),
            conf.schema()
        );

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("sql", conf.sql());
        input.put("connectionId", conf.connectionId());
        if (conf.database() != null) input.put("database", conf.database());
        if (conf.schema() != null) input.put("schema", conf.schema());

        return executeSql(ctx, input, conf.sql(), resolved);
    }

    private Map<String, Object> executeSql(ActionContext ctx, Map<String, Object> input,
                                            String sql, ResolvedSqlContext resolved) {
        ConnectionRecord cr = withDatabase(resolved.connection(), resolved.database());
        int pageSize = pageSize(input.get("pageSize"));

        UserPreferences prefs = userPrefsService.getPreferences();
        ZoneId userZoneId = prefs.timezone();
        String dateFormat = prefs.dateFormat();

        long started = clock.millis();
        List<String> columns = new ArrayList<>();
        List<Integer> columnTypes = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean truncated = false;
        Integer affectedRows = null;

        String effectiveUsername = ConnectionKind.OCEANBASE.equals(cr.kind())
            ? ConnectionService.composeOceanBaseUsername(cr)
            : cr.username();
        try (Connection c = DriverManager.getConnection(JdbcUrlBuilder.build(cr), effectiveUsername,
                connSvc.decryptPassword(cr.id()));
             PreparedStatement ps = c.prepareStatement(sql)) {
            applyExecutionContext(c, cr.kind(), resolved.schema());
            ps.setQueryTimeout(30);

            if (isQuery(sql)) {
                ps.setMaxRows(pageSize + 1);
                try (ResultSet rs = ps.executeQuery()) {
                    var md = rs.getMetaData();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        columns.add(md.getColumnLabel(i));
                        columnTypes.add(md.getColumnType(i));
                    }
                    while (rs.next()) {
                        if (rows.size() >= pageSize) {
                            truncated = true;
                            break;
                        }
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= md.getColumnCount(); i++) {
                            row.put(columns.get(i - 1), JdbcResultValueNormalizer.normalize(
                                rs.getObject(i), columnTypes.get(i - 1), userZoneId, dateFormat));
                        }
                        rows.add(row);
                    }
                }
            } else {
                int count = ps.executeUpdate();
                affectedRows = count;
            }
        } catch (SQLTimeoutException e) {
            long duration = clock.millis() - started;
            recordFailureSafely(ctx, sql, resolved, DataTalkErrorCodes.SQL_TIMEOUT, e.getMessage(), started, duration);
            throw new DataTalkException(DataTalkErrorCodes.SQL_TIMEOUT, translator.get("error.sql.query_timeout"), false);
        } catch (SQLException e) {
            long duration = clock.millis() - started;
            recordFailureSafely(ctx, sql, resolved, DataTalkErrorCodes.SQL_SYNTAX_ERROR, e.getMessage(), started, duration);
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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifactId", artifactId);
        result.put("version", version);
        result.put("handle", handle);
        result.put("columns", columns);
        result.put("preview", preview);
        result.put("rowCount", rows.size());
        result.put("truncated", truncated);
        result.put("durationMs", (int) duration);
        result.put("metadata", buildMetadata(ctx));
        if (affectedRows != null) {
            result.put("affectedRows", affectedRows);
        }
        int recordedRowCount = affectedRows != null ? affectedRows : rows.size();
        recordSuccessSafely(ctx, sql, resolved, started, duration, recordedRowCount);
        return result;
    }

    private void recordSuccessSafely(ActionContext ctx, String sql, ResolvedSqlContext resolved,
                                      long startedAt, long durationMs, int rowCount) {
        try {
            historyService.record(SqlExecutionRecord.success(
                ctx.sessionId(),
                resolved.connection().id(),
                resolved.database(),
                resolved.schema(),
                sql,
                startedAt,
                durationMs,
                rowCount
            ));
        } catch (Exception e) {
            log.warn("sql_execution_history record (success) failed for session={}: {}",
                ctx.sessionId(), e.toString());
        }
    }

    private void recordFailureSafely(ActionContext ctx, String sql, ResolvedSqlContext resolved,
                                      String errorCode, String errorMessage,
                                      long startedAt, long durationMs) {
        try {
            historyService.record(SqlExecutionRecord.failure(
                ctx.sessionId(),
                resolved.connection().id(),
                resolved.database(),
                resolved.schema(),
                sql,
                errorCode,
                errorMessage,
                startedAt,
                durationMs
            ));
        } catch (Exception e) {
            log.warn("sql_execution_history record (failure) failed for session={}: {}",
                ctx.sessionId(), e.toString());
        }
    }

    /**
     * Detect whether SQL is a query (SELECT/WITH) that produces a result set.
     */
    private boolean isQuery(String sql) {
        if (sql == null || sql.isBlank()) return false;
        String trimmed = sql.trim().toUpperCase();
        return trimmed.startsWith("SELECT") || trimmed.startsWith("WITH")
            || trimmed.startsWith("EXPLAIN") || trimmed.startsWith("SHOW")
            || trimmed.startsWith("DESCRIBE") || trimmed.startsWith("DESC");
    }

    /**
     * Detect whether SQL contains a DELETE statement.
     */
    private boolean containsDelete(String sql) {
        if (sql == null || sql.isBlank()) return false;
        String trimmed = sql.trim().toUpperCase();
        if (trimmed.startsWith("DELETE") || trimmed.startsWith("DELETE\t")) {
            return true;
        }
        // Check for multi-statement with semicolon-separated DELETE
        // Use simple string matching for the chat path
        String upper = sql.toUpperCase();
        // Match DELETE at statement boundaries (after ; or at start)
        if (upper.startsWith("DELETE ") || upper.startsWith("DELETE\t")) {
            return true;
        }
        // Check for ; DELETE patterns
        int idx;
        int searchFrom = 0;
        while ((idx = upper.indexOf(';', searchFrom)) >= 0) {
            String after = upper.substring(idx + 1).trim();
            if (after.startsWith("DELETE ") || after.startsWith("DELETE\t")) {
                return true;
            }
            searchFrom = idx + 1;
        }
        return false;
    }

    private static int pageSize(Object value) {
        int parsed = DEFAULT_PAGE_SIZE;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else if (value instanceof String text && hasText(text)) {
            try {
                parsed = Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                parsed = DEFAULT_PAGE_SIZE;
            }
        }
        if (parsed < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(parsed, MAX_PAGE_SIZE);
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
            throw new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING, translator.get("error.connection.no_active"), false);
        }

        ConnectionRecord connection = connRepo.findById(connectionId)
            .orElseThrow(() -> new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING,
                translator.get("error.connection.unknown_connection", connectionId), false));
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
            || "h2".equalsIgnoreCase(kind)
            || "sqlserver".equalsIgnoreCase(kind)
            || "kingbase".equalsIgnoreCase(kind))
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
            connection.lastTestAt(),
            connection.oracleServiceType(),
            connection.sqlserverEncrypt(),
            connection.sqlserverTrustServerCertificate(),
            connection.sqlserverInstanceName(),
            connection.readOnly(),
            connection.compatibilityMode(),
            connection.oceanbaseTenant(),
            connection.oceanbaseCluster()
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
