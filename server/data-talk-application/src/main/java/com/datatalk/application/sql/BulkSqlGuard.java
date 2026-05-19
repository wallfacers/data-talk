package com.datatalk.application.sql;

import com.datatalk.domain.action.CallerKind;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hard contract guard for {@code datatalk_execute_sql} when the caller is the AI.
 * Rejects bulk write SQL that should have been routed to {@code datatalk_import_data}
 * — see BUG-0070 and openspec/changes/execute-sql-bulk-redirect-guard.
 */
@Component
public class BulkSqlGuard {

    public static final int SIZE_THRESHOLD_BYTES = 4096;
    public static final int INSERT_COUNT_THRESHOLD = 20;

    public static final String REASON_SIZE = "size_threshold";
    public static final String REASON_INSERT_COUNT = "insert_count_threshold";
    public static final String REASON_FILE_ORIGIN = "originated_from_file";

    private static final Set<String> WRITE_KEYWORDS = Set.of(
        "INSERT", "UPDATE", "DELETE", "REPLACE", "MERGE", "UPSERT",
        "CREATE", "DROP", "ALTER", "TRUNCATE", "RENAME", "COMMENT",
        "GRANT", "REVOKE"
    );

    private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern FIRST_KEYWORD = Pattern.compile("^\\s*(\\w+)");
    private static final Pattern INSERT_TARGET = Pattern.compile(
        "INSERT\\s+(?:IGNORE\\s+|OR\\s+(?:REPLACE|IGNORE|ABORT|FAIL|ROLLBACK)\\s+)?INTO\\s+([\\w.`\"\\[\\]]+)",
        Pattern.CASE_INSENSITIVE
    );

    private final SqlStatementSplitters splitters;

    public BulkSqlGuard(SqlStatementSplitters splitters) {
        this.splitters = splitters;
    }

    public BulkSqlVerdict evaluate(String sql,
                                   CallerKind callerKind,
                                   String sourceFileId,
                                   String connectionId,
                                   String connectionKind) {
        if (callerKind != CallerKind.AI) {
            return BulkSqlVerdict.pass();
        }
        if (sql == null || sql.isBlank()) {
            return BulkSqlVerdict.pass();
        }

        List<String> statements;
        try {
            statements = splitters.split(connectionKind, sql);
        } catch (RuntimeException ex) {
            statements = List.of(sql);
        }
        if (statements == null || statements.isEmpty()) {
            statements = List.of(sql);
        }

        List<String> stripped = statements.stream()
            .map(this::stripComments)
            .filter(s -> !s.isBlank())
            .toList();
        if (stripped.isEmpty()) {
            return BulkSqlVerdict.pass();
        }

        boolean hasWrite = stripped.stream().anyMatch(this::isWriteStatement);
        if (!hasWrite) {
            return BulkSqlVerdict.pass();
        }

        long insertCount = stripped.stream().filter(this::isInsertStatement).count();
        int byteSize = sql.getBytes(StandardCharsets.UTF_8).length;
        boolean hasFileOrigin = sourceFileId != null && !sourceFileId.isBlank();

        String tableName = resolveSingleTargetTable(stripped);

        if (hasFileOrigin) {
            return BulkSqlVerdict.reject(
                REASON_FILE_ORIGIN,
                "SQL originated from file (sourceFileId=" + sourceFileId
                    + "). Use datatalk_import_data instead.",
                buildNextActionParams(connectionId, tableName, sourceFileId)
            );
        }
        if (byteSize > SIZE_THRESHOLD_BYTES) {
            return BulkSqlVerdict.reject(
                REASON_SIZE,
                "SQL size " + byteSize + " bytes exceeds limit " + SIZE_THRESHOLD_BYTES
                    + ". Use datatalk_import_data instead.",
                buildNextActionParams(connectionId, tableName, sourceFileId)
            );
        }
        if (insertCount > INSERT_COUNT_THRESHOLD) {
            String suffix = tableName == null
                ? " Multi-table INSERT detected; split per table and call import_data for each."
                : "";
            return BulkSqlVerdict.reject(
                REASON_INSERT_COUNT,
                "SQL contains " + insertCount + " INSERT statements exceeding limit "
                    + INSERT_COUNT_THRESHOLD + ". Use datatalk_import_data instead." + suffix,
                buildNextActionParams(connectionId, tableName, sourceFileId)
            );
        }

        return BulkSqlVerdict.pass();
    }

    /**
     * Parse {@code INSERT INTO X} target table from a raw SQL string.
     * Returns the single table name if all INSERTs target the same table; {@code null}
     * if multiple distinct tables are involved or no INSERT is present.
     */
    public String parseTargetTable(String sql) {
        if (sql == null || sql.isBlank()) return null;
        List<String> statements;
        try {
            statements = splitters.split(null, sql);
        } catch (RuntimeException ex) {
            statements = List.of(sql);
        }
        if (statements == null || statements.isEmpty()) {
            statements = List.of(sql);
        }
        List<String> stripped = statements.stream()
            .map(this::stripComments)
            .filter(s -> !s.isBlank())
            .toList();
        return resolveSingleTargetTable(stripped);
    }

    private String resolveSingleTargetTable(List<String> strippedStatements) {
        Set<String> tables = new LinkedHashSet<>();
        for (String statement : strippedStatements) {
            Matcher m = INSERT_TARGET.matcher(statement);
            if (m.find()) {
                tables.add(unquoteIdentifier(m.group(1)));
            }
        }
        return tables.size() == 1 ? tables.iterator().next() : null;
    }

    private String unquoteIdentifier(String identifier) {
        if (identifier == null) return null;
        return identifier.replace("`", "")
            .replace("\"", "")
            .replace("[", "")
            .replace("]", "");
    }

    private boolean isInsertStatement(String strippedStatement) {
        String head = firstKeyword(strippedStatement);
        return "INSERT".equals(head) || "REPLACE".equals(head);
    }

    private boolean isWriteStatement(String strippedStatement) {
        String head = firstKeyword(strippedStatement);
        return WRITE_KEYWORDS.contains(head);
    }

    private String firstKeyword(String strippedStatement) {
        Matcher m = FIRST_KEYWORD.matcher(strippedStatement);
        if (!m.find()) return "";
        return m.group(1).toUpperCase(Locale.ROOT);
    }

    private String stripComments(String sql) {
        if (sql == null) return "";
        return BLOCK_COMMENT.matcher(LINE_COMMENT.matcher(sql).replaceAll(""))
            .replaceAll("")
            .trim();
    }

    private Map<String, Object> buildNextActionParams(String connectionId,
                                                      String tableName,
                                                      String sourceFileId) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "file");
        source.put("fileId", sourceFileId);

        Map<String, Object> target = new LinkedHashMap<>();
        target.put("connectionId", connectionId);
        target.put("tableName", tableName);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("source", source);
        params.put("target", target);
        return params;
    }
}
