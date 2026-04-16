package com.datatalk.adapter.actions;

import com.datatalk.domain.action.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * CLIENT-executor action. The handler must not be called by the dispatcher
 * (dispatch routes CLIENT executors through SessionBus.invoke); if it is,
 * surface a clear error.
 */
@org.springframework.stereotype.Component
@DataTalkAction(
    id = "datatalk.pin_artifact",
    executor = Executor.CLIENT,
    description = "Pin an artifact in the client's timeline so it survives scroll-away.",
    timeoutMs = 2_000
)
public class PinArtifactAction implements ActionHandler<Map, Map> {

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "required", List.of("artifactId"),
            "properties", Map.of("artifactId", Map.of("type", "string")));
    }
    @Override public Map<String, Object> outputSchema() {
        return Map.of("type", "object", "required", List.of("pinned"),
            "properties", Map.of("pinned", Map.of("type", "boolean")));
    }
    @Override public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.PATCH_ARTIFACT); }
    @Override public Class<Map> inputType() { return Map.class; }

    @Override
    public CompletionStage<Map> handle(com.datatalk.domain.action.ActionContext ctx, Map input) {
        throw new UnsupportedOperationException("pin_artifact runs on client; dispatcher must not call handler");
    }
}
