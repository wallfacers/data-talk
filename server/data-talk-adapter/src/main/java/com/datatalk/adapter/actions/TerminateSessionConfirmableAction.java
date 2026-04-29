package com.datatalk.adapter.actions;

import com.datatalk.application.diagnostics.DiagnosticsService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.action.*;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

@Component
@DataTalkAction(
    id = "datatalk.terminate_session",
    executor = Executor.SERVER,
    description = "action.terminate_session.description",
    timeoutMs = 30_000,
    requiresConnection = true,
    exposeToMcp = true,
    riskLevel = { RiskLevel.L2 },
    category = { Category.MUTATION }
)
public class TerminateSessionConfirmableAction implements ActionHandler<Map, Map> {

    private static final Pattern SESSION_ID = Pattern.compile("^[0-9]+$");
    private final DiagnosticsService diagnosticsService;
    private final Translator translator;

    public TerminateSessionConfirmableAction(DiagnosticsService diagnosticsService, Translator translator) {
        this.diagnosticsService = diagnosticsService;
        this.translator = translator;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("sessionId"),
            "properties", Map.of(
                "sessionId", Map.of("type", "string"),
                "confirm", Map.of("type", "boolean"),
                "confirmationToken", Map.of("type", "string")
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "properties", Map.of(
                "confirm_required", Map.of("type", "boolean"),
                "confirmation_token", Map.of("type", "string"),
                "preview", Map.of("type", "object"),
                "ok", Map.of("type", "boolean"),
                "sessionId", Map.of("type", "string"),
                "message", Map.of("type", "string"),
                "unsupported", Map.of("type", "boolean"),
                "reason", Map.of("type", "string"),
                "error", Map.of("type", "object")
            ));
    }

    @Override
    public List<OntologyEffect> sideEffects() {
        return List.of(OntologyEffect.NONE);
    }

    @Override
    public Class<Map> inputType() {
        return Map.class;
    }

    @Override
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String targetSessionId = string(input, "sessionId");
        if (targetSessionId == null || !SESSION_ID.matcher(targetSessionId).matches()) {
            return CompletableFuture.completedFuture(DiagnosticsActionSupport.invalidInput(
                translator.get("diagnostics.error.invalid_session_id")
            ));
        }
        boolean confirm = Boolean.TRUE.equals(input.get("confirm"));
        return CompletableFuture.supplyAsync(() -> confirm
            ? execute(ctx, input, targetSessionId)
            : preview(ctx, targetSessionId)
        );
    }

    private Map<String, Object> preview(ActionContext ctx, String targetSessionId) {
        DiagnosticResult<TerminateSessionPreview> result =
            diagnosticsService.terminateSessionPreview(ctx.sessionId(), targetSessionId);
        return switch (result) {
            case DiagnosticResult.Ok<TerminateSessionPreview> ok -> previewResponse(ctx.sessionId(), targetSessionId, ok.value());
            case DiagnosticResult.Unsupported<TerminateSessionPreview> unsupported -> DiagnosticsActionSupport.unsupported(unsupported.reason());
            case DiagnosticResult.DiagnosticError<TerminateSessionPreview> err -> DiagnosticsActionSupport.error(err.errorType(), err.message());
        };
    }

    private Map<String, Object> execute(ActionContext ctx, Map input, String targetSessionId) {
        String providedToken = string(input, "confirmationToken");
        if (providedToken == null || providedToken.isBlank()) {
            throw new IllegalArgumentException(translator.get("diagnostics.error.confirmation_token_required"));
        }
        DiagnosticResult<TerminateSessionPreview> preview =
            diagnosticsService.terminateSessionPreview(ctx.sessionId(), targetSessionId);
        return switch (preview) {
            case DiagnosticResult.Ok<TerminateSessionPreview> ok -> {
                String expected = token(ctx.sessionId(), targetSessionId, ok.value());
                if (!expected.equals(providedToken)) {
                    throw new IllegalArgumentException(translator.get("diagnostics.error.confirmation_token_mismatch"));
                }
                DiagnosticResult<TerminateSessionResult> result =
                    diagnosticsService.terminateSession(ctx.sessionId(), targetSessionId);
                yield switch (result) {
                    case DiagnosticResult.Ok<TerminateSessionResult> executed -> DiagnosticsActionSupport.serializeTerminateResult(executed.value());
                    case DiagnosticResult.Unsupported<TerminateSessionResult> unsupported -> DiagnosticsActionSupport.unsupported(unsupported.reason());
                    case DiagnosticResult.DiagnosticError<TerminateSessionResult> err -> DiagnosticsActionSupport.error(err.errorType(), err.message());
                };
            }
            case DiagnosticResult.Unsupported<TerminateSessionPreview> unsupported -> DiagnosticsActionSupport.unsupported(unsupported.reason());
            case DiagnosticResult.DiagnosticError<TerminateSessionPreview> err -> DiagnosticsActionSupport.error(err.errorType(), err.message());
        };
    }

    private static Map<String, Object> previewResponse(String sessionId, String targetSessionId, TerminateSessionPreview preview) {
        var out = new LinkedHashMap<String, Object>();
        out.put("confirm_required", true);
        out.put("confirmation_token", token(sessionId, targetSessionId, preview));
        out.put("preview", DiagnosticsActionSupport.serializeTerminatePreview(preview));
        return out;
    }

    private static String token(String sessionId, String targetSessionId, TerminateSessionPreview preview) {
        return DiagnosticsActionSupport.confirmationToken(sessionId, targetSessionId, preview.willRunSql());
    }

    private static String string(Map input, String key) {
        Object value = input.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
