package com.datatalk.application.report;

import com.fasterxml.jackson.databind.JsonNode;
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
        m.put("REPORT_THEME_INVALID", "theme.accent 必须是 6 位十六进制颜色（如 #1f4e79）");
        m.put("REPORT_SECTIONS_MISSING", "顶层用非空的 sections 数组而不是 blocks");
        m.put("REPORT_BLOCK_INVALID", "block 必须是 JSON object");
        m.put("REPORT_BLOCK_TYPE_MISSING", "每个 block 必须显式声明 type 字段");
        m.put("REPORT_BLOCK_TYPE_UNKNOWN", "type 必须是 ALLOWED_BLOCK_TYPES 集合内的值（见 section-patterns.md）");
        m.put("REPORT_TABLE_ROWS_MISSING", "table block 必须有 rows 数组");
        m.put("REPORT_TABLE_OVERSIZE_NO_APPENDIX",
                "table 超过 200 行：先调用 datatalk_export_data 拿到 fileArtifactId，写到 appendixCsvRef，inline rows 截断到前 N 行");
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

        JsonNode theme = root.path("theme");
        if (theme.isObject()) {
            String accent = theme.path("accent").asText("");
            if (!accent.isBlank() && !accent.matches("^#[0-9a-fA-F]{6}$")) {
                violations.add(new Violation("REPORT_THEME_INVALID", "theme.accent",
                        "theme.accent must be a 6-digit hex color"));
            }
        }

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
            case "chapter" -> {
                JsonNode blocks = block.path("blocks");
                if (blocks.isArray()) {
                    for (int i = 0; i < blocks.size(); i++) {
                        validateBlock(blocks.get(i), path + ".blocks[" + i + "]", violations);
                    }
                }
            }
            default -> { /* primitive types: no recursive structure */ }
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
    }
}
