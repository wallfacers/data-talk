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
}
