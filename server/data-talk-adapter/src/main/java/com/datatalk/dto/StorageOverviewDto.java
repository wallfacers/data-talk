package com.datatalk.dto;

import java.util.Map;

public record StorageOverviewDto(
        String workdir,
        long totalBytes,
        Map<String, BreakdownItem> breakdown,
        String lastHousekeepingRunAt
) {
    public record BreakdownItem(long bytes, String label) {}
}
