package com.datatalk.adapter.actions;

import com.datatalk.application.report.ReportArtifactService;
import com.datatalk.application.report.ReportValidationException;
import com.datatalk.application.report.Violation;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.report.ReportDerivativeStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the promote-action error contract: validation failures surface as
 * { error, errorCode, errorCodes[], violations[], recoveryHints{} } so AI can
 * fix every violation in a single retry.
 */
class PromoteReportActionHandlerTest {

    private ReportArtifactService service;
    private ObjectMapper mapper;
    private PromoteReportActionHandler handler;

    @BeforeEach
    void setUp() {
        service = mock(ReportArtifactService.class);
        mapper = new ObjectMapper();
        handler = new PromoteReportActionHandler(service, mapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void successPathReturnsArtifactPaths() throws Exception {
        ReportArtifactService.ArtifactPaths paths = new ReportArtifactService.ArtifactPaths(
                "/tmp/r/report.json", "/tmp/r/report.html", null, null);
        when(service.promote(any(), eq("ws-1"), any(), any())).thenReturn(
                new ReportArtifactService.PromoteResult(
                        "r-1", "g-1", 1, paths,
                        ReportDerivativeStatus.PROCESSING, ReportDerivativeStatus.PROCESSING));

        Map<String, Object> input = Map.of(
                "report", Map.of(
                        "schemaVersion", 1, "kind", "report",
                        "meta", Map.of("title", "T", "templateId", "x"),
                        "sections", List.of(Map.of("type", "cover", "title", "T"))),
                "workspaceId", "ws-1");
        Map<String, Object> out = (Map<String, Object>) handler
                .handle(new ActionContext("s-1", "c-1", null, null), input)
                .toCompletableFuture().get();

        assertThat(out).containsKey("reportId").doesNotContainKey("error");
        assertThat(out.get("reportId")).isEqualTo("r-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void validationFailureReturnsCollectAllErrorShape() throws Exception {
        List<Violation> violations = List.of(
                new Violation("REPORT_META_MISSING", "meta.title", "meta.title is required"),
                new Violation("REPORT_SECTIONS_MISSING", "sections", "sections array must be non-empty"));
        when(service.promote(any(), any(), any(), any()))
                .thenThrow(new ReportValidationException(violations));

        Map<String, Object> input = Map.of(
                "report", Map.of("schemaVersion", 1, "kind", "report"),
                "workspaceId", "ws-1");
        Map<String, Object> out = (Map<String, Object>) handler
                .handle(new ActionContext("s-1", "c-1", null, null), input)
                .toCompletableFuture().get();

        // Legacy single-error field kept for backward compatibility
        assertThat(out.get("errorCode")).isEqualTo("REPORT_META_MISSING");
        // New collect-all fields
        assertThat((List<String>) out.get("errorCodes"))
                .containsExactly("REPORT_META_MISSING", "REPORT_SECTIONS_MISSING");
        List<Map<String, Object>> vs = (List<Map<String, Object>>) out.get("violations");
        assertThat(vs).hasSize(2);
        assertThat(vs.get(0)).containsEntry("code", "REPORT_META_MISSING")
                             .containsEntry("path", "meta.title");
        assertThat(vs.get(1)).containsEntry("code", "REPORT_SECTIONS_MISSING")
                             .containsEntry("path", "sections");
        // recoveryHints contains hints for emitted codes
        Map<String, String> hints = (Map<String, String>) out.get("recoveryHints");
        assertThat(hints).containsKey("REPORT_META_MISSING")
                         .containsKey("REPORT_SECTIONS_MISSING");
        assertThat(hints.get("REPORT_META_MISSING")).isNotBlank();
        // Aggregated human-readable message
        assertThat((String) out.get("error")).contains("meta.title").contains("sections");
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingReportFieldReturnsErrorShape() throws ExecutionException, InterruptedException {
        Map<String, Object> input = Map.of("workspaceId", "ws-1");
        Map<String, Object> out = (Map<String, Object>) handler
                .handle(new ActionContext("s-1", "c-1", null, null), input)
                .toCompletableFuture().get();

        assertThat(out.get("errorCode")).isEqualTo("REPORT_INVALID");
        assertThat(out).containsKeys("errorCodes", "violations", "recoveryHints");
        assertThat((List<String>) out.get("errorCodes")).containsExactly("REPORT_INVALID");
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingWorkspaceIdReturnsErrorShape() throws ExecutionException, InterruptedException {
        Map<String, Object> input = Map.of("report", Map.of("schemaVersion", 1));
        Map<String, Object> out = (Map<String, Object>) handler
                .handle(new ActionContext("s-1", "c-1", null, null), input)
                .toCompletableFuture().get();

        assertThat(out.get("errorCode")).isEqualTo("REPORT_WORKSPACE_MISSING");
        // recoveryHints may not have an entry for this top-level code; that's fine
        assertThat(out).containsKeys("errorCodes", "violations", "recoveryHints");
    }
}
