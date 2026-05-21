package com.datatalk.adapter.controller;

import com.datatalk.application.dashboard.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden-file snapshot tests for the dashboard compiler.
 * Compiles each of the 6 layout templates and verifies the output HTML
 * contains required structural markers.
 *
 * <p>Compiled HTML is saved to {@code tmp/golden/} for offline inspection.</p>
 *
 * <p>Widget IDs must match {@code ^[a-z]+_w_[a-zA-Z0-9_]{4,32}$}
 * (enforced by dashboard-schema.json). All IDs below follow this pattern.</p>
 */
class DashboardGoldenFileTest {

    private static DashboardCompiler compiler;
    private static ObjectMapper mapper;
    private static Path goldenDir;

    @BeforeAll
    static void setUp() throws IOException {
        PatternCatalog catalog = new PatternCatalog();
        mapper = new ObjectMapper();
        DashboardSchemaValidator validator = new DashboardSchemaValidator(mapper);
        compiler = new DashboardCompiler(catalog, validator, mapper);

        goldenDir = Path.of("tmp/golden");
        Files.createDirectories(goldenDir);
    }

    // ---- Template test cases ----

    static Stream<TemplateTestCase> templateCases() {
        return Stream.of(
            new TemplateTestCase("single-focus", "hero"),
            new TemplateTestCase("two-column-left-heavy", "main"),
            new TemplateTestCase("two-column-right-heavy", "main"),
            new TemplateTestCase("three-column-kpi-center", "center-main"),
            new TemplateTestCase("top-kpi-bottom-charts", "chart-grid"),
            new TemplateTestCase("grid-equal", "grid")
        );
    }

    @ParameterizedTest(name = "template={0}")
    @MethodSource("templateCases")
    @DisplayName("Empty dashboard compiles for each template")
    void emptyDashboardCompiles(TemplateTestCase tc) throws IOException {
        JsonNode dashboard = buildDashboard(tc.templateId, List.of());
        var result = compiler.compile(dashboard);

        assertThat(result.ok())
            .as("compile should succeed for template '%s', errors: %s", tc.templateId, result.errors())
            .isTrue();

        String html = result.html();
        saveGolden(tc.templateId + "_empty.html", html);

        assertCommonMarkers(html, tc.templateId);
    }

    @ParameterizedTest(name = "template={0}")
    @MethodSource("templateCases")
    @DisplayName("Dashboard with one chart widget compiles for each template")
    void dashboardWithWidgetCompiles(TemplateTestCase tc) throws IOException {
        String widgetId = "chart_w_" + padToFour(tc.templateId);
        JsonNode widget = buildWidget(widgetId, "chart",
            tc.firstSlot, "Test Chart", "generic.echarts-card", "bar");
        JsonNode dashboard = buildDashboard(tc.templateId, List.of(widget));

        var result = compiler.compile(dashboard);

        assertThat(result.ok())
            .as("compile with widget should succeed for template '%s', errors: %s",
                tc.templateId, result.errors())
            .isTrue();

        String html = result.html();
        saveGolden(tc.templateId + "_with_widget.html", html);

        assertCommonMarkers(html, tc.templateId);

        // Widget-specific markers
        assertThat(html).contains(widgetId);
        assertThat(html).contains("widget-chart");
        assertThat(html).contains("chart-container");
        assertThat(html).contains("Test Chart");
    }

    @Test
    @DisplayName("Compilation is deterministic — same input produces same output")
    void compilationIsDeterministic() {
        JsonNode widget = buildWidget("chart_w_determABCD", "chart",
            "hero", "Det Chart", "generic.echarts-card", "bar");
        JsonNode dashboard = buildDashboard("single-focus", List.of(widget));

        var result1 = compiler.compile(dashboard);
        var result2 = compiler.compile(dashboard);

        assertThat(result1.ok()).isTrue();
        assertThat(result2.ok()).isTrue();
        assertThat(result1.html()).isEqualTo(result2.html());
    }

    @Test
    @DisplayName("KPI widget in top-kpi-bottom-charts kpi-bar slot")
    void kpiWidgetInKpiBarSlot() throws IOException {
        JsonNode widget = buildWidget("kpi_w_gmvTotalX", "kpi",
            "kpi-bar", "GMV Total", "generic.kpi-tile", null);
        JsonNode dashboard = buildDashboard("top-kpi-bottom-charts", List.of(widget));

        var result = compiler.compile(dashboard);

        assertThat(result.ok())
            .as("compile should succeed, errors: %s", result.errors())
            .isTrue();

        String html = result.html();
        saveGolden("top-kpi-bottom-charts_kpi_widget.html", html);

        assertCommonMarkers(html, "top-kpi-bottom-charts");
        assertThat(html).contains("kpi_w_gmvTotalX");
        assertThat(html).contains("kpi-value");
        assertThat(html).contains("GMV Total");
    }

    @Test
    @DisplayName("Three-column template with widgets in all three slots")
    void threeColumnAllSlots() throws IOException {
        JsonNode kpiWidget = buildWidget("kpi_w_revnXYZ01", "kpi",
            "kpi-rail", "Revenue", "generic.kpi-tile", null);
        JsonNode chartWidget = buildWidget("chart_w_trendXYZ02", "chart",
            "center-main", "Trend", "generic.echarts-card", "line");
        JsonNode tableWidget = buildWidget("table_w_dtlXYZ003", "table",
            "right-detail", "Details", "generic.table", null);

        JsonNode dashboard = buildDashboard("three-column-kpi-center",
            List.of(kpiWidget, chartWidget, tableWidget));

        var result = compiler.compile(dashboard);

        assertThat(result.ok())
            .as("compile should succeed, errors: %s", result.errors())
            .isTrue();

        String html = result.html();
        saveGolden("three-column-kpi-center_all_slots.html", html);

        assertCommonMarkers(html, "three-column-kpi-center");
        assertThat(html).contains("kpi_w_revnXYZ01");
        assertThat(html).contains("chart_w_trendXYZ02");
        assertThat(html).contains("table_w_dtlXYZ003");
    }

    @Test
    @DisplayName("Slot capacity overflow produces error")
    void slotCapacityOverflow() {
        // single-focus hero slot has capacity 1 — put 2 widgets
        JsonNode w1 = buildWidget("chart_w_overflowA1", "chart",
            "hero", "Chart A", "generic.echarts-card", "bar");
        JsonNode w2 = buildWidget("chart_w_overflowB2", "chart",
            "hero", "Chart B", "generic.echarts-card", "bar");
        JsonNode dashboard = buildDashboard("single-focus", List.of(w1, w2));

        var result = compiler.compile(dashboard);

        assertThat(result.ok()).isFalse();
        assertThat(result.errors()).anySatisfy(err ->
            assertThat(err.getMessage()).contains("exceeded capacity")
        );
    }

    @Test
    @DisplayName("Invalid template ID produces error")
    void invalidTemplateId() {
        JsonNode dashboard = buildDashboard("nonexistent-template", List.of());
        var result = compiler.compile(dashboard);

        assertThat(result.ok()).isFalse();
        // Schema validation rejects unknown template IDs before template resolve stage
        assertThat(result.errors()).anySatisfy(err ->
            assertThat(err.getMessage()).containsAnyOf(
                "Failed to load template",
                "does not have a value in the enumeration")
        );
    }

    @Test
    @DisplayName("Widget with unknown slot produces error")
    void unknownSlot() {
        JsonNode widget = buildWidget("chart_w_badSlotXYZ", "chart",
            "nonexistent-slot", "Bad", "generic.echarts-card", "bar");
        JsonNode dashboard = buildDashboard("single-focus", List.of(widget));

        var result = compiler.compile(dashboard);

        assertThat(result.ok()).isFalse();
        assertThat(result.errors()).anySatisfy(err ->
            assertThat(err.getMessage()).contains("not found in template")
        );
    }

    // ---- Assertion helpers ----

    private void assertCommonMarkers(String html, String templateId) {
        // 1. CSP meta tag
        assertThat(html)
            .as("compiled HTML must contain CSP meta tag")
            .contains("Content-Security-Policy");

        // 2. __BEZEL_SERVER_ORIGIN__ placeholder
        assertThat(html)
            .as("compiled HTML must contain __BEZEL_SERVER_ORIGIN__ placeholder")
            .contains("__BEZEL_SERVER_ORIGIN__");

        // 3. __BEZEL_CONFIG__ JSON element
        assertThat(html)
            .as("compiled HTML must contain __BEZEL_CONFIG__ JSON element")
            .contains("id=\"__BEZEL_CONFIG__\"");

        // 4. External scheduler script
        assertThat(html)
            .as("compiled HTML must reference external scheduler.js")
            .contains("/bezel/scheduler.js");

        // 5. Template name in data-template attribute
        assertThat(html)
            .as("compiled HTML must contain data-template=\"%s\"", templateId)
            .contains("data-template=\"" + templateId + "\"");

        // 6. HtmlValidator must pass
        var validation = HtmlValidator.validate(html);
        assertThat(validation.ok())
            .as("HtmlValidator must pass, errors: %s", validation.errors())
            .isTrue();

        // 7. Slot markers resolved (no leftover placeholders)
        assertThat(html)
            .as("compiled HTML must not contain unresolved slot placeholders")
            .doesNotContain("<!-- __BEZEL_SLOT_");

        // 8. Core structural elements
        assertThat(html).contains("<!DOCTYPE html>");
        assertThat(html).contains("</html>");
        assertThat(html).contains("bezel-json-hash");
    }

    // ---- JSON builders ----

    private JsonNode buildDashboard(String templateId, List<JsonNode> widgets) {
        var node = mapper.createObjectNode();
        node.put("schemaVersion", 3);
        node.put("id", "dash_golden_test");
        node.put("title", "Golden Test Dashboard");
        node.put("theme", "industry-ecommerce");
        node.put("renderer", "bezel");
        node.putArray("parameters");
        node.putObject("layout")
            .put("engine", "free")
            .put("template", templateId);
        node.put("version", 1);
        node.put("createdAt", 0);
        node.put("updatedAt", 0);

        ArrayNode widgetsArray = node.putArray("widgets");
        for (JsonNode w : widgets) {
            widgetsArray.add(w);
        }

        return node;
    }

    private JsonNode buildWidget(String id, String type, String slot,
                                 String title, String patternId,
                                 String chartType) {
        var node = mapper.createObjectNode();
        node.put("id", id);
        node.put("type", type);
        node.put("slot", slot);
        node.put("title", title);
        node.put("patternId", patternId);
        node.putObject("options");

        if (chartType != null) {
            node.putObject("chartSemantics").put("chartType", chartType);
        }

        return node;
    }

    // ---- File I/O ----

    private void saveGolden(String fileName, String html) throws IOException {
        Path file = goldenDir.resolve(fileName);
        Files.writeString(file, html);
    }

    // ---- Helpers ----

    /**
     * Generate a deterministic 4+ char alphanumeric suffix from a template ID,
     * ensuring widget IDs like "chart_w_sing" match the schema pattern
     * {@code ^[a-z]+_w_[a-zA-Z0-9_]{4,32}$}.
     */
    private static String padToFour(String templateId) {
        // Take first 8 chars of templateId (all template IDs are > 4 chars)
        String base = templateId.replace("-", "");
        return base.length() >= 8 ? base.substring(0, 8) : String.format("%-8s", base).replace(' ', 'x');
    }

    // ---- Test data record ----

    record TemplateTestCase(String templateId, String firstSlot) {}
}
