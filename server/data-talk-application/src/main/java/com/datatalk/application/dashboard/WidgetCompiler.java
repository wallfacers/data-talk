package com.datatalk.application.dashboard;

import com.datatalk.domain.dashboard.ChartSemantics;
import com.datatalk.domain.dashboard.Widget;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compiles a single widget into an HTML fragment and a __BEZEL_CONFIG__ entry.
 */
public final class WidgetCompiler {

    public record WidgetCompileResult(String htmlFragment, Map<String, Object> configEntry) {}

    public static WidgetCompileResult compile(Widget widget, String dashboardId, PatternCatalog catalog) {
        var pattern = catalog.getPattern(widget.patternId());
        if (pattern == null) {
            throw new DashboardCompiler.CompileError("widget-compile",
                "widgets[" + widget.id() + "].patternId",
                "Unknown patternId: " + widget.patternId());
        }

        String renderKind = pattern.renderKind();
        boolean isChart = "chart".equals(renderKind);

        String htmlFragment = buildHtmlFragment(widget, isChart);
        Map<String, Object> configEntry = buildConfigEntry(widget, dashboardId, isChart, catalog);

        return new WidgetCompileResult(htmlFragment, configEntry);
    }

    private static String buildHtmlFragment(Widget widget, boolean isChart) {
        String id = widget.id();
        String type = widget.type();
        String title = widget.title() != null ? widget.title() : "";

        StringBuilder sb = new StringBuilder();
        if (isChart) {
            sb.append("<div id=\"").append(esc(id)).append("\" ")
              .append("class=\"widget widget-chart\" ")
              .append("data-widget-id=\"").append(esc(id)).append("\">")
              .append("<div class=\"widget-title\">").append(esc(title)).append("</div>")
              .append("<div class=\"chart-container\" id=\"").append(esc(id)).append("_chart\"></div>")
              .append("</div>");
        } else {
            sb.append("<div id=\"").append(esc(id)).append("\" ")
              .append("class=\"widget widget-").append(esc(type)).append("\" ")
              .append("data-widget-id=\"").append(esc(id)).append("\" ")
              .append("data-bezel-render-kind=\"").append(esc(type)).append("\">")
              .append("<div class=\"widget-title\">").append(esc(title)).append("</div>")
              .append("<div class=\"widget-body\">").append(placeholderContent(type, title)).append("</div>")
              .append("</div>");
        }
        return sb.toString();
    }

    private static String placeholderContent(String type, String title) {
        return switch (type) {
            case "kpi" -> "<div class=\"kpi-value\">--</div><div class=\"kpi-label\">" + esc(title) + "</div>";
            case "table" -> "<div class=\"table-placeholder\">Loading...</div>";
            case "markdown" -> "<div class=\"markdown-placeholder\"></div>";
            case "filter" -> "<div class=\"filter-placeholder\"></div>";
            default -> "";
        };
    }

    private static Map<String, Object> buildConfigEntry(Widget widget, String dashboardId,
                                                         boolean isChart, PatternCatalog catalog) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", widget.id());
        entry.put("type", widget.type());

        int intervalMs = 0;
        if (widget.refresh() != null && widget.refresh().intervalMs() != null) {
            intervalMs = widget.refresh().intervalMs();
        }
        entry.put("intervalMs", intervalMs);
        entry.put("endpoint", "__BEZEL_SERVER_ORIGIN__/api/dashboards/" + dashboardId
            + "/widgets/" + widget.id() + "/data");
        entry.put("params", widget.query() != null && widget.query().paramRefs() != null
            ? widget.query().paramRefs() : Map.of());

        if (isChart) {
            ChartSemantics semantics = widget.chartSemantics();
            Map<String, Object> baseOption = OptionMerger.merge(
                semantics != null ? semantics.chartType() : "bar", semantics, catalog);
            entry.put("baseOption", baseOption);
        } else {
            entry.put("baseOption", null);
        }

        return entry;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private WidgetCompiler() {}
}
