package com.datatalk.domain.dashboard;

import java.util.Map;

/**
 * High-level chart semantic fields that the compiler translates into ECharts options.
 * Replaces requiring AI to hand-write full ECharts option objects.
 */
public record ChartSemantics(
    String chartType,
    String colorScheme,
    Boolean stacked,
    Boolean showLegend,
    Boolean showTooltip,
    Boolean showAreaFill,
    String labelPosition,
    String gridGap,
    Map<String, Object> rawEchartsOption
) {
    public ChartSemantics {
        rawEchartsOption = rawEchartsOption == null ? Map.of() : Map.copyOf(rawEchartsOption);
    }
}
