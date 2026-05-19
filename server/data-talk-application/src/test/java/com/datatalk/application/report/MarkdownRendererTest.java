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
