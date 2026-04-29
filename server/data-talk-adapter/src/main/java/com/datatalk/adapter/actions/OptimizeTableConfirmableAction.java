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
    id = "datatalk.optimize_table",
    executor = Executor.SERVER,
    description = "action.optimize_table.description",
    timeoutMs = 30_000,
    requiresConnection = true,
    exposeToMcp = true,
    riskLevel = { RiskLevel.L2 },
    category = { Category.MUTATION }
)
public class OptimizeTableConfirmableAction implements ActionHandler<Map, Map> {

    private static final Pattern IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");
    private final DiagnosticsService diagnosticsService;
    private final Translator translator;

    public OptimizeTableConfirmableAction(DiagnosticsService diagnosticsService, Translator translator) {
        this.diagnosticsService = diagnosticsService;
        this.translator = translator;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("table"),
            "properties", Map.of(
                "table", Map.of("type", "string"),
                "schemaName", Map.of("type", "string"),
                "confirm", Map.of("type", "boolean"),
                "confirmationToken", Map.of("type", "string")
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "properties", Map.ofEntries(
                Map.entry("confirm_required", Map.of("type", "boolean")),
                Map.entry("confirmation_token", Map.of("type", "string")),
                Map.entry("preview", Map.of("type", "object")),
                Map.entry("recommendations", Map.of("type", "array")),
                Map.entry("ok", Map.of("type", "boolean")),
                Map.entry("table", Map.of("type", "string")),
                Map.entry("schemaName", Map.of("type", "string")),
                Map.entry("durationMs", Map.of("type", "integer")),
                Map.entry("reclaimedBytes", Map.of("type", "integer")),
                Map.entry("message", Map.of("type", "string")),
                Map.entry("unsupported", Map.of("type", "boolean")),
                Map.entry("reason", Map.of("type", "string")),
                Map.entry("error", Map.of("type", "object"))
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
        String table = string(input, "table");
        String schemaName = string(input, "schemaName");
        if (table == null || !IDENTIFIER.matcher(table).matches()) {
            return CompletableFuture.completedFuture(DiagnosticsActionSupport.invalidInput(
                translator.get("diagnostics.error.invalid_table_name")
            ));
        }
        if (schemaName != null && !schemaName.isBlank() && !IDENTIFIER.matcher(schemaName).matches()) {
            return CompletableFuture.completedFuture(DiagnosticsActionSupport.invalidInput(
                translator.get("diagnostics.error.invalid_schema_name")
            ));
        }
        boolean confirm = Boolean.TRUE.equals(input.get("confirm"));
        return CompletableFuture.supplyAsync(() -> confirm
            ? execute(ctx, input, table, blankToNull(schemaName))
            : preview(ctx, table, blankToNull(schemaName))
        );
    }

    private Map<String, Object> preview(ActionContext ctx, String table, String schemaName) {
        DiagnosticResult<OptimizeTablePreview> result =
            diagnosticsService.optimizeTablePreview(ctx.sessionId(), table, schemaName);
        return switch (result) {
            case DiagnosticResult.Ok<OptimizeTablePreview> ok -> previewResponse(ctx.sessionId(), ok.value());
            case DiagnosticResult.Unsupported<OptimizeTablePreview> unsupported -> DiagnosticsActionSupport.unsupported(unsupported.reason());
            case DiagnosticResult.DiagnosticError<OptimizeTablePreview> err -> DiagnosticsActionSupport.error(err.errorType(), err.message());
        };
    }

    private Map<String, Object> execute(ActionContext ctx, Map input, String table, String schemaName) {
        String providedToken = string(input, "confirmationToken");
        if (providedToken == null || providedToken.isBlank()) {
            throw new IllegalArgumentException(translator.get("diagnostics.error.confirmation_token_required"));
        }
        DiagnosticResult<OptimizeTablePreview> preview =
            diagnosticsService.optimizeTablePreview(ctx.sessionId(), table, schemaName);
        return switch (preview) {
            case DiagnosticResult.Ok<OptimizeTablePreview> ok -> {
                String expected = token(ctx.sessionId(), ok.value());
                if (!expected.equals(providedToken)) {
                    throw new IllegalArgumentException(translator.get("diagnostics.error.confirmation_token_mismatch"));
                }
                DiagnosticResult<OptimizeTableResult> result =
                    diagnosticsService.optimizeTable(ctx.sessionId(), table, schemaName);
                yield switch (result) {
                    case DiagnosticResult.Ok<OptimizeTableResult> executed -> DiagnosticsActionSupport.serializeOptimizeResult(executed.value());
                    case DiagnosticResult.Unsupported<OptimizeTableResult> unsupported -> DiagnosticsActionSupport.unsupported(unsupported.reason());
                    case DiagnosticResult.DiagnosticError<OptimizeTableResult> err -> DiagnosticsActionSupport.error(err.errorType(), err.message());
                };
            }
            case DiagnosticResult.Unsupported<OptimizeTablePreview> unsupported -> DiagnosticsActionSupport.unsupported(unsupported.reason());
            case DiagnosticResult.DiagnosticError<OptimizeTablePreview> err -> DiagnosticsActionSupport.error(err.errorType(), err.message());
        };
    }

    private static Map<String, Object> previewResponse(String sessionId, OptimizeTablePreview preview) {
        var out = new LinkedHashMap<String, Object>();
        out.put("confirm_required", true);
        out.put("confirmation_token", token(sessionId, preview));
        out.put("preview", DiagnosticsActionSupport.serializeOptimizePreview(preview));
        out.put("recommendations", DiagnosticsActionSupport.serializeRecommendations(preview.recommendations()));
        return out;
    }

    private static String token(String sessionId, OptimizeTablePreview preview) {
        return DiagnosticsActionSupport.confirmationToken(
            sessionId,
            preview.table(),
            preview.schemaName(),
            preview.willRunSql()
        );
    }

    private static String string(Map input, String key) {
        Object value = input.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
