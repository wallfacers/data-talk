package com.datatalk.adapter.actions.semantic;

import com.datatalk.application.semantic.SemanticModelRepository;
import com.datatalk.domain.action.*;
import com.datatalk.domain.event.DtEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk_semantic_propose_change",
    executor = Executor.SERVER,
    description = "Propose a new or modified semantic model YAML. Writes to pending/ for user review. Use this when AI infers a new domain or the user asks to create a semantic model.",
    timeoutMs = 10_000,
    requiresConnection = true,
    riskLevel = { RiskLevel.L1 },
    category = { Category.MUTATION }
)
public class SemanticProposeChangeActionHandler implements ActionHandler<Map, Map> {

    private static final Logger log = LoggerFactory.getLogger(SemanticProposeChangeActionHandler.class);
    private final SemanticModelRepository repository;
    private final ApplicationEventPublisher events;

    public SemanticProposeChangeActionHandler(SemanticModelRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("domain", "yaml_text", "reason"),
            "properties", Map.of(
                "domain", Map.of("type", "string", "description", "Domain name, e.g. 'orders'"),
                "yaml_text", Map.of("type", "string", "description", "Full Semantic Model YAML content"),
                "reason", Map.of("type", "string", "description", "Why this change is proposed")
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "proposalId", Map.of("type", "string"),
                "domain", Map.of("type", "string"),
                "status", Map.of("type", "string")
            )
        );
    }

    @Override
    public List<OntologyEffect> sideEffects() {
        return List.of(OntologyEffect.CREATE_ARTIFACT);
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
            error.put("message", "No active connection bound to the current session. 请先选择一个数据库连接。");
            return CompletableFuture.completedFuture(error);
        }

        String domain = ((String) input.get("domain")).toLowerCase().replaceAll("[^a-z0-9_]", "_");
        String yamlText = (String) input.get("yaml_text");
        String reason = (String) input.get("reason");

        // Basic validation: yaml_text must not be empty
        if (yamlText == null || yamlText.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "SCHEMA_VALIDATION_FAILED");
            error.put("message", "yaml_text must not be empty");
            return CompletableFuture.completedFuture(error);
        }

        try {
            repository.savePending(connectionId, domain, yamlText);
            log.info("Semantic model proposal saved: {}/pending/{} (reason: {})", connectionId, domain, reason);
            events.publishEvent(new DtEvent.SemanticPendingCreated(connectionId, domain, reason));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("proposalId", domain);
            result.put("domain", domain);
            result.put("status", "pending_review");
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("Failed to save proposal {}/{}: {}", connectionId, domain, e.getMessage());
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "SCHEMA_VALIDATION_FAILED");
            error.put("message", e.getMessage());
            return CompletableFuture.completedFuture(error);
        }
    }
}
