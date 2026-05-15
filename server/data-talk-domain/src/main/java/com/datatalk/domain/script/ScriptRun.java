package com.datatalk.domain.script;

import java.time.Instant;

public record ScriptRun(
    String id,
    String scriptContent,
    ScriptLanguage language,
    ScriptStatus status,
    Integer exitCode,
    String stdoutText,
    String connectionId,
    String targetTable,
    int rowsWritten,
    String name,
    String createdByKind,
    String createdBySessionId,
    String errorMessage,
    Instant startedAt,
    Instant finishedAt,
    Long durationMs
) {}
