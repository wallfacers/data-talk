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

    /**
     * Rich fixture exercising the modern visual redesign: full palette theme + all 5 new
     * primitives (callout/stat-highlight/comparison/quote/divider) + table cellFormats
     * (bar/delta/heat) + chart palette injection. Output for manual visual verification of
     * {@code ledger-modern-visual-redesign}.
     */
    @Test
    void generateRichFixtureReportHtml() throws Exception {
        JsonNode fixture = mapper.readTree("""
            {
              "schemaVersion": 1,
              "kind": "report",
              "meta": {
                "title": "现代视觉重设计验证报告",
                "subtitle": "ledger-modern-visual-redesign",
                "author": "数据分析团队",
                "generatedAt": "2026-05-20T10:00:00+08:00",
                "templateId": "ledger.monthly-business-review.v1",
                "templateVersion": "v1"
              },
              "theme": {
                "primary": "#0F2A4A",
                "accent": "#2F6FBF",
                "surface": "#F4F7FB"
              },
              "sections": [
                {
                  "type": "cover",
                  "title": "现代视觉重设计验证报告",
                  "subtitle": "2026-05 月度复盘 · 新设计语言全原语演示",
                  "author": "数据分析团队",
                  "period": "2026-05"
                },
                { "type": "toc" },
                {
                  "type": "executive-summary",
                  "bullets": [
                    "GMV 同比 +18%，达成季度目标的 104%",
                    "新增富视觉原语 5 种：callout / stat-highlight / comparison / quote / divider",
                    "表格支持列级 cellFormats：bar / delta / heat"
                  ]
                },
                {
                  "type": "chapter",
                  "heading": "业务总览",
                  "blocks": [
                    {
                      "type": "stat-highlight",
                      "value": "¥3.24M",
                      "label": "本期总 GMV",
                      "delta": "+18%",
                      "context": "同比增长，达成季度目标 104%",
                      "source": "internal · gmv_daily as of 2026-05-19"
                    },
                    {
                      "type": "kpi-strip",
                      "items": [
                        { "label": "GMV", "value": "3.24M", "delta": "+18%" },
                        { "label": "订单", "value": "12,345", "delta": "+8%" },
                        { "label": "客单价", "value": "¥262", "delta": "+9%" },
                        { "label": "退款率", "value": "1.2%", "delta": "-0.3%" }
                      ]
                    },
                    { "type": "callout", "variant": "insight", "markdown": "**核心洞察**：抖音渠道 GMV 同比 +28%，已成为第二增长曲线。" },
                    { "type": "callout", "variant": "warning", "markdown": "**风险提示**：华东区库存周转天数升至 45 天，高于警戒线 30 天。" },
                    { "type": "callout", "variant": "note", "markdown": "口径说明：GMV 含税、不含取消订单。" },
                    { "type": "callout", "variant": "success", "markdown": "里程碑：本期首次实现单月盈利。" }
                  ]
                },
                {
                  "type": "chapter",
                  "heading": "渠道表现",
                  "blocks": [
                    {
                      "type": "table",
                      "columns": ["渠道", "GMV", "同比", "健康度"],
                      "cellFormats": ["text", "bar", "delta", "heat"],
                      "rows": [
                        ["自营", "1200000", "+15%", "85"],
                        ["抖音", "800000", "+28%", "92"],
                        ["天猫", "640000", "-5%", "48"],
                        ["京东", "600000", "+3%", "70"]
                      ],
                      "caption": "各渠道 GMV 与健康度（bar/delta/heat 富单元格）",
                      "source": "internal · channel_gmv as of 2026-05-19"
                    }
                  ]
                },
                {
                  "type": "chapter",
                  "heading": "区域分析",
                  "blocks": [
                    {
                      "type": "comparison",
                      "items": [
                        { "label": "华东", "value": "1.4M", "delta": "+12%" },
                        { "label": "华南", "value": "0.9M", "delta": "+22%" },
                        { "label": "华北", "value": "0.7M", "delta": "-3%" }
                      ]
                    },
                    {
                      "type": "chart",
                      "id": "ch-region",
                      "echartsOption": {
                        "tooltip": { "trigger": "axis" },
                        "legend": { "data": ["GMV", "目标"] },
                        "xAxis": { "type": "category", "data": ["华东", "华南", "华北", "西南", "东北"] },
                        "yAxis": { "type": "value" },
                        "series": [
                          { "name": "GMV", "type": "bar", "data": [140, 90, 70, 50, 30] },
                          { "name": "目标", "type": "line", "data": [130, 80, 75, 45, 35] }
                        ]
                      },
                      "caption": "区域 GMV vs 目标（验证调色板注入）",
                      "source": "internal · region_gmv as of 2026-05-19"
                    },
                    { "type": "divider", "label": "小结" },
                    { "type": "quote", "text": "数据不是为了好看，而是为了支撑决策。", "attribution": "数据团队" }
                  ]
                }
              ],
              "appendix": [
                {
                  "type": "appendix",
                  "subType": "glossary",
                  "title": "术语表",
                  "items": [
                    { "term": "GMV", "definition": "成交总额，含税不含取消订单" },
                    { "term": "库存周转天数", "definition": "平均库存 / 日均销量" }
                  ]
                }
              ]
            }
        """);

        String html = renderer.toHtml(
                fixture,
                "http://localhost:8080/api/reports/_assets/"
        );

        Path projectRoot = locateProjectRoot();
        Path outDir = projectRoot.resolve("tmp/e2e-ledger-quality");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("fixture-rich.html");
        Files.writeString(outFile, html);
        System.out.println("Wrote rich E2E fixture report HTML to " + outFile);
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
