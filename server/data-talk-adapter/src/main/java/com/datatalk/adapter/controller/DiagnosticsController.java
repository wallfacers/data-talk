package com.datatalk.adapter.controller;

import com.datatalk.adapter.actions.ExplainQueryAction;
import com.datatalk.adapter.actions.IndexHintsAction;
import com.datatalk.application.diagnostics.DiagnosticsService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.diagnostics.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions/{sessionId}/diagnostics")
public class DiagnosticsController {

    private final DiagnosticsService service;
    private final Translator translator;

    public DiagnosticsController(DiagnosticsService service, Translator translator) {
        this.service = service;
        this.translator = translator;
    }

    @PostMapping("/explain")
    public ResponseEntity<Map<String, Object>> explain(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body) {
        String sql = (String) body.get("sql");
        if (sql == null || sql.isBlank()) {
            return ResponseEntity.badRequest().body(messageBody("error.sql.required", "SQL is required"));
        }

        DiagnosticResult<ExplainPlan> result = service.explain(sessionId, sql);
        return ResponseEntity.ok(switch (result) {
            case DiagnosticResult.Ok<ExplainPlan> ok -> ExplainQueryAction.serializePlan(ok.value());
            case DiagnosticResult.Unsupported<ExplainPlan> unsupported -> Map.<String, Object>of(
                "unsupported", true,
                "reason", unsupported.reason()
            );
            case DiagnosticResult.DiagnosticError<ExplainPlan> err -> Map.<String, Object>of(
                "error", Map.of("type", err.errorType(), "message", err.message())
            );
        });
    }

    @PostMapping("/index-hints")
    public ResponseEntity<Map<String, Object>> indexHints(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body) {
        String sql = (String) body.get("sql");
        if (sql == null || sql.isBlank()) {
            return ResponseEntity.badRequest().body(messageBody("error.sql.required", "SQL is required"));
        }

        DiagnosticResult<List<IndexRecommendation>> result = service.indexHints(sessionId, sql);
        return ResponseEntity.ok(switch (result) {
            case DiagnosticResult.Ok<List<IndexRecommendation>> ok -> {
                List<IndexRecommendation> recs = ok.value();
                yield Map.<String, Object>of(
                    "recommendations", recs.stream().map(IndexHintsAction::serializeRec).toList(),
                    "summary", IndexHintsAction.buildSummary(recs)
                );
            }
            case DiagnosticResult.Unsupported<List<IndexRecommendation>> unsupported -> Map.<String, Object>of(
                "unsupported", true,
                "reason", unsupported.reason()
            );
            case DiagnosticResult.DiagnosticError<List<IndexRecommendation>> err -> Map.<String, Object>of(
                "error", Map.of("type", err.errorType(), "message", err.message())
            );
        });
    }

    private Map<String, Object> messageBody(String code, String fallback) {
        String message = translator.getOrDefault(code, fallback);
        if (message == null) {
            message = fallback;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        return body;
    }
}
