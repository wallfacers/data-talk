package com.datatalk.application.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * BUG-0077 真实链路验证工具:读取 ~/.data-talk/reports/{id}/report.json,用当前 ReportRenderer
 * 重新渲染 HTML 并覆盖磁盘 report.html。运行后,后端 /api/reports/{id}/download/html
 * 就会返回包含 ANCHOR_INTERCEPTOR_SCRIPT 的产物,可在 Tauri/Vite Report Viewer 中点 TOC 验证。
 *
 * <p>默认禁用(避免误改产物),通过 -Drerender.reportId=r-616f3980 启用:
 * mvn test -pl data-talk-application -Dtest=RerenderExistingReportTest -Drerender.reportId=r-616f3980
 */
class RerenderExistingReportTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ReportRenderer renderer = new ReportRenderer(mapper);

    @Test
    @EnabledIfSystemProperty(named = "rerender.reportId", matches = ".+")
    void rerenderExistingReport() throws Exception {
        String reportId = System.getProperty("rerender.reportId");
        Path reportDir = Paths.get(System.getProperty("user.home"), ".data-talk", "reports", reportId);
        Path jsonPath = reportDir.resolve("report.json");
        Path htmlPath = reportDir.resolve("report.html");
        if (!Files.exists(jsonPath)) {
            throw new IllegalStateException("report.json not found: " + jsonPath);
        }
        JsonNode reportJson = mapper.readTree(jsonPath.toFile());
        String html = renderer.toHtml(reportJson);
        Files.writeString(htmlPath, html);
        System.out.println("Re-rendered " + reportId + " -> " + htmlPath + " (" + html.length() + " bytes)");
    }
}
