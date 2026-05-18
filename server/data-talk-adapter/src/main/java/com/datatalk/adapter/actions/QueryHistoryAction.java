package com.datatalk.adapter.actions;

import com.datatalk.application.history.SqlExecutionHistoryService;
import com.datatalk.application.history.SqlExecutionRecord;
import com.datatalk.domain.action.*;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.query_history",
    executor = Executor.SERVER,
    description = "action.query_history.description",
    requiresConnection = false,
    timeoutMs = 3_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.METADATA }
)
public class QueryHistoryAction implements ActionHandler<Map, Map> {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final SqlExecutionHistoryService historyService;

    public QueryHistoryAction(SqlExecutionHistoryService historyService) {
        this.historyService = historyService;
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "properties", Map.of(
                "limit", Map.of("type", "integer", "minimum", 1, "maximum", MAX_LIMIT),
                "status", Map.of("type", "string", "enum", List.of("success", "failure", "all")),
                "connectionId", Map.of("type", "string"),
                "database", Map.of("type", "string")
            ));
    }

    @Override public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "required", List.of("queries", "totalQueries"),
            "properties", Map.of(
                "queries", Map.of("type", "array"),
                "totalQueries", Map.of("type", "integer")
            ));
    }

    @Override public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.NONE); }
    @Override public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        return CompletableFuture.supplyAsync(() -> execute(ctx, input));
    }

    private Map<String, Object> execute(ActionContext ctx, Map<String, Object> input) {
        int limit = boundedLimit(intValue(input.get("limit"), DEFAULT_LIMIT));
        SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter filter =
            parseStatusFilter(nullableString(input, "status"));
        String connectionId = nullableString(input, "connectionId");
        String database = nullableString(input, "database");

        List<SqlExecutionRecord> records = historyService.list(
            new SqlExecutionHistoryService.SqlExecutionHistoryQuery(
                ctx.sessionId(), connectionId, database, filter, limit
            )
        );

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("queries", records.stream().map(QueryHistoryAction::toMap).toList());
        out.put("totalQueries", records.size());
        return out;
    }

    private static Map<String, Object> toMap(SqlExecutionRecord r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sqlText", r.sqlText());
        out.put("status", r.status() == SqlExecutionRecord.Status.SUCCESS ? "success" : "failure");
        out.put("executedAt", r.executedAt());
        if (r.durationMs() != null) out.put("durationMs", r.durationMs());
        if (r.rowCount() != null) out.put("rowCount", r.rowCount());
        if (r.errorCode() != null) out.put("errorCode", r.errorCode());
        if (r.errorMessage() != null) out.put("errorMessage", r.errorMessage());
        return out;
    }

    private static SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter parseStatusFilter(String raw) {
        if (raw == null) return SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter.SUCCESS;
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "failure" -> SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter.FAILURE;
            case "all" -> SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter.ALL;
            default -> SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter.SUCCESS;
        };
    }

    private static int boundedLimit(int requested) {
        if (requested <= 0) return DEFAULT_LIMIT;
        return Math.min(requested, MAX_LIMIT);
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s && !s.isBlank()) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private static String nullableString(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
