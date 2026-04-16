package com.datatalk.adapter.actions;

import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Minimal demo Action that proves the Action Registry -> OpenCode -> ToolCallBridge
 * -> ActionDispatcher loop end to end. Takes a {@code text} field and returns
 * its reversed form under {@code reversed}.
 */
@Component
@DataTalkAction(
    id = "datatalk.demo.echo",
    executor = Executor.SERVER,
    description = "Return the reverse of the provided text. Used for smoke testing.",
    timeoutMs = 3_000
)
public class DemoEchoAction implements ActionHandler<Map, Map> {

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("text"),
            "properties", Map.of("text", Map.of("type", "string"))
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("reversed"),
            "properties", Map.of("reversed", Map.of("type", "string"))
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
        String text = String.valueOf(input.get("text"));
        String reversed = new StringBuilder(text).reverse().toString();
        return CompletableFuture.completedFuture(Map.of("reversed", reversed));
    }
}
