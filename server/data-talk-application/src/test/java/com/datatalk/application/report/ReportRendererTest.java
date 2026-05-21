package com.datatalk.application.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportRendererTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ReportRenderer renderer = new ReportRenderer(mapper);

    @Test
    void auto_fills_missing_chart_ids_uniquely_including_nested_and_preserving_existing() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [
                { "type": "chart", "echartsOption": {} },
                { "type": "chapter", "heading": "H", "blocks": [
                  { "type": "chart", "id": "ch-auto-1", "echartsOption": {} },
                  { "type": "chart", "echartsOption": {} }
                ] }
              ] }
        """);
        ReportRenderer.normalizeChartIds(root);

        String topId = root.path("sections").get(0).path("id").asText("");
        String keptId = root.path("sections").get(1).path("blocks").get(0).path("id").asText("");
        String nestedId = root.path("sections").get(1).path("blocks").get(1).path("id").asText("");

        assertThat(keptId).isEqualTo("ch-auto-1"); // 既有 id 保留
        assertThat(topId).isNotBlank();
        assertThat(nestedId).isNotBlank();
        // 三个 id 互不相同（补全时跳过已占用的 ch-auto-1）
        assertThat(java.util.Set.of(topId, keptId, nestedId)).hasSize(3);
    }

    @Test
    void chart_without_id_renders_with_usable_id_in_html() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [ { "type": "chapter", "heading": "H", "blocks": [
                { "type": "chart", "echartsOption": { "series": [] }, "caption": "无 id 图表" }
              ] } ] }
        """);
        String html = renderer.toHtml(root);
        // 不再出现空 id 的容器（会导致 querySelector 匹配失败 → 图表不初始化）
        assertThat(html).doesNotContain("data-ledger-chart-id=\"\"");
        assertThat(html).contains("data-ledger-chart-id=\"ch-auto-1\"");
    }

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
        // 显式 accent 被尊重（hex 归一化为大写），并注入完整角色色调色板
        assertThat(html).contains("--ledger-accent:#1F4E79");
        assertThat(html).contains("--ledger-primary:");
        assertThat(html).contains("--ledger-surface:");
        assertThat(html).contains("--ledger-tint-1:");
    }

    @Test
    void injects_full_default_palette_when_no_theme() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [{"type":"cover","title":"T"}] }
        """);
        String html = renderer.toHtml(root);
        // 无 theme → 整套默认设计 token
        assertThat(html).contains("--ledger-primary:#0F2A4A");
        assertThat(html).contains("--ledger-accent:#2F6FBF");
        assertThat(html).contains("--ledger-surface:#F4F7FB");
        assertThat(html).contains("--ledger-tint-1:#E0E9F5");
        assertThat(html).contains("--ledger-positive:#1F7A4E");
    }

    @Test
    void injects_echarts_palette_before_report_option() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [ {"type":"chapter","heading":"C","blocks":[
                {"type":"chart","id":"c1","echartsOption":{"series":[]},"caption":""} ]} ] }
        """);
        String html = renderer.toHtml(root);
        // PALETTE 注入，且 setOption({color:PALETTE},false) 先于 setOption(c.option)
        assertThat(html).contains("var PALETTE = [");
        assertThat(html).contains("inst.setOption({ color: PALETTE }, false);");
        int paletteSet = html.indexOf("inst.setOption({ color: PALETTE }, false);");
        int optionSet = html.indexOf("inst.setOption(c.option);");
        assertThat(paletteSet).isLessThan(optionSet);
    }

    @Test
    void renders_five_new_rich_visual_blocks() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [ {"type":"chapter","heading":"C","blocks":[
                {"type":"callout","variant":"insight","title":"标题","markdown":"**核心**洞察"},
                {"type":"stat-highlight","value":"¥3.2M","label":"GMV","delta":"+18%"},
                {"type":"comparison","items":[
                  {"label":"自营","value":"1.2M"},{"label":"抖音","value":"0.8M"}]},
                {"type":"quote","text":"引用文本","attribution":"张三"},
                {"type":"divider","label":"小结"}
              ]} ] }
        """);
        String html = renderer.toHtml(root);
        assertThat(html).contains("ledger-callout ledger-callout--insight");
        assertThat(html).contains("💡");
        assertThat(html).contains("<strong>核心</strong>洞察");
        assertThat(html).contains("ledger-stat-highlight__value");
        assertThat(html).contains("ledger-stat-highlight__delta--up");
        assertThat(html).contains("ledger-comparison__item");
        assertThat(html).contains("ledger-quote__text");
        assertThat(html).contains("ledger-divider--labeled");
        assertThat(html).contains("小结");
    }

    @Test
    void renders_table_cell_formats() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [ {"type":"chapter","heading":"C","blocks":[
                {"type":"table","columns":["渠道","GMV","同比","占比"],
                 "cellFormats":["text","bar","delta","heat"],
                 "rows":[["自营","100","+15%","80"],["抖音","50","-3%","20"]]}
              ]} ] }
        """);
        String html = renderer.toHtml(root);
        assertThat(html).contains("ledger-cell--bar");
        assertThat(html).contains("ledger-cell-bar__fill");
        assertThat(html).contains("ledger-cell--delta-up");
        assertThat(html).contains("ledger-cell--delta-down");
        assertThat(html).contains("ledger-cell--heat");
    }

    @Test
    void cell_format_falls_back_to_text_for_non_numeric() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [ {"type":"chapter","heading":"C","blocks":[
                {"type":"table","columns":["a","b"],
                 "cellFormats":["text","bar"],
                 "rows":[["x","N/A"]]}
              ]} ] }
        """);
        // bar 列遇非数值 → 安全回退纯 <td>，不抛异常、不产出 bar 结构
        String html = renderer.toHtml(root);
        assertThat(html).contains("<td>N/A</td>");
    }

    @Test
    void mixed_form_old_theme_old_blocks_plus_one_callout() throws Exception {
        // 向后兼容：旧 theme.accent + 旧块 + 新增一个 callout，互不影响
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "theme": { "accent": "#1f4e79" },
              "sections": [
                {"type":"cover","title":"T"},
                {"type":"chapter","heading":"C","blocks":[
                  {"type":"kpi-strip","items":[{"label":"x","value":"1","delta":"+5%"}]},
                  {"type":"callout","variant":"warning","markdown":"注意风险"},
                  {"type":"narrative","markdown":"段落"}
                ]} ] }
        """);
        String html = renderer.toHtml(root);
        assertThat(html).contains("--ledger-accent:#1F4E79");      // 显式 accent 尊重
        assertThat(html).contains("ledger-kpi-card__value");       // 旧块正常
        assertThat(html).contains("ledger-callout--warning");      // 新块正常
        assertThat(html).contains("⚠️");
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
        // chart 容器含内联 fallback 高度
        assertThat(html).contains("style=\"height:360px\"");
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
    void source_footnote_is_not_rendered_even_when_source_field_present() throws Exception {
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
        assertThat(html).doesNotContain("ledger-block-source");
        assertThat(html).doesNotContain("mysql-prod · sales_summary");
    }

    @Test
    void toc_renders_chapter_links_with_chap_anchors() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [
                {"type":"cover","title":"T"},
                {"type":"executive-summary","bullets":["a"]},
                {"type":"toc"},
                {"type":"chapter","heading":"业务总览","blocks":[]},
                {"type":"chapter","heading":"区域分析","blocks":[]},
                {"type":"chapter","heading":"渠道表现","blocks":[]}
              ] }
        """);
        String html = renderer.toHtml(root);
        // TOC contains anchor links pointing at chap-1/2/3
        assertThat(html).contains("<section class=\"ledger-toc\">");
        assertThat(html).contains("<a href=\"#chap-1\">业务总览</a>");
        assertThat(html).contains("<a href=\"#chap-2\">区域分析</a>");
        assertThat(html).contains("<a href=\"#chap-3\">渠道表现</a>");
        // Chapter <section> tags carry matching id anchors
        assertThat(html).contains("id=\"chap-1\"");
        assertThat(html).contains("id=\"chap-2\"");
        assertThat(html).contains("id=\"chap-3\"");
    }

    @Test
    void toc_anchor_interceptor_script_injected_to_neutralize_base_href() throws Exception {
        // BUG-0077: with <base href="…/_assets/"> a bare `<a href="#chap-N">` would otherwise
        // resolve against the base, triggering cross-document navigation. The interceptor
        // below converts hash links into scrollIntoView calls so TOC stays same-document.
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [
                {"type":"toc"},
                {"type":"chapter","heading":"业务","blocks":[]}
              ] }
        """);
        String html = renderer.toHtml(root);
        assertThat(html).contains(ReportRenderer.ANCHOR_INTERCEPTOR_SCRIPT);
        // sanity: key behavior tokens present in the injected script
        assertThat(html).contains("a[href^=\"#\"]");
        assertThat(html).contains("preventDefault");
        assertThat(html).contains("scrollIntoView");
    }

    @Test
    void toc_renders_empty_ol_when_no_chapters() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [
                {"type":"cover","title":"T"},
                {"type":"executive-summary","bullets":["a"]},
                {"type":"toc"}
              ] }
        """);
        String html = renderer.toHtml(root);
        assertThat(html).contains("<section class=\"ledger-toc\"><h2>目录</h2><ol></ol></section>");
    }

    @Test
    void cover_author_watermark_gets_sanitized_at_render() throws Exception {
        for (String watermark : new String[] {
                "DataTalk 自动生成", "DataTalk·自动生成", "DataTalk-Auto-generated",
                "AI 生成", "AI生成", "自动生成", "系统生成", "Auto-generated",
                "Generated by DataTalk", "generated by xyz"
        }) {
            String esc = watermark.replace("\"", "\\\"");
            JsonNode root = mapper.readTree("""
                { "schemaVersion": 1, "kind": "report",
                  "meta": { "title": "T", "templateId": "x" },
                  "sections": [
                    {"type":"cover","title":"T","author":"%s","date":"2026-05-19"}
                  ] }
            """.formatted(esc));
            String html = renderer.toHtml(root);
            // 渲染后 cover meta 行不应包含黑名单文案
            assertThat(html).as("watermark %s should be stripped", watermark)
                    .doesNotContain(watermark);
            // date 仍正常显示
            assertThat(html).contains("2026-05-19");
        }
    }

    @Test
    void cover_author_legal_value_preserved() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [
                {"type":"cover","title":"T","author":"数据分析团队","date":"2026-05-19"}
              ] }
        """);
        String html = renderer.toHtml(root);
        assertThat(html).contains("数据分析团队");
        assertThat(html).contains("2026-05-19");
        // 渲染后 meta 行格式：作者 · 日期
        assertThat(html).contains("数据分析团队 · 2026-05-19");
    }

    @Test
    void sanitize_author_unit_table() {
        // 命中黑名单 → 空
        assertThat(ReportRenderer.sanitizeAuthor("DataTalk 自动生成")).isEmpty();
        assertThat(ReportRenderer.sanitizeAuthor("  DataTalk 自动生成  ")).isEmpty();
        assertThat(ReportRenderer.sanitizeAuthor("AI 生成")).isEmpty();
        assertThat(ReportRenderer.sanitizeAuthor("自动生成")).isEmpty();
        assertThat(ReportRenderer.sanitizeAuthor("Generated by Foo")).isEmpty();
        assertThat(ReportRenderer.sanitizeAuthor("Auto-generated")).isEmpty();
        assertThat(ReportRenderer.sanitizeAuthor(null)).isEmpty();
        assertThat(ReportRenderer.sanitizeAuthor("")).isEmpty();
        // 合法值保留
        assertThat(ReportRenderer.sanitizeAuthor("数据分析团队")).isEqualTo("数据分析团队");
        assertThat(ReportRenderer.sanitizeAuthor("张三")).isEqualTo("张三");
        assertThat(ReportRenderer.sanitizeAuthor("DataTalk 团队张三")).isEqualTo("DataTalk 团队张三");
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

    @Test
    void drops_sql_listing_appendix_and_emits_no_empty_appendix_section() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [ { "type": "chapter", "heading": "C", "blocks": [] } ],
              "appendix": [
                { "type": "appendix", "subType": "sql-listing", "title": "SQL 清单",
                  "items": [{ "connectionId": "c1", "sql": "SELECT 1", "purpose": "p" }] }
              ] }
        """);
        String html = renderer.toHtml(root);
        assertThat(html).doesNotContain("SQL 清单");
        assertThat(html).doesNotContain("SELECT 1");
        assertThat(html).doesNotContain("ledger-appendix");
    }

    @Test
    void keeps_glossary_appendix_while_dropping_sql_listing() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [ { "type": "chapter", "heading": "C", "blocks": [] } ],
              "appendix": [
                { "type": "appendix", "subType": "sql-listing", "title": "SQL 清单",
                  "items": [{ "connectionId": "c1", "sql": "SELECT 1", "purpose": "p" }] },
                { "type": "appendix", "subType": "glossary", "title": "术语表",
                  "items": [{ "term": "GMV", "definition": "成交总额" }] }
              ] }
        """);
        String html = renderer.toHtml(root);
        assertThat(html).doesNotContain("SQL 清单");
        assertThat(html).doesNotContain("SELECT 1");
        assertThat(html).contains("术语表");
        assertThat(html).contains("GMV");
    }

    @Test
    void drops_sql_bearing_appendix_even_when_subtype_mislabeled() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [ { "type": "chapter", "heading": "C", "blocks": [] } ],
              "appendix": [
                { "type": "appendix", "subType": "glossary", "title": "查询",
                  "items": [{ "sql": "SELECT 42", "purpose": "p" }] }
              ] }
        """);
        String html = renderer.toHtml(root);
        assertThat(html).doesNotContain("SELECT 42");
        assertThat(html).doesNotContain("ledger-appendix");
    }

    @Test
    void drops_appendix_titled_sql_listing_regardless_of_subtype() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [ { "type": "chapter", "heading": "C", "blocks": [] } ],
              "appendix": [
                { "type": "appendix", "subType": "glossary", "title": "SQL清单",
                  "items": [{ "term": "x", "definition": "y" }] }
              ] }
        """);
        String html = renderer.toHtml(root);
        assertThat(html).doesNotContain("SQL清单");
        assertThat(html).doesNotContain("ledger-appendix");
    }
}
