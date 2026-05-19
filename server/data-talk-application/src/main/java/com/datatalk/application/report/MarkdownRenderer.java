package com.datatalk.application.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;

/**
 * 报告 JSON → Markdown 派生器。
 *
 * <p>chart block 渲染为 {@code ![caption](./assets/chart-<blockId>.png)}，PNG 路径来自
 * {@link ChartCaptureRenderer} 的截图结果（{@code chartPngPaths} 入参）。
 * 截图缺失的 chart 渲染为占位提示。
 *
 * <p>table block 渲染为 GFM table；> 200 行的 table 引用 {@code appendixCsvRef}
 * 附录 CSV 链接（Markdown 中以相对路径 {@code ./assets/<csvId>.csv} 引用）。
 */
@Service
public class MarkdownRenderer {

    /**
     * @param reportJson    promote 输入的 report.json
     * @param chartPngPaths blockId → png absolute path（由 ChartCaptureRenderer 输出）；
     *                      调用方负责把这些 PNG 文件相对放置到 markdown 输出目录的 ./assets/ 下。
     */
    public String toMarkdown(JsonNode reportJson, Map<String, Path> chartPngPaths) {
        StringBuilder md = new StringBuilder(8192);
        JsonNode meta = reportJson.path("meta");
        String title = meta.path("title").asText("");
        String subtitle = meta.path("subtitle").asText("");

        // Top heading + meta
        md.append("# ").append(title).append("\n\n");
        if (!subtitle.isBlank()) {
            md.append("> ").append(subtitle).append("\n\n");
        }
        StringBuilder metaLine = new StringBuilder();
        if (!meta.path("author").asText("").isBlank()) metaLine.append(meta.path("author").asText(""));
        if (!meta.path("generatedAt").asText("").isBlank()) {
            if (metaLine.length() > 0) metaLine.append(" · ");
            metaLine.append(meta.path("generatedAt").asText(""));
        }
        if (metaLine.length() > 0) md.append("_").append(metaLine).append("_\n\n");

        // sections
        JsonNode sections = reportJson.path("sections");
        if (sections.isArray()) {
            for (JsonNode block : sections) {
                renderBlock(block, md, chartPngPaths, 2);
            }
        }
        // appendix
        JsonNode appendix = reportJson.path("appendix");
        if (appendix.isArray() && !appendix.isEmpty()) {
            md.append("\n---\n\n## 附录\n\n");
            for (JsonNode b : appendix) {
                renderBlock(b, md, chartPngPaths, 3);
            }
        }
        return md.toString();
    }

    private void renderBlock(JsonNode block, StringBuilder md, Map<String, Path> charts, int depth) {
        String type = block.path("type").asText("");
        switch (type) {
            case "cover" -> {
                // top heading already written; skip
            }
            case "executive-summary" -> {
                md.append("## 摘要\n\n");
                JsonNode bullets = block.path("bullets");
                if (bullets.isArray()) {
                    for (JsonNode bul : bullets) {
                        md.append("- ").append(bul.asText("")).append("\n");
                    }
                }
                md.append("\n");
            }
            case "toc" -> {
                // 目录在 Markdown 里通常工具自动生成，这里不输出
            }
            case "chapter" -> {
                String heading = block.path("heading").asText("");
                md.append("#".repeat(Math.max(2, depth))).append(" ").append(heading).append("\n\n");
                JsonNode blocks = block.path("blocks");
                if (blocks.isArray()) {
                    for (JsonNode c : blocks) renderBlock(c, md, charts, depth + 1);
                }
            }
            case "kpi-strip" -> renderKpiStripMd(block, md);
            case "narrative" -> {
                String mdText = block.path("markdown").asText("");
                md.append(mdText).append("\n\n");
            }
            case "chart" -> renderChartMd(block, md, charts);
            case "table" -> renderTableMd(block, md);
            case "risk-list" -> renderRiskListMd(block, md);
            case "timeline" -> renderTimelineMd(block, md);
            case "appendix" -> renderAppendixMd(block, md);
            default -> md.append("<!-- unknown block: ").append(type).append(" -->\n");
        }
    }

    private void renderKpiStripMd(JsonNode b, StringBuilder md) {
        JsonNode items = b.path("items");
        if (!items.isArray() || items.isEmpty()) return;
        StringBuilder header = new StringBuilder("|");
        StringBuilder divider = new StringBuilder("|");
        StringBuilder value = new StringBuilder("|");
        for (JsonNode it : items) {
            header.append(" ").append(escapePipe(it.path("label").asText(""))).append(" |");
            divider.append(" --- |");
            String v = it.path("value").asText("");
            String delta = it.path("delta").asText("");
            if (!delta.isBlank()) {
                String arrow = delta.contains("+") || delta.startsWith("↑") ? "↑" : "↓";
                v = v + " " + arrow + delta.replace("+", "").replace("-", "");
            }
            value.append(" ").append(escapePipe(v)).append(" |");
        }
        md.append(header).append("\n").append(divider).append("\n").append(value).append("\n");
        renderSourceMd(b, md);
        md.append("\n");
    }

    private void renderChartMd(JsonNode b, StringBuilder md, Map<String, Path> charts) {
        String id = b.path("id").asText("");
        String caption = b.path("caption").asText("");
        Path png = charts != null ? charts.get(id) : null;
        if (png != null) {
            md.append("![").append(escapeAlt(caption)).append("](./assets/chart-").append(id).append(".png)\n\n");
        } else {
            md.append("> ⚠️ 图表渲染失败：").append(caption).append("\n\n");
        }
        renderSourceMd(b, md);
    }

    private void renderTableMd(JsonNode b, StringBuilder md) {
        JsonNode columns = b.path("columns");
        JsonNode rows = b.path("rows");
        String caption = b.path("caption").asText("");
        if (!caption.isBlank()) {
            md.append("**").append(caption).append("**\n\n");
        }
        if (columns.isArray()) {
            md.append("|");
            for (JsonNode c : columns) md.append(" ").append(escapePipe(c.asText(""))).append(" |");
            md.append("\n|");
            for (int i = 0; i < columns.size(); i++) md.append(" --- |");
            md.append("\n");
        }
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                md.append("|");
                if (row.isArray()) {
                    for (JsonNode cell : row) {
                        md.append(" ").append(escapePipe(cell.asText(""))).append(" |");
                    }
                }
                md.append("\n");
            }
        }
        String appendixCsvRef = b.path("appendixCsvRef").asText("");
        if (!appendixCsvRef.isBlank()) {
            md.append("\n_完整数据见附录 CSV：[下载](./assets/appendix-").append(appendixCsvRef).append(".csv)_\n");
        }
        renderSourceMd(b, md);
        md.append("\n");
    }

    private void renderRiskListMd(JsonNode b, StringBuilder md) {
        JsonNode items = b.path("items");
        if (!items.isArray()) return;
        for (JsonNode it : items) {
            String severity = it.path("severity").asText("low");
            String emoji = switch (severity) {
                case "critical" -> "🔴";
                case "high" -> "🟠";
                case "medium" -> "🟡";
                default -> "🟢";
            };
            md.append("- ").append(emoji).append(" ").append(it.path("description").asText(""));
            StringBuilder meta = new StringBuilder();
            if (!it.path("owner").asText("").isBlank()) meta.append(it.path("owner").asText(""));
            if (!it.path("dueDate").asText("").isBlank()) {
                if (meta.length() > 0) meta.append(" · ");
                meta.append(it.path("dueDate").asText(""));
            }
            if (meta.length() > 0) md.append(" _(").append(meta).append(")_");
            md.append("\n");
        }
        md.append("\n");
    }

    private void renderTimelineMd(JsonNode b, StringBuilder md) {
        JsonNode events = b.path("events");
        if (!events.isArray()) return;
        for (JsonNode ev : events) {
            md.append("- **").append(ev.path("at").asText("")).append("** — ")
                    .append(ev.path("title").asText("")).append("：")
                    .append(ev.path("description").asText("")).append("\n");
        }
        md.append("\n");
    }

    private void renderAppendixMd(JsonNode b, StringBuilder md) {
        String subType = b.path("subType").asText("");
        String title = b.path("title").asText("");
        if (!title.isBlank()) md.append("### ").append(title).append("\n\n");
        JsonNode items = b.path("items");
        if (items.isArray()) {
            switch (subType) {
                case "sql-listing" -> {
                    for (JsonNode it : items) {
                        md.append("_").append(it.path("purpose").asText("")).append("_\n\n");
                        md.append("```sql\n").append(it.path("sql").asText("")).append("\n```\n\n");
                    }
                }
                case "glossary" -> {
                    for (JsonNode it : items) {
                        md.append("**").append(it.path("term").asText("")).append("** — ")
                                .append(it.path("definition").asText("")).append("\n\n");
                    }
                }
                case "csv-link" -> {
                    for (JsonNode it : items) {
                        md.append("- [").append(it.path("caption").asText("")).append("](./assets/appendix-")
                                .append(it.path("fileArtifactId").asText("")).append(".csv)\n");
                    }
                    md.append("\n");
                }
            }
        }
    }

    private void renderSourceMd(JsonNode b, StringBuilder md) {
        String source = b.path("source").asText("");
        if (!source.isBlank()) {
            md.append("_▸ ").append(source).append("_\n");
        }
    }

    private static String escapePipe(String s) {
        if (s == null) return "";
        return s.replace("|", "\\|").replace("\n", " ");
    }

    private static String escapeAlt(String s) {
        if (s == null) return "";
        return s.replace("]", "\\]").replace("[", "\\[");
    }
}
