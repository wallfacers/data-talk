package com.datatalk.adapter.actions;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.metadata.SchemaSearchService;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.domain.action.*;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.schema_search",
    executor = Executor.SERVER,
    description = "action.schema_search.description",
    requiresConnection = true,
    timeoutMs = 5_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.METADATA }
)
public class SchemaSearchAction implements ActionHandler<Map, Map> {

    private final SchemaSearchService searchService;
    private final SessionDataContextService sessionContexts;
    private final Translator translator;

    public SchemaSearchAction(SchemaSearchService searchService,
                              SessionDataContextService sessionContexts,
                              Translator translator) {
        this.searchService = searchService;
        this.sessionContexts = sessionContexts;
        this.translator = translator;
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("keyword"),
            "properties", Map.of(
                "keyword", Map.of("type", "string"),
                "connectionId", Map.of("type", "string"),
                "database", Map.of("type", "string"),
                "schema", Map.of("type", "string"),
                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 30)
            ));
    }

    @Override public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "required", List.of("candidates", "totalCandidates", "truncated"),
            "properties", Map.of(
                "candidates", Map.of("type", "array"),
                "totalCandidates", Map.of("type", "integer"),
                "truncated", Map.of("type", "boolean"),
                "hint", Map.of("type", "string")
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
        String keyword = nullableString(input, "keyword");
        SessionDataContextRecord sessionContext = sessionContexts.get(ctx.sessionId());
        String connectionId = firstNonBlank(
            nullableString(input, "connectionId"),
            sessionContext.connectionId(),
            ctx.connectionId()
        );
        if (!hasText(connectionId)) {
            throw new IllegalArgumentException(translator.get("error.connection.active_required"));
        }
        boolean inheritsSessionScope = connectionId.equals(sessionContext.connectionId());
        String database = firstNonBlank(
            nullableString(input, "database"),
            inheritsSessionScope ? sessionContext.databaseName() : null
        );
        String schema = firstNonBlank(
            nullableString(input, "schema"),
            inheritsSessionScope ? sessionContext.schemaName() : null
        );
        int limit = intValue(input.get("limit"), 10);

        SchemaSearchService.SchemaSearchResult result = searchService.search(
            new SchemaSearchService.SchemaSearchRequest(connectionId, keyword, database, schema, limit)
        );

        List<Map<String, Object>> candidates = result.candidates().stream()
            .map(SchemaSearchAction::toMap)
            .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("candidates", candidates);
        out.put("totalCandidates", result.totalCandidates());
        out.put("truncated", result.truncated());
        if (result.hint() != null) out.put("hint", result.hint());
        return out;
    }

    private static Map<String, Object> toMap(SchemaSearchService.TableMatch m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("table", m.table());
        out.put("schema", m.schema());
        out.put("database", m.database());
        out.put("score", m.score());
        out.put("matchedOn", m.matchedOn());
        out.put("commentSnippet", m.commentSnippet() == null ? "" : m.commentSnippet());
        return out;
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (hasText(v)) return v;
        return null;
    }
}
