package com.datatalk.domain.diagnostics;

import java.util.List;

public record SpaceReport(
    List<TableSpaceEntry> tables,
    List<DiagnosticRecommendation> recommendations
) {
    public record TableSpaceEntry(
        String table,
        String schemaName,
        long rowCount,
        long dataSizeBytes,
        long indexSizeBytes,
        Long freeSpaceBytes
    ) {}
}
