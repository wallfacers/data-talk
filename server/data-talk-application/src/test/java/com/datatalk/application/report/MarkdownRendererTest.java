package com.datatalk.application.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    void renders_table_as_gfm() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [
                {"type":"cover","title":"T"},
                {"type":"chapter","heading":"渠道","blocks":[
                  {"type":"table","columns":["渠道","GMV","同比"],
                   "rows":[["自营","1.2M","+15%"],["抖音","0.8M","+28%"]]}
                ]} ] }
        """);
        String md = renderer.toMarkdown(root, Map.of());
        assertThat(md).contains("| 渠道 | GMV | 同比 |");
        assertThat(md).contains("| --- | --- | --- |");
        assertThat(md).contains("| 自营 | 1.2M | +15% |");
    }

    @Test
    void renders_new_blocks_as_gfm_downgrade() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [ {"type":"chapter","heading":"C","blocks":[
                {"type":"callout","variant":"insight","markdown":"核心洞察"},
                {"type":"callout","variant":"warning","markdown":"风险提示"},
                {"type":"stat-highlight","value":"¥3.2M","label":"总 GMV","delta":"+18%"},
                {"type":"comparison","items":[
                  {"label":"自营","value":"1.2M"},{"label":"抖音","value":"0.8M"}]},
                {"type":"quote","text":"数据说话","attribution":"CEO"},
                {"type":"divider","label":"小结"}
              ]} ] }
        """);
        String md = renderer.toMarkdown(root, Map.of());
        assertThat(md).contains("> 💡 **洞察**：核心洞察");
        assertThat(md).contains("> ⚠️ **警告**：风险提示");
        assertThat(md).contains("**¥3.2M** 总 GMV（+18%）");
        assertThat(md).contains("| 自营 | 抖音 |");
        assertThat(md).contains("> 数据说话");
        assertThat(md).contains("> — CEO");
        assertThat(md).contains("---");
    }

    @Test
    void table_markdown_ignores_cell_formats_outputs_plain_gfm() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [ {"type":"chapter","heading":"C","blocks":[
                {"type":"table","columns":["渠道","GMV"],
                 "cellFormats":["text","bar"],
                 "rows":[["自营","100"]]}
              ]} ] }
        """);
        String md = renderer.toMarkdown(root, Map.of());
        // 富单元格修饰被忽略，纯文本 GFM
        assertThat(md).contains("| 自营 | 100 |");
        assertThat(md).doesNotContain("ledger-cell");
    }

    @Test
    void renders_kpi_strip_as_single_row_gfm() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [
                {"type":"chapter","heading":"H","blocks":[
                  {"type":"kpi-strip","items":[
                    {"label":"GMV","value":"1.2M","delta":"+12%"},
                    {"label":"订单","value":"12,345","delta":"+8%"}
                  ]}
                ]} ] }
        """);
        String md = renderer.toMarkdown(root, Map.of());
        assertThat(md).contains("| GMV | 订单 |");
        assertThat(md).contains("1.2M ↑12%");
    }

    @Test
    void renders_chart_with_png_path() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [
                {"type":"chapter","heading":"H","blocks":[
                  {"type":"chart","id":"ch1","echartsOption":{},"caption":"4 月 GMV"}
                ]} ] }
        """);
        String md = renderer.toMarkdown(root, Map.of("ch1", Path.of("/tmp/ch1.png")));
        assertThat(md).contains("![4 月 GMV](./assets/chart-ch1.png)");
    }

    @Test
    void renders_chart_placeholder_when_png_missing() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [
                {"type":"chapter","heading":"H","blocks":[
                  {"type":"chart","id":"ch1","echartsOption":{},"caption":"4 月 GMV"}
                ]} ] }
        """);
        String md = renderer.toMarkdown(root, Map.of());
        assertThat(md).contains("⚠️ 图表渲染失败：4 月 GMV");
    }

    @Test
    void renders_timeline_as_ordered_list() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [
                {"type":"chapter","heading":"H","blocks":[
                  {"type":"timeline","events":[
                    {"at":"10:23","title":"首次报警","description":"延迟升至 35s"},
                    {"at":"10:45","title":"DBA 介入","description":"确认大事务"}
                  ]}
                ]} ] }
        """);
        String md = renderer.toMarkdown(root, Map.of());
        assertThat(md).contains("- **10:23** — 首次报警：延迟升至 35s");
        assertThat(md).contains("- **10:45** — DBA 介入：确认大事务");
    }

    @Test
    void toc_renders_gfm_anchor_links() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [
                {"type":"cover","title":"T"},
                {"type":"toc"},
                {"type":"chapter","heading":"业务总览","blocks":[]},
                {"type":"chapter","heading":"区域分析","blocks":[]}
              ] }
        """);
        String md = renderer.toMarkdown(root, Map.of());
        assertThat(md).contains("## 目录");
        assertThat(md).contains("- [业务总览](#业务总览)");
        assertThat(md).contains("- [区域分析](#区域分析)");
    }

    @Test
    void toc_does_not_emit_section_when_no_chapters() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [
                {"type":"cover","title":"T"},
                {"type":"toc"}
              ] }
        """);
        String md = renderer.toMarkdown(root, Map.of());
        assertThat(md).doesNotContain("## 目录");
    }

    @Test
    void meta_author_watermark_sanitized_in_markdown() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x",
                        "author": "DataTalk 自动生成", "generatedAt": "2026-05-19T10:00:00+08:00" },
              "sections": [ {"type":"cover","title":"T"} ] }
        """);
        String md = renderer.toMarkdown(root, Map.of());
        // 顶部 meta 行不含水印；仅含 generatedAt
        assertThat(md).doesNotContain("DataTalk 自动生成");
        assertThat(md).contains("2026-05-19T10:00:00+08:00");
    }

    @Test
    void meta_author_legal_value_preserved_in_markdown() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x",
                        "author": "数据分析团队", "generatedAt": "2026-05-19T10:00:00+08:00" },
              "sections": [ {"type":"cover","title":"T"} ] }
        """);
        String md = renderer.toMarkdown(root, Map.of());
        assertThat(md).contains("数据分析团队");
        assertThat(md).contains("数据分析团队 · 2026-05-19T10:00:00+08:00");
    }

    @Test
    void renders_risk_list_with_severity_emoji() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [
                {"type":"chapter","heading":"H","blocks":[
                  {"type":"risk-list","items":[
                    {"severity":"critical","description":"严重项"},
                    {"severity":"high","description":"高项","owner":"DBA","dueDate":"2026-05-30"},
                    {"severity":"medium","description":"中项"},
                    {"severity":"low","description":"低项"}
                  ]}
                ]} ] }
        """);
        String md = renderer.toMarkdown(root, Map.of());
        assertThat(md).contains("🔴 严重项");
        assertThat(md).contains("🟠 高项 _(DBA · 2026-05-30)_");
        assertThat(md).contains("🟡 中项");
        assertThat(md).contains("🟢 低项");
    }
}
