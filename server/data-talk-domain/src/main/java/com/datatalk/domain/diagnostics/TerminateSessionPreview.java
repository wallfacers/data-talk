package com.datatalk.domain.diagnostics;

public record TerminateSessionPreview(
    String engine,
    String sessionId,
    String willRunSql,
    String currentSql
) {}
