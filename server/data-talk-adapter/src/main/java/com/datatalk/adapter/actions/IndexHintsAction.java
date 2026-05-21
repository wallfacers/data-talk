package com.datatalk.adapter.actions;

import com.datatalk.application.diagnostics.DiagnosticsService;
import com.datatalk.domain.action.*;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@Component
@DataTalkAction(
    id = "datatalk.index_hints",
    executor = Executor.SERVER,
    description = "action.index_hints.description",
    requiresConnection = true,
    exposeToMcp = true,
    riskLevel = { RiskLevel.L1 },
    category = { Category.QUERY }
)
public class IndexHintsAction implements ActionHandler<Map, Map> {

    private final DiagnosticsService diagnosticsService;

    public IndexHintsAction(DiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("sql"),
            "properties", Map.of(
                "sql", Map.of("type", "string", "description", "SQL statement to analyze for index recommendations")
            ));
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "properties", Map.of(
                "recommendations", Map.of("type", "array"),
                "summary", Map.of("type", "string"),
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
            DiagnosticResult<List<IndexRecommendation>> result = diagnosticsService.indexHints(ctx.sessionId(), sql);
            return switch (result) {
                case DiagnosticResult.Ok<List<IndexRecommendation>> ok -> {
                    List<IndexRecommendation> recs = ok.value();
                    yield Map.<String, Object>of(
                        "recommendations", recs.stream().map(IndexHintsAction::serializeRec).toList(),
                        "summary", buildSummary(recs)
                    );
                }
                case DiagnosticResult.Unsupported<List<IndexRecommendation>> unsupported -> Map.<String, Object>of(
                    "unsupported", true,
                    "reason", unsupported.reason()
                );
                case DiagnosticResult.DiagnosticError<List<IndexRecommendation>> err -> Map.<String, Object>of(
                    "error", Map.of("type", err.errorType(), "message", err.message())
                );
            };
        });
    }

    public static Map<String, Object> serializeRec(IndexRecommendation rec) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("table", rec.table());
        map.put("columns", rec.columns());
        map.put("indexType", rec.indexType());
        map.put("impact", rec.impact().name());
        map.put("rationale", rec.rationale());
        return map;
    }

    public static String buildSummary(List<IndexRecommendation> recs) {
        if (recs.isEmpty()) {
            return "No index recommendations found.";
        }
        long high = recs.stream().filter(r -> r.impact() == Impact.HIGH).count();
        long medium = recs.stream().filter(r -> r.impact() == Impact.MEDIUM).count();
        String tables = recs.stream()
            .map(IndexRecommendation::table)
            .distinct()
            .collect(Collectors.joining(", "));
        return String.format("Found %d recommendation(s) (%d HIGH, %d MEDIUM) on table(s): %s",
            recs.size(), high, medium, tables);
    }
}
