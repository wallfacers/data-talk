package com.datatalk.domain.diagnostics;

import java.util.List;

public record LockReport(
    List<LockEntry> blockingChain,
    List<DiagnosticRecommendation> recommendations
) {
    public record LockEntry(
        String table,
        String lockType,
        String holderId,
        String waiterId,
        Long waitMillis,
        String holderSql,
        String waiterSql
    ) {}
}
