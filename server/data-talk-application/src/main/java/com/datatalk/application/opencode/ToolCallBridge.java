package com.datatalk.application.opencode;

import com.datatalk.application.session.ActionDispatcher;
import com.datatalk.domain.action.ActionContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionStage;

/**
 * Bridges OpenCode tool-call HTTP callbacks into our {@link ActionDispatcher}.
 * Resolves the session context via {@link OpenCodeSessionMap} and then hands
 * off to the dispatcher, which decides where the handler actually runs.
 */
@Component
public class ToolCallBridge {

    private final ActionDispatcher dispatcher;
    private final OpenCodeSessionMap map;

    public ToolCallBridge(ActionDispatcher dispatcher, OpenCodeSessionMap map) {
        this.dispatcher = dispatcher;
        this.map = map;
    }

    public CompletionStage<Object> handle(String actionId, String callId,
                                          String openCodeSessionId, Object input) {
        String dtSessionId = map.dataTalkFor(openCodeSessionId);
        if (dtSessionId == null) {
            throw new IllegalStateException(
                "unknown OpenCode session: " + openCodeSessionId);
        }
        ActionContext ctx = new ActionContext(dtSessionId, callId, null, openCodeSessionId);
        return dispatcher.dispatch(actionId, input, callId, ctx);
    }
}
