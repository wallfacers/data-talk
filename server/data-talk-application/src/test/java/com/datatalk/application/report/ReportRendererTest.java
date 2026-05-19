package com.datatalk.application.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportRendererTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ReportRenderer renderer = new ReportRenderer(mapper);

    @Test
    void emits_self_contained_html_with_base_href() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "Hi 月报", "templateId": "x" },
              "theme": { "accent": "#1f4e79" },
              "sections": [ { "type": "cover", "title": "Hi 月报", "author": "DAT" } ] }
        """);
        String html = renderer.toHtml(root);
        assertThat(html).contains("<base href=\"/api/reports/_assets/\">");
        assertThat(html).contains("href=\"styles/ledger.css\"");
        assertThat(html).contains("src=\"scripts/echarts.min.js\"");
        assertThat(html).contains("Hi 月报");
        // 不含外网 URL
        assertThat(html).doesNotContain("http://");
        assertThat(html).doesNotContain("https://");
        // 含 accent token
        assertThat(html).contains("--ledger-accent: #1f4e79");
    }

    @Test
    void emits_ledger_ready_immediately_for_zero_charts() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [{"type":"cover","title":"T"}] }
        """);
        String html = renderer.toHtml(root);
        // 0 chart 场景：bootstrap script 中 pending == 0 立即 set LEDGER_READY
        assertThat(html).contains("if (pending === 0) { window.__LEDGER_READY__ = true; return; }");
    }

    @Test
    void emits_chart_with_data_ledger_chart_id_attribute() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [
                { "type": "chapter", "heading": "C",
                  "blocks": [
                    { "type": "chart", "id": "ch1", "echartsOption": {"series":[]}, "caption": "图表 1" }
                  ] }
              ] }
        """);
        String html = renderer.toHtml(root);
        assertThat(html).contains("data-ledger-chart-id=\"ch1\"");
        // bootstrap 包含 chart id
        assertThat(html).contains("\"ch1\"");
        assertThat(html).contains("inst.on('finished', markFinished)");
    }

    @Test
    void escapes_html_in_user_strings() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "<script>x</script>", "templateId": "x" },
              "sections": [{"type":"cover","title":"<b>x</b>"}] }
        """);
        String html = renderer.toHtml(root);
        assertThat(html).contains("&lt;script&gt;x&lt;/script&gt;");
        assertThat(html).contains("&lt;b&gt;x&lt;/b&gt;");
        assertThat(html).doesNotContain("<script>x</script>");
    }

    @Test
    void renders_table_with_paged_class_when_over_30_rows() throws Exception {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            if (i > 0) rows.append(",");
            rows.append("[\"r").append(i).append("\"]");
        }
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [
                { "type": "chapter", "heading": "C",
                  "blocks": [{"type":"table","columns":["c"],"rows":[%s]}] }
              ] }
        """.formatted(rows));
        String html = renderer.toHtml(root);
        assertThat(html).contains("ledger-table--paged");
    }

    @Test
    void renders_source_footnote_when_source_field_present() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [
                { "type": "chapter", "heading": "C",
                  "blocks": [{"type":"table","columns":["c"],"rows":[["a"]],
                              "source":"mysql-prod · sales_summary"}] }
              ] }
        """);
        String html = renderer.toHtml(root);
        assertThat(html).contains("ledger-block-source");
        assertThat(html).contains("mysql-prod · sales_summary");
    }

    @Test
    void renders_appendix_csv_link_when_appendix_csv_ref_present() throws Exception {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            if (i > 0) rows.append(",");
            rows.append("[\"r").append(i).append("\"]");
        }
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [
                { "type": "chapter", "heading": "C",
                  "blocks": [{"type":"table","columns":["c"],"rows":[%s],
                              "appendixCsvRef": "fa-csv-9e3d4"}] }
              ] }
        """.formatted(rows));
        String html = renderer.toHtml(root);
        assertThat(html).contains("完整数据见附录 CSV");
        assertThat(html).contains("/api/file-artifacts/fa-csv-9e3d4/download");
    }
}
