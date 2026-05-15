package com.datatalk.adapter.actions.semantic;

import com.datatalk.application.semantic.SemanticModelRepository;
import com.datatalk.domain.action.*;
import com.datatalk.domain.semantic.SemanticModel;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk_semantic_lookup",
    executor = Executor.SERVER,
    description = "Search semantic model entities, dimensions, measures, or metrics by name or label. Use this to understand business terminology before writing SQL.",
    timeoutMs = 5_000,
    requiresConnection = true,
    riskLevel = { RiskLevel.L1 },
    category = { Category.METADATA }
)
public class SemanticLookupActionHandler implements ActionHandler<Map, Map> {

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
        String query = ((String) input.get("query")).toLowerCase();
        String kind = (String) input.getOrDefault("kind", null);
        String connectionId = ctx.connectionId();

        List<Map<String, Object>> matches = new ArrayList<>();
        for (String domain : repository.listDomains(connectionId)) {
            var modelOpt = repository.loadDomain(connectionId, domain);
            if (modelOpt.isEmpty()) continue;
            SemanticModel m = modelOpt.get();

            if (kind == null || "entity".equals(kind)) {
                for (var e : m.entities()) {
                    if (matchesQuery(query, e.name(), e.description())) {
                        matches.add(Map.of("kind", "entity", "name", e.name(), "domain", domain,
                            "table", e.physical().table(), "type", e.type()));
                    }
                }
            }
            if (kind == null || "measure".equals(kind)) {
                for (var e : m.measures()) {
                    if (matchesQuery(query, e.name(), e.labelZh(), e.labelEn(), e.description())) {
                        matches.add(Map.of("kind", "measure", "name", e.name(), "domain", domain,
                            "label_zh", e.labelZh(), "label_en", e.labelEn(), "agg", e.agg()));
                    }
                }
            }
            if (kind == null || "metric".equals(kind)) {
                for (var e : m.metrics()) {
                    if (matchesQuery(query, e.name(), e.labelZh(), e.labelEn(), e.description())) {
                        matches.add(Map.of("kind", "metric", "name", e.name(), "domain", domain,
                            "label_zh", e.labelZh(), "label_en", e.labelEn(), "type", e.type()));
                    }
                }
            }
            if (kind == null || "dimension".equals(kind)) {
                for (var e : m.dimensions()) {
                    if (matchesQuery(query, e.name(), e.labelZh(), e.labelEn(), e.description())) {
                        matches.add(Map.of("kind", "dimension", "name", e.name(), "domain", domain,
                            "label_zh", e.labelZh(), "label_en", e.labelEn(), "dim_type", e.type()));
                    }
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matches", matches);
        result.put("total", matches.size());
        return CompletableFuture.completedFuture(result);
    }

    private static boolean matchesQuery(String query, String... fields) {
        for (String f : fields) {
            if (f != null && f.toLowerCase().contains(query)) return true;
        }
        return false;
    }
}
