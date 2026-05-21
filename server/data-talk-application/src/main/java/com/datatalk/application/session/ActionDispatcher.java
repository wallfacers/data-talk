package com.datatalk.application.session;

import com.datatalk.application.persistence.ActionInvocationRepository;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.registry.JsonSchemaLoader;
import com.datatalk.application.sql.SqlBearingActionInspector;
import com.datatalk.application.sql.SqlRiskAnalysis;
import com.datatalk.application.sql.SqlRiskAnalyzer;
import com.datatalk.domain.action.ActionExecutionMetadata;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionDescriptor;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.action.SqlExecutionRisk;
import com.datatalk.domain.event.DtEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
public class ActionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ActionDispatcher.class);

    private final ActionRegistry registry;
    private final JsonSchemaLoader schemas;
    private final SessionBusRegistry buses;
    private final ActionInvocationRepository invocations;
    private final ArtifactRepository artifacts;
    private final PendingCallRegistry pending;
    private final SqlRiskAnalyzer sqlRiskAnalyzer;
    private final SqlBearingActionInspector sqlInspector;
    private final ConnectionRepository connections;
    private final ObjectMapper om;
    private final Clock clock;

    public ActionDispatcher(ActionRegistry registry, JsonSchemaLoader schemas,
                            SessionBusRegistry buses, ActionInvocationRepository invocations,
                            ArtifactRepository artifacts, PendingCallRegistry pending,
                            SqlRiskAnalyzer sqlRiskAnalyzer, SqlBearingActionInspector sqlInspector,
                            ConnectionRepository connections,
                            ObjectMapper om, Clock clock) {
        this.registry = registry;
        this.schemas = schemas;
        this.buses = buses;
        this.invocations = invocations;
        this.artifacts = artifacts;
        this.pending = pending;
        this.sqlRiskAnalyzer = sqlRiskAnalyzer;
        this.sqlInspector = sqlInspector;
        this.connections = connections;
        this.om = om;
        this.clock = clock;
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    public CompletionStage<Object> dispatch(String actionId, Object input,
                                            String callId, ActionContext ctx) {
        ActionDescriptor desc = registry.require(actionId);
        validate(desc.inputSchema(), input, "input", actionId);
        ActionContext execCtx = enrichContext(desc, input, ctx);

        long now = clock.millis();
        try {
            invocations.start(callId, ctx.sessionId(), actionId, om.writeValueAsString(input), now);
        } catch (Exception e) {
            return CompletableFuture.failedStage(new IllegalStateException("cannot persist invocation", e));
        }

        SessionBus bus = buses.getOrCreate(ctx.sessionId());

        return switch (desc.executor()) {
            case SERVER -> {
                ActionHandler<Object, Object> handler = (ActionHandler<Object, Object>) registry.handler(actionId);
                yield handler.handle(execCtx, input).thenApply(output -> {
                    validate(desc.outputSchema(), output, "output", actionId);
                    applyEffects(desc, output, execCtx, bus);
                    try {
                        invocations.complete(callId, om.writeValueAsString(output), clock.millis());
                    } catch (Exception e) {
                        log.warn("Failed to record action completion for callId={}", callId, e);
                    }
                    return output;
                }).whenComplete((res, err) -> {
                    if (err != null) {
                        try {
                            invocations.fail(callId, om.writeValueAsString(
                                Map.of("message", err.getMessage())), clock.millis());
                        } catch (Exception e) {
                            log.warn("Failed to record action failure for callId={}", callId, e);
                        }
                    }
                });
            }
            case OPENCODE -> {
                ActionHandler<Object, Object> handler = (ActionHandler<Object, Object>) registry.handler(actionId);
                yield handler.handle(execCtx, input).thenApply(output -> {
                    validate(desc.outputSchema(), output, "output", actionId);
                    try {
                        invocations.complete(callId, om.writeValueAsString(output), clock.millis());
                    } catch (Exception e) {
                        log.warn("Failed to record opencode action completion for callId={}", callId, e);
                    }
                    return output;
                });
            }
            case CLIENT -> {
                if (!bus.hasSubscribers()) {
                    CompletableFuture<Object> failed = CompletableFuture.failedFuture(
                        new NoClientSubscriberException("no client subscriber"));
                    yield failed.whenComplete((res, err) -> recordClientOutcome(callId, res, err));
                }
                CompletableFuture<Object> fut = new CompletableFuture<>();
                pending.register(callId, fut, desc.timeoutMs());
                bus.publish(new DtEvent.ActionInvoke(callId, actionId,
                    (Map<String, Object>) input, desc.timeoutMs()));
                yield fut.whenComplete((res, err) -> recordClientOutcome(callId, res, err));
            }
        };
    }

    private void recordClientOutcome(String callId, Object result, Throwable error) {
        long t = clock.millis();
        if (error != null) {
            try {
                invocations.fail(callId, om.writeValueAsString(
                    Map.of("message", error.getMessage())), t);
            } catch (Exception e) {
                log.warn("Failed to record client action failure for callId={}", callId, e);
            }
            return;
        }
        try {
            invocations.complete(callId, om.writeValueAsString(result), t);
        } catch (Exception e) {
            log.warn("Failed to record client action completion for callId={}", callId, e);
        }
    }

    private ActionContext enrichContext(ActionDescriptor desc, Object input, ActionContext ctx) {
        return sqlInspector.extractSql(input)
            .map(sql -> {
                SqlRiskAnalysis analysis = sqlRiskAnalyzer.analyze(
                    sql,
                    desc.category(),
                    resolveConnectionKind(input, ctx)
                );
                SqlExecutionRisk risk = new SqlExecutionRisk(
                    analysis.riskLevel(),
                    analysis.reason(),
                    analysis.requiresStrongConfirmation(),
                    analysis.fallbackUsed()
                );
                return new ActionContext(
                    ctx.sessionId(),
                    ctx.callId(),
                    ctx.connectionId(),
                    ctx.openCodeSessionId(),
                    new ActionExecutionMetadata(risk, ctx.metadata().callerKind())
                );
            })
            .orElse(ctx);
    }

    private String resolveConnectionKind(Object input, ActionContext ctx) {
        String connectionId = extractConnectionId(input);
        if (connectionId == null || connectionId.isBlank()) {
            connectionId = ctx.connectionId();
        }
        if (connectionId == null || connectionId.isBlank()) {
            return null;
        }
        return connections.findById(connectionId)
            .map(connection -> connection.kind())
            .orElse(null);
    }

    private String extractConnectionId(Object input) {
        if (!(input instanceof Map<?, ?> map)) {
            return null;
        }
        Object connectionId = map.get("connectionId");
        if (!(connectionId instanceof String value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validate(Map<String, Object> schema, Object data, String label, String actionId) {
        JsonSchemaLoader.Result r = schemas.validate(schema, data);
        if (!r.valid()) {
            throw new SchemaValidationException(actionId, label, r.errors());
        }
    }

    @SuppressWarnings("unchecked")
    private void applyEffects(ActionDescriptor desc, Object output,
                              ActionContext ctx, SessionBus bus) {
        if (desc.sideEffects().contains(OntologyEffect.CREATE_ARTIFACT)
                || desc.sideEffects().contains(OntologyEffect.PATCH_ARTIFACT)) {
            if (output instanceof Map<?, ?> m) {
                Object artifactIdObj = m.get("artifactId");
                if (artifactIdObj instanceof String artifactId) {
                    Object versionObj = m.get("version");
                    int version = versionObj instanceof Number n ? ((Number) versionObj).intValue() : 1;
                    bus.publish(new DtEvent.OntologyUpdated(
                        "datatalk.artifact", artifactId, "upsert",
                        Map.of("version", version, "producedBy", ctx.callId(), "full", m)));
                }
            }
        }
    }

    public static class SchemaValidationException extends RuntimeException {
        public final String actionId;
        public final String label;
        public final java.util.List<String> errors;
        public SchemaValidationException(String actionId, String label, java.util.List<String> errors) {
            super(actionId + "." + label + " invalid: " + errors);
            this.actionId = actionId;
            this.label = label;
            this.errors = errors;
        }
    }

    public static class NoClientSubscriberException extends RuntimeException {
        public NoClientSubscriberException(String message) {
            super(message);
        }
    }
}
