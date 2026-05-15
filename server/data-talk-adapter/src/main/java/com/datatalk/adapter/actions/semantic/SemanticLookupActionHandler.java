package com.datatalk.adapter.actions.semantic;

import com.datatalk.application.semantic.SemanticModelRepository;
import com.datatalk.domain.action.*;
import com.datatalk.domain.error.DataTalkException;
import com.datatalk.domain.semantic.SemanticModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.semantic_lookup",
    executor = Executor.SERVER,
    description = "Search semantic model entities, dimensions, measures, or metrics by name or label. Use this to understand business terminology before writing SQL.",
    timeoutMs = 5_000,
    requiresConnection = true,
    riskLevel = { RiskLevel.L1 },
    category = { Category.METADATA }
)
public class SemanticLookupActionHandler implements ActionHandler<Map, Map> {

    private static final Logger log = LoggerFactory.getLogger(SemanticLookupActionHandler.class);

    private final SemanticModelRepository repository;

    public SemanticLookupActionHandler(SemanticModelRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("query"),
            "properties", Map.of(
                "query", Map.of("type", "string", "description", "Search term to match against names and labels"),
                "kind", Map.of("type", "string", "enum", List.of("entity", "measure", "metric", "dimension"),
                    "description", "Optional filter by kind")
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "matches", Map.of("type", "array", "items", Map.of("type", "object"))
            )
        );
    }

    @Override
    public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.NONE); }

    @Override
    public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String connectionId = ctx.connectionId();
        Object rawQuery = input != null ? input.get("query") : null;
        String queryStr = rawQuery instanceof String s ? s : null;

        if (queryStr == null || queryStr.isBlank()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("matches", List.of());
            empty.put("total", 0);
            empty.put("warning", "empty_query");
            return CompletableFuture.completedFuture(empty);
        }

        if (connectionId == null || connectionId.isBlank()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("matches", List.of());
            empty.put("total", 0);
            empty.put("warning", "no_active_connection");
            return CompletableFuture.completedFuture(empty);
        }

        String query = queryStr.toLowerCase();
        String kind = (String) (input != null ? input.getOrDefault("kind", null) : null);

        try {
            List<Map<String, Object>> matches = new ArrayList<>();
            for (String domain : repository.listDomains(connectionId)) {
                var modelOpt = repository.loadDomain(connectionId, domain);
                if (modelOpt.isEmpty()) continue;
                SemanticModel m = modelOpt.get();

                if (kind == null || "entity".equals(kind)) {
                    for (var e : m.entities()) {
                        if (matchesQuery(query, e.name(), e.description())) {
                            Map<String, Object> match = new LinkedHashMap<>();
                            match.put("kind", "entity");
                            match.put("name", e.name());
                            match.put("domain", domain);
                            match.put("table", e.physical() != null ? e.physical().table() : null);
                            match.put("type", e.type());
                            matches.add(match);
                        }
                    }
                }
                if (kind == null || "measure".equals(kind)) {
                    for (var e : m.measures()) {
                        if (matchesQuery(query, e.name(), e.labelZh(), e.labelEn(), e.description())) {
                            Map<String, Object> match = new LinkedHashMap<>();
                            match.put("kind", "measure");
                            match.put("name", e.name());
                            match.put("domain", domain);
                            match.put("label_zh", e.labelZh());
                            match.put("label_en", e.labelEn());
                            match.put("agg", e.agg());
                            matches.add(match);
                        }
                    }
                }
                if (kind == null || "metric".equals(kind)) {
                    for (var e : m.metrics()) {
                        if (matchesQuery(query, e.name(), e.labelZh(), e.labelEn(), e.description())) {
                            Map<String, Object> match = new LinkedHashMap<>();
                            match.put("kind", "metric");
                            match.put("name", e.name());
                            match.put("domain", domain);
                            match.put("label_zh", e.labelZh());
                            match.put("label_en", e.labelEn());
                            match.put("type", e.type());
                            matches.add(match);
                        }
                    }
                }
                if (kind == null || "dimension".equals(kind)) {
                    for (var e : m.dimensions()) {
                        if (matchesQuery(query, e.name(), e.labelZh(), e.labelEn(), e.description())) {
                            Map<String, Object> match = new LinkedHashMap<>();
                            match.put("kind", "dimension");
                            match.put("name", e.name());
                            match.put("domain", domain);
                            match.put("label_zh", e.labelZh());
                            match.put("label_en", e.labelEn());
                            match.put("dim_type", e.type());
                            matches.add(match);
                        }
                    }
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("matches", matches);
            result.put("total", matches.size());
            return CompletableFuture.completedFuture(result);
        } catch (RuntimeException ex) {
            log.error("semantic_lookup failed: connectionId={} query={}", connectionId, query, ex);
            String detail = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            DataTalkException wrapped = new DataTalkException("semantic.lookup_failed", detail, false);
            wrapped.initCause(ex);
            throw wrapped;
        }
    }

    private static boolean matchesQuery(String query, String... fields) {
        for (String f : fields) {
            if (f != null && f.toLowerCase().contains(query)) return true;
        }
        return false;
    }
}
