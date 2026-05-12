package com.datatalk.application.ingestion.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

/**
 * Shared dot-path flattening logic for JSON object nodes.
 *
 * <p>Always called at depth 0 (top-level object per row).
 * Expansion rules:
 * <ul>
 *   <li>Primitive value at top level → {@code $.<key>}</li>
 *   <li>Nested object at top level → expand its children as {@code $.<parent>.<child>}</li>
 *   <li>If a child is itself an object → treat as JSON blob at {@code $.<parent>.<child>}</li>
 * </ul>
 */
final class DotPathFlattener {

    private final ObjectMapper om;

    DotPathFlattener(ObjectMapper om) {
        this.om = om;
    }

    /** Expose the ObjectMapper for callers that need readTree / createArrayNode. */
    ObjectMapper objectMapper() {
        return om;
    }

    /**
     * Flatten a top-level JSON object into dot-path → raw Java values.
     *
     * @param node    the object to flatten (always called at depth 0)
     * @param columns accumulator: sourcePath → list of raw values across rows
     */
    void flatten(ObjectNode node, Map<String, List<Object>> columns) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            JsonNode value = field.getValue();

            if (value.isObject()) {
                // Expand one level into child keys
                ObjectNode child = (ObjectNode) value;
                Iterator<Map.Entry<String, JsonNode>> childFields = child.fields();
                while (childFields.hasNext()) {
                    Map.Entry<String, JsonNode> childField = childFields.next();
                    String childKey = childField.getKey();
                    JsonNode childValue = childField.getValue();
                    String path = "$." + key + "." + childKey;

                    if (childValue.isObject() || childValue.isArray()) {
                        // Nested deeper → JSON blob
                        columns.computeIfAbsent(path, k -> new ArrayList<>())
                            .add(toJava(childValue));
                    } else {
                        columns.computeIfAbsent(path, k -> new ArrayList<>())
                            .add(rawValue(childValue));
                    }
                }
            } else if (value.isArray()) {
                // Top-level array → JSON blob
                columns.computeIfAbsent("$." + key, k -> new ArrayList<>())
                    .add(toJava(value));
            } else {
                String path = "$." + key;
                columns.computeIfAbsent(path, k -> new ArrayList<>()).add(rawValue(value));
            }
        }
    }

    /**
     * Convert a JsonNode to its Java equivalent for type inference.
     */
    private Object rawValue(JsonNode node) {
        if (node.isNull()) return null;
        if (node.isBoolean()) return node.booleanValue();
        if (node.isInt()) return node.intValue();
        if (node.isLong()) return node.longValue();
        if (node.isDouble()) return node.doubleValue();
        if (node.isBigDecimal()) return node.decimalValue();
        if (node.isTextual()) return node.textValue();
        // Fallback for unexpected types
        return toJava(node);
    }

    private Object toJava(JsonNode node) {
        if (node.isNull()) return null;
        if (node.isBoolean()) return node.booleanValue();
        if (node.isInt()) return node.intValue();
        if (node.isLong()) return node.longValue();
        if (node.isDouble()) return node.doubleValue();
        if (node.isBigDecimal()) return node.decimalValue();
        if (node.isTextual()) return node.textValue();
        if (node.isArray()) return om.convertValue(node, List.class);
        if (node.isObject()) return om.convertValue(node, Map.class);
        return node.asText();
    }
}
