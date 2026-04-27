package com.datatalk.domain.diagnostics;

import java.util.List;

public record SpaceReport(List<TableSpaceEntry> tables) {
    public record TableSpaceEntry(String table, long rowCount, long dataSizeBytes, long indexSizeBytes) {}
}
