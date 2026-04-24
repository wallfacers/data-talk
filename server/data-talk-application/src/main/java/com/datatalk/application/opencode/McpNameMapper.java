package com.datatalk.application.opencode;

import com.datatalk.domain.action.ActionDescriptor;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class McpNameMapper {

    public static final String SERVER_NAME = "datatalk";
    private static final String SERVER_PREFIX = SERVER_NAME + ".";
    private static final String OPENCODE_PREFIX = SERVER_NAME + "_";

    private final Map<String, String> actionIdToMcpToolName;
    private final Map<String, String> mcpToolNameToActionId;
    private final Map<String, String> openCodeToolNameToActionId;

    public McpNameMapper(Collection<ActionDescriptor> descriptors) {
        this(descriptors.stream().map(ActionDescriptor::id).toList(), true);
    }

    public static McpNameMapper forActionIds(List<String> actionIds) {
        return new McpNameMapper(actionIds, false);
    }

    private McpNameMapper(Collection<String> actionIds, boolean unused) {
        Map<String, String> actionToMcp = new LinkedHashMap<>();
        Map<String, String> mcpToAction = new LinkedHashMap<>();
        Map<String, String> openCodeToAction = new LinkedHashMap<>();

        for (String actionId : actionIds) {
            String mcpToolName = sanitize(rawToolName(actionId));
            String openCodeToolName = OPENCODE_PREFIX + mcpToolName;
            putUnique(actionToMcp, actionId, mcpToolName, "action id");
            putUnique(mcpToAction, mcpToolName, actionId, "MCP tool name");
            putUnique(openCodeToAction, openCodeToolName, actionId, "OpenCode tool name");
        }

        this.actionIdToMcpToolName = Collections.unmodifiableMap(actionToMcp);
        this.mcpToolNameToActionId = Collections.unmodifiableMap(mcpToAction);
        this.openCodeToolNameToActionId = Collections.unmodifiableMap(openCodeToAction);
    }

    public String toMcpToolName(String actionId) {
        return require(actionIdToMcpToolName, actionId, "action id");
    }

    public String toOpenCodeToolName(String actionId) {
        return OPENCODE_PREFIX + toMcpToolName(actionId);
    }

    public String toActionIdFromMcpToolName(String mcpToolName) {
        return require(mcpToolNameToActionId, mcpToolName, "MCP tool name");
    }

    public String toActionIdFromOpenCodeToolName(String openCodeToolName) {
        return require(openCodeToolNameToActionId, openCodeToolName, "OpenCode tool name");
    }

    public Set<String> mcpToolNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(mcpToolNameToActionId.keySet()));
    }

    public Set<String> openCodeToolNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(openCodeToolNameToActionId.keySet()));
    }

    private static String rawToolName(String actionId) {
        if (!actionId.startsWith(SERVER_PREFIX)) {
            throw new IllegalArgumentException("Unsupported action id for MCP mapping: " + actionId);
        }
        return actionId.substring(SERVER_PREFIX.length()).replace('.', '_');
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static String require(Map<String, String> index, String key, String label) {
        String value = index.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Unknown " + label + ": " + key);
        }
        return value;
    }

    private static void putUnique(Map<String, String> index, String key, String value, String label) {
        String existing = index.putIfAbsent(key, value);
        if (existing != null) {
            throw new IllegalStateException("Duplicate " + label + ": " + key);
        }
    }
}
