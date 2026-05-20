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
          "layout": { "engine": "free", "template": "grid-equal" },
          "version": 1
        }
        """;
        JsonNode doc = mapper.readTree(json);
        ValidationResult result = validator.validate(doc);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors().stream().anyMatch(e -> e.message().contains("schemaVersion"))).isTrue();
    }

    @Test
    void wrongSchemaVersionFails() throws Exception {
        String json = """
        {
          "schemaVersion": 2,
          "id": "dash_test1234",
          "title": "Test",
          "theme": "industry-default",
          "renderer": "bezel",
          "parameters": [],
          "widgets": [],
          "layout": { "engine": "free", "template": "grid-equal" },
          "version": 1
        }
        """;
        JsonNode doc = mapper.readTree(json);
        ValidationResult result = validator.validate(doc);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors().stream().anyMatch(e -> e.message().contains("schemaVersion"))).isTrue();
    }

    @Test
    void duplicateWidgetIdFails() throws Exception {
        String json = """
        {
          "schemaVersion": 3,
          "id": "dash_test1234",
          "title": "Test",
          "theme": "industry-ecommerce",
          "renderer": "bezel",
          "parameters": [],
          "widgets": [
            {
              "id": "chart_w_abc12345",
              "type": "chart",
              "slot": "main",
              "title": "A",
              "patternId": "test.pattern",
              "options": {}
            },
            {
              "id": "chart_w_abc12345",
              "type": "kpi",
              "slot": "main",
              "title": "B",
              "patternId": "test.pattern",
              "options": {}
            }
          ],
          "layout": { "engine": "free", "template": "grid-equal" },
          "version": 1
        }
        """;
        JsonNode doc = mapper.readTree(json);
        ValidationResult result = validator.validate(doc);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors().stream().anyMatch(e -> e.code().equals("duplicate_widget_id"))).isTrue();
    }

    @Test
    void invalidThemeFormatFails() throws Exception {
        String json = """
        {
          "schemaVersion": 3,
          "id": "dash_test1234",
          "title": "Test",
          "theme": "bad-theme",
          "renderer": "bezel",
          "parameters": [],
          "widgets": [],
          "layout": { "engine": "free", "template": "grid-equal" },
          "version": 1
        }
        """;
        JsonNode doc = mapper.readTree(json);
        ValidationResult result = validator.validate(doc);
        assertThat(result.ok()).isFalse();
    }

    @Test
    void widgetWithInvalidIdFails() throws Exception {
        String json = """
        {
          "schemaVersion": 3,
          "id": "dash_test1234",
          "title": "Test",
          "theme": "industry-ecommerce",
          "renderer": "bezel",
          "parameters": [],
          "widgets": [
            {
              "id": "bad_id",
              "type": "chart",
              "slot": "main",
              "title": "Bad",
              "patternId": "test.pattern",
              "options": {}
            }
          ],
          "layout": { "engine": "free", "template": "grid-equal" },
          "version": 1
        }
        """;
        JsonNode doc = mapper.readTree(json);
        ValidationResult result = validator.validate(doc);
        assertThat(result.ok()).isFalse();
    }

    private static final String CLEAN_DASHBOARD = """
        {
          "schemaVersion": 3,
          "id": "dash_test1234",
          "title": "Sales Dashboard",
          "description": "Monthly overview",
          "defaultConnectionId": "conn_1",
          "theme": "industry-ecommerce",
          "renderer": "bezel",
          "parameters": [
            {
              "id": "global:date_range",
              "scope": "global",
              "ownerWidgetId": null,
              "name": "dateRange",
              "type": "date_range",
              "default": "last_7d"
            }
          ],
          "widgets": [
            {
              "id": "chart_w_abc12345",
              "type": "chart",
              "slot": "grid",
              "title": "Total Orders",
              "patternId": "test.pattern",
              "chartSemantics": { "chartType": "bar" },
              "parameters": [],
              "query": {
                "connectionId": null,
                "sql": "SELECT * FROM orders",
                "paramRefs": {}
              },
              "options": {}
            }
          ],
          "layout": { "engine": "free", "template": "grid-equal" },
          "version": 1,
          "createdAt": 1700000000000,
          "updatedAt": 1700000000000
        }
        """;
}
