package com.datatalk.application.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compares old and new dashboard JSON to determine incremental vs full rebuild.
 *
 * Full rebuild: template/theme change, widget add/remove, >3 widget attribute changes.
 * Incremental: ≤3 widget attribute changes (chartSemantics, title, query).
 */
public final class DashboardDiffer {

    private static final int INCREMENTAL_THRESHOLD = 3;
    private static final ObjectMapper SHARED_MAPPER = new ObjectMapper();

    public sealed interface DiffResult {
        record FullRebuild(String html) implements DiffResult {}
        record Incremental(int version, List<WidgetChange> changes) implements DiffResult {}
    }

    public record WidgetChange(String widgetId, Object baseOption, String html) {}

    public static boolean needsFullRebuild(JsonNode oldJson, JsonNode newJson) {
        // Template change
        if (!jsonEquals(oldJson.path("layout").path("template"),
                        newJson.path("layout").path("template"))) return true;

        // Theme change
        if (!jsonEquals(oldJson.path("theme"), newJson.path("theme"))) return true;

        // Widget count change
        ArrayNode oldWidgets = (ArrayNode) oldJson.path("widgets");
        ArrayNode newWidgets = (ArrayNode) newJson.path("widgets");
        if (oldWidgets.size() != newWidgets.size()) return true;

        // Check for widget ID changes (add/remove)
        var oldIds = widgetIds(oldWidgets);
        var newIds = widgetIds(newWidgets);
        if (!oldIds.equals(newIds)) return true;

        // Check for slot reassignments
        for (JsonNode ow : oldWidgets) {
            String id = ow.path("id").asText("");
            for (JsonNode nw : newWidgets) {
                if (id.equals(nw.path("id").asText(""))) {
                    if (!jsonEquals(ow.path("slot"), nw.path("slot"))) return true;
                    break;
                }
            }
        }

        return false;
    }

    public static List<WidgetChange> computeIncrementalChanges(JsonNode oldJson, JsonNode newJson,
                                                                 String dashboardId, PatternCatalog catalog) {
        ArrayNode oldWidgets = (ArrayNode) oldJson.path("widgets");
        ArrayNode newWidgets = (ArrayNode) newJson.path("widgets");

        int defaultIntervalMs = newJson.path("refresh").path("defaultIntervalMs").asInt(0);

        List<WidgetChange> changes = new ArrayList<>();
        for (JsonNode nw : newWidgets) {
            String id = nw.path("id").asText("");
            JsonNode ow = findWidget(oldWidgets, id);
            if (ow == null) continue;

            if (widgetAttributesChanged(ow, nw)) {
                var widget = toWidget(nw);
                var compile = WidgetCompiler.compile(widget, dashboardId, defaultIntervalMs, catalog);
                Object baseOption = compile.configEntry().get("baseOption");
                changes.add(new WidgetChange(id, baseOption, compile.htmlFragment()));
            }
        }
        return changes;
    }

    private static boolean widgetAttributesChanged(JsonNode old, JsonNode nw) {
        return !jsonEquals(old.path("chartSemantics"), nw.path("chartSemantics"))
            || !jsonEquals(old.path("title"), nw.path("title"))
            || !jsonEquals(old.path("query"), nw.path("query"))
            || !jsonEquals(old.path("options"), nw.path("options"))
            || !jsonEquals(old.path("patternId"), nw.path("patternId"))
            || !jsonEquals(old.path("type"), nw.path("type"));
    }

    private static JsonNode findWidget(ArrayNode widgets, String id) {
        for (JsonNode w : widgets) {
            if (id.equals(w.path("id").asText(""))) return w;
        }
        return null;
    }

    private static java.util.Set<String> widgetIds(ArrayNode widgets) {
        var ids = new java.util.LinkedHashSet<String>();
        for (JsonNode w : widgets) ids.add(w.path("id").asText(""));
        return ids;
    }

    private static boolean jsonEquals(JsonNode a, JsonNode b) {
        if (a.isMissingNode() && b.isMissingNode()) return true;
        if (a.isMissingNode() || b.isMissingNode()) return false;
        return a.equals(b);
    }

    private static com.datatalk.domain.dashboard.Widget toWidget(JsonNode wn) {
        return DashboardCompiler.toWidget(wn, SHARED_MAPPER);
    }

    private DashboardDiffer() {}
}
