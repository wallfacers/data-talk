package com.datatalk.application.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardSchemaValidatorTest {

    private DashboardSchemaValidator validator;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        validator = new DashboardSchemaValidator(mapper);
    }

    @Test
    void cleanDashboardPasses() throws Exception {
        JsonNode doc = mapper.readTree(CLEAN_DASHBOARD);
        ValidationResult result = validator.validate(doc);
        assertThat(result.ok()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void missingSchemaVersionFails() throws Exception {
        String json = """
        {
          "id": "dash_test1234",
          "title": "Test",
          "parameters": [],
          "widgets": [],
          "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 },
          "version": 1
        }
        """;
        JsonNode doc = mapper.readTree(json);
        ValidationResult result = validator.validate(doc);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors().stream().anyMatch(e -> e.message().contains("schemaVersion"))).isTrue();
    }

    @Test
    void overlappingWidgetsFails() throws Exception {
        String json = """
        {
          "schemaVersion": 1,
          "id": "dash_test1234",
          "title": "Test",
          "parameters": [],
          "widgets": [
            {
              "id": "chart_w_abc12345",
              "type": "chart",
              "position": { "x": 0, "y": 0, "w": 6, "h": 4 },
              "options": { "title": "A" }
            },
            {
              "id": "chart_w_def67890",
              "type": "chart",
              "position": { "x": 3, "y": 0, "w": 6, "h": 4 },
              "options": { "title": "B" }
            }
          ],
          "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 },
          "version": 1
        }
        """;
        JsonNode doc = mapper.readTree(json);
        ValidationResult result = validator.validate(doc);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors().stream().anyMatch(e -> e.code().equals("overlap"))).isTrue();
    }

    @Test
    void nonZeroZInGridFails() throws Exception {
        String json = """
        {
          "schemaVersion": 1,
          "id": "dash_test1234",
          "title": "Test",
          "parameters": [],
          "widgets": [
            {
              "id": "chart_w_abc12345",
              "type": "chart",
              "position": { "x": 0, "y": 0, "w": 6, "h": 4, "z": 3 },
              "options": { "title": "A" }
            }
          ],
          "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 },
          "version": 1
        }
        """;
        JsonNode doc = mapper.readTree(json);
        ValidationResult result = validator.validate(doc);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors().stream().anyMatch(e -> e.code().equals("z_must_be_zero_in_grid"))).isTrue();
    }

    @Test
    void duplicateWidgetIdFails() throws Exception {
        String json = """
        {
          "schemaVersion": 1,
          "id": "dash_test1234",
          "title": "Test",
          "parameters": [],
          "widgets": [
            {
              "id": "chart_w_abc12345",
              "type": "chart",
              "position": { "x": 0, "y": 0, "w": 6, "h": 4 },
              "options": { "title": "A" }
            },
            {
              "id": "chart_w_abc12345",
              "type": "kpi",
              "position": { "x": 6, "y": 0, "w": 6, "h": 4 },
              "options": { "title": "B" }
            }
          ],
          "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 },
          "version": 1
        }
        """;
        JsonNode doc = mapper.readTree(json);
        ValidationResult result = validator.validate(doc);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors().stream().anyMatch(e -> e.code().equals("duplicate_widget_id"))).isTrue();
    }

    private static final String CLEAN_DASHBOARD = """
        {
          "schemaVersion": 1,
          "id": "dash_test1234",
          "title": "Sales Dashboard",
          "description": "Monthly overview",
          "defaultConnectionId": "conn_1",
          "parameters": [
            {
              "id": "global:date_range",
              "scope": "global",
              "ownerWidgetId": null,
              "name": "dateRange",
              "type": "date_range",
              "defaultValue": "last_7d"
            }
          ],
          "widgets": [
            {
              "id": "chart_w_abc12345",
              "type": "chart",
              "position": { "x": 0, "y": 0, "w": 6, "h": 8 },
              "parameters": [],
              "query": {
                "connectionId": null,
                "sql": "SELECT * FROM orders",
                "paramRefs": {}
              },
              "options": {
                "title": "Total Orders",
                "echartsOption": {},
                "dataMapping": { "rowsAsDataset": true }
              }
            }
          ],
          "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 },
          "version": 1,
          "createdAt": 1700000000000,
          "updatedAt": 1700000000000
        }
        """;
}
