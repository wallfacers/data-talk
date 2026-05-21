package com.datatalk.adapter.actions.semantic;

import com.datatalk.application.semantic.SemanticModelRepository;
import com.datatalk.domain.action.*;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.semantic.VerifiedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.verified_query_record",
    executor = Executor.SERVER,
    description = "Record a user-confirmed question-SQL pair as a verified query. Call this when the user marks an AI-generated SQL as correct.",
    timeoutMs = 5_000,
    requiresConnection = true,
    riskLevel = { RiskLevel.L1 },
    category = { Category.MUTATION }
)
public class VerifiedQueryRecordActionHandler implements ActionHandler<Map, Map> {

    private static final Logger log = LoggerFactory.getLogger(VerifiedQueryRecordActionHandler.class);
    private final SemanticModelRepository repository;
    private final ApplicationEventPublisher events;

    public VerifiedQueryRecordActionHandler(SemanticModelRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("question", "sql", "modelRef"),
            "properties", Map.of(
                "question", Map.of("type", "string", "description", "The natural language question"),
                "sql", Map.of("type", "string", "description", "The confirmed SQL"),
                "modelRef", Map.of("type", "string", "description", "Domain model reference, e.g. 'orders'")
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "id", Map.of("type", "string"),
                "question", Map.of("type", "string")
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
            error.put("message", "No active connection bound to the current session.");
            return CompletableFuture.completedFuture(error);
        }

        String question = (String) input.get("question");
        String sql = (String) input.get("sql");
        String modelRef = (String) input.get("modelRef");
        Instant now = Instant.now();
        String id = "vq_" + modelRef + "_" + now.toEpochMilli();

        VerifiedQuery vq = new VerifiedQuery(id, question, sql, modelRef, 1, now, "user", now, false);
        String vqId = repository.recordVerifiedQuery(connectionId, vq);
        log.info("Verified query recorded: {} (connection={}, modelRef={})", vqId, connectionId, modelRef);

        events.publishEvent(new DtEvent.VerifiedQueryRecorded(vqId, question, modelRef));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", vqId);
        result.put("question", question);
        return CompletableFuture.completedFuture(result);
    }
}
