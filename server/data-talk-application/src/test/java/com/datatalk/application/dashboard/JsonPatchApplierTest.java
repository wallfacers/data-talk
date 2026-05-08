package com.datatalk.application.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonPatchApplierTest {

    private JsonPatchApplier applier;
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode baseDashboard;

    @BeforeEach
    void setUp() throws Exception {
        applier = new JsonPatchApplier(mapper);
        baseDashboard = mapper.readTree("""
        {
          "schemaVersion": 1,
          "id": "dash_test1234",
          "title": "Test Dashboard",
          "description": null,
          "defaultConnectionId": "conn_1",
          "parameters": [],
          "widgets": [
            {
              "id": "chart_w_abc12345",
              "type": "chart",
              "position": { "x": 0, "y": 0, "w": 6, "h": 8 },
              "options": { "title": "Chart A" }
            }
          ],
          "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 },
          "version": 1,
          "createdAt": 1700000000000,
          "updatedAt": 1700000000000
        }
        """);
    }

    @Test
    void replaceByMatchKeyAppliesAndBumpsVersion() throws Exception {
        List<JsonPatchApplier.PatchOp> ops = List.of(
            new JsonPatchApplier.PatchOp("replace", "/widgets[id=chart_w_abc12345]/options/title", mapper.readValue("\"Updated Title\"", JsonNode.class))
        );

        JsonNode result = applier.apply(baseDashboard, 1, ops);

        assertThat(result.get("version").asInt()).isEqualTo(2);
        assertThat(result.get("widgets").get(0).get("options").get("title").asText()).isEqualTo("Updated Title");
    }

    @Test
    void addToWidgetsAppendsNewWidget() throws Exception {
        JsonNode newWidget = mapper.readTree("""
        {
          "id": "chart_w_def67890",
          "type": "kpi",
          "position": { "x": 6, "y": 0, "w": 6, "h": 8 },
          "options": { "title": "KPI 1" }
        }
        """);

        List<JsonPatchApplier.PatchOp> ops = List.of(
            new JsonPatchApplier.PatchOp("add", "/widgets/-", newWidget)
        );

        JsonNode result = applier.apply(baseDashboard, 1, ops);

        assertThat(result.get("widgets")).hasSize(2);
        assertThat(result.get("widgets").get(1).get("id").asText()).isEqualTo("chart_w_def67890");
        assertThat(result.get("version").asInt()).isEqualTo(2);
    }

    @Test
    void removeByMatchKeyRemovesWidget() throws Exception {
        List<JsonPatchApplier.PatchOp> ops = List.of(
            new JsonPatchApplier.PatchOp("remove", "/widgets[id=chart_w_abc12345]", null)
        );

        JsonNode result = applier.apply(baseDashboard, 1, ops);

        assertThat(result.get("widgets")).hasSize(0);
        assertThat(result.get("version").asInt()).isEqualTo(2);
    }

    @Test
    void atomicityRollsBackOnAnyOpFailure() throws Exception {
        // Try to replace a non-existent match key
        List<JsonPatchApplier.PatchOp> ops = List.of(
            new JsonPatchApplier.PatchOp("replace", "/widgets[id=nonexistent]/options/title", mapper.readValue("\"X\"", JsonNode.class))
        );

        assertThatThrownBy(() -> applier.apply(baseDashboard, 1, ops))
            .isInstanceOf(JsonPatchApplier.PatchRejectException.class);

        // Original should be unchanged
        assertThat(baseDashboard.get("version").asInt()).isEqualTo(1);
    }

    @Test
    void baseVersionMismatchThrows409Like() {
        assertThatThrownBy(() -> applier.apply(baseDashboard, 99, List.of()))
            .isInstanceOf(JsonPatchApplier.VersionConflictException.class);
    }

    @Test
    void plainPathReplaceOnTopLevelField() throws Exception {
        List<JsonPatchApplier.PatchOp> ops = List.of(
            new JsonPatchApplier.PatchOp("replace", "/title", mapper.readValue("\"New Title\"", JsonNode.class))
        );

        JsonNode result = applier.apply(baseDashboard, 1, ops);
        assertThat(result.get("title").asText()).isEqualTo("New Title");
        assertThat(result.get("version").asInt()).isEqualTo(2);
    }
}
