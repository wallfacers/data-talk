package com.datatalk.domain.diagnostics;

public record OptimizeTableResult(
    boolean ok,
    String table,
    String schemaName,
    Long durationMs,
    Long reclaimedBytes,
    String message
) {}
