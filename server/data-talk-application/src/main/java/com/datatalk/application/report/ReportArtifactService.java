package com.datatalk.application.report;

import com.datatalk.application.fileartifact.AtomicFileWriterBridge;
import com.datatalk.application.fileartifact.FileArtifactConflictException;
import com.datatalk.application.fileartifact.FileArtifactService;
import com.datatalk.application.fileartifact.SessionWorkdirRoot;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.report.Report;
import com.datatalk.domain.report.ReportDerivativeStatus;
import com.datatalk.repository.ReportRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Use-case orchestrator: 接收 AI promote 的 report.json，原子写入 + 同步派生 HTML
 * + 异步派生 PDF/Markdown。
 *
 * <p>失败语义：HTML 派生在同步阶段失败 → 整体 rollback（不落 DB）。PDF/Markdown 在异步阶段
 * 各自独立，一个失败不影响另一个；通过 ReportRepository.updatePdfStatus / updateMdStatus 反馈。
 */
@Service
public class ReportArtifactService {

    private static final Logger log = LoggerFactory.getLogger(ReportArtifactService.class);

    private final ReportRepository reportRepo;
    private final ReportSchemaValidator validator;
    private final ReportRenderer htmlRenderer;
    private final MarkdownRenderer mdRenderer;
    private final ChartCaptureRenderer chartCapture;
    private final PdfRenderer pdfRenderer;
    private final FileArtifactService fileArtifactService;
    private final SessionWorkdirRoot workdirRoot;
    private final ReportSystemStatus systemStatus;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ReportArtifactService(
            ReportRepository reportRepo,
            ReportSchemaValidator validator,
            ReportRenderer htmlRenderer,
            MarkdownRenderer mdRenderer,
            ChartCaptureRenderer chartCapture,
            PdfRenderer pdfRenderer,
            FileArtifactService fileArtifactService,
            SessionWorkdirRoot workdirRoot,
            ReportSystemStatus systemStatus,
            ObjectMapper mapper,
            Clock clock) {
        this.reportRepo = reportRepo;
        this.validator = validator;
        this.htmlRenderer = htmlRenderer;
        this.mdRenderer = mdRenderer;
        this.chartCapture = chartCapture;
        this.pdfRenderer = pdfRenderer;
        this.fileArtifactService = fileArtifactService;
        this.workdirRoot = workdirRoot;
        this.systemStatus = systemStatus;
        this.mapper = mapper;
        this.clock = clock;
    }

    public record PromoteResult(
            String reportId,
            String groupId,
            int version,
            ArtifactPaths artifactPaths,
            ReportDerivativeStatus pdfStatus,
            ReportDerivativeStatus mdStatus) {}

    public record ArtifactPaths(String json, String html, String pdf, String md) {}

    public PromoteResult promote(JsonNode reportJson, String workspaceId, String groupId, String sessionId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new ReportValidationException("REPORT_WORKSPACE_MISSING", "workspaceId is required");
        }
        java.util.List<Violation> violations = validator.validate(reportJson);
        if (!violations.isEmpty()) {
            throw new ReportValidationException(violations);
        }

        // group_id / version 解析
        String effectiveGroupId;
        int version;
        if (groupId != null && !groupId.isBlank()) {
            if (!reportRepo.existsGroup(workspaceId, groupId)) {
                throw new ReportValidationException("REPORT_GROUP_NOT_FOUND",
                        "groupId not found in workspace: " + groupId);
            }
            effectiveGroupId = groupId;
            version = reportRepo.currentMaxVersionInGroup(workspaceId, groupId) + 1;
        } else {
            effectiveGroupId = ReportIds.newGroupId();
            version = 1;
        }

        String reportId = ReportIds.newReportId();
        Instant now = Instant.now(clock);
        Path reportDir = workdirRoot.reportDir(reportId).toAbsolutePath();
        try {
            Files.createDirectories(reportDir);
        } catch (IOException e) {
            throw new ReportRenderingException("Failed to mkdir " + reportDir, e);
        }

        // JSON & HTML 同步派生
        byte[] jsonBytes;
        try {
            jsonBytes = mapper.writeValueAsBytes(reportJson);
        } catch (JsonProcessingException e) {
            throw new ReportRenderingException("Failed to serialize report.json", e);
        }
        Path jsonPath = reportDir.resolve("report.json");
        Path htmlPath = reportDir.resolve("report.html");
        try {
            AtomicFileWriterBridge.write(jsonPath, jsonBytes);
        } catch (IOException e) {
            cleanup(reportDir);
            throw new ReportRenderingException("Failed to write report.json", e);
        }

        String html;
        try {
            html = htmlRenderer.toHtml(reportJson);
        } catch (Exception e) {
            cleanup(reportDir);
            throw new ReportRenderingException("Failed to render HTML", e);
        }
        try {
            AtomicFileWriterBridge.write(htmlPath, html.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            cleanup(reportDir);
            throw new ReportRenderingException("Failed to write report.html", e);
        }

        // Domain entity
        JsonNode meta = reportJson.path("meta");
        JsonNode theme = reportJson.path("theme");
        String title = meta.path("title").asText("DataTalk Report");
        String subtitle = meta.path("subtitle").asText("");
        String templateId = meta.path("templateId").asText("");
        String templateVersion = meta.path("templateVersion").asText("v1");
        String accent = theme.path("accent").asText("#1f4e79");
        String userPrompt = meta.path("userPrompt").asText("");

        ArtifactPaths pathDto = new ArtifactPaths(
                jsonPath.toString(),
                htmlPath.toString(),
                null,
                null);
        String artifactJson;
        try {
            artifactJson = mapper.writeValueAsString(pathDto);
        } catch (JsonProcessingException e) {
            artifactJson = "{}";
        }

        Report report = new Report(
                reportId,
                workspaceId,
                effectiveGroupId,
                version,
                title,
                subtitle.isBlank() ? null : subtitle,
                templateId,
                templateVersion,
                accent,
                now,
                sessionId,
                userPrompt.isBlank() ? null : truncate(userPrompt, 4096),
                artifactJson,
                ReportDerivativeStatus.PROCESSING,
                ReportDerivativeStatus.PROCESSING,
                null,
                null
        );
        reportRepo.save(report);

        // Register file artifact (JSON + HTML)
        try {
            fileArtifactService.registerExternal(
                    reportId, FileArtifactKind.REPORT, FileArtifactScope.WORKSPACE,
                    null, sessionId, jsonPath, title, subtitle.isBlank() ? null : subtitle,
                    Map.of("htmlPath", htmlPath.toString(), "workspaceId", workspaceId));
        } catch (IOException | FileArtifactConflictException e) {
            log.warn("registerExternal failed for {}: {}", reportId, e.getMessage());
        }

        // 异步触发 PDF + MD 派生
        Thread.ofVirtual().name("report-derive-" + reportId).start(() -> {
            try {
                deriveAsync(reportId, reportJson, htmlPath, reportDir);
            } catch (Throwable t) {
                log.error("Async derivation failed for {}", reportId, t);
            }
        });

        return new PromoteResult(reportId, effectiveGroupId, version, pathDto,
                ReportDerivativeStatus.PROCESSING, ReportDerivativeStatus.PROCESSING);
    }

    private void deriveAsync(String reportId, JsonNode reportJson, Path htmlPath, Path reportDir) {
        if (!systemStatus.chromiumReady()) {
            log.warn("Chromium not ready when deriving report {}", reportId);
            reportRepo.updatePdfStatus(reportId, ReportDerivativeStatus.FAILED, "chromium_not_ready");
            reportRepo.updateMdStatus(reportId, ReportDerivativeStatus.FAILED, "chromium_not_ready");
            return;
        }

        // 1. ChartCapture → chartPngPaths
        Map<String, Path> chartPngs;
        try {
            chartPngs = chartCapture.captureChartsToPng(htmlPath, reportId);
        } catch (Exception e) {
            log.error("ChartCapture failed for {}", reportId, e);
            chartPngs = new HashMap<>();
            reportRepo.updateMdStatus(reportId, ReportDerivativeStatus.FAILED, "chart_capture_failed: " + safeMsg(e));
        }

        // 2. Markdown 派生
        if (reportRepo.findById(reportId).map(r -> r.mdStatus() == ReportDerivativeStatus.PROCESSING).orElse(false)) {
            try {
                String md = mdRenderer.toMarkdown(reportJson, chartPngs);
                Path mdPath = reportDir.resolve("report.md");
                AtomicFileWriterBridge.write(mdPath, md.getBytes(StandardCharsets.UTF_8));
                // copy chart pngs into ./assets/
                Path assetsDir = reportDir.resolve("assets");
                Files.createDirectories(assetsDir);
                for (Map.Entry<String, Path> ent : chartPngs.entrySet()) {
                    Path src = ent.getValue();
                    Path dst = assetsDir.resolve("chart-" + ent.getKey() + ".png");
                    if (Files.exists(src) && !src.equals(dst)) {
                        Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                updateArtifactPath(reportId, "md", mdPath.toString());
                reportRepo.updateMdStatus(reportId, ReportDerivativeStatus.READY, null);
            } catch (Exception e) {
                log.error("Markdown render failed for {}", reportId, e);
                reportRepo.updateMdStatus(reportId, ReportDerivativeStatus.FAILED, safeMsg(e));
            }
        }

        // 3. PDF 派生
        try {
            Path pdfPath = reportDir.resolve("report.pdf");
            pdfRenderer.render(htmlPath, pdfPath);
            updateArtifactPath(reportId, "pdf", pdfPath.toString());
            reportRepo.updatePdfStatus(reportId, ReportDerivativeStatus.READY, null);
        } catch (Exception e) {
            log.error("PDF render failed for {}", reportId, e);
            reportRepo.updatePdfStatus(reportId, ReportDerivativeStatus.FAILED, safeMsg(e));
        }
    }

    private void updateArtifactPath(String reportId, String key, String value) {
        reportRepo.findById(reportId).ifPresent(r -> {
            try {
                JsonNode existing = mapper.readTree(r.artifactPathsJson());
                com.fasterxml.jackson.databind.node.ObjectNode mutable =
                        existing.isObject() ? (com.fasterxml.jackson.databind.node.ObjectNode) existing.deepCopy()
                                            : mapper.createObjectNode();
                mutable.put(key, value);
                reportRepo.updateArtifactPaths(reportId, mapper.writeValueAsString(mutable));
            } catch (IOException e) {
                log.warn("Failed to merge artifact path for {}: {}", reportId, e.getMessage());
            }
        });
    }

    private static String safeMsg(Throwable t) {
        String m = t.getMessage();
        return m == null ? t.getClass().getSimpleName() : truncate(m, 512);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static void cleanup(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                            });
                }
            }
        } catch (IOException ignored) {}
    }
}
