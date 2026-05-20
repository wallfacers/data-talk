package com.datatalk.application.dashboard;

import com.datatalk.domain.dashboard.DashboardLayout;
import com.datatalk.domain.dashboard.Widget;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Six-stage compiler: Schema Validate → Template Resolve → Widget Compile
 * → Option Merge → Assemble → HTML Validate.
 * Pure function: same JSON always produces identical HTML.
 */
@Component
public class DashboardCompiler {

    private static final Logger log = LoggerFactory.getLogger(DashboardCompiler.class);
    private static final int MAX_CACHE_SIZE = 64;

    private final PatternCatalog catalog;
    private final DashboardSchemaValidator schemaValidator;
    private final ObjectMapper mapper;
    private final LinkedHashMap<String, String> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    public DashboardCompiler(PatternCatalog catalog,
                             DashboardSchemaValidator schemaValidator,
                             ObjectMapper mapper) {
        this.catalog = catalog;
        this.schemaValidator = schemaValidator;
        this.mapper = mapper;
    }

    public record CompileResult(String html, List<CompileError> errors) {
        public boolean ok() { return errors.isEmpty(); }
    }

    public static class CompileError extends RuntimeException {
        private final String stage;
        private final String path;
        public CompileError(String stage, String path, String message) {
            super(stage + " @ " + path + ": " + message);
            this.stage = stage;
            this.path = path;
        }
        public String stage() { return stage; }
        public String path() { return path; }
    }

    public CompileResult compile(JsonNode dashboard) {
        String cacheKey = sha256Hex(dashboard.toString());
        String cached;
        synchronized (cache) {
            cached = cache.get(cacheKey);
        }
        if (cached != null) return new CompileResult(cached, List.of());

        List<CompileError> errors = new ArrayList<>();

        // Stage 1: Schema Validate
        var validation = schemaValidator.validate(dashboard);
        if (!validation.ok()) {
            for (var e : validation.errors()) {
                errors.add(new CompileError("schema-validate", e.path(), e.code() + ": " + e.message()));
            }
            return new CompileResult(null, errors);
        }

        String templateId = dashboard.path("layout").path("template").asText("");
        String themeSlug = dashboard.path("theme").asText("").replace("industry-", "");
        String title = dashboard.path("title").asText("Dashboard");
        String dashboardId = dashboard.path("id").asText("");

        // Stage 2: Template Resolve
        String templateHtml;
        try {
            templateHtml = TemplateResolver.resolveTemplate(templateId);
        } catch (Exception e) {
            errors.add(new CompileError("template-resolve", "layout.template",
                "Failed to load template '" + templateId + "': " + e.getMessage()));
            return new CompileResult(null, errors);
        }

        String css;
        try {
            css = TemplateResolver.resolveCss(themeSlug, catalog);
        } catch (Exception e) {
            errors.add(new CompileError("template-resolve", "theme",
                "Failed to load theme '" + themeSlug + "': " + e.getMessage()));
            return new CompileResult(null, errors);
        }

        // Stage 3+4: Widget Compile + Option Merge
        var templateDef = catalog.getTemplate(templateId);
        if (templateDef == null) {
            errors.add(new CompileError("template-resolve", "layout.template",
                "Unknown template: " + templateId));
            return new CompileResult(null, errors);
        }

        Map<String, List<String>> slotWidgets = new LinkedHashMap<>();
        List<Map<String, Object>> configWidgets = new ArrayList<>();
        Map<String, String> widgetIdToSlot = new LinkedHashMap<>();

        ArrayNode widgets = (ArrayNode) dashboard.path("widgets");
        for (JsonNode wn : widgets) {
            String widgetId = wn.path("id").asText("");
            String slot = wn.path("slot").asText("");
            String patternId = wn.path("patternId").asText("");

            // Validate slot
            boolean slotExists = templateDef.slots().stream().anyMatch(s -> s.id().equals(slot));
            if (!slotExists) {
                errors.add(new CompileError("widget-compile", "widgets[" + widgetId + "].slot",
                    "Slot '" + slot + "' not found in template '" + templateId + "'"));
                continue;
            }

            // Build domain Widget for WidgetCompiler
            Widget widget = toWidget(wn);

            // Compile
            try {
                var result = WidgetCompiler.compile(widget, dashboardId, catalog);
                slotWidgets.computeIfAbsent(slot, k -> new ArrayList<>()).add(result.htmlFragment());
                configWidgets.add(result.configEntry());
                widgetIdToSlot.put(widgetId, slot);
            } catch (CompileError ce) {
                errors.add(ce);
            }
        }

        if (!errors.isEmpty()) return new CompileResult(null, errors);

        // Check slot capacity
        for (var slotDef : templateDef.slots()) {
            List<String> contents = slotWidgets.getOrDefault(slotDef.id(), List.of());
            if (contents.size() > slotDef.capacity()) {
                errors.add(new CompileError("widget-compile", "slot:" + slotDef.id(),
                    "Slot '" + slotDef.id() + "' exceeded capacity " + slotDef.capacity()
                    + " (got " + contents.size() + " widgets)"));
            }
        }
        if (!errors.isEmpty()) return new CompileResult(null, errors);

        // Stage 5: Assemble
        String html = templateHtml;

        // Replace placeholders
        String jsonHash = sha256Hex(dashboard.toString()).substring(0, 16);
        html = html.replace("__CSP_POLICY__", CspInjector.generateCsp());
        html = html.replace("__JSON_HASH__", jsonHash);
        html = html.replace("__TITLE__", escapeHtml(title));
        html = html.replace("__BASE_CSS__", css); // resolveCss returns base+theme concatenated
        html = html.replace("__THEME_CSS__", ""); // already included in base+theme
        html = html.replace("__LAYOUT_TEMPLATE__", templateId);

        // Inject widgets into slots
        for (var slotDef : templateDef.slots()) {
            String marker = "<!-- __BEZEL_SLOT_" + slotDef.id() + "__ -->";
            List<String> contents = slotWidgets.getOrDefault(slotDef.id(), List.of());
            html = html.replace(marker, String.join("\n", contents));
        }

        // Build __BEZEL_CONFIG__
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("dashboardId", dashboardId);
        config.put("jsonHash", jsonHash);
        // Placeholder filled at serve time (CSP-safe: carried in the JSON data block,
        // not an inline script). scheduler.js reads cfg.serverOrigin for geo/asset fetches.
        config.put("serverOrigin", "__BEZEL_SERVER_ORIGIN__");
        config.put("widgets", configWidgets);

        try {
            String configJson = mapper.writeValueAsString(config);
            html = html.replace("__BEZEL_CONFIG_JSON__", configJson);
        } catch (Exception e) {
            errors.add(new CompileError("assemble", "__BEZEL_CONFIG__",
                "Failed to serialize config: " + e.getMessage()));
            return new CompileResult(null, errors);
        }

        // Stage 6: HTML Validate
        var htmlValidation = HtmlValidator.validate(html);
        if (!htmlValidation.ok()) {
            for (String err : htmlValidation.errors()) {
                errors.add(new CompileError("html-validate", "", err));
            }
            return new CompileResult(null, errors);
        }

        synchronized (cache) {
            cache.put(cacheKey, html);
        }
        return new CompileResult(html, List.of());
    }

    private Widget toWidget(JsonNode wn) {
        return new Widget(
            wn.path("id").asText(""),
            wn.path("type").asText(""),
            wn.path("slot").asText(""),
            wn.path("title").asText(""),
            wn.path("patternId").asText(""),
            toChartSemantics(wn.path("chartSemantics")),
            null, // refresh - not needed for compile
            List.of(), // parameters
            null, // query
            toOptions(wn.path("options"))
        );
    }

    private com.datatalk.domain.dashboard.ChartSemantics toChartSemantics(JsonNode cs) {
        if (cs == null || cs.isMissingNode() || cs.isNull()) return null;
        java.util.Map<String, Object> raw = cs.has("rawEchartsOption") && cs.get("rawEchartsOption").isObject()
            ? mapper.convertValue(cs.get("rawEchartsOption"), new com.fasterxml.jackson.core.type.TypeReference<>() {})
            : Map.of();
        return new com.datatalk.domain.dashboard.ChartSemantics(
            cs.path("chartType").asText(null),
            cs.path("colorScheme").asText(null),
            cs.path("stacked").asBoolean(false) ? true : null,
            cs.path("showLegend").asBoolean(false) ? true : null,
            cs.path("showTooltip").asBoolean(true) ? true : null,
            cs.path("showAreaFill").asBoolean(false) ? true : null,
            cs.path("labelPosition").asText(null),
            cs.path("gridGap").asText(null),
            raw
        );
    }

    private Map<String, Object> toOptions(JsonNode opts) {
        if (opts == null || opts.isMissingNode() || opts.isNull()) return Map.of();
        return mapper.convertValue(opts, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    static String sha256Hex(String input) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
