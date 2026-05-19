package com.datatalk.application.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validator returns {@link Violation} list — empty means accepted. Callers are responsible
 * for wrapping a non-empty result into {@link ReportValidationException}.
 */
class ReportSchemaValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ReportSchemaValidator validator = new ReportSchemaValidator();

    @Test
    void accepts_minimal_valid_report() throws Exception {
        JsonNode root = mapper.readTree("""
            {
              "schemaVersion": 1,
              "kind": "report",
              "meta": { "title": "Hi", "templateId": "ledger.monthly-business-review.v1" },
              "theme": { "accent": "#1f4e79" },
              "sections": [ { "type": "cover", "title": "Hi" } ]
            }
        """);
        assertThat(validator.validate(root)).isEmpty();
    }

    @Test
    void rejects_schema_version_2() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 2, "kind": "report",
              "meta": { "title": "Hi", "templateId": "x" }, "sections": [{"type":"cover","title":"Hi"}] }
        """);
        List<Violation> violations = validator.validate(root);
        assertThat(violations).extracting(Violation::code)
                .contains("REPORT_SCHEMA_VERSION_UNSUPPORTED");
    }

    @Test
    void rejects_kind_dashboard() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "dashboard",
              "meta": { "title": "Hi", "templateId": "x" }, "sections": [{"type":"cover","title":"Hi"}] }
        """);
        assertThat(validator.validate(root))
                .extracting(Violation::code)
                .contains("REPORT_KIND_INVALID");
    }

    @Test
    void rejects_unknown_block_type_with_path() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "Hi", "templateId": "x" },
              "sections": [ { "type": "video" } ] }
        """);
        List<Violation> violations = validator.validate(root);
        Violation v = violations.stream()
                .filter(x -> "REPORT_BLOCK_TYPE_UNKNOWN".equals(x.code()))
                .findFirst().orElseThrow();
        assertThat(v.path()).isEqualTo("sections[0]");
        assertThat(v.message()).contains("video");
    }

    @Test
    void rejects_oversize_table_without_appendix() throws Exception {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 201; i++) {
            if (i > 0) rows.append(",");
            rows.append("[\"r").append(i).append("\"]");
        }
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "Hi", "templateId": "x" },
              "sections": [ { "type": "chapter", "heading": "Ch",
                "blocks": [ { "type": "table", "columns": ["c"], "rows": [%s] } ] } ] }
        """.formatted(rows));
        List<Violation> violations = validator.validate(root);
        Violation v = violations.stream()
                .filter(x -> "REPORT_TABLE_OVERSIZE_NO_APPENDIX".equals(x.code()))
                .findFirst().orElseThrow();
        assertThat(v.path()).isEqualTo("sections[0].blocks[0]");
    }

    @Test
    void accepts_oversize_table_with_appendix_ref() throws Exception {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 201; i++) {
            if (i > 0) rows.append(",");
            rows.append("[\"r").append(i).append("\"]");
        }
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "Hi", "templateId": "x" },
              "sections": [ { "type": "chapter", "heading": "Ch",
                "blocks": [ { "type": "table", "columns": ["c"], "rows": [%s],
                              "appendixCsvRef": "fa-csv-9e3d4" } ] } ] }
        """.formatted(rows));
        assertThat(validator.validate(root)).isEmpty();
    }

    @Test
    void rejects_blank_title_with_meta_path() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "", "templateId": "x" },
              "sections": [{"type":"cover","title":"Hi"}] }
        """);
        List<Violation> violations = validator.validate(root);
        Violation v = violations.stream()
                .filter(x -> "REPORT_META_MISSING".equals(x.code()) && "meta.title".equals(x.path()))
                .findFirst().orElseThrow();
        assertThat(v.message()).contains("meta.title");
    }

    @Test
    void rejects_invalid_accent() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "Hi", "templateId": "x" },
              "theme": { "accent": "blueish" },
              "sections": [{"type":"cover","title":"Hi"}] }
        """);
        assertThat(validator.validate(root))
                .extracting(Violation::code)
                .contains("REPORT_THEME_INVALID");
    }

    @Test
    void accepts_all_known_block_types() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "All", "templateId": "ledger.monthly-business-review.v1" },
              "sections": [
                { "type": "cover", "title": "T" },
                { "type": "executive-summary", "bullets": ["a"] },
                { "type": "toc" },
                { "type": "chapter", "heading": "H", "blocks": [
                  { "type": "kpi-strip", "items": [{"label":"x","value":"1"}] },
                  { "type": "narrative", "markdown": "**bold**" },
                  { "type": "chart", "id": "c1", "echartsOption": {}, "caption": "" },
                  { "type": "table", "columns": ["c"], "rows": [["a"]] },
                  { "type": "risk-list", "items": [{"severity":"high","description":"x"}] },
                  { "type": "timeline", "events": [{"at":"now","title":"t","description":"d"}] }
                ] }
              ],
              "appendix": [
                { "type": "appendix", "subType": "sql-listing", "title": "SQL", "items": [] }
              ]
            }
        """);
        assertThat(ReportSchemaValidator.ALLOWED_BLOCK_TYPES).contains(
                "cover", "executive-summary", "toc", "chapter",
                "kpi-strip", "narrative", "chart", "table",
                "risk-list", "timeline", "appendix");
        assertThat(validator.validate(root)).isEmpty();
    }

    @Test
    void collects_multiple_violations_in_one_pass() throws Exception {
        // schemaVersion=2 + kind=dashboard + meta.title 缺 + sections 缺 + 未知 block (在 appendix 处)
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 2, "kind": "dashboard",
              "meta": { "templateId": "" },
              "sections": [],
              "appendix": [ { "type": "video" } ] }
        """);
        List<Violation> violations = validator.validate(root);
        List<String> codes = violations.stream().map(Violation::code).toList();
        assertThat(codes)
                .contains("REPORT_SCHEMA_VERSION_UNSUPPORTED")
                .contains("REPORT_KIND_INVALID")
                .contains("REPORT_META_MISSING")
                .contains("REPORT_SECTIONS_MISSING")
                .contains("REPORT_BLOCK_TYPE_UNKNOWN");
        assertThat(violations.size()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void recovery_hints_cover_every_emitted_code() {
        // 每个会出现的 violation code 都必须有对应的 recovery hint，否则 AI 看不到指引。
        Set<String> emittedCodes = Set.of(
                "REPORT_INVALID",
                "REPORT_SCHEMA_VERSION_UNSUPPORTED",
                "REPORT_KIND_INVALID",
                "REPORT_META_MISSING",
                "REPORT_THEME_INVALID",
                "REPORT_SECTIONS_MISSING",
                "REPORT_BLOCK_INVALID",
                "REPORT_BLOCK_TYPE_MISSING",
                "REPORT_BLOCK_TYPE_UNKNOWN",
                "REPORT_TABLE_ROWS_MISSING",
                "REPORT_TABLE_OVERSIZE_NO_APPENDIX"
        );
        for (String code : emittedCodes) {
            assertThat(ReportSchemaValidator.RECOVERY_HINTS).containsKey(code);
            assertThat(ReportSchemaValidator.RECOVERY_HINTS.get(code)).isNotBlank();
        }
    }

    @Test
    void path_format_for_nested_blocks() throws Exception {
        // chapter.blocks 中第二个 block (idx 1) type 未知 → path 应为 sections[0].blocks[1]
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "T", "templateId": "x" },
              "sections": [
                { "type": "chapter", "heading": "H", "blocks": [
                  { "type": "narrative", "markdown": "ok" },
                  { "type": "alien" }
                ] }
              ] }
        """);
        List<Violation> violations = validator.validate(root);
        Violation v = violations.stream()
                .filter(x -> "REPORT_BLOCK_TYPE_UNKNOWN".equals(x.code()))
                .findFirst().orElseThrow();
        assertThat(v.path()).isEqualTo("sections[0].blocks[1]");
    }

    @Test
    void exception_wraps_violations_with_first_code() {
        List<Violation> vs = List.of(
                new Violation("REPORT_META_MISSING", "meta.title", "meta.title is required"),
                new Violation("REPORT_SECTIONS_MISSING", "sections", "sections array must be non-empty"));
        ReportValidationException e = new ReportValidationException(vs);
        assertThat(e.getErrorCode()).isEqualTo("REPORT_META_MISSING");
        assertThat(e.getErrorCodes()).containsExactly("REPORT_META_MISSING", "REPORT_SECTIONS_MISSING");
        assertThat(e.getMessage()).contains("meta.title").contains("sections");
        assertThat(e.getViolations()).hasSize(2);
    }
}
