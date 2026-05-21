package com.datatalk.adapter.actions;

import java.util.List;
import java.util.Map;

/**
 * JSON schema constants for the ui_find action input and output.
 */
final class UiFindSchemas {

    private UiFindSchemas() {
    }

    static final Map<String, Object> INPUT_SCHEMA = Map.of(
        "type", "object",
        "properties", Map.of(
            "filter", Map.of(
                "type", "object",
                "description", "Metadata filters (AND-combined). All fields optional.",
                "properties", Map.ofEntries(
                    Map.entry("type", Map.of("type", "string")),
                    Map.entry("connectionId", Map.of("type", "string")),
                    Map.entry("objectId", Map.of("type", "string")),
                    Map.entry("originSessionId", Map.of("type", "string")),
                    Map.entry("lastTouchedAfter", Map.of("type", "integer")),
                    Map.entry("lastTouchedBefore", Map.of("type", "integer")),
                    Map.entry("includeArchived", Map.of("type", "boolean", "default", false)),
                    Map.entry("pinned", Map.of("type", "boolean"))
                )
            ),
            "query", Map.of(
                "type", "object",
                "description", "Content match (omit for metadata-only listing).",
                "properties", Map.of(
                    "mode", Map.of("type", "string", "enum", List.of("substring", "regex", "fts")),
                    "pattern", Map.of("type", "string"),
                    "caseInsensitive", Map.of("type", "boolean", "default", true),
                    "multiline", Map.of("type", "boolean", "default", false)
                ),
                "required", List.of("mode", "pattern")
            ),
            "read", Map.of(
                "type", "object",
                "description", "Read content from named tabs.",
                "properties", Map.of(
                    "tabIds", Map.of("type", "array", "items", Map.of("type", "string")),
                    "range", Map.of("oneOf", List.of(
                        Map.of("type", "string", "enum", List.of("full")),
                        Map.of(
                            "type", "object",
                            "properties", Map.of(
                                "lineStart", Map.of("type", "integer", "minimum", 1),
                                "lineEnd", Map.of("type", "integer", "minimum", 1)
                            ),
                            "required", List.of("lineStart", "lineEnd")
                        )
                    ), "default", "full"),
                    "contextLines", Map.of("type", "integer", "minimum", 0, "maximum", 20, "default", 0)
                )
            ),
            "output", Map.of(
                "type", "object",
                "properties", Map.of(
                    "mode", Map.of("type", "string", "enum", List.of("metadata", "matches", "tabs_only", "count"), "default", "metadata"),
                    "headLimit", Map.of("type", "integer", "minimum", 1, "default", 100),
                    "maxTabs", Map.of("type", "integer", "minimum", 1, "default", 50)
                )
            )
        )
    );

    static final Map<String, Object> OUTPUT_SCHEMA = Map.of(
        "type", "object",
        "properties", Map.ofEntries(
            Map.entry("items", Map.of("type", "array", "items", Map.of("type", "object"))),
            Map.entry("tabIds", Map.of("type", "array", "items", Map.of("type", "string"))),
            Map.entry("totalMatched", Map.of("type", "integer")),
            Map.entry("tabsMatched", Map.of("type", "integer")),
            Map.entry("truncated", Map.of("type", "boolean")),
            Map.entry("reads", Map.of("type", "array", "items", Map.of("type", "object"))),
            Map.entry("warnings", Map.of("type", "array", "items", Map.of("type", "string"))),
            Map.entry("error", Map.of("type", "object"))
        )
    );
}
