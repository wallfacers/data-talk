package com.datatalk.application.opencode;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lenient JSON-string → object/array auto-parse for MCP tool arguments.
 *
 * <p>Some LLMs (notably qwen / glm / deepseek variants) serialize nested
 * object or array fields as JSON strings in tool call arguments. The raw
 * JSON Schema validator (correctly) rejects these; this normalizer walks
 * the schema in parallel with the value and rewrites such fields in place
 * so structured inputs survive LLM quirks.
 *
 * <p>Scope is intentionally narrow:
 * <ul>
 *   <li>Only types {@code object} or {@code array} are parsed;</li>
 *   <li>Number / boolean / enum coercion is NOT performed — silent string
 *       → primitive conversion is a common source of misinterpretation;</li>
 *   <li>Every rewrite emits a WARN log with tool name and field path, so
 *       noisy models surface in telemetry instead of being silently fixed.</li>
 * </ul>
 */
@Component
public class McpArgumentsNormalizer {

    private static final Logger log = LoggerFactory.getLogger(McpArgumentsNormalizer.class);

    private final ObjectMapper om;

    public McpArgumentsNormalizer(ObjectMapper om) {
        this.om = om;
    }

    public void normalize(Map<String, Object> args, Map<String, Object> schema, String toolName) {
        if (args == null || args.isEmpty() || schema == null || schema.isEmpty()) return;
        normalizeObject(args, schema, "", toolName);
    }

    @SuppressWarnings("unchecked")
    private void normalizeObject(Map<String, Object> obj, Map<String, Object> schema, String path, String toolName) {
        if (obj == null || schema == null) return;

        Map<String, List<Map<String, Object>>> propertySchemas = new LinkedHashMap<>();
        collectProperties(schema, propertySchemas);

        for (String key : new ArrayList<>(obj.keySet())) {
            List<Map<String, Object>> propSchemas = propertySchemas.get(key);
            if (propSchemas == null || propSchemas.isEmpty()) continue;

            Object value = obj.get(key);
            String childPath = path.isEmpty() ? key : path + "." + key;
            Set<String> expectedTypes = collectTypes(propSchemas);

            if (value instanceof String raw && !raw.isEmpty()) {
                String trimmed = raw.stripLeading();
                Object replacement = null;
                String replacementType = null;
                if (expectedTypes.contains("object") && trimmed.startsWith("{")) {
                    Map<String, Object> parsed = tryParseObject(raw);
                    if (parsed != null) {
                        replacement = parsed;
                        replacementType = "object";
                    }
                } else if (expectedTypes.contains("array") && trimmed.startsWith("[")) {
                    List<Object> parsed = tryParseArray(raw);
                    if (parsed != null) {
                        replacement = parsed;
                        replacementType = "array";
                    }
                }
                if (replacement != null) {
                    log.warn("[mcp-normalize] auto-parsed stringified field tool={} path={} parsed={}",
                        toolName, childPath, replacementType);
                    obj.put(key, replacement);
                    value = replacement;
                }
            }

            if (value instanceof Map<?, ?> childObj) {
                for (Map<String, Object> ps : propSchemas) {
                    normalizeObject((Map<String, Object>) childObj, ps, childPath, toolName);
                }
            } else if (value instanceof List<?> childArr) {
                for (Map<String, Object> ps : propSchemas) {
                    Object items = ps.get("items");
                    if (items instanceof Map<?, ?> itemSchema) {
                        normalizeArray(childArr, (Map<String, Object>) itemSchema, childPath, toolName);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void normalizeArray(List<?> arr, Map<String, Object> itemSchema, String path, String toolName) {
        for (int i = 0; i < arr.size(); i++) {
            Object item = arr.get(i);
            String childPath = path + "[" + i + "]";
            if (item instanceof Map<?, ?> m) {
                normalizeObject((Map<String, Object>) m, itemSchema, childPath, toolName);
            } else if (item instanceof String raw && !raw.isEmpty()) {
                Set<String> expectedTypes = collectTypes(List.of(itemSchema));
                String trimmed = raw.stripLeading();
                if (expectedTypes.contains("object") && trimmed.startsWith("{")) {
                    Map<String, Object> parsed = tryParseObject(raw);
                    if (parsed != null) {
                        log.warn("[mcp-normalize] auto-parsed stringified field tool={} path={} parsed=object",
                            toolName, childPath);
                        ((List<Object>) arr).set(i, parsed);
                        normalizeObject(parsed, itemSchema, childPath, toolName);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectProperties(Map<String, Object> schema, Map<String, List<Map<String, Object>>> out) {
        Object props = schema.get("properties");
        if (props instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() instanceof String k && e.getValue() instanceof Map<?, ?> v) {
                    out.computeIfAbsent(k, kk -> new ArrayList<>()).add((Map<String, Object>) v);
                }
            }
        }
        for (String combinator : List.of("oneOf", "anyOf", "allOf")) {
            Object branches = schema.get(combinator);
            if (branches instanceof List<?> list) {
                for (Object branch : list) {
                    if (branch instanceof Map<?, ?> b) {
                        collectProperties((Map<String, Object>) b, out);
                    }
                }
            }
        }
    }

    private Set<String> collectTypes(List<Map<String, Object>> schemas) {
        Set<String> types = new HashSet<>();
        for (Map<String, Object> s : schemas) {
            addTypes(s.get("type"), types);
            for (String combinator : List.of("oneOf", "anyOf", "allOf")) {
                Object branches = s.get(combinator);
                if (branches instanceof List<?> list) {
                    for (Object branch : list) {
                        if (branch instanceof Map<?, ?> b) {
                            addTypes(b.get("type"), types);
                        }
                    }
                }
            }
        }
        return types;
    }

    private static void addTypes(Object typeField, Set<String> out) {
        if (typeField instanceof String s) {
            out.add(s);
        } else if (typeField instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) out.add(s);
            }
        } else if (typeField instanceof String[] arr) {
            out.addAll(Arrays.asList(arr));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tryParseObject(String raw) {
        try {
            Object parsed = om.readValue(raw, Object.class);
            if (parsed instanceof Map<?, ?> m) {
                return (Map<String, Object>) m;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Object> tryParseArray(String raw) {
        try {
            Object parsed = om.readValue(raw, Object.class);
            if (parsed instanceof List<?> l) {
                return (List<Object>) l;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
