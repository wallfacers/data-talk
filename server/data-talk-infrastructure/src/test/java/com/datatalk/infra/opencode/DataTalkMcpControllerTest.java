package com.datatalk.infra.opencode;

import com.datatalk.application.opencode.DataTalkMcpService;
import com.datatalk.application.opencode.McpActionBridge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DataTalkMcpControllerTest {

    private DataTalkMcpService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(DataTalkMcpService.class);
        mvc = MockMvcBuilders.standaloneSetup(new DataTalkMcpController(service, ""))
            .build();
    }

    @Test
    void initializeReturnsJsonRpcResult() throws Exception {
        when(service.initialize(anyMap())).thenReturn(Map.of(
            "protocolVersion", "2025-06-18",
            "capabilities", Map.of("tools", Map.of("listChanged", false)),
            "serverInfo", Map.of("name", "datatalk", "version", "dev")
        ));

        mvc.perform(loopback(post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"jsonrpc":"2.0","id":"req-1","method":"initialize","params":{"protocolVersion":"2025-06-18"}}
                    """)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jsonrpc").value("2.0"))
            .andExpect(jsonPath("$.id").value("req-1"))
            .andExpect(jsonPath("$.result.protocolVersion").value("2025-06-18"));
    }

    @Test
    void toolsCallUsesAsyncDeferredResult() throws Exception {
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        when(service.httpTimeoutMs("execute_sql")).thenReturn(35_000L);
        when(service.callTool(eq("execute_sql"), anyMap())).thenReturn(future);

        MvcResult pending = mvc.perform(loopback(post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"jsonrpc":"2.0","id":"req-2","method":"tools/call","params":{"name":"execute_sql","arguments":{"sql":"select 1"}}}
                    """)))
            .andExpect(request().asyncStarted())
            .andReturn();

        future.complete(Map.of(
            "content", List.of(Map.of("type", "text", "text", "{\"ok\":true}")),
            "structuredContent", Map.of("ok", true)
        ));

        mvc.perform(asyncDispatch(pending))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("req-2"))
            .andExpect(jsonPath("$.result.content[0].type").value("text"))
            .andExpect(jsonPath("$.result.structuredContent.ok").value(true));
    }

    @Test
    void toolsCallMapsProtocolErrorsToJsonRpcError() throws Exception {
        when(service.httpTimeoutMs("execute_sql")).thenReturn(35_000L);
        when(service.callTool(eq("execute_sql"), anyMap()))
            .thenReturn(CompletableFuture.failedStage(new McpActionBridge.McpCallException(-32001, "unauthenticated bridge")));

        MvcResult pending = mvc.perform(loopback(post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"jsonrpc":"2.0","id":"req-3","method":"tools/call","params":{"name":"execute_sql","arguments":{"sql":"select 1"}}}
                    """)))
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(pending))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("req-3"))
            .andExpect(jsonPath("$.error.code").value(-32001))
            .andExpect(jsonPath("$.error.message").value("unauthenticated bridge"));
    }

    @Test
    void rejectsNonLoopbackRequests() throws Exception {
        mvc.perform(post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"jsonrpc":"2.0","id":"req-4","method":"tools/list"}
                    """)
                .with(request -> {
                    request.setRemoteAddr("10.0.0.10");
                    return request;
                }))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnexpectedOriginHeader() throws Exception {
        mvc.perform(loopback(post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Origin", "https://evil.example")
                .content("""
                    {"jsonrpc":"2.0","id":"req-5","method":"tools/list"}
                    """)))
            .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder loopback(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr("127.0.0.1");
            return request;
        });
    }
}
