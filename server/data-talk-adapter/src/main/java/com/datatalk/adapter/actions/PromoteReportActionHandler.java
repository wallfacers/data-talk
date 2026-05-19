package com.datatalk.adapter.actions;

import com.datatalk.application.report.ReportArtifactService;
import com.datatalk.application.report.ReportSchemaValidator;
import com.datatalk.application.report.ReportValidationException;
import com.datatalk.application.report.Violation;
import com.datatalk.domain.action.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * AI 工具入口：接收 report.json + workspaceId + 可选 groupId，调 ReportArtifactService.promote。
 *
 * <p>失败时返回 {@code { error, errorCode }} 结构，AI 侧据此判定重试或修正后再 promote。
 */
@Component
@DataTalkAction(
        id = "datatalk.promote_report",
        executor = Executor.SERVER,
        description = "action.promote_report.description",
        riskLevel = { RiskLevel.L1 },
        category = { Category.ARTIFACT },
        timeoutMs = 60_000
)
public class PromoteReportActionHandler implements ActionHandler<Map, Map> {

    private final ReportArtifactService reportService;
    private final ObjectMapper mapper;

    public PromoteReportActionHandler(ReportArtifactService reportService, ObjectMapper mapper) {
        this.reportService = reportService;
        this.mapper = mapper;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("report", "workspaceId"),
                "properties", Map.of(
                        "report", Map.of("type", "object", "description", "Full report.json payload (schemaVersion=1, kind='report')"),
                        "workspaceId", Map.of("type", "string", "description", "Target workspace id"),
                        "groupId", Map.of("type", "string", "description", "Optional. When regenerating, pass the original report's groupId so the new version is grouped with it")
                )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        // Success-path schema. Error responses (on validation failure) carry an
        // additional shape: { error, errorCode, errorCodes[], violations[], recoveryHints{} }.
        // The error shape is described in the ledger skill's data-contract.md for AI consumers
        // and is not enforced here — AI parses it dynamically when `error` field is present.
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "reportId", Map.of("type", "string"),
                        "groupId", Map.of("type", "string"),
                        "version", Map.of("type", "integer"),
                        "artifactPaths", Map.of("type", "object"),
                        "pdfStatus", Map.of("type", "string", "enum", List.of("processing", "ready", "failed")),
                        "mdStatus", Map.of("type", "string", "enum", List.of("processing", "ready", "failed"))
                )
        );
    }

    @Override
    public List<OntologyEffect> sideEffects() {
        return List.of(OntologyEffect.CREATE_ARTIFACT);
    }

    @Override
    public Class<Map> inputType() {
        return Map.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        return CompletableFuture.supplyAsync(() -> {
            Object reportObj = input.get("report");
            String workspaceId = stringOrNull(input.get("workspaceId"));
            String groupId = stringOrNull(input.get("groupId"));
            if (reportObj == null) {
                return error("REPORT_INVALID", "report is required");
            }
            if (workspaceId == null || workspaceId.isBlank()) {
                return error("REPORT_WORKSPACE_MISSING", "workspaceId is required");
            }

            JsonNode reportJson = mapper.valueToTree(reportObj);
            try {
                ReportArtifactService.PromoteResult res = reportService.promote(
                        reportJson, workspaceId, groupId, ctx.sessionId());
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("reportId", res.reportId());
                out.put("groupId", res.groupId());
                out.put("version", res.version());
                out.put("artifactPaths", res.artifactPaths());
                out.put("pdfStatus", res.pdfStatus().dbValue());
                out.put("mdStatus", res.mdStatus().dbValue());
                return out;
            } catch (ReportValidationException e) {
                return validationError(e);
            } catch (Exception e) {
                return error("REPORT_PROMOTE_FAILED", "promote failed: " + e.getMessage());
            }
        });
    }

    private static String stringOrNull(Object o) {
        return o instanceof String s ? s : null;
    }

    private static Map<String, Object> error(String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        m.put("errorCode", code);
        m.put("errorCodes", List.of(code));
        m.put("violations", List.of(violationMap(code, "", message)));
        Map<String, String> hint = new LinkedHashMap<>();
        String h = ReportSchemaValidator.RECOVERY_HINTS.get(code);
        if (h != null) hint.put(code, h);
        m.put("recoveryHints", hint);
        return m;
    }

    private static Map<String, Object> validationError(ReportValidationException e) {
        List<Violation> violations = e.getViolations();
        List<String> codes = new ArrayList<>(violations.size());
        List<Map<String, Object>> violationList = new ArrayList<>(violations.size());
        Map<String, String> hints = new LinkedHashMap<>();
        for (Violation v : violations) {
            codes.add(v.code());
            violationList.add(violationMap(v.code(), v.path(), v.message()));
            String hint = ReportSchemaValidator.RECOVERY_HINTS.get(v.code());
            if (hint != null) hints.putIfAbsent(v.code(), hint);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", e.getMessage());
        m.put("errorCode", codes.get(0));
        m.put("errorCodes", codes);
        m.put("violations", violationList);
        m.put("recoveryHints", hints);
        return m;
    }

    private static Map<String, Object> violationMap(String code, String path, String message) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("code", code);
        v.put("path", path);
        v.put("message", message);
        return v;
    }
}
