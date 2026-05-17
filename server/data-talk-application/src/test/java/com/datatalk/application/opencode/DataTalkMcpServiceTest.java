package com.datatalk.application.opencode;

import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.domain.action.ActionDescriptor;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataTalkMcpServiceTest {

    private McpActionBridge bridge;
    private DataTalkMcpService service;

    @BeforeEach
    void setUp() {
        ActionRegistry registry = mock(ActionRegistry.class);
        when(registry.mcpExposed()).thenReturn(List.of(
            descriptor("datatalk.execute_sql", Executor.SERVER),
            descriptor("datatalk.ui.read", Executor.CLIENT)
        ));
        bridge = mock(McpActionBridge.class);
        when(bridge.handle(anyString(), any())).thenReturn(
            CompletableFuture.completedFuture(McpActionBridge.ToolCallOutcome.success(Map.of("ok", true)))
        );
        service = new DataTalkMcpService(registry, bridge, new ObjectMapper());
    }

    @Test
    void successMapOutputIncludesStructuredContent() {
        when(bridge.handle(anyString(), any())).thenReturn(
            CompletableFuture.completedFuture(McpActionBridge.ToolCallOutcome.success(
                Map.of("artifactId", "a1", "rowCount", 5)
            ))
        );

        Map<String, Object> result = service.callTool("execute_sql", Map.of()).toCompletableFuture().join();

        assertThat(result).containsKey("structuredContent");
        @SuppressWarnings("unchecked")
        Map<String, Object> sc = (Map<String, Object>) result.get("structuredContent");
        assertThat(sc.get("artifactId")).isEqualTo("a1");
        assertThat(result).doesNotContainKey("isError");
    }

    @Test
    void errorOutputOmitsStructuredContent() {
        when(bridge.handle(anyString(), any())).thenReturn(
            CompletableFuture.completedFuture(McpActionBridge.ToolCallOutcome.error(
                Map.of("message", "SQL syntax error")
            ))
        );

        Map<String, Object> result = service.callTool("execute_sql", Map.of()).toCompletableFuture().join();

        assertThat(result).doesNotContainKey("structuredContent");
        assertThat(result.get("isError")).isEqualTo(true);
        assertThat(result).containsKey("content");
    }

    @Test
    void listOutputWrapsInItemsForStructuredContent() {
        List<Map<String, Object>> actions = List.of(
            Map.of("name", "open", "description", "Open a tab"),
            Map.of("name", "close", "description", "Close a tab")
        );
        when(bridge.handle(anyString(), any())).thenReturn(
            CompletableFuture.completedFuture(McpActionBridge.ToolCallOutcome.success(actions))
        );

        Map<String, Object> result = service.callTool("ui_read", Map.of()).toCompletableFuture().join();

        assertThat(result).containsKey("structuredContent");
        @SuppressWarnings("unchecked")
        Map<String, Object> sc = (Map<String, Object>) result.get("structuredContent");
        assertThat(sc.get("items")).isEqualTo(actions);
        assertThat(result).doesNotContainKey("isError");
    }

    @Test
    void textContentAlwaysPresentRegardlessOfErrorState() {
        when(bridge.handle(anyString(), any())).thenReturn(
            CompletableFuture.completedFuture(McpActionBridge.ToolCallOutcome.error(
                Map.of("message", "timeout")
            ))
        );

        Map<String, Object> result = service.callTool("execute_sql", Map.of()).toCompletableFuture().join();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("type")).isEqualTo("text");
        assertThat(content.get(0).get("text")).asString().contains("timeout");
    }

    @Test
    void imageDataUriOutputIsSplitIntoTextAndImageContentBlocks() {
        // Realistic shape of FileReadActionHandler output for an image file: a `content`
        // field holding a `data:image/...;base64,...` URI plus the compression metadata.
        // The MCP layer MUST surface this to the model as a separate `image` content
        // block — otherwise the LLM only sees the JSON-stringified base64 and cannot
        // actually look at the picture (root cause of BUG-0060).
        String base64 = "/9j/4AAQSkZJRgABAQEASABIAAD/REPLACED_BY_REAL_BASE64==";
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("fileId", "f1");
        output.put("offset", 0);
        output.put("content", "data:image/jpeg;base64," + base64);
        output.put("bytesRead", 19242);
        output.put("originalBytes", 40678);
        output.put("compressedBytes", 19242);
        output.put("compressionApplied", true);
        output.put("compressedMimeType", "image/jpeg");
        when(bridge.handle(anyString(), any())).thenReturn(
            CompletableFuture.completedFuture(McpActionBridge.ToolCallOutcome.success(output))
        );

        Map<String, Object> result = service.callTool("file_read", Map.of()).toCompletableFuture().join();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertThat(content).hasSize(2);

        Map<String, Object> textBlock = content.get(0);
        assertThat(textBlock.get("type")).isEqualTo("text");
        // Text block keeps observability metadata but MUST NOT include the bulky base64
        // (avoids ~2× token cost from duplicating the payload as both text and image).
        assertThat(textBlock.get("text")).asString()
            .contains("\"fileId\":\"f1\"")
            .contains("\"compressionApplied\":true")
            .contains("\"compressedMimeType\":\"image/jpeg\"")
            .doesNotContain(base64)
            .doesNotContain("data:image");

        Map<String, Object> imageBlock = content.get(1);
        assertThat(imageBlock.get("type")).isEqualTo("image");
        assertThat(imageBlock.get("mimeType")).isEqualTo("image/jpeg");
        // Critical: `data` MUST be raw base64 with no `data:` URI prefix — MCP spec
        // forbids the prefix and AI SDK validators reject it.
        assertThat(imageBlock.get("data")).isEqualTo(base64);

        // structuredContent retains the full original output (incl. content data URI)
        // so programmatic clients can still recover the unmodified handler response.
        @SuppressWarnings("unchecked")
        Map<String, Object> structured = (Map<String, Object>) result.get("structuredContent");
        assertThat(structured.get("content")).asString().startsWith("data:image/jpeg;base64,");
    }

    @Test
    void imageBranchToleratesMissingCompressedMimeTypeViaDataUriHeader() {
        // Older callers may only set the data URI without compressedMimeType.
        // We should still emit an `image` content block, inferring mimeType from the URI.
        String base64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=";
        Map<String, Object> output = Map.of(
            "fileId", "f2",
            "content", "data:image/png;base64," + base64,
            "bytesRead", 70
        );
        when(bridge.handle(anyString(), any())).thenReturn(
            CompletableFuture.completedFuture(McpActionBridge.ToolCallOutcome.success(output))
        );

        Map<String, Object> result = service.callTool("file_read", Map.of()).toCompletableFuture().join();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertThat(content).hasSize(2);
        assertThat(content.get(1).get("mimeType")).isEqualTo("image/png");
        assertThat(content.get(1).get("data")).isEqualTo(base64);
    }

    @Test
    void nonImageContentFieldStaysAsSingleTextBlock() {
        // Defensive: a string `content` that does not look like an image data URI
        // (e.g. text file read output) must keep the legacy single-text behavior.
        Map<String, Object> output = Map.of(
            "fileId", "f3",
            "offset", 0,
            "content", "id,name\n1,foo\n2,bar\n",
            "bytesRead", 20
        );
        when(bridge.handle(anyString(), any())).thenReturn(
            CompletableFuture.completedFuture(McpActionBridge.ToolCallOutcome.success(output))
        );

        Map<String, Object> result = service.callTool("file_read", Map.of()).toCompletableFuture().join();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("type")).isEqualTo("text");
        assertThat(content.get(0).get("text")).asString().contains("id,name");
    }

    @Test
    void errorOutputWithImageDataUriDoesNotEmitImageContent() {
        // Belt and braces: image extraction should be gated on non-error outcomes
        // so a failure payload that coincidentally contains "data:image/..." text
        // (e.g. echoed user input) does not get split.
        Map<String, Object> output = Map.of(
            "message", "rejected",
            "content", "data:image/jpeg;base64,AAAA"
        );
        when(bridge.handle(anyString(), any())).thenReturn(
            CompletableFuture.completedFuture(McpActionBridge.ToolCallOutcome.error(output))
        );

        Map<String, Object> result = service.callTool("file_read", Map.of()).toCompletableFuture().join();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("type")).isEqualTo("text");
        assertThat(result.get("isError")).isEqualTo(true);
    }

    @Test
    void oversizedSuccessOutputIsReplacedWithCompactTruncationPayload() {
        DataTalkMcpService smallBudgetService = new DataTalkMcpService(
            registryWith("datatalk.execute_sql", Executor.SERVER),
            bridge,
            new ObjectMapper(),
            120
        );
        when(bridge.handle(anyString(), any())).thenReturn(
            CompletableFuture.completedFuture(McpActionBridge.ToolCallOutcome.success(
                Map.of("rows", "x".repeat(500))
            ))
        );

        Map<String, Object> result = smallBudgetService.callTool("execute_sql", Map.of()).toCompletableFuture().join();

        @SuppressWarnings("unchecked")
        Map<String, Object> structured = (Map<String, Object>) result.get("structuredContent");
        assertThat(structured)
            .containsEntry("truncated", true)
            .containsEntry("reason", "output_too_large");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertThat(content.getFirst().get("text")).asString()
            .contains("\"truncated\":true")
            .contains("Use narrower arguments");
    }

    private static ActionDescriptor descriptor(String id, Executor executor) {
        return new ActionDescriptor(id, executor, "desc", Map.of(), Map.of(),
            List.of(), List.of(OntologyEffect.NONE), false, 3_000, null, null);
    }

    private static ActionRegistry registryWith(String id, Executor executor) {
        ActionRegistry registry = mock(ActionRegistry.class);
        when(registry.mcpExposed()).thenReturn(List.of(descriptor(id, executor)));
        return registry;
    }
}
