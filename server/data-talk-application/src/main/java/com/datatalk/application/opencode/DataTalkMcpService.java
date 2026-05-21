package com.datatalk.application.opencode;

import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.domain.action.ActionDescriptor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        List<Map<String, Object>> content = new ArrayList<>(2);
        ImagePayload image = !outcome.isError() ? extractImagePayload(output) : null;
        Map<String, Object> imageMetadata = null;
        if (image != null) {
            // Strip the data URI from the text serialization so the same base64 payload
            // is not duplicated as both text and vision input — the model already "sees"
            // the bytes via the image content block, and the text block keeps the
            // observability metadata (fileId, originalBytes, compressionApplied, …).
            imageMetadata = new LinkedHashMap<>((Map<String, Object>) output);
            imageMetadata.remove("content");
            content.add(Map.of("type", "text", "text", serialize(imageMetadata)));
            content.add(Map.of(
                "type", "image",
                "data", image.data(),
                "mimeType", image.mimeType()
            ));
        } else {
            content.add(Map.of("type", "text", "text", serialize(output)));
        }
        result.put("content", content);

        if (!outcome.isError()) {
            if (image != null) {
                // structuredContent mirrors the metadata-only view: callers that want the
                // raw bytes should consume the dedicated image content block (or re-call
                // file_read which is idempotent). Keeping the data URI here would let
                // any caller that pastes the whole tool result into prompt context
                // double-pay the base64 token cost.
                result.put("structuredContent", imageMetadata);
            } else if (output instanceof Map<?, ?> rawMap) {
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

    private static final Pattern IMAGE_DATA_URI = Pattern.compile(
        "^data:(image/[a-zA-Z0-9.+-]+);base64,(.+)$", Pattern.DOTALL);

    /**
     * Detect a base64-encoded image payload in the action output and split it into a
     * MCP {@code image} content block. Models cannot OCR a raw base64 string embedded
     * inside a JSON text response — the MCP spec requires images to travel as their own
     * {@code {type:"image", data, mimeType}} content block so the AI SDK can forward
     * them as a vision part to the underlying LLM provider.
     *
     * <p>Detection rules (intentionally strict — every clause MUST hold to avoid
     * misclassifying ordinary text outputs that happen to embed a data URI string,
     * e.g. a CSV cell or a JSON value):
     * <ul>
     *   <li>output is a {@link Map}</li>
     *   <li>{@code compressedMimeType} is a non-blank {@code image/*} string — this is
     *       the strong signal that {@code FileReadActionHandler} actually walked the
     *       image branch and produced base64 bytes (text branch never emits this
     *       field, no other action emits it either)</li>
     *   <li>{@code content} is a {@code data:image/...;base64,...} string</li>
     * </ul>
     * Returns {@code null} otherwise — caller falls back to a single text content
     * block, preserving the legacy behaviour for every non-image case.
     */
    private static ImagePayload extractImagePayload(Object output) {
        if (!(output instanceof Map<?, ?> map)) {
            return null;
        }
        Object declared = map.get("compressedMimeType");
        if (!(declared instanceof String mimeType) || mimeType.isBlank() || !mimeType.startsWith("image/")) {
            return null;
        }
        Object contentValue = map.get("content");
        if (!(contentValue instanceof String contentStr)) {
            return null;
        }
        Matcher matcher = IMAGE_DATA_URI.matcher(contentStr);
        if (!matcher.matches()) {
            return null;
        }
        return new ImagePayload(matcher.group(2), mimeType);
    }

    private record ImagePayload(String data, String mimeType) {}

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
