package com.datatalk.adapter.actions.semantic;

import com.datatalk.application.semantic.SemanticModelRepository;
import com.datatalk.application.semantic.VerifiedQueryRouter;
import com.datatalk.domain.action.*;
import com.datatalk.domain.semantic.VerifiedQuery;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk_verified_query_find",
    executor = Executor.SERVER,
    description = "Search verified queries (L0 exact match, L1 normalized match, L2 top-K candidates). Always call this before writing SQL — it may return a cached, human-confirmed answer.",
    timeoutMs = 5_000,
    requiresConnection = true,
    riskLevel = { RiskLevel.L1 },
    category = { Category.METADATA }
)
public class VerifiedQueryFindActionHandler implements ActionHandler<Map, Map> {

    private final SemanticModelRepository repository;
    private final VerifiedQueryRouter router;

    public VerifiedQueryFindActionHandler(SemanticModelRepository repository, VerifiedQueryRouter router) {
        this.repository = repository;
        this.router = router;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("question"),
            "properties", Map.of(
                "question", Map.of("type", "string", "description", "The user's natural language question"),
                "topK", Map.of("type", "integer", "minimum", 1, "maximum", 20, "default", 5,
                    "description", "Number of top candidates to return")
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "hit", Map.of("type", "boolean"),
                "layer", Map.of("type", "string", "enum", List.of("L0", "L1", "L2", "NONE")),
                "candidates", Map.of("type", "array")
            )
        );
    }

    @Override
    public List<OntologyEffect> sideEffects() {
        return List.of(OntologyEffect.NONE);
    }

    @Override
    public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String question = (String) input.get("question");
        int topK = input.containsKey("topK") ? ((Number) input.get("topK")).intValue() : 5;
        String connectionId = ctx.connectionId();

        Map<String, Object> result = new LinkedHashMap<>();

        // L0: exact match
        Optional<VerifiedQuery> l0 = router.findExact(connectionId, question);
        if (l0.isPresent()) {
            repository.incHit(connectionId, l0.get().id());
            result.put("hit", true);
            result.put("layer", "L0");
            result.put("candidates", List.of(toMap(l0.get())));
            return CompletableFuture.completedFuture(result);
        }

        // L1: normalized match
        Optional<VerifiedQuery> l1 = router.findNormalized(connectionId, question);
        if (l1.isPresent()) {
            repository.incHit(connectionId, l1.get().id());
            result.put("hit", true);
            result.put("layer", "L1");
            result.put("candidates", List.of(toMap(l1.get())));
            return CompletableFuture.completedFuture(result);
        }

        // L2: top-K candidates
        List<VerifiedQuery> l2 = router.topKByHits(connectionId, topK);
        result.put("hit", false);
        result.put("layer", l2.isEmpty() ? "NONE" : "L2");
        result.put("candidates", l2.stream().map(this::toMap).toList());
        return CompletableFuture.completedFuture(result);
    }

    private Map<String, Object> toMap(VerifiedQuery vq) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", vq.id());
        m.put("question", vq.question());
        m.put("sql", vq.sql());
        m.put("modelRef", vq.modelRef());
        m.put("hitCount", vq.hitCount());
        m.put("stale", vq.stale());
        return m;
    }
}
