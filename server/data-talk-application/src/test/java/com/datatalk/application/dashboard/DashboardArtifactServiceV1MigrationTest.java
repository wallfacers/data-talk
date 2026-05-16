package com.datatalk.application.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DashboardArtifactServiceV1MigrationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test void migratesV1JsonToV2OnLoad() throws Exception {
        String v1 = """
            {
              "schemaVersion": 1,
              "id": "dash_test",
              "title": "Old",
              "parameters": [],
              "widgets": [
                { "id": "chart_w_aaaa", "type": "chart", "position": {"x":0,"y":0,"w":4,"h":3},
                  "options": { "echartsOption": {} } },
                { "id": "kpi_w_bbbb", "type": "kpi", "position": {"x":4,"y":0,"w":2,"h":2}, "options": {} }
              ],
              "layout": { "engine": "grid", "cols": 12, "rowHeight": 80, "gap": 8 },
              "version": 5, "createdAt": 0, "updatedAt": 0
            }
            """;
        JsonNode v2 = DashboardArtifactService.migrateV1ToV2(mapper.readTree(v1), mapper);
        assertThat(v2.path("schemaVersion").asInt()).isEqualTo(2);
        assertThat(v2.path("renderer").asText()).isEqualTo("bezel");
        assertThat(v2.path("theme").asText()).isEqualTo("industry-neutral");
        assertThat(v2.path("refresh").path("defaultIntervalMs").asInt()).isEqualTo(10000);
        assertThat(v2.path("layout").path("engine").asText()).isEqualTo("free");
        assertThat(v2.path("widgets").get(0).path("patternId").asText()).isEqualTo("generic.echarts-card");
        assertThat(v2.path("widgets").get(1).path("patternId").asText()).isEqualTo("generic.kpi-tile");
    }

    @Test void infersTypeFromPatternIdWhenTypeMissing() throws Exception {
        // v1 dashboards sometimes shipped widgets with only patternId.
        // migrateV1ToV2 must back-fill `type` so the runtime scheduler can branch.
        String v1 = """
            {
              "schemaVersion": 1,
              "id": "dash_legacy",
              "title": "Legacy",
              "parameters": [],
              "widgets": [
                { "id": "w1", "patternId": "generic.kpi-tile",
                  "position": {"x":0,"y":0,"w":4,"h":2}, "options": {} },
                { "id": "w2", "patternId": "generic.echarts-card",
                  "position": {"x":4,"y":0,"w":4,"h":4}, "options": {} },
                { "id": "w3", "patternId": "generic.table",
                  "position": {"x":8,"y":0,"w":4,"h":4}, "options": {} },
                { "id": "w4", "patternId": "generic.filter-bar",
                  "position": {"x":0,"y":4,"w":12,"h":1}, "options": {} }
              ],
              "layout": { "engine": "grid", "cols": 12, "rowHeight": 80, "gap": 8 },
              "version": 1, "createdAt": 0, "updatedAt": 0
            }
            """;
        JsonNode v2 = DashboardArtifactService.migrateV1ToV2(mapper.readTree(v1), mapper);
        assertThat(v2.path("widgets").get(0).path("type").asText()).isEqualTo("kpi");
        assertThat(v2.path("widgets").get(1).path("type").asText()).isEqualTo("chart");
        assertThat(v2.path("widgets").get(2).path("type").asText()).isEqualTo("table");
        assertThat(v2.path("widgets").get(3).path("type").asText()).isEqualTo("filter");
    }

    @Test void fallsBackToChartWhenBothTypeAndPatternMissing() throws Exception {
        String v1 = """
            {
              "schemaVersion": 1,
              "id": "dash_bare",
              "title": "Bare",
              "parameters": [],
              "widgets": [
                { "id": "w1", "position": {"x":0,"y":0,"w":4,"h":3}, "options": {} }
              ],
              "layout": { "engine": "grid", "cols": 12, "rowHeight": 80, "gap": 8 },
              "version": 1, "createdAt": 0, "updatedAt": 0
            }
            """;
        JsonNode v2 = DashboardArtifactService.migrateV1ToV2(mapper.readTree(v1), mapper);
        // Falls back to 'chart' (safest default — KPI/table containers would need
        // structured DOM that v1 didn't provide).
        assertThat(v2.path("widgets").get(0).path("type").asText()).isEqualTo("chart");
        // The downstream patternId fill kicks in afterward.
        assertThat(v2.path("widgets").get(0).path("patternId").asText()).isEqualTo("generic.echarts-card");
    }

    @Test void industrySpecificPatternDefaultsToChart() throws Exception {
        // Industry patternIds (e.g. ecommerce.funnel-gradient) aren't in the generic.*
        // catalog. They should default to chart with a log warning.
        String v1 = """
            {
              "schemaVersion": 1,
              "id": "dash_industry",
              "title": "Industry",
              "parameters": [],
              "widgets": [
                { "id": "w1", "patternId": "ecommerce.funnel-gradient",
                  "position": {"x":0,"y":0,"w":4,"h":3}, "options": {} }
              ],
              "layout": { "engine": "grid", "cols": 12, "rowHeight": 80, "gap": 8 },
              "version": 1, "createdAt": 0, "updatedAt": 0
            }
            """;
        JsonNode v2 = DashboardArtifactService.migrateV1ToV2(mapper.readTree(v1), mapper);
        assertThat(v2.path("widgets").get(0).path("type").asText()).isEqualTo("chart");
    }
}
