package com.datatalk.application.stage;

import com.datatalk.domain.stage.StageTabContent;

import java.util.List;
import java.util.Map;

/**
 * Result model for the ui_find action.
 */
public record StageFindResult(
    StageFindQuery.OutputMode outputMode,
    List<Map<String, Object>> items,
    List<String> tabIds,
    int totalMatched,
    int tabsMatched,
    boolean truncated,
    List<StageTabContent> reads,
    List<String> warnings
) {
    public static StageFindResult metadata(List<Map<String, Object>> items, int totalMatched, boolean truncated) {
        return new StageFindResult(
            StageFindQuery.OutputMode.METADATA, items, List.of(),
            totalMatched, items.size(), truncated, List.of(), List.of());
    }

    public static StageFindResult count(int totalMatched) {
        return new StageFindResult(
            StageFindQuery.OutputMode.COUNT, List.of(), List.of(),
            totalMatched, 0, false, List.of(), List.of());
    }

    public static StageFindResult tabsOnly(List<String> tabIds, int totalMatched, boolean truncated) {
        return new StageFindResult(
            StageFindQuery.OutputMode.TABS_ONLY, List.of(), tabIds,
            totalMatched, tabIds.size(), truncated, List.of(), List.of());
    }
}
