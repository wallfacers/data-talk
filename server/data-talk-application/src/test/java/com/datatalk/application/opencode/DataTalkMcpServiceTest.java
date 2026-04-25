package com.datatalk.application.opencode;

import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.domain.action.ActionDescriptor;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    private static ActionDescriptor descriptor(String id, Executor executor) {
        return new ActionDescriptor(id, executor, "desc", Map.of(), Map.of(),
            List.of(), List.of(OntologyEffect.NONE), false, 3_000, null, null);
    }
}
