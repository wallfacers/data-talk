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
    id = "datatalk.pool_status",
    executor = Executor.SERVER,
    description = "action.pool_status.description",
    requiresConnection = true,
    exposeToMcp = true,
    riskLevel = { RiskLevel.L1 },
    category = { Category.QUERY }
)
public class PoolStatusAction implements ActionHandler<Map, Map> {

    private final DiagnosticsService diagnosticsService;

    public PoolStatusAction(DiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "properties", Map.ofEntries(
                Map.entry("scope", Map.of("type", "string")),
                Map.entry("activeConnections", Map.of("type", "integer")),
                Map.entry("idleConnections", Map.of("type", "integer")),
                Map.entry("maxConnections", Map.of("type", "integer")),
                Map.entry("threadsRunning", Map.of("type", "integer")),
                Map.entry("waitingConnections", Map.of("type", "integer")),
                Map.entry("identifier", Map.of("type", "string")),
                Map.entry("recommendations", Map.of("type", "array")),
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
        return CompletableFuture.supplyAsync(() -> {
            DiagnosticResult<PoolReport> result = diagnosticsService.poolStatus(ctx.sessionId());
            return switch (result) {
                case DiagnosticResult.Ok<PoolReport> ok -> serialize(ok.value());
                case DiagnosticResult.Unsupported<PoolReport> unsupported -> DiagnosticsActionSupport.unsupported(unsupported.reason());
                case DiagnosticResult.DiagnosticError<PoolReport> err -> DiagnosticsActionSupport.error(err.errorType(), err.message());
            };
        });
    }

    static Map<String, Object> serialize(PoolReport report) {
        var out = new LinkedHashMap<String, Object>();
        out.put("scope", report.scope());
        out.put("activeConnections", report.activeConnections());
        out.put("idleConnections", report.idleConnections());
        out.put("maxConnections", report.maxConnections());
        out.put("threadsRunning", report.threadsRunning());
        out.put("waitingConnections", report.waitingConnections());
        out.put("identifier", report.identifier());
        out.put("recommendations", DiagnosticsActionSupport.serializeRecommendations(report.recommendations()));
        return out;
    }
}
