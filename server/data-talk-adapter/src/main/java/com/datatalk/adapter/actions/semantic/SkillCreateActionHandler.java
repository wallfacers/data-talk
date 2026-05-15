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
    id = "datatalk.skill_create",
    executor = Executor.SERVER,
    description = "Create a new business domain semantic model (skill). This is the primary output channel for the skill-creator. Writes to pending/ for user review.",
    timeoutMs = 10_000,
    requiresConnection = true,
    riskLevel = { RiskLevel.L1 },
    category = { Category.MUTATION }
)
public class SkillCreateActionHandler implements ActionHandler<Map, Map> {

    private static final Logger log = LoggerFactory.getLogger(SkillCreateActionHandler.class);
    private final SemanticModelRepository repository;
    private final ApplicationEventPublisher events;

    public SkillCreateActionHandler(SemanticModelRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("name", "yaml_text"),
            "properties", Map.of(
                "name", Map.of("type", "string", "description", "Business domain name, e.g. 'subscriptions'"),
                "yaml_text", Map.of("type", "string", "description", "Full Semantic Model YAML content for this domain")
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "proposalId", Map.of("type", "string"),
                "name", Map.of("type", "string"),
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

        String name = ((String) input.get("name")).toLowerCase().replaceAll("[^a-z0-9_]", "_");
        String yamlText = (String) input.get("yaml_text");

        if (yamlText == null || yamlText.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "SCHEMA_VALIDATION_FAILED");
            error.put("message", "yaml_text must not be empty");
            return CompletableFuture.completedFuture(error);
        }

        // Ensure yaml contains the required authored_by field for AI-inferred models
        if (!yamlText.contains("authored_by:")) {
            yamlText = yamlText.stripTrailing() + "\nauthored_by: ai_inferred\n";
        }

        try {
            repository.savePending(connectionId, name, yamlText);
            log.info("Skill created as pending proposal: {}/pending/{}", connectionId, name);
            events.publishEvent(new DtEvent.SemanticPendingCreated(connectionId, name, "Skill created by AI"));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("proposalId", name);
            result.put("name", name);
            result.put("status", "pending_review");
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("Failed to create skill {}: {}", name, e.getMessage());
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "SCHEMA_VALIDATION_FAILED");
            error.put("message", e.getMessage());
            return CompletableFuture.completedFuture(error);
        }
    }
}
