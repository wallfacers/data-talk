package com.datatalk.domain.er;

public record SkippedOp(
    String opType,
    String table,
    String column,
    String reason,
    String hint
) {}
