package com.datatalk.infra.opencode;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.opencode.ToolCallBridge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/opencode-tool")
public class ToolCallController {

    private final ToolCallBridge bridge;
    private final String sharedSecret;
    private final Translator translator;

    public ToolCallController(
        ToolCallBridge bridge,
        Translator translator,
        @Value("${datatalk.opencode.shared-secret:}") String sharedSecret
    ) {
        this.bridge = bridge;
        this.translator = translator;
        this.sharedSecret = sharedSecret;
    }

    @PostMapping("/{actionId}")
    public ResponseEntity<?> handle(
        @PathVariable String actionId,
        @RequestHeader(value = "X-OpenCode-Call-Id", required = false) String callId,
        @RequestHeader(value = "X-OpenCode-Session-Id", required = false) String openCodeSessionId,
        @RequestHeader(value = "X-OpenCode-Secret", required = false) String secret,
        @RequestBody(required = false) Map<String, Object> input
    ) {
        if (!sharedSecret.isEmpty() && !sharedSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", translator.get("error.opencode.bad_secret")));
        }
        if (callId == null || openCodeSessionId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", translator.get("error.opencode.missing_headers")));
        }
        try {
            Object output = bridge.handle(actionId, callId, openCodeSessionId,
                input == null ? Map.of() : input).toCompletableFuture().get();
            return ResponseEntity.ok(Map.of("output", output));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", translator.get("error.opencode.interrupted")));
        } catch (ExecutionException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
        }
    }
}
