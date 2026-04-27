package com.datatalk.adapter.actions;

import com.datatalk.application.diagnostics.DiagnosticsService;
import com.datatalk.domain.action.*;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.explain_query",
    executor = Executor.SERVER,
    description = "action.explain_query.description",
    requiresConnection = true,
    exposeToMcp = true,
    riskLevel = { RiskLevel.L1 },
    category = { Category.QUERY }
)
public class ExplainQueryAction implements ActionHandler<Map, Map> {

    private final DiagnosticsService diagnosticsService;

    public ExplainQueryAction(DiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("sql"),
            "properties", Map.of(
                "sql", Map.of("type", "string", "description", "SQL statement to explain")
            ));
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "properties", Map.of(
                "dialect", Map.of("type", "string"),
                "nodes", Map.of("type", "array"),
                "totalCostEstimate", Map.of("type", "number"),
                "warnings", Map.of("type", "array"),
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
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String sql = String.valueOf(input.get("sql"));
        return CompletableFuture.supplyAsync(() -> {
            DiagnosticResult<ExplainPlan> result = diagnosticsService.explain(ctx.sessionId(), sql);
            return switch (result) {
                case DiagnosticResult.Ok<ExplainPlan> ok -> serializePlan(ok.value());
                case DiagnosticResult.Unsupported<ExplainPlan> unsupported -> Map.<String, Object>of(
                    "unsupported", true,
                    "reason", unsupported.reason()
                );
                case DiagnosticResult.DiagnosticError<ExplainPlan> err -> Map.<String, Object>of(
                    "error", Map.of("type", err.errorType(), "message", err.message())
                );
            };
        });
    }

    public static Map<String, Object> serializePlan(ExplainPlan plan) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("dialect", plan.dialect());
        if (plan.rawText() != null) {
            map.put("rawText", plan.rawText());
        }
        map.put("nodes", plan.nodes().stream().map(ExplainQueryAction::serializeNode).toList());
        if (plan.totalCostEstimate() != null) {
            map.put("totalCostEstimate", plan.totalCostEstimate());
        }
        if (plan.warnings() != null && !plan.warnings().isEmpty()) {
            map.put("warnings", plan.warnings());
        }
        return map;
    }

    public static Map<String, Object> serializeNode(ExplainNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("operation", node.operation());
        if (node.table() != null) {
            map.put("table", node.table());
        }
        map.put("scanType", node.scanType().name());
        map.put("rows", node.rows());
        if (node.cost() != null) {
            map.put("cost", node.cost());
        }
        if (node.extra() != null) {
            map.put("extra", node.extra());
        }
        if (node.children() != null && !node.children().isEmpty()) {
            map.put("children", node.children().stream().map(ExplainQueryAction::serializeNode).toList());
        }
        return map;
    }
}
