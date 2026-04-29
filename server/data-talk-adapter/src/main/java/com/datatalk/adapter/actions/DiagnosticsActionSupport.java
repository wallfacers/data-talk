package com.datatalk.adapter.actions;

import com.datatalk.domain.diagnostics.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DiagnosticsActionSupport {

    private DiagnosticsActionSupport() {}

    static Map<String, Object> unsupported(String reason) {
        return Map.of("unsupported", true, "reason", reason);
    }

    static Map<String, Object> error(String type, String message) {
        return Map.of("error", Map.of("type", type, "message", message));
    }

    static Map<String, Object> invalidInput(String message) {
        return error("INVALID_INPUT", message);
    }

    static Map<String, Object> serializeRecommendation(DiagnosticRecommendation recommendation) {
        var out = new LinkedHashMap<String, Object>();
        out.put("severity", recommendation.severity());
        out.put("summary", recommendation.summary());
        out.put("suggestedActionId", recommendation.suggestedActionId());
        out.put("suggestedToolName", recommendation.suggestedToolName());
        out.put("suggestedActionArgs", recommendation.suggestedActionArgs());
        out.put("suggestedSql", recommendation.suggestedSql());
        return out;
    }

    static List<Map<String, Object>> serializeRecommendations(List<DiagnosticRecommendation> recommendations) {
        if (recommendations == null) {
            return List.of();
        }
        return recommendations.stream().map(DiagnosticsActionSupport::serializeRecommendation).toList();
    }

    static Map<String, Object> serializeTerminatePreview(TerminateSessionPreview preview) {
        var out = new LinkedHashMap<String, Object>();
        out.put("engine", preview.engine());
        out.put("sessionId", preview.sessionId());
        out.put("willRunSql", preview.willRunSql());
        out.put("currentSql", preview.currentSql());
        return out;
    }

    static Map<String, Object> serializeTerminateResult(TerminateSessionResult result) {
        var out = new LinkedHashMap<String, Object>();
        out.put("ok", result.ok());
        out.put("sessionId", result.sessionId());
        out.put("message", result.message());
        return out;
    }

    static Map<String, Object> serializeOptimizePreview(OptimizeTablePreview preview) {
        var out = new LinkedHashMap<String, Object>();
        out.put("engine", preview.engine());
        out.put("table", preview.table());
        out.put("schemaName", preview.schemaName());
        out.put("willRunSql", preview.willRunSql());
        out.put("currentDataFree", preview.currentDataFree());
        out.put("currentTotalSize", preview.currentTotalSize());
        return out;
    }

    static Map<String, Object> serializeOptimizeResult(OptimizeTableResult result) {
        var out = new LinkedHashMap<String, Object>();
        out.put("ok", result.ok());
        out.put("table", result.table());
        out.put("schemaName", result.schemaName());
        out.put("durationMs", result.durationMs());
        out.put("reclaimedBytes", result.reclaimedBytes());
        out.put("message", result.message());
        return out;
    }

    static String confirmationToken(String... values) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                md.update((value == null ? "<null>" : value).getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException("cannot create confirmation token", e);
        }
    }
}
