package com.datatalk.adapter.actions;

import java.util.List;
import java.util.Map;

/**
 * JSON schema constants for the ui_find action input and output.
 */
final class UiFindSchemas {

    private UiFindSchemas() {}

    static final Map<String, Object> INPUT_SCHEMA = Map.of(
        "type", "object",
        "properties", Map.of(
            "outputMode", Map.of(
                "type", "string",
                "enum", List.of("metadata", "count", "content", "tabs_only", "read"),
                "description", "Output mode: metadata=list tab metadata, count=return total, content=FTS search, tabs_only=IDs only, read=full payload"
            ),
            "filter", Map.of(
                "type", "object",
                "properties", Map.of(
                    "scope", Map.of("type", "string", "enum", List.of("workspace", "session")),
                    "type", Map.of("type", "string", "description", "Tab type, e.g. query_editor, chart"),
                    "connectionId", Map.of("type", "string"),
                    "originSessionId", Map.of("type", "string"),
                    "includeArchived", Map.of("type", "boolean"),
                    "pinned", Map.of("type", "boolean"),
                    "lastTouchedAfter", Map.of("type", "integer", "description", "Epoch millis"),
                    "lastTouchedBefore", Map.of("type", "integer", "description", "Epoch millis"),
                    "limit", Map.of("type", "integer", "minimum", 1, "maximum", 10_000)
                )
            ),
            "contentQuery", Map.of(
                "type", "object",
                "properties", Map.of(
                    "pattern", Map.of("type", "string", "description", "FTS search pattern"),
                    "includeArchived", Map.of("type", "boolean"),
                    "limit", Map.of("type", "integer")
                )
            ),
            "reads", Map.of(
                "type", "array",
                "items", Map.of(
                    "type", "object",
                    "required", List.of("tabId"),
                    "properties", Map.of(
                        "tabId", Map.of("type", "string"),
                        "includePayload", Map.of("type", "boolean")
                    )
                )
            )
        )
    );

    static final Map<String, Object> OUTPUT_SCHEMA = Map.of(
        "type", "object",
        "required", List.of("outputMode"),
        "properties", Map.of(
            "outputMode", Map.of("type", "string"),
            "items", Map.of(
                "type", "array",
                "items", Map.of("type", "object")
            ),
            "tabIds", Map.of("type", "array", "items", Map.of("type", "string")),
            "totalMatched", Map.of("type", "integer"),
            "tabsMatched", Map.of("type", "integer"),
            "truncated", Map.of("type", "boolean"),
            "reads", Map.of("type", "array"),
            "warnings", Map.of("type", "array", "items", Map.of("type", "string"))
        )
    );
}
