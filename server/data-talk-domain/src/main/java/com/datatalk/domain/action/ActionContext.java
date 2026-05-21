package com.datatalk.domain.action;

public record ActionContext(
    String sessionId,
    String callId,
    String connectionId,
    String openCodeSessionId,
    ActionExecutionMetadata metadata
) {

    public ActionContext(String sessionId, String callId, String connectionId, String openCodeSessionId) {
        this(sessionId, callId, connectionId, openCodeSessionId, ActionExecutionMetadata.empty());
    }
}
