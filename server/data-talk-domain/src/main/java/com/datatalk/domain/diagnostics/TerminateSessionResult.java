package com.datatalk.domain.diagnostics;

public record TerminateSessionResult(
    boolean ok,
    String sessionId,
    String message
) {}
