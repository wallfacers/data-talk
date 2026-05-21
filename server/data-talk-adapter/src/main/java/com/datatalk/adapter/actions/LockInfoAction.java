package com.datatalk.adapter.actions;

import com.datatalk.application.diagnostics.DiagnosticsService;
import com.datatalk.domain.action.*;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.lock_info",
    executor = Executor.SERVER,
    description = "action.lock_info.description",
    requiresConnection = true,
    exposeToMcp = true,
    riskLevel = { RiskLevel.L1 },
    category = { Category.QUERY }
)
public class LockInfoAction implements ActionHandler<Map, Map> {

    private final DiagnosticsService diagnosticsService;

    public LockInfoAction(DiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "properties", Map.of(
                "blockingChain", Map.of("type", "array"),
                "recommendations", Map.of("type", "array"),
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
        return CompletableFuture.supplyAsync(() -> {
            DiagnosticResult<LockReport> result = diagnosticsService.lockInfo(ctx.sessionId());
            return switch (result) {
                case DiagnosticResult.Ok<LockReport> ok -> serialize(ok.value());
                case DiagnosticResult.Unsupported<LockReport> unsupported -> DiagnosticsActionSupport.unsupported(unsupported.reason());
                case DiagnosticResult.DiagnosticError<LockReport> err -> DiagnosticsActionSupport.error(err.errorType(), err.message());
            };
        });
    }

    static Map<String, Object> serialize(LockReport report) {
        var out = new LinkedHashMap<String, Object>();
        out.put("blockingChain", report.blockingChain().stream().map(LockInfoAction::serializeEntry).toList());
        out.put("recommendations", DiagnosticsActionSupport.serializeRecommendations(report.recommendations()));
        return out;
    }

    private static Map<String, Object> serializeEntry(LockReport.LockEntry entry) {
        var out = new LinkedHashMap<String, Object>();
        out.put("table", entry.table());
        out.put("lockType", entry.lockType());
        out.put("holderId", entry.holderId());
        out.put("waiterId", entry.waiterId());
        out.put("waitMillis", entry.waitMillis());
        out.put("holderSql", entry.holderSql());
        out.put("waiterSql", entry.waiterSql());
        return out;
    }
}
