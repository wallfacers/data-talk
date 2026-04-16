package com.datatalk.application.session;

import com.datatalk.application.persistence.ActionInvocationRepository;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.registry.JsonSchemaLoader;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionDescriptor;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.event.DtEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
public class ActionDispatcher {

    private final ActionRegistry registry;
    private final JsonSchemaLoader schemas;
    private final SessionBusRegistry buses;
    private final ActionInvocationRepository invocations;
    private final ArtifactRepository artifacts;
    private final PendingCallRegistry pending;
    private final ObjectMapper om;
    private final Clock clock;

    public ActionDispatcher(ActionRegistry registry, JsonSchemaLoader schemas,
                            SessionBusRegistry buses, ActionInvocationRepository invocations,
                            ArtifactRepository artifacts, PendingCallRegistry pending,
                            ObjectMapper om, Clock clock) {
        this.registry = registry;
        this.schemas = schemas;
        this.buses = buses;
        this.invocations = invocations;
        this.artifacts = artifacts;
        this.pending = pending;
        this.om = om;
        this.clock = clock;
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    public CompletionStage<Object> dispatch(String actionId, Object input,
                                            String callId, ActionContext ctx) {
        ActionDescriptor desc = registry.require(actionId);
        validate(desc.inputSchema(), input, "input", actionId);

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
                yield handler.handle(ctx, input).thenApply(output -> {
                    validate(desc.outputSchema(), output, "output", actionId);
                    applyEffects(desc, output, ctx, bus);
                    try {
                        invocations.complete(callId, om.writeValueAsString(output), clock.millis());
                    } catch (Exception ignore) {}
                    return output;
                }).whenComplete((res, err) -> {
                    if (err != null) {
                        try {
                            invocations.fail(callId, om.writeValueAsString(
                                Map.of("message", err.getMessage())), clock.millis());
                        } catch (Exception ignore) {}
                    }
                });
            }
            case OPENCODE -> {
                ActionHandler<Object, Object> handler = (ActionHandler<Object, Object>) registry.handler(actionId);
                yield handler.handle(ctx, input).thenApply(output -> {
                    validate(desc.outputSchema(), output, "output", actionId);
                    try {
                        invocations.complete(callId, om.writeValueAsString(output), clock.millis());
                    } catch (Exception ignore) {}
                    return output;
                });
            }
            case CLIENT -> {
                CompletableFuture<Object> fut = new CompletableFuture<>();
                pending.register(callId, fut, desc.timeoutMs());
                bus.publish(new DtEvent.ActionInvoke(callId, actionId,
                    (Map<String, Object>) input, desc.timeoutMs()));
                yield fut.whenComplete((res, err) -> {
                    long t = clock.millis();
                    if (err != null) {
                        try {
                            invocations.fail(callId, om.writeValueAsString(
                                Map.of("message", err.getMessage())), t);
                        } catch (Exception ignore) {}
                    } else {
                        try {
                            invocations.complete(callId, om.writeValueAsString(res), t);
                        } catch (Exception ignore) {}
                    }
                });
            }
        };
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
                    bus.publish(new DtEvent.OntologyUpdated(artifactId, version));
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
}
