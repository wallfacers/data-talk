package com.datatalk.application.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 校验 AI 提交的 report.json：
 * - schemaVersion 必须为 1
 * - kind 必须为 "report"
 * - meta.title / meta.templateId 必填
 * - 所有 section / 嵌套 chapter.blocks 的 type 在 {@link #ALLOWED_BLOCK_TYPES} 集合内
 * - table.rows.length > 200 时必须有 appendixCsvRef
 *
 * 不做 ECharts option 内部结构校验（推迟到 v1+，避免与 ECharts 版本绑死）。
 */
@Component
public class ReportSchemaValidator {

    public static final int SCHEMA_VERSION = 1;
    public static final String KIND = "report";

    public static final Set<String> ALLOWED_BLOCK_TYPES = Set.of(
            "cover",
            "executive-summary",
            "toc",
            "chapter",
            "kpi-strip",
            "narrative",
            "chart",
            "table",
            "risk-list",
            "timeline",
            "appendix"
    );

    /** rows 超过此阈值必须配 appendixCsvRef。 */
    public static final int MAX_INLINE_TABLE_ROWS = 200;

    public void validate(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw fail("REPORT_INVALID", "report.json must be a JSON object");
        }
        int schemaVersion = root.path("schemaVersion").asInt(-1);
        if (schemaVersion != SCHEMA_VERSION) {
            throw fail("REPORT_SCHEMA_VERSION_UNSUPPORTED",
                    "Unsupported schemaVersion: " + root.path("schemaVersion") + " (expected " + SCHEMA_VERSION + ")");
        }
        String kind = root.path("kind").asText("");
        if (!KIND.equals(kind)) {
            throw fail("REPORT_KIND_INVALID", "kind must equal '" + KIND + "', got '" + kind + "'");
        }
        JsonNode meta = root.path("meta");
        if (!meta.isObject()) {
            throw fail("REPORT_META_MISSING", "meta object is required");
        }
        if (meta.path("title").asText("").isBlank()) {
            throw fail("REPORT_META_MISSING", "meta.title is required");
        }
        if (meta.path("templateId").asText("").isBlank()) {
            throw fail("REPORT_META_MISSING", "meta.templateId is required");
        }

        JsonNode theme = root.path("theme");
        if (theme.isObject()) {
            String accent = theme.path("accent").asText("");
            if (!accent.isBlank() && !accent.matches("^#[0-9a-fA-F]{6}$")) {
                throw fail("REPORT_THEME_INVALID", "theme.accent must be a 6-digit hex color");
            }
        }

        JsonNode sections = root.path("sections");
        if (!sections.isArray() || sections.isEmpty()) {
            throw fail("REPORT_SECTIONS_MISSING", "sections array must be non-empty");
        }
        for (JsonNode section : sections) {
            validateBlock(section);
        }

        JsonNode appendix = root.path("appendix");
        if (appendix.isArray()) {
            for (JsonNode item : appendix) {
                validateBlock(item);
            }
        }
    }

    private void validateBlock(JsonNode block) {
        if (block == null || !block.isObject()) {
            throw fail("REPORT_BLOCK_INVALID", "block must be a JSON object");
        }
        String type = block.path("type").asText("");
        if (type.isBlank()) {
            throw fail("REPORT_BLOCK_TYPE_MISSING", "block.type is required");
        }
        if (!ALLOWED_BLOCK_TYPES.contains(type)) {
            throw fail("REPORT_BLOCK_TYPE_UNKNOWN", "Unknown block type: " + type);
        }
        switch (type) {
            case "table" -> validateTableBlock(block);
            case "chapter" -> {
                JsonNode blocks = block.path("blocks");
                if (blocks.isArray()) {
                    for (JsonNode child : blocks) {
                        validateBlock(child);
                    }
                }
            }
            default -> { /* primitive types: no recursive structure */ }
        }
    }

    private void validateTableBlock(JsonNode block) {
        JsonNode rows = block.path("rows");
        if (!rows.isArray()) {
            throw fail("REPORT_TABLE_ROWS_MISSING", "table.rows must be an array");
        }
        int rowCount = rows.size();
        if (rowCount > MAX_INLINE_TABLE_ROWS) {
            String appendixCsvRef = block.path("appendixCsvRef").asText("");
            if (appendixCsvRef.isBlank()) {
                throw fail("REPORT_TABLE_OVERSIZE_NO_APPENDIX",
                        "table block has " + rowCount + " rows (> " + MAX_INLINE_TABLE_ROWS
                                + "); appendixCsvRef must be present");
            }
        }
    }

    private static ReportValidationException fail(String code, String message) {
        return new ReportValidationException(code, message);
    }
}
