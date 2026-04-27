package com.datatalk.application.stage;

import com.datatalk.domain.stage.StageTabContent;

import java.util.ArrayList;
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
            totalMatched, totalMatched, false, List.of(), List.of());
    }

    public static StageFindResult tabsOnly(List<String> tabIds, int totalMatched, boolean truncated) {
        return new StageFindResult(
            StageFindQuery.OutputMode.TABS_ONLY, List.of(), tabIds,
            totalMatched, tabIds.size(), truncated, List.of(), List.of());
    }

    /**
     * Return a new instance with the reads field replaced.
     */
    public StageFindResult withReads(List<StageTabContent> reads) {
        return withReadsAndWarnings(reads, List.of());
    }

    /**
     * Return a new instance with reads replaced and additional warnings appended.
     */
    public StageFindResult withReadsAndWarnings(List<StageTabContent> reads, List<String> additionalWarnings) {
        var mergedWarnings = new ArrayList<>(warnings);
        mergedWarnings.addAll(additionalWarnings);
        return new StageFindResult(
            outputMode, items, tabIds, totalMatched, tabsMatched,
            truncated, reads, List.copyOf(mergedWarnings));
    }

    /**
     * Return a new instance with additional warnings appended.
     */
    public StageFindResult withWarnings(List<String> additionalWarnings) {
        var mergedWarnings = new ArrayList<>(warnings);
        mergedWarnings.addAll(additionalWarnings);
        return new StageFindResult(
            outputMode, items, tabIds, totalMatched, tabsMatched,
            truncated, reads, List.copyOf(mergedWarnings));
    }
}
