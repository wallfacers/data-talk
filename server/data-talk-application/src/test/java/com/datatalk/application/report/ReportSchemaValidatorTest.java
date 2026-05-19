package com.datatalk.application.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        validator.validate(root);
    }

    @Test
    void rejects_schema_version_2() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 2, "kind": "report",
              "meta": { "title": "Hi", "templateId": "x" }, "sections": [{"type":"cover","title":"Hi"}] }
        """);
        assertThatThrownBy(() -> validator.validate(root))
                .isInstanceOf(ReportValidationException.class)
                .extracting(e -> ((ReportValidationException) e).getErrorCode())
                .isEqualTo("REPORT_SCHEMA_VERSION_UNSUPPORTED");
    }

    @Test
    void rejects_kind_dashboard() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "dashboard",
              "meta": { "title": "Hi", "templateId": "x" }, "sections": [{"type":"cover","title":"Hi"}] }
        """);
        assertThatThrownBy(() -> validator.validate(root))
                .isInstanceOf(ReportValidationException.class)
                .extracting(e -> ((ReportValidationException) e).getErrorCode())
                .isEqualTo("REPORT_KIND_INVALID");
    }

    @Test
    void rejects_unknown_block_type() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "Hi", "templateId": "x" },
              "sections": [ { "type": "video" } ] }
        """);
        assertThatThrownBy(() -> validator.validate(root))
                .isInstanceOf(ReportValidationException.class)
                .extracting(e -> ((ReportValidationException) e).getErrorCode())
                .isEqualTo("REPORT_BLOCK_TYPE_UNKNOWN");
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
        assertThatThrownBy(() -> validator.validate(root))
                .isInstanceOf(ReportValidationException.class)
                .extracting(e -> ((ReportValidationException) e).getErrorCode())
                .isEqualTo("REPORT_TABLE_OVERSIZE_NO_APPENDIX");
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
        validator.validate(root);
    }

    @Test
    void rejects_blank_title() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "", "templateId": "x" },
              "sections": [{"type":"cover","title":"Hi"}] }
        """);
        assertThatThrownBy(() -> validator.validate(root))
                .isInstanceOf(ReportValidationException.class)
                .extracting(e -> ((ReportValidationException) e).getErrorCode())
                .isEqualTo("REPORT_META_MISSING");
    }

    @Test
    void rejects_invalid_accent() throws Exception {
        JsonNode root = mapper.readTree("""
            { "schemaVersion": 1, "kind": "report",
              "meta": { "title": "Hi", "templateId": "x" },
              "theme": { "accent": "blueish" },
              "sections": [{"type":"cover","title":"Hi"}] }
        """);
        assertThatThrownBy(() -> validator.validate(root))
                .extracting(e -> ((ReportValidationException) e).getErrorCode())
                .isEqualTo("REPORT_THEME_INVALID");
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
        validator.validate(root);
    }
}
