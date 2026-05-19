package com.datatalk.application.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Fixture generator for §8 manual E2E verification of {@code ledger-report-quality-fixes}.
 *
 * <p>Writes a self-contained report HTML to {@code tmp/e2e-ledger-quality/fixture-report.html},
 * with assets base href pointing at {@code http://localhost:8080/api/reports/_assets/} so that
 * the file can be loaded from disk (file://) while still fetching CSS / fonts / scripts from
 * the running backend. Combined with {@code host.html} that wraps it in a
 * {@code sandbox="allow-scripts"} iframe, this drives the same Origin:null + CORS path the
 * production Report Viewer takes.
 *
 * <p>Test is idempotent (~50 ms) and always green; output lives under git-ignored {@code tmp/}.
 */
class LedgerReportE2EFixtureGeneratorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ReportRenderer renderer = new ReportRenderer(mapper);

    @Test
    void generateFixtureReportHtml() throws Exception {
        JsonNode fixture = mapper.readTree("""
            {
              "schemaVersion": 1,
              "kind": "report",
              "meta": {
                "title": "DataTalk Ledger E2E 验证报告",
                "subtitle": "ledger-report-quality-fixes",
                "templateId": "ledger.monthly-business-review.v1",
                "templateVersion": "v1"
              },
              "theme": { "accent": "#1f4e79" },
              "sections": [
                {
                  "type": "cover",
                  "title": "DataTalk Ledger E2E 验证报告",
                  "subtitle": "2026-05 月度回归",
                  "author": "DataTalk 自动生成",
                  "period": "2026-05"
                },
                { "type": "toc" },
                {
                  "type": "chapter",
                  "heading": "执行摘要",
                  "blocks": [
                    { "type": "narrative", "text": "本期 KPI 全部命中目标，未出现严重事件。" }
                  ]
                },
                {
                  "type": "chapter",
                  "heading": "业务指标",
                  "blocks": [
                    { "type": "narrative", "text": "GMV、活跃用户、留存率均呈同比增长。" }
                  ]
                }
              ]
            }
        """);

        String html = renderer.toHtml(
                fixture,
                "http://localhost:8080/api/reports/_assets/"
        );

        // tmp/ lives at project root, three levels above this module's working dir
        Path projectRoot = locateProjectRoot();
        Path outDir = projectRoot.resolve("tmp/e2e-ledger-quality");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("fixture-report.html");
        Files.writeString(outFile, html);
        System.out.println("Wrote E2E fixture report HTML to " + outFile);
    }

    /** Walk up from CWD until we find the {@code openspec} directory — that's the project root. */
    private static Path locateProjectRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("openspec"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("project root not found (no `openspec` dir above CWD)");
        }
        return p;
    }
}
