package com.datatalk.dto;

import java.util.Map;

public record StorageOverviewDto(
        String workdir,
        long totalBytes,
        Map<String, BreakdownItem> breakdown,
        String lastHousekeepingRunAt,
        Map<String, ResourceDirSummary> resourceDirectories
) {
    public record BreakdownItem(long bytes, String label) {}
    public record ResourceDirSummary(long count, long sizeBytes) {}
}
