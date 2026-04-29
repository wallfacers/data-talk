package com.datatalk.domain.diagnostics;

import java.util.List;

public record OptimizeTablePreview(
    String engine,
    String table,
    String schemaName,
    String willRunSql,
    Long currentDataFree,
    Long currentTotalSize,
    List<DiagnosticRecommendation> recommendations
) {}
