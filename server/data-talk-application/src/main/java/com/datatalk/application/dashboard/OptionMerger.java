package com.datatalk.application.dashboard;

import com.datatalk.domain.dashboard.ChartSemantics;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Three-layer deep merge producing the final ECharts option for a chart widget.
 *
 * <p>Layer 1: echarts-options/{chartType}.json — baseline defaults loaded from classpath.
 * Layer 2: {@link ChartSemantics} field conversion — high-level semantic fields translated
 *          into specific ECharts option paths.
 * Layer 3: {@code rawEchartsOption} — user escape hatch for arbitrary overrides.</p>
 *
 * <p>All methods are static (pure functions). Default option files are cached in a
 * {@link ConcurrentHashMap} keyed by chart type name.</p>
 */
public final class OptionMerger {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static final ConcurrentHashMap<String, Map<String, Object>> DEFAULT_OPTION_CACHE =
        new ConcurrentHashMap<>();

    // ---- Color palettes (4 schemes from pattern-catalog.yaml) ----

    private static final Map<String, List<String>> COLOR_PALETTES = Map.of(
        "warm",       List.of("#ff6b35", "#ffa726", "#ff7043", "#ffb74d", "#e65100", "#f57c00"),
        "cool",       List.of("#2196f3", "#64b5f6", "#42a5f5", "#90caf9", "#1e88e5", "#1565c0"),
        "monochrome", List.of("#9e9e9e", "#bdbdbd", "#757575", "#e0e0e0", "#616161", "#424242"),
        "brand",      List.of("#6366f1", "#818cf8", "#4f46e5", "#a5b4fc", "#4338ca", "#3730a3")
    );

    // ---- Grid gap presets ----

    private static final Map<String, Map<String, Object>> GRID_GAP_PRESETS = Map.of(
        "compact",  Map.of("left", "2%", "right", "2%", "top", 40, "bottom", "2%"),
        "normal",   Map.of("left", "3%", "right", "4%", "top", 48, "bottom", "3%"),
        "spacious", Map.of("left", "5%", "right", "6%", "top", 56, "bottom", "5%")
    );

    private OptionMerger() {}

    // ---- Public API ----

    /**
     * Three-layer merge producing the final ECharts option.
     *
     * @param chartType the chart type name (bar, line, area, pie, funnel, scatter, radar, map)
     * @param semantics chart semantic fields; may be {@code null} for non-chart widgets
     * @param catalog   the pattern catalog for resolving default option file paths
     * @return merged ECharts option map; empty map if semantics is null and chartType is null
     */
    public static Map<String, Object> merge(String chartType,
                                            ChartSemantics semantics,
                                            PatternCatalog catalog) {
        if (semantics == null && chartType == null) {
            return new LinkedHashMap<>();
        }

        String ct = chartType != null ? chartType : "bar";

        // Layer 1: default option from classpath
        Map<String, Object> layer1 = loadDefaultOption(ct, catalog);

        // Layer 2: semantic field conversion
        Map<String, Object> layer2 = new LinkedHashMap<>();
        if (semantics != null) {
            applySemantics(layer2, semantics, ct);
        }

        // Layer 3: rawEchartsOption escape hatch
        Map<String, Object> layer3 = semantics != null && semantics.rawEchartsOption() != null
            ? semantics.rawEchartsOption() : Map.of();

        // Apply: deepMerge is non-mutating — creates new maps at each level
        return deepMerge(deepMerge(new LinkedHashMap<>(layer1), layer2), layer3);
    }

    /**
     * Deep merge two maps. Object values are merged recursively; arrays and scalars are replaced.
     * Does not mutate either input map.
     *
     * @param base     the base map (not modified)
     * @param override the override map (takes precedence)
     * @return a new merged map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepMerge(Map<String, Object> base,
                                                 Map<String, Object> override) {
        if (override == null || override.isEmpty()) return base;
        Map<String, Object> result = new LinkedHashMap<>(base);
        for (var entry : override.entrySet()) {
            String key = entry.getKey();
            Object overVal = entry.getValue();

            Object baseVal = result.get(key);
            if (baseVal instanceof Map && overVal instanceof Map) {
                result.put(key, deepMerge(
                    new LinkedHashMap<>((Map<String, Object>) baseVal),
                    (Map<String, Object>) overVal));
            } else {
                result.put(key, overVal);
            }
        }
        return result;
    }

    // ---- Layer 1: Default option loading ----

    private static Map<String, Object> loadDefaultOption(String chartType, PatternCatalog catalog) {
        return DEFAULT_OPTION_CACHE.computeIfAbsent(chartType, ct -> {
            // Resolve path from catalog if available, otherwise use convention
            var ctDef = catalog != null ? catalog.getChartType(ct) : null;
            String path = ctDef != null && ctDef.defaultOptionFile() != null
                ? "dashboard/" + ctDef.defaultOptionFile()
                : "dashboard/echarts-options/" + ct + ".json";

            try (InputStream is = OptionMerger.class.getClassLoader().getResourceAsStream(path)) {
                if (is == null) return new LinkedHashMap<>();
                return JSON_MAPPER.readValue(is, new TypeReference<LinkedHashMap<String, Object>>() {});
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load echarts default option: " + path, e);
            }
        });
    }

    // ---- Layer 2: Semantic field conversion ----

    private static void applySemantics(Map<String, Object> layer2,
                                       ChartSemantics s,
                                       String chartType) {
        // Build series[0] with chartType
        Map<String, Object> series0 = new LinkedHashMap<>();
        series0.put("type", chartType);
        layer2.put("series", List.of(series0));

        // colorScheme -> color palette
        if (s.colorScheme() != null) {
            List<String> palette = COLOR_PALETTES.getOrDefault(s.colorScheme(), COLOR_PALETTES.get("brand"));
            layer2.put("color", new ArrayList<>(palette));
        }

        // stacked -> series[0].stack = "total", areaStyle for area/line
        if (Boolean.TRUE.equals(s.stacked())) {
            series0.put("stack", "total");
            if ("area".equals(chartType) || "line".equals(chartType)) {
                series0.put("areaStyle", Map.of());
            }
        }

        // showLegend -> legend.show
        if (s.showLegend() != null) {
            layer2.put("legend", Map.of("show", s.showLegend()));
        }

        // showTooltip -> tooltip.show
        if (s.showTooltip() != null) {
            layer2.put("tooltip", Map.of("show", s.showTooltip()));
        }

        // showAreaFill -> series[0].areaStyle = {}
        if (Boolean.TRUE.equals(s.showAreaFill())) {
            series0.put("areaStyle", Map.of());
        }

        // labelPosition -> series[0].label.position
        if (s.labelPosition() != null && !"none".equals(s.labelPosition())) {
            series0.put("label", Map.of("show", true, "position", s.labelPosition()));
        } else if ("none".equals(s.labelPosition())) {
            series0.put("label", Map.of("show", false));
        }

        // gridGap -> grid.left/right/top/bottom
        if (s.gridGap() != null) {
            Map<String, Object> gap = GRID_GAP_PRESETS.get(s.gridGap());
            if (gap != null) {
                layer2.put("grid", new LinkedHashMap<>(gap));
            }
        }
    }
}
