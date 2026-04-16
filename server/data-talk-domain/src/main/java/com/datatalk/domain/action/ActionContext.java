package com.datatalk.domain.action;

public record ActionContext(String sessionId, String callId, String connectionId, String openCodeSessionId) {}
