package com.datatalk.application.dashboard;

import com.datatalk.domain.dashboard.ChartSemantics;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OptionMergerTest {

    // ---- deepMerge tests ----

    @Test
    void deepMerge_objectRecursiveMerge() {
        Map<String, Object> base = Map.of("tooltip",
            new LinkedHashMap<>(Map.of("trigger", "axis")));
        Map<String, Object> override = Map.of("tooltip",
            new LinkedHashMap<>(Map.of("show", false)));

        Map<String, Object> result = OptionMerger.deepMerge(base, override);

        @SuppressWarnings("unchecked")
        Map<String, Object> tooltip = (Map<String, Object>) result.get("tooltip");
        assertThat(tooltip).containsEntry("trigger", "axis");
        assertThat(tooltip).containsEntry("show", false);
    }

    @Test
    void deepMerge_arrayWholeReplacement() {
        Map<String, Object> base = Map.of("color",
            new LinkedHashMap<>(Map.of("items", List.of("#a", "#b", "#c"))));
        Map<String, Object> override = Map.of("color",
            new LinkedHashMap<>(Map.of("items", List.of("#x"))));

        Map<String, Object> result = OptionMerger.deepMerge(base, override);

        @SuppressWarnings("unchecked")
        Map<String, Object> color = (Map<String, Object>) result.get("color");
        assertThat(color.get("items")).isEqualTo(List.of("#x"));
    }

    @Test
    void deepMerge_scalarOverride() {
        Map<String, Object> base = Map.of("animate", true);
        Map<String, Object> override = Map.of("animate", false);

        Map<String, Object> result = OptionMerger.deepMerge(base, override);
        assertThat(result).containsEntry("animate", false);
    }

    @Test
    void deepMerge_emptyOverrideReturnsBase() {
        Map<String, Object> base = Map.of("key", "value");
        Map<String, Object> result = OptionMerger.deepMerge(base, Map.of());
        assertThat(result).containsEntry("key", "value");
    }

    @Test
    void deepMerge_nullOverrideReturnsBase() {
        Map<String, Object> base = Map.of("key", "value");
        Map<String, Object> result = OptionMerger.deepMerge(base, null);
        assertThat(result).containsEntry("key", "value");
    }

    @Test
    void deepMerge_newKeyAdded() {
        Map<String, Object> base = Map.of("a", 1);
        Map<String, Object> override = Map.of("b", 2);

        Map<String, Object> result = OptionMerger.deepMerge(base, override);
        assertThat(result).containsEntry("a", 1).containsEntry("b", 2);
    }

    // ---- merge (three-layer) tests ----

    @Test
    void merge_nullSemanticsNullChartType_returnsEmpty() {
        Map<String, Object> result = OptionMerger.merge(null, null, null);
        assertThat(result).isEmpty();
    }

    @Test
    void merge_nullSemantics_returnsLayer1Defaults() {
        // "bar" has a known default option file on classpath
        Map<String, Object> result = OptionMerger.merge("bar", null, null);

        // bar.json has tooltip.trigger = "axis"
        @SuppressWarnings("unchecked")
        Map<String, Object> tooltip = (Map<String, Object>) result.get("tooltip");
        assertThat(tooltip).containsEntry("trigger", "axis");

        // bar.json series[0].type = "bar"
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> series = (List<Map<String, Object>>) result.get("series");
        assertThat(series.get(0)).containsEntry("type", "bar");
    }

    @Test
    void merge_appliesColorScheme() {
        ChartSemantics semantics = new ChartSemantics(
            "bar", "warm", null, null, null, null, null, null, null);

        Map<String, Object> result = OptionMerger.merge("bar", semantics, null);

        @SuppressWarnings("unchecked")
        List<String> color = (List<String>) result.get("color");
        assertThat(color).containsExactly(
            "#ff6b35", "#ffa726", "#ff7043", "#ffb74d", "#e65100", "#f57c00");
    }

    @Test
    void merge_appliesStacked() {
        ChartSemantics semantics = new ChartSemantics(
            "bar", null, true, null, null, null, null, null, null);

        Map<String, Object> result = OptionMerger.merge("bar", semantics, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> series = (List<Map<String, Object>>) result.get("series");
        assertThat(series.get(0)).containsEntry("stack", "total");
    }

    @Test
    void merge_stackedLineAddsAreaStyle() {
        ChartSemantics semantics = new ChartSemantics(
            "line", null, true, null, null, null, null, null, null);

        Map<String, Object> result = OptionMerger.merge("line", semantics, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> series = (List<Map<String, Object>>) result.get("series");
        assertThat(series.get(0)).containsEntry("stack", "total");
        assertThat(series.get(0)).containsKey("areaStyle");
    }

    @Test
    void merge_rawEchartsOptionOverridesLayer1() {
        // Layer1 bar.json has tooltip.trigger = "axis"
        // Layer3 rawEchartsOption should override to "item"
        Map<String, Object> rawOption = Map.of("tooltip",
            Map.of("trigger", "item"));

        ChartSemantics semantics = new ChartSemantics(
            "bar", null, null, null, null, null, null, null, rawOption);

        Map<String, Object> result = OptionMerger.merge("bar", semantics, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> tooltip = (Map<String, Object>) result.get("tooltip");
        assertThat(tooltip.get("trigger")).isEqualTo("item");
    }

    @Test
    void merge_appliesGridGapCompact() {
        ChartSemantics semantics = new ChartSemantics(
            "bar", null, null, null, null, null, null, "compact", null);

        Map<String, Object> result = OptionMerger.merge("bar", semantics, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> grid = (Map<String, Object>) result.get("grid");
        assertThat(grid).containsEntry("left", "2%");
        assertThat(grid).containsEntry("right", "2%");
    }

    @Test
    void merge_appliesShowLegend() {
        ChartSemantics semantics = new ChartSemantics(
            "bar", null, null, false, null, null, null, null, null);

        Map<String, Object> result = OptionMerger.merge("bar", semantics, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> legend = (Map<String, Object>) result.get("legend");
        assertThat(legend).containsEntry("show", false);
    }

    @Test
    void merge_appliesLabelPosition() {
        ChartSemantics semantics = new ChartSemantics(
            "bar", null, null, null, null, null, "top", null, null);

        Map<String, Object> result = OptionMerger.merge("bar", semantics, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> series = (List<Map<String, Object>>) result.get("series");
        @SuppressWarnings("unchecked")
        Map<String, Object> label = (Map<String, Object>) series.get(0).get("label");
        assertThat(label).containsEntry("show", true);
        assertThat(label).containsEntry("position", "top");
    }

    @Test
    void merge_labelPositionNone_hidesLabels() {
        ChartSemantics semantics = new ChartSemantics(
            "bar", null, null, null, null, null, "none", null, null);

        Map<String, Object> result = OptionMerger.merge("bar", semantics, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> series = (List<Map<String, Object>>) result.get("series");
        @SuppressWarnings("unchecked")
        Map<String, Object> label = (Map<String, Object>) series.get(0).get("label");
        assertThat(label).containsEntry("show", false);
    }
}
