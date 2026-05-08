package com.datatalk.application.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DashboardSchemaValidator {

    private final JsonSchema schema;

    public DashboardSchemaValidator(ObjectMapper mapper) {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        InputStream is = getClass().getResourceAsStream("/dashboard/dashboard-schema.json");
        if (is == null) {
            throw new IllegalStateException("dashboard-schema.json not found on classpath");
        }
        this.schema = factory.getSchema(is);
    }

    public ValidationResult validate(JsonNode doc) {
        List<ValidationResult.Error> errors = new ArrayList<>();

        // JSON Schema validation
        Set<ValidationMessage> messages = schema.validate(doc);
        for (ValidationMessage msg : messages) {
            errors.add(new ValidationResult.Error(
                msg.getInstanceLocation().toString(),
                msg.getCode(),
                msg.getMessage()
            ));
        }

        // Cross-widget validations (only if schema-level passed enough to have widgets)
        JsonNode widgets = doc.get("widgets");
        if (widgets != null && widgets.isArray()) {
            crossValidateWidgets(widgets, doc, errors);
        }

        return new ValidationResult(errors);
    }

    private void crossValidateWidgets(JsonNode widgets, JsonNode doc, List<ValidationResult.Error> errors) {
        // 1. Duplicate widget IDs
        Set<String> seenIds = new HashSet<>();
        for (JsonNode w : widgets) {
            JsonNode idNode = w.get("id");
            if (idNode != null) {
                String id = idNode.asText();
                if (!seenIds.add(id)) {
                    errors.add(new ValidationResult.Error(
                        "/widgets[id=" + id + "]",
                        "duplicate_widget_id",
                        "Duplicate widget id: " + id
                    ));
                }
            }
        }

        // 2. Layout overlap check + z=0 in grid mode
        JsonNode layoutNode = doc.get("layout");
        boolean isGrid = layoutNode != null
            && layoutNode.has("engine")
            && "grid".equals(layoutNode.path("engine").asText());

        List<int[]> rects = new ArrayList<>(); // [x, y, w, h]
        for (int i = 0; i < widgets.size(); i++) {
            JsonNode w = widgets.get(i);
            JsonNode pos = w.get("position");
            if (pos == null) continue;

            int x = pos.path("x").asInt(0);
            int y = pos.path("y").asInt(0);
            int ww = pos.path("w").asInt(0);
            int h = pos.path("h").asInt(0);

            // z must be 0 in grid mode
            if (isGrid && pos.has("z") && !pos.get("z").isNull() && pos.get("z").asInt() != 0) {
                String wid = w.path("id").asText("?");
                errors.add(new ValidationResult.Error(
                    "/widgets[" + i + "]/position/z",
                    "z_must_be_zero_in_grid",
                    "Widget " + wid + " has z=" + pos.get("z").asInt() + " but grid mode requires z=0 or null"
                ));
            }

            rects.add(new int[]{x, y, ww, h});
        }

        // Overlap detection (O(n^2) is fine for P1)
        for (int i = 0; i < rects.size(); i++) {
            for (int j = i + 1; j < rects.size(); j++) {
                if (overlaps(rects.get(i), rects.get(j))) {
                    String idA = widgets.get(i).path("id").asText("?");
                    String idB = widgets.get(j).path("id").asText("?");
                    errors.add(new ValidationResult.Error(
                        "/widgets",
                        "overlap",
                        "Widgets " + idA + " and " + idB + " overlap"
                    ));
                }
            }
        }
    }

    private static boolean overlaps(int[] a, int[] b) {
        // a = [x, y, w, h], b = [x, y, w, h]
        return a[0] < b[0] + b[2] && a[0] + a[2] > b[0]
            && a[1] < b[1] + b[3] && a[1] + a[3] > b[1];
    }
}
