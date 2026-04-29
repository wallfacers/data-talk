package com.datatalk.infra.opencode;

import com.datatalk.application.opencode.DataTalkMcpService;
import com.datatalk.application.opencode.McpActionBridge;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;

@RestController
@RequestMapping("/mcp")
public class DataTalkMcpController {

    private static final Logger log = LoggerFactory.getLogger(DataTalkMcpController.class);

    private final DataTalkMcpService service;
    private final Set<String> allowedOrigins;

    public DataTalkMcpController(DataTalkMcpService service,
                                 @Value("${datatalk.mcp.allowed-origins:}") String allowedOrigins) {
        this.service = service;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * MCP Streamable HTTP 客户端会主动 GET 同一 endpoint 来打开可选的
     * server-to-client SSE 通道。本服务只走 request/response 模式，按规范返回
     * 405 + Allow: POST。客户端读到该响应后会自动回退到 only-POST 模式，属预期
     * 行为，因此这里不打 WARN（避免每次启动都把日志刷成 405）。
     */
    @GetMapping
    public ResponseEntity<Void> noServerStream() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ALLOW, "POST");
        return new ResponseEntity<>(headers, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @PostMapping
    public Object handle(@RequestBody Map<String, Object> requestBody,
                         HttpServletRequest request,
                         @RequestHeader(value = "Origin", required = false) String origin) {
        if (!isLoopback(request.getRemoteAddr())) {
            log.warn("[mcp-controller] rejected non-loopback remoteAddr={} origin={}", request.getRemoteAddr(), origin);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!isOriginAllowed(origin)) {
            log.warn("[mcp-controller] rejected disallowed origin={} allowed={}", origin, allowedOrigins);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Object id = requestBody.get("id");
        try {
            String method = requireString(requestBody.get("method"), "method");
            Map<String, Object> params = asMap(requestBody.get("params"));
            return switch (method) {
                case "initialize" -> ResponseEntity.ok(rpcResult(id, service.initialize(params)));
                case "notifications/initialized" -> id == null
                    ? ResponseEntity.accepted().build()
                    : ResponseEntity.ok(rpcResult(id, Map.of()));
                case "tools/list" -> ResponseEntity.ok(rpcResult(id, service.listTools()));
                case "tools/call" -> handleToolsCall(id, params);
                default -> {
                    log.warn("[mcp-controller] method not found method={}", method);
                    yield ResponseEntity.ok(rpcError(id, -32601, "Method not found"));
                }
            };
        } catch (McpActionBridge.McpCallException e) {
            return ResponseEntity.ok(rpcError(id, e.getCode(), e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("[mcp-controller] invalid params id={} reason={}", id, e.getMessage());
            return ResponseEntity.ok(rpcError(id, -32602, e.getMessage()));
        }
    }

    private DeferredResult<ResponseEntity<Map<String, Object>>> handleToolsCall(Object id, Map<String, Object> params) {
        String name = requireString(params.get("name"), "tools/call.name");
        Map<String, Object> arguments = asMap(params.get("arguments"));

        DeferredResult<ResponseEntity<Map<String, Object>>> deferred = new DeferredResult<>(service.httpTimeoutMs(name));
        deferred.onTimeout(() -> {
            log.warn("[mcp-controller] DeferredResult timed out id={} tool={}", id, name);
            deferred.setResult(ResponseEntity.ok(rpcError(id, -32003, "client action timed out")));
        });

        service.callTool(name, arguments).whenComplete((result, error) -> {
            if (error == null) {
                deferred.setResult(ResponseEntity.ok(rpcResult(id, result)));
                return;
            }

            Throwable cause = unwrap(error);
            if (cause instanceof McpActionBridge.McpCallException mcpError) {
                deferred.setResult(ResponseEntity.ok(rpcError(id, mcpError.getCode(), mcpError.getMessage())));
                return;
            }
            if (cause instanceof IllegalArgumentException illegalArgumentException) {
                log.warn("[mcp-controller] invalid params async id={} tool={} reason={}",
                    id, name, illegalArgumentException.getMessage());
                deferred.setResult(ResponseEntity.ok(rpcError(id, -32602, illegalArgumentException.getMessage())));
                return;
            }
            log.error("[mcp-controller] internal error id={} tool={}", id, name, cause);
            deferred.setResult(ResponseEntity.ok(rpcError(id, -32603, safeMessage(cause))));
        });
        return deferred;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> raw) {
            return (Map<String, Object>) raw;
        }
        throw new IllegalArgumentException("params must be an object");
    }

    private boolean isOriginAllowed(String origin) {
        if (origin == null || origin.isBlank()) {
            return true;
        }
        return allowedOrigins.contains(origin);
    }

    private static boolean isLoopback(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(remoteAddr);
            return address.isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }

    private static String requireString(Object value, String label) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new IllegalArgumentException("missing " + label);
    }

    private static Map<String, Object> rpcResult(Object id, Object result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("result", result);
        return body;
    }

    private static Map<String, Object> rpcError(Object id, int code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("error", Map.of(
            "code", code,
            "message", message
        ));
        return body;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String safeMessage(Throwable error) {
        if (error == null) {
            return "internal error";
        }
        if (error.getMessage() != null && !error.getMessage().isBlank()) {
            return error.getMessage();
        }
        return error.getClass().getSimpleName();
    }
}
