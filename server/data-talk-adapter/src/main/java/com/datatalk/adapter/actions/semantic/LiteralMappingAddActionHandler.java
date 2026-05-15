package com.datatalk.adapter.actions.semantic;

import com.datatalk.application.semantic.SemanticModelRepository;
import com.datatalk.domain.action.*;
import com.datatalk.domain.semantic.PatchOp;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.literal_mapping_add",
    executor = Executor.SERVER,
    description = "Add a natural-language-to-database-value mapping for a dimension. E.g. map '已完成' to 'COMPLETED' for the order_status dimension.",
    timeoutMs = 5_000,
    requiresConnection = true,
    riskLevel = { RiskLevel.L1 },
    category = { Category.MUTATION }
)
public class LiteralMappingAddActionHandler implements ActionHandler<Map, Map> {

    private final SemanticModelRepository repository;

    public LiteralMappingAddActionHandler(SemanticModelRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("domain", "dimension", "natural", "dbValue"),
            "properties", Map.of(
                "domain", Map.of("type", "string", "description", "Domain name, e.g. 'orders'"),
                "dimension", Map.of("type", "string", "description", "Dimension name, e.g. 'order_status'"),
                "natural", Map.of("type", "string", "description", "Natural language value, e.g. '已完成'"),
                "dbValue", Map.of("type", "string", "description", "Database value, e.g. 'COMPLETED'")
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "dimension", Map.of("type", "string"),
                "mapping", Map.of("type", "object")
            )
        );
    }

    @Override
    public List<OntologyEffect> sideEffects() {
        return List.of(OntologyEffect.PATCH_ARTIFACT);
    }

    @Override
    public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String connectionId = ctx.connectionId();
        if (connectionId == null || connectionId.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "NO_ACTIVE_CONNECTION");
            error.put("message", "No active connection bound to the current session.");
            return CompletableFuture.completedFuture(error);
        }

        String domain = (String) input.get("domain");
        String dimension = (String) input.get("dimension");
        String natural = (String) input.get("natural");
        String dbValue = (String) input.get("dbValue");

        Map<String, String> map = Map.of(natural, dbValue);
        PatchOp patch = new PatchOp.AddLiteralMapping("ADD_LITERAL_MAPPING", dimension, map, "ai", Instant.now());
        repository.appendPatch(connectionId, domain, patch);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimension", dimension);
        result.put("mapping", map);
        return CompletableFuture.completedFuture(result);
    }
}
