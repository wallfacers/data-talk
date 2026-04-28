package com.datatalk.application.opencode;

import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.domain.action.ActionDescriptor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

@Component
public class DataTalkMcpService {

    private static final long HTTP_TIMEOUT_BUFFER_MS = 5_000L;
    private static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";
    private static final int DEFAULT_MAX_TOOL_RESULT_BYTES = 128 * 1024;

    private final ActionRegistry registry;
    private final McpActionBridge bridge;
    private final ObjectMapper objectMapper;
    private final int maxToolResultBytes;

    @Autowired
    public DataTalkMcpService(ActionRegistry registry, McpActionBridge bridge, ObjectMapper objectMapper) {
        this(registry, bridge, objectMapper, DEFAULT_MAX_TOOL_RESULT_BYTES);
    }

    DataTalkMcpService(ActionRegistry registry, McpActionBridge bridge, ObjectMapper objectMapper, int maxToolResultBytes) {
        this.registry = registry;
        this.bridge = bridge;
        this.objectMapper = objectMapper;
        this.maxToolResultBytes = maxToolResultBytes;
    }

    public Map<String, Object> initialize(Map<String, Object> params) {
        String protocolVersion = stringValue(params.get("protocolVersion"));
        if (protocolVersion == null || protocolVersion.isBlank()) {
            protocolVersion = DEFAULT_PROTOCOL_VERSION;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", protocolVersion);
        result.put("capabilities", Map.of("tools", Map.of("listChanged", false)));
        result.put("serverInfo", Map.of(
            "name", McpNameMapper.SERVER_NAME,
            "version", implementationVersion()
        ));
        return result;
    }

    public void initialized() {
        // No-op for the minimal server.
    }

    public Map<String, Object> listTools() {
        McpNameMapper mapper = new McpNameMapper(registry.mcpExposed());
        List<Map<String, Object>> tools = registry.mcpExposed().stream()
            .map(descriptor -> toToolDefinition(mapper, descriptor))
            .toList();
        return Map.of("tools", tools);
    }

    public long httpTimeoutMs(String mcpToolName) {
        String actionId = new McpNameMapper(registry.mcpExposed()).toActionIdFromMcpToolName(mcpToolName);
        return registry.require(actionId).timeoutMs() + HTTP_TIMEOUT_BUFFER_MS;
    }

    public CompletionStage<Map<String, Object>> callTool(String mcpToolName, Map<String, Object> arguments) {
        return bridge.handle(mcpToolName, arguments)
            .thenApply(this::toToolResult);
    }

    private Map<String, Object> toToolDefinition(McpNameMapper mapper, ActionDescriptor descriptor) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", mapper.toMcpToolName(descriptor.id()));
        tool.put("description", descriptor.description());
        tool.put("inputSchema", descriptor.inputSchema());
        if (descriptor.outputSchema() != null && !descriptor.outputSchema().isEmpty()) {
            tool.put("outputSchema", descriptor.outputSchema());
        }
        return tool;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toToolResult(McpActionBridge.ToolCallOutcome outcome) {
        Object output = outputWithinBudget(outcome.output());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", List.of(Map.of(
            "type", "text",
            "text", serialize(output)
        )));
        if (!outcome.isError()) {
            if (output instanceof Map<?, ?> rawMap) {
                result.put("structuredContent", (Map<String, Object>) rawMap);
            } else if (output instanceof List<?> list) {
                result.put("structuredContent", Map.of("items", list));
            }
        }
        if (outcome.isError()) {
            result.put("isError", true);
        }
        return result;
    }

    private Object outputWithinBudget(Object output) {
        String serialized = serialize(output);
        int bytes = serialized.getBytes(StandardCharsets.UTF_8).length;
        if (bytes <= maxToolResultBytes) {
            return output;
        }
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("truncated", true);
        compact.put("reason", "output_too_large");
        compact.put("originalBytes", bytes);
        compact.put("maxBytes", maxToolResultBytes);
        compact.put("hint", "Use narrower arguments, such as pattern, limit, explicit tables, pageSize, or cursor.");
        return compact;
    }

    private String serialize(Object output) {
        if (output instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(output);
        } catch (JsonProcessingException e) {
            return String.valueOf(output);
        }
    }

    private String implementationVersion() {
        String version = DataTalkMcpService.class.getPackage().getImplementationVersion();
        return (version == null || version.isBlank()) ? "dev" : version;
    }

    private static String stringValue(Object value) {
        if (value instanceof String text) {
            return text;
        }
        return null;
    }
}
