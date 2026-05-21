package com.datatalk.application.opencode;

import com.datatalk.application.channel.ChannelService;
import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.session.ActionDispatcher;
import com.datatalk.application.stage.EditConflictMarkdownFormatter;
import com.datatalk.application.stage.StageTabRepository;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionExecutionMetadata;
import com.datatalk.domain.event.ErrorInfo;
import com.datatalk.domain.stage.StageTab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;

@Component
public class McpActionBridge {

    private static final Logger log = LoggerFactory.getLogger(McpActionBridge.class);

    private final ActionDispatcher dispatcher;
    private final ActionRegistry registry;
    private final OpenCodeSessionMap sessionMap;
    private final OpenCodeBridgeStatus bridgeStatus;
    private final McpArgumentsNormalizer normalizer;
    private final StageTabRepository stageTabRepository;

    @Autowired
    public McpActionBridge(ActionDispatcher dispatcher,
                           ActionRegistry registry,
                           OpenCodeSessionMap sessionMap,
                           OpenCodeBridgeStatus bridgeStatus,
                           McpArgumentsNormalizer normalizer,
                           StageTabRepository stageTabRepository) {
        this.dispatcher = dispatcher;
        this.registry = registry;
        this.sessionMap = sessionMap;
        this.bridgeStatus = bridgeStatus;
        this.normalizer = normalizer;
        this.stageTabRepository = stageTabRepository;
    }

    McpActionBridge(ActionDispatcher dispatcher,
                    ActionRegistry registry,
                    OpenCodeSessionMap sessionMap,
                    String bridgeNonce) {
        this(dispatcher, registry, sessionMap, bridgeStatus(bridgeNonce),
            new McpArgumentsNormalizer(new com.fasterxml.jackson.databind.ObjectMapper()), null);
    }

    public CompletionStage<ToolCallOutcome> handle(String mcpToolName, Map<String, Object> arguments) {
        List<String> incomingArgKeys = arguments == null ? List.of() : List.copyOf(arguments.keySet());
        Map<String, Object> strippedArguments = new LinkedHashMap<>(arguments == null ? Map.of() : arguments);
        BridgeFields bridgeFields = BridgeFields.extract(strippedArguments);

        if (bridgeFields.missingSessionContext()) {
            log.warn("[mcp-bridge] missing session context tool={} missingSessionId={} missingCallId={} incomingArgKeys={}",
                mcpToolName,
                isBlankStr(bridgeFields.openCodeSessionId()),
                isBlankStr(bridgeFields.callId()),
                incomingArgKeys);
            return failed(new McpCallException(-32602, "invalid params: missing session context"));
        }
        String bridgeNonce = bridgeStatus.bridgeNonce();
        if (bridgeNonce == null || bridgeNonce.isBlank() || !bridgeNonce.equals(bridgeFields.bridgeNonce())) {
            log.warn("[mcp-bridge] unauthenticated bridge tool={} expectedNoncePresent={} receivedNoncePresent={}",
                mcpToolName,
                !(bridgeNonce == null || bridgeNonce.isBlank()),
                !isBlankStr(bridgeFields.bridgeNonce()));
            return failed(new McpCallException(-32001, "unauthenticated bridge"));
        }

        String dataTalkSessionId = sessionMap.dataTalkFor(bridgeFields.openCodeSessionId());
        if (dataTalkSessionId == null || dataTalkSessionId.isBlank()) {
            log.warn("[mcp-bridge] unknown opencode session tool={} openCodeSessionId={}",
                mcpToolName, bridgeFields.openCodeSessionId());
            return failed(new McpCallException(-32002, "unknown opencode session"));
        }

        final String actionId;
        try {
            actionId = new McpNameMapper(registry.mcpExposed()).toActionIdFromMcpToolName(mcpToolName);
        } catch (IllegalArgumentException e) {
            log.warn("[mcp-bridge] unknown mcp tool name tool={} reason={}", mcpToolName, e.getMessage());
            return failed(new McpCallException(-32602, e.getMessage()));
        }

        try {
            normalizer.normalize(strippedArguments, registry.require(actionId).inputSchema(), mcpToolName);
        } catch (RuntimeException e) {
            log.warn("[mcp-bridge] normalize failed tool={} action={} reason={}",
                mcpToolName, actionId, e.getMessage());
        }

        log.debug("[mcp-bridge] dispatch tool={} action={} ocSid={} dtSid={} callId={}",
            mcpToolName, actionId, bridgeFields.openCodeSessionId(), dataTalkSessionId, bridgeFields.callId());

        CompletionStage<Object> dispatchResult;
        try {
            dispatchResult = dispatcher.dispatch(
                actionId,
                strippedArguments,
                bridgeFields.callId(),
                new ActionContext(dataTalkSessionId, bridgeFields.callId(), null, bridgeFields.openCodeSessionId(), ActionExecutionMetadata.aiInitiated())
            );
        } catch (ActionDispatcher.SchemaValidationException e) {
            log.warn("[mcp-bridge] schema validation failed tool={} action={} reason={}",
                mcpToolName, actionId, e.getMessage());
            return failed(new McpCallException(-32602, e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("[mcp-bridge] dispatch rejected tool={} action={} reason={}",
                mcpToolName, actionId, e.getMessage());
            return failed(new McpCallException(-32602, e.getMessage()));
        }

        return dispatchResult.handle((output, error) -> {
            if (error == null) {
                return ToolCallOutcome.success(output);
            }
            Throwable cause = unwrap(error);
            if (cause instanceof McpCallException mcpError) {
                log.warn("[mcp-bridge] dispatch mcp error tool={} action={} code={} message={}",
                    mcpToolName, actionId, mcpError.getCode(), mcpError.getMessage());
                throw new CompletionException(mcpError);
            }
            if (cause instanceof TimeoutException) {
                log.warn("[mcp-bridge] client action timed out tool={} action={} callId={}",
                    mcpToolName, actionId, bridgeFields.callId());
                throw new CompletionException(new McpCallException(-32003, "client action timed out"));
            }
            if (cause instanceof ActionDispatcher.NoClientSubscriberException) {
                log.warn("[mcp-bridge] no client subscriber tool={} action={} dtSid={}",
                    mcpToolName, actionId, dataTalkSessionId);
                throw new CompletionException(new McpCallException(-32004, "no client subscriber"));
            }
            if (cause instanceof ActionDispatcher.SchemaValidationException) {
                log.warn("[mcp-bridge] schema validation failed async tool={} action={} reason={}",
                    mcpToolName, actionId, cause.getMessage());
                throw new CompletionException(new McpCallException(-32602, cause.getMessage()));
            }
            if (cause instanceof ChannelService.ActionResultError actionError) {
                log.warn("[mcp-bridge] client action error tool={} action={} message={}",
                    mcpToolName, actionId, safeMessage(actionError));
                return ToolCallOutcome.error(toClientErrorPayload(actionError.info, actionId, strippedArguments));
            }
            log.error("[mcp-bridge] dispatch unexpected error tool={} action={}", mcpToolName, actionId, cause);
            return ToolCallOutcome.error(Map.of("message", safeMessage(cause)));
        });
    }

    private static boolean isBlankStr(String value) {
        return value == null || value.isBlank();
    }

    private static CompletionStage<ToolCallOutcome> failed(RuntimeException error) {
        return CompletableFuture.failedStage(error);
    }

    private static OpenCodeBridgeStatus bridgeStatus(String bridgeNonce) {
        OpenCodeBridgeStatus status = new OpenCodeBridgeStatus(Clock.systemUTC());
        status.rotateNonce(bridgeNonce);
        return status;
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
            return "unknown tool error";
        }
        return safeMessage(error.getMessage());
    }

    private static String safeMessage(String message) {
        if (message != null && !message.isBlank()) {
            return message;
        }
        return "unknown tool error";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toClientErrorPayload(ErrorInfo info, String actionId, Map<String, Object> input) {
        LinkedHashMap<String, Object> error = new LinkedHashMap<>();
        if (info != null && info.code() != null && !info.code().isBlank()) {
            error.put("code", info.code());
        }
        error.put("message", safeMessage(info == null ? null : info.message()));

        if (info != null && info.details() != null && !info.details().isEmpty()) {
            Object currentState = info.details().get("currentState");
            if (currentState != null) {
                error.put("currentState", currentState);
            }

            Object markdown = info.details().get("markdown");
            if (markdown == null) {
                markdown = info.details().get("markdown_note");
            }
            if (markdown != null) {
                error.put("markdown", markdown);
            }

            Object details = info.details().get("details");
            if (details instanceof Map<?, ?> rawDetails && !rawDetails.isEmpty()) {
                error.put("details", (Map<String, Object>) rawDetails);
            }
        }

        if (!error.containsKey("markdown")) {
            synthesizeMarkdown(info, actionId, input).ifPresent(rendered -> error.put("markdown", rendered));
        }

        return error;
    }

    private Optional<String> synthesizeMarkdown(ErrorInfo info, String actionId, Map<String, Object> input) {
        if (info == null || info.code() == null || stageTabRepository == null) {
            return Optional.empty();
        }
        Map<String, Object> detail = detailsMap(info.details());
        String tabId = extractTabId(detail, input);
        if (tabId == null || tabId.isBlank()) {
            if ("tab_not_found".equals(info.code())) {
                return Optional.of(EditConflictMarkdownFormatter.tabNotFound("active"));
            }
            return Optional.empty();
        }

        if ("tab_not_found".equals(info.code())) {
            return Optional.of(EditConflictMarkdownFormatter.tabNotFound(tabId));
        }

        Optional<StageTab> tabOpt = stageTabRepository.findById(tabId);
        if (tabOpt.isEmpty()) {
            return "tab_not_found".equals(info.code())
                ? Optional.of(EditConflictMarkdownFormatter.tabNotFound(tabId))
                : Optional.empty();
        }

        StageTab tab = tabOpt.get();
        long deltaMs = Math.max(0L, System.currentTimeMillis() - tab.lastTouchedAt());
        Integer actualVersion = extractInteger(detailsMap(detail.get("currentState")).get("version"));

        return switch (info.code()) {
            case "version_conflict" -> {
                Integer requestedBase = extractRequestedBase(actionId, input);
                if (requestedBase == null || actualVersion == null) {
                    yield Optional.empty();
                }
                yield Optional.of(EditConflictMarkdownFormatter.versionConflict(
                    tab, requestedBase, actualVersion, deltaMs));
            }
            case "expected_text_mismatch" -> {
                Map<String, Object> mismatch = detailsMap(detail.get("details"));
                Integer editIndex = extractInteger(mismatch.get("editIndex"));
                String expected = extractString(mismatch.get("expected"));
                String actual = extractString(mismatch.get("actual"));
                if (editIndex == null || actualVersion == null || expected == null || actual == null) {
                    yield Optional.empty();
                }
                Integer requestedBase = extractRequestedBase(actionId, input);
                yield Optional.of(EditConflictMarkdownFormatter.expectedTextMismatch(
                    tab, editIndex, expected, actual, requestedBase, actualVersion, deltaMs));
            }
            case "tab_archived" -> Optional.of(EditConflictMarkdownFormatter.tabArchived(tab));
            default -> Optional.empty();
        };
    }

    private static String extractTabId(Map<String, Object> detail, Map<String, Object> input) {
        String fromCurrentState = extractString(detailsMap(detail.get("currentState")).get("tabId"));
        if (fromCurrentState != null && !fromCurrentState.isBlank()) {
            return fromCurrentState;
        }
        String target = extractString(input.get("target"));
        if (target != null && !target.isBlank() && !"active".equals(target)) {
            return target;
        }
        return null;
    }

    private static Integer extractRequestedBase(String actionId, Map<String, Object> input) {
        if ("datatalk.ui.exec".equals(actionId)) {
            return extractInteger(detailsMap(input.get("params")).get("baseVersion"));
        }
        if (!"datatalk.ui.patch".equals(actionId)) {
            return null;
        }
        Object rawOps = input.get("ops");
        if (!(rawOps instanceof List<?> ops)) {
            return null;
        }
        for (Object rawOp : ops) {
            Map<String, Object> op = detailsMap(rawOp);
            if ("/content".equals(extractString(op.get("path")))) {
                return extractInteger(op.get("baseVersion"));
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> detailsMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static Integer extractInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String extractString(Object value) {
        return value instanceof String text ? text : null;
    }

    record BridgeFields(String openCodeSessionId, String callId, String bridgeNonce) {
        private static final String SESSION_KEY = "__dtOpenCodeSessionId";
        private static final String CALL_ID_KEY = "__dtCallId";
        private static final String NONCE_KEY = "__dtBridgeNonce";

        static BridgeFields extract(Map<String, Object> args) {
            String openCodeSessionId = null;
            String callId = null;
            String bridgeNonce = null;

            for (String key : args.keySet().stream().filter(name -> name.startsWith("__dt")).toList()) {
                Object value = args.remove(key);
                if (SESSION_KEY.equals(key)) {
                    openCodeSessionId = asString(value);
                } else if (CALL_ID_KEY.equals(key)) {
                    callId = asString(value);
                } else if (NONCE_KEY.equals(key)) {
                    bridgeNonce = asString(value);
                }
            }

            return new BridgeFields(openCodeSessionId, callId, bridgeNonce);
        }

        boolean missingSessionContext() {
            return isBlank(openCodeSessionId) || isBlank(callId);
        }

        private static String asString(Object value) {
            if (value instanceof String text) {
                return text;
            }
            return null;
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }

    public record ToolCallOutcome(Object output, boolean isError) {
        public static ToolCallOutcome success(Object output) {
            return new ToolCallOutcome(output, false);
        }

        public static ToolCallOutcome error(Object output) {
            return new ToolCallOutcome(output, true);
        }
    }

    public static class McpCallException extends RuntimeException {
        private final int code;

        public McpCallException(int code, String message) {
            super(message);
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }
}
