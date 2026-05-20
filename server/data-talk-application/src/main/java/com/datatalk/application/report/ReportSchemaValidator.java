package com.datatalk.application.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 校验 AI 提交的 report.json，collect-all 全部违规一次性返回：
 * - schemaVersion 必须为 1
 * - kind 必须为 "report"
 * - meta.title / meta.templateId 必填
 * - 所有 section / 嵌套 chapter.blocks 的 type 在 {@link #ALLOWED_BLOCK_TYPES} 集合内
 * - table.rows.length > 200 时必须有 appendixCsvRef
 *
 * <p>每条 {@link Violation} 携带稳定的 errorCode、JSON path 与说明；调用方决定
 * 是否抛 {@link ReportValidationException} 或转换为 action 错误返回。
 *
 * <p>不做 ECharts option 内部结构校验（推迟到 v1+，避免与 ECharts 版本绑死）。
 */
@Component
public class ReportSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(ReportSchemaValidator.class);

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
            "appendix",
            // 富视觉原语（现代报告设计语言）
            "callout",
            "stat-highlight",
            "comparison",
            "quote",
            "divider"
    );

    /** callout.variant 合法取值。 */
    public static final Set<String> ALLOWED_CALLOUT_VARIANTS = Set.of(
            "insight", "warning", "note", "success");

    /** table.cellFormats 合法取值。 */
    public static final Set<String> ALLOWED_CELL_FORMATS = Set.of(
            "text", "bar", "delta", "heat");

    /** comparison.items 长度区间（含端点）。 */
    public static final int COMPARISON_MIN_ITEMS = 2;
    public static final int COMPARISON_MAX_ITEMS = 4;

    /** theme.tints 软上限：超限仅 warn 不 reject。 */
    public static final int TINTS_SOFT_CAP = 8;

    /** rows 超过此阈值必须配 appendixCsvRef。 */
    public static final int MAX_INLINE_TABLE_ROWS = 200;

    /**
     * code → 中文修复提示。值文本面向 AI（OpenCode prompt），不直接面向用户。
     * 维护时保持 key 与 {@link #validate(JsonNode)} 中所有 violation code 一一对应。
     */
    public static final Map<String, String> RECOVERY_HINTS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("REPORT_INVALID", "report.json 必须是 JSON object");
        m.put("REPORT_SCHEMA_VERSION_UNSUPPORTED", "把 schemaVersion 设为 1");
        m.put("REPORT_KIND_INVALID", "把 kind 设为 \"report\"");
        m.put("REPORT_META_MISSING", "把 title / templateId 等必填字段放进顶层 meta 对象");
        m.put("REPORT_THEME_INVALID",
                "theme 的 accent/primary/surface 与 tints[] 每项都必须是 6 位十六进制颜色（如 #2F6FBF）");
        m.put("REPORT_SECTIONS_MISSING", "顶层用非空的 sections 数组而不是 blocks");
        m.put("REPORT_BLOCK_INVALID", "block 必须是 JSON object");
        m.put("REPORT_BLOCK_TYPE_MISSING", "每个 block 必须显式声明 type 字段");
        m.put("REPORT_BLOCK_TYPE_UNKNOWN", "type 必须是 ALLOWED_BLOCK_TYPES 集合内的值（见 section-patterns.md）");
        m.put("REPORT_TABLE_ROWS_MISSING", "table block 必须有 rows 数组");
        m.put("REPORT_TABLE_OVERSIZE_NO_APPENDIX",
                "table 超过 200 行：先调用 datatalk_export_data 拿到 fileArtifactId，写到 appendixCsvRef，inline rows 截断到前 N 行");
        m.put("REPORT_TABLE_CELLFORMAT_INVALID",
                "table.cellFormats 若存在：长度必须等于 columns 长度，每项 ∈ {text,bar,delta,heat}");
        m.put("REPORT_BLOCK_FIELD_INVALID",
                "富视觉原语字段不合法：callout.variant ∈ {insight,warning,note,success} 且 markdown 必填、"
                        + "stat-highlight.value 必填、comparison.items 长度 2-4、quote.text 必填");
        RECOVERY_HINTS = Map.copyOf(m);
    }

    /**
     * 校验报告 JSON，返回全部违规。空列表代表通过。
     *
     * <p>不抛异常 — 调用方根据需要将非空结果包装为 {@link ReportValidationException}。
     */
    public List<Violation> validate(JsonNode root) {
        List<Violation> violations = new ArrayList<>();
        if (root == null || !root.isObject()) {
            violations.add(new Violation("REPORT_INVALID", "", "report.json must be a JSON object"));
            return violations;
        }
        int schemaVersion = root.path("schemaVersion").asInt(-1);
        if (schemaVersion != SCHEMA_VERSION) {
            violations.add(new Violation("REPORT_SCHEMA_VERSION_UNSUPPORTED", "schemaVersion",
                    "Unsupported schemaVersion: " + root.path("schemaVersion") + " (expected " + SCHEMA_VERSION + ")"));
        }
        String kind = root.path("kind").asText("");
        if (!KIND.equals(kind)) {
            violations.add(new Violation("REPORT_KIND_INVALID", "kind",
                    "kind must equal '" + KIND + "', got '" + kind + "'"));
        }
        JsonNode meta = root.path("meta");
        if (!meta.isObject()) {
            violations.add(new Violation("REPORT_META_MISSING", "meta", "meta object is required"));
        } else {
            if (meta.path("title").asText("").isBlank()) {
                violations.add(new Violation("REPORT_META_MISSING", "meta.title", "meta.title is required"));
            }
            if (meta.path("templateId").asText("").isBlank()) {
                violations.add(new Violation("REPORT_META_MISSING", "meta.templateId", "meta.templateId is required"));
            }
        }

        validateTheme(root.path("theme"), violations);

        JsonNode sections = root.path("sections");
        if (!sections.isArray() || sections.isEmpty()) {
            violations.add(new Violation("REPORT_SECTIONS_MISSING", "sections",
                    "sections array must be non-empty"));
        } else {
            for (int i = 0; i < sections.size(); i++) {
                validateBlock(sections.get(i), "sections[" + i + "]", violations);
            }
        }

        JsonNode appendix = root.path("appendix");
        if (appendix.isArray()) {
            for (int i = 0; i < appendix.size(); i++) {
                validateBlock(appendix.get(i), "appendix[" + i + "]", violations);
            }
        }
        return violations;
    }

    private static final java.util.regex.Pattern HEX6 =
            java.util.regex.Pattern.compile("^#[0-9a-fA-F]{6}$");

    private static boolean isBadHex(String v) {
        return !v.isBlank() && !HEX6.matcher(v).matches();
    }

    /**
     * theme 调色板校验：accent/primary/surface 与 tints[] 每项若出现必须是合法 hex；任意子集合法、缺省不报错。
     * tints 软上限 ≤ 8：超限仅 warn 不 reject。
     */
    private void validateTheme(JsonNode theme, List<Violation> violations) {
        if (!theme.isObject()) return;
        for (String role : new String[]{"accent", "primary", "surface"}) {
            String v = theme.path(role).asText("");
            if (isBadHex(v)) {
                violations.add(new Violation("REPORT_THEME_INVALID", "theme." + role,
                        "theme." + role + " must be a 6-digit hex color"));
            }
        }
        JsonNode tints = theme.path("tints");
        if (tints.isArray()) {
            for (int i = 0; i < tints.size(); i++) {
                String v = tints.get(i).asText("");
                if (isBadHex(v)) {
                    violations.add(new Violation("REPORT_THEME_INVALID", "theme.tints[" + i + "]",
                            "theme.tints[" + i + "] must be a 6-digit hex color"));
                }
            }
            if (tints.size() > TINTS_SOFT_CAP) {
                // 软上限：超限仅 warn 不 reject（CSS 注入只取前 4 个）
                log.warn("theme.tints has {} entries (> soft cap {}); only the first 4 are used",
                        tints.size(), TINTS_SOFT_CAP);
            }
        }
    }

    private void validateBlock(JsonNode block, String path, List<Violation> violations) {
        if (block == null || !block.isObject()) {
            violations.add(new Violation("REPORT_BLOCK_INVALID", path, "block must be a JSON object"));
            return;
        }
        String type = block.path("type").asText("");
        if (type.isBlank()) {
            violations.add(new Violation("REPORT_BLOCK_TYPE_MISSING", path + ".type", "block.type is required"));
            return;
        }
        if (!ALLOWED_BLOCK_TYPES.contains(type)) {
            violations.add(new Violation("REPORT_BLOCK_TYPE_UNKNOWN", path,
                    "Unknown block type: " + type));
            return;
        }
        switch (type) {
            case "table" -> validateTableBlock(block, path, violations);
            case "callout" -> validateCalloutBlock(block, path, violations);
            case "stat-highlight" -> validateStatHighlightBlock(block, path, violations);
            case "comparison" -> validateComparisonBlock(block, path, violations);
            case "quote" -> validateQuoteBlock(block, path, violations);
            case "chapter" -> {
                JsonNode blocks = block.path("blocks");
                if (blocks.isArray()) {
                    for (int i = 0; i < blocks.size(); i++) {
                        validateBlock(blocks.get(i), path + ".blocks[" + i + "]", violations);
                    }
                }
            }
            default -> { /* primitive types (incl. divider): no required structure */ }
        }
    }

    private void validateCalloutBlock(JsonNode block, String path, List<Violation> violations) {
        String variant = block.path("variant").asText("");
        if (!ALLOWED_CALLOUT_VARIANTS.contains(variant)) {
            violations.add(new Violation("REPORT_BLOCK_FIELD_INVALID", path + ".variant",
                    "callout.variant must be one of " + ALLOWED_CALLOUT_VARIANTS + ", got '" + variant + "'"));
        }
        if (block.path("markdown").asText("").isBlank()) {
            violations.add(new Violation("REPORT_BLOCK_FIELD_INVALID", path + ".markdown",
                    "callout.markdown is required"));
        }
    }

    private void validateStatHighlightBlock(JsonNode block, String path, List<Violation> violations) {
        if (block.path("value").asText("").isBlank()) {
            violations.add(new Violation("REPORT_BLOCK_FIELD_INVALID", path + ".value",
                    "stat-highlight.value is required"));
        }
    }

    private void validateComparisonBlock(JsonNode block, String path, List<Violation> violations) {
        JsonNode items = block.path("items");
        int n = items.isArray() ? items.size() : 0;
        if (n < COMPARISON_MIN_ITEMS || n > COMPARISON_MAX_ITEMS) {
            violations.add(new Violation("REPORT_BLOCK_FIELD_INVALID", path + ".items",
                    "comparison.items length must be " + COMPARISON_MIN_ITEMS + "-" + COMPARISON_MAX_ITEMS
                            + ", got " + n));
        }
    }

    private void validateQuoteBlock(JsonNode block, String path, List<Violation> violations) {
        if (block.path("text").asText("").isBlank()) {
            violations.add(new Violation("REPORT_BLOCK_FIELD_INVALID", path + ".text",
                    "quote.text is required"));
        }
    }

    private void validateTableBlock(JsonNode block, String path, List<Violation> violations) {
        JsonNode rows = block.path("rows");
        if (!rows.isArray()) {
            violations.add(new Violation("REPORT_TABLE_ROWS_MISSING", path + ".rows",
                    "table.rows must be an array"));
            return;
        }
        int rowCount = rows.size();
        if (rowCount > MAX_INLINE_TABLE_ROWS) {
            String appendixCsvRef = block.path("appendixCsvRef").asText("");
            if (appendixCsvRef.isBlank()) {
                violations.add(new Violation("REPORT_TABLE_OVERSIZE_NO_APPENDIX", path,
                        "table block has " + rowCount + " rows (> " + MAX_INLINE_TABLE_ROWS
                                + "); appendixCsvRef must be present"));
            }
        }

        // cellFormats 可选：若存在，长度必须 = columns.length，每项 ∈ {text,bar,delta,heat}
        JsonNode cellFormats = block.path("cellFormats");
        if (cellFormats.isArray()) {
            JsonNode columns = block.path("columns");
            int colCount = columns.isArray() ? columns.size() : 0;
            if (cellFormats.size() != colCount) {
                violations.add(new Violation("REPORT_TABLE_CELLFORMAT_INVALID", path + ".cellFormats",
                        "cellFormats length (" + cellFormats.size() + ") must equal columns length ("
                                + colCount + ")"));
            }
            for (int i = 0; i < cellFormats.size(); i++) {
                String fmt = cellFormats.get(i).asText("");
                if (!ALLOWED_CELL_FORMATS.contains(fmt)) {
                    violations.add(new Violation("REPORT_TABLE_CELLFORMAT_INVALID",
                            path + ".cellFormats[" + i + "]",
                            "cellFormats[" + i + "] must be one of " + ALLOWED_CELL_FORMATS
                                    + ", got '" + fmt + "'"));
                }
            }
        }
    }
}
