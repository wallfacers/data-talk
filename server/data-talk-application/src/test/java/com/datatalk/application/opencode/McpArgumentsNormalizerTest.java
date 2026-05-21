package com.datatalk.application.opencode;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpArgumentsNormalizerTest {

    private final McpArgumentsNormalizer normalizer = new McpArgumentsNormalizer(new ObjectMapper());

    @Test
    void parsesStringifiedObjectInsideTopLevelProperty() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of(
                "params", Map.of("type", "object")
            )
        );
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("params", "{\"type\":\"query_editor\"}");

        normalizer.normalize(args, schema, "datatalk_ui_exec");

        assertThat(args).containsEntry("params", Map.of("type", "query_editor"));
    }

    @Test
    void parsesStringifiedArrayInsideTopLevelProperty() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of(
                "edits", Map.of("type", "array", "items", Map.of("type", "object"))
            )
        );
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("edits", "[{\"text\":\"a\"}]");

        normalizer.normalize(args, schema, "datatalk_ui_exec");

        assertThat(args.get("edits")).isInstanceOf(List.class);
        List<?> parsed = (List<?>) args.get("edits");
        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0)).isEqualTo(Map.of("text", "a"));
    }

    @Test
    void resolvesObjectTypeThroughOneOfBranches() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "oneOf", List.of(
                Map.of(
                    "properties", Map.of(
                        "object", Map.of("type", "string", "enum", List.of("workspace")),
                        "params", Map.of("type", "object")
                    )
                ),
                Map.of(
                    "properties", Map.of(
                        "object", Map.of("type", "string", "enum", List.of("query_editor")),
                        "params", Map.of("type", "object")
                    )
                )
            )
        );
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("object", "workspace");
        args.put("action", "open");
        args.put("params", "{\"type\":\"query_editor\"}");

        normalizer.normalize(args, schema, "datatalk_ui_exec");

        assertThat(args).containsEntry("params", Map.of("type", "query_editor"));
    }

    @Test
    void recursesIntoNestedObjectSchema() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of(
                "params", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "edits", Map.of("type", "array", "items", Map.of("type", "object"))
                    )
                )
            )
        );
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("edits", "[{\"text\":\"a\"}]");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("params", inner);

        normalizer.normalize(args, schema, "datatalk_ui_exec");

        Map<?, ?> params = (Map<?, ?>) args.get("params");
        assertThat(params.get("edits")).isInstanceOf(List.class);
    }

    @Test
    void handlesDoublyStringifiedNesting() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of(
                "params", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "edits", Map.of("type", "array", "items", Map.of("type", "object"))
                    )
                )
            )
        );
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("params", "{\"edits\":\"[{\\\"text\\\":\\\"a\\\"}]\"}");

        normalizer.normalize(args, schema, "datatalk_ui_exec");

        Map<?, ?> params = (Map<?, ?>) args.get("params");
        assertThat(params.get("edits")).isInstanceOf(List.class);
    }

    @Test
    void doesNotCoerceNumberFromString() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of(
                "limit", Map.of("type", "number")
            )
        );
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("limit", "100");

        normalizer.normalize(args, schema, "datatalk_execute_sql");

        assertThat(args).containsEntry("limit", "100");
    }

    @Test
    void doesNotTouchAlreadyCorrectObjectValue() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of("params", Map.of("type", "object"))
        );
        Map<String, Object> params = Map.of("type", "query_editor");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("params", params);

        normalizer.normalize(args, schema, "datatalk_ui_exec");

        assertThat(args.get("params")).isSameAs(params);
    }

    @Test
    void skipsFieldsNotInSchema() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of("params", Map.of("type", "object"))
        );
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("unknown", "{\"a\":1}");

        normalizer.normalize(args, schema, "datatalk_ui_exec");

        assertThat(args).containsEntry("unknown", "{\"a\":1}");
    }

    @Test
    void leavesStringAloneWhenNotValidJson() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of("params", Map.of("type", "object"))
        );
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("params", "{not valid json");

        normalizer.normalize(args, schema, "datatalk_ui_exec");

        assertThat(args).containsEntry("params", "{not valid json");
    }

    @Test
    void handlesTypeDeclaredAsArrayWithNull() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of(
                "params", Map.of("type", List.of("object", "null"))
            )
        );
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("params", "{\"type\":\"query_editor\"}");

        normalizer.normalize(args, schema, "datatalk_ui_exec");

        assertThat(args).containsEntry("params", Map.of("type", "query_editor"));
    }

    @Test
    void parsesStringifiedObjectInsideArrayItem() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of(
                "edits", Map.of(
                    "type", "array",
                    "items", Map.of("type", "object")
                )
            )
        );
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("edits", new java.util.ArrayList<>(List.of("{\"text\":\"a\"}")));

        normalizer.normalize(args, schema, "datatalk_ui_exec");

        List<?> parsed = (List<?>) args.get("edits");
        assertThat(parsed.get(0)).isEqualTo(Map.of("text", "a"));
    }

    @Test
    void safeOnNullOrEmptyInputs() {
        normalizer.normalize(null, Map.of(), "tool");
        normalizer.normalize(new LinkedHashMap<>(), null, "tool");
        normalizer.normalize(new LinkedHashMap<>(), Map.of(), "tool");
    }
}
