package com.datatalk.application.importexport;

import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads SQL files containing INSERT statements and extracts rows.
 * Uses {@link SqlStatementSplitter} for statement-level splitting,
 * then regex-matches INSERT INTO ... (cols) VALUES (vals).
 */
class SqlStreamReader {

    private static final Logger log = LoggerFactory.getLogger(SqlStreamReader.class);

    // INSERT INTO [schema.]table (col1, col2, ...) VALUES (v1, v2, ...), (v3, v4, ...)
    // Table name supports schema prefix: mydb.orders, "public"."orders", `db`.`tbl`
    private static final Pattern INSERT_PATTERN = Pattern.compile(
        "(?is)\\bINSERT\\s+INTO\\s+(?<table>[\\w.\"'`]+)\\s*\\((?<cols>[^)]+)\\)\\s*VALUES\\s*(?<values>.+)"
    );

    private static final Pattern DROP_TABLE_PATTERN = Pattern.compile(
        "(?is)^\\s*DROP\\s+TABLE\\s+(IF\\s+EXISTS\\s+)?(?<table>[\\w.\"'`]+)\\s*$"
    );

    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
        "(?is)^\\s*CREATE\\s+TABLE\\s+(IF\\s+NOT\\s+EXISTS\\s+)?(?<table>[\\w.\"'`]+)\\s*\\(.*"
    );

    private static final Pattern UNSUPPORTED_DDL_PATTERN = Pattern.compile(
        "(?is)^\\s*(ALTER\\s|CREATE\\s+(UNIQUE\\s+)?INDEX\\s|TRUNCATE\\s|GRANT\\s|REVOKE\\s)"
    );

    /** Accumulated DDL prefix (DROP TABLE / CREATE TABLE) extracted during the last stream() call. */
    private String ddlPrefix = "";

    record StreamReadResult(int totalRows, List<String> columns, List<Map<String, Object>> sampleRows) {}

    /**
     * Streams rows from INSERT statements in a SQL file.
     *
     * @param file           SQL file path
     * @param batchSize      batch size for consumer callbacks
     * @param batchConsumer  receives batches of rows
     * @param columnMappings optional column name mappings (old -> new)
     * @param columnTypes    optional column type overrides
     * @return summary with total rows, columns, and sample rows
     */
    StreamReadResult stream(Path file, int batchSize, Consumer<List<Map<String, Object>>> batchConsumer,
                            Map<String, String> columnMappings, Map<String, String> columnTypes) {
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> sampleRows = new ArrayList<>();
        List<Map<String, Object>> batch = new ArrayList<>(batchSize);
        int totalRows = 0;
        int warningCount = 0;

        // Reset DDL prefix for each stream call
        ddlPrefix = "";

        SqlStatementSplitter splitter = new SqlStatementSplitter();
        List<String> statements = new ArrayList<>();

        try {
            splitter.split(file, statements::add);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read SQL file: " + e.getMessage(), e);
        }

        List<String> ddlStatements = new ArrayList<>();

        for (String stmt : statements) {
            // Check for supported DDL: DROP TABLE / CREATE TABLE
            Matcher dropMatcher = DROP_TABLE_PATTERN.matcher(stmt);
            if (dropMatcher.matches()) {
                ddlStatements.add(stmt);
                continue;
            }

            Matcher createMatcher = CREATE_TABLE_PATTERN.matcher(stmt);
            if (createMatcher.matches()) {
                ddlStatements.add(stmt);
                continue;
            }

            // Check for unsupported DDL
            if (UNSUPPORTED_DDL_PATTERN.matcher(stmt).find()) {
                throw new DataTalkException(DataTalkErrorCodes.UNSUPPORTED_DDL,
                    "Unsupported DDL statement in SQL import file: " + stmt.substring(0, Math.min(80, stmt.length())),
                    false);
            }

            // Check for INSERT
            Matcher m = INSERT_PATTERN.matcher(stmt);
            if (!m.matches()) {
                log.debug("Skipping non-INSERT statement: {}...", stmt.substring(0, Math.min(80, stmt.length())));
                continue;
            }

            List<String> cols = parseColumnList(m.group("cols"));
            // Apply column mappings
            List<String> mappedCols = new ArrayList<>(cols.size());
            for (String col : cols) {
                String mapped = (columnMappings != null && columnMappings.containsKey(col))
                    ? columnMappings.get(col) : col;
                mappedCols.add(mapped);
            }

            // First INSERT establishes the column set
            if (columns.isEmpty()) {
                columns.addAll(mappedCols);
            }

            // Parse multi-row VALUES: (v1,v2),(v3,v4),...
            String valuesSection = m.group("values").trim();
            List<String> valueTuples = splitValueTuples(valuesSection);

            for (String tuple : valueTuples) {
                List<String> rawValues = parseValueList(tuple);

                // Column count mismatch — skip row
                if (rawValues.size() != columns.size()) {
                    warningCount++;
                    log.warn("Column count mismatch: expected {}, got {}. Skipping row.", columns.size(), rawValues.size());
                    continue;
                }

                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < columns.size(); i++) {
                    String colName = columns.get(i);
                    row.put(colName, parseSqlValue(rawValues.get(i), colName, columnTypes));
                }

                if (totalRows < 3) {
                    sampleRows.add(new LinkedHashMap<>(row));
                }
                batch.add(row);
                totalRows++;

                if (batch.size() >= batchSize) {
                    batchConsumer.accept(new ArrayList<>(batch));
                    batch.clear();
                }
            }
        }

        if (!batch.isEmpty()) {
            batchConsumer.accept(batch);
        }

        // Build ddlPrefix from accumulated DDL statements
        if (!ddlStatements.isEmpty()) {
            ddlPrefix = String.join("; ", ddlStatements);
        }

        if (warningCount > 0) {
            log.warn("SQL stream read completed with {} warnings", warningCount);
        }
        log.info("SQL stream read complete: {} rows, {} columns", totalRows, columns.size());
        return new StreamReadResult(totalRows, columns, sampleRows);
    }

    /**
     * Extracts the set of target table names from all INSERT statements in the file.
     */
    Set<String> extractTargetTables(Path file) {
        Set<String> tables = new java.util.HashSet<>();
        SqlStatementSplitter splitter = new SqlStatementSplitter();
        try {
            splitter.split(file, stmt -> {
                Matcher m = INSERT_PATTERN.matcher(stmt);
                if (m.matches()) {
                    String rawTable = m.group("table");
                    // Strip quotes and extract final segment for schema.table forms
                    String clean = stripQuotes(rawTable.trim());
                    int dotIdx = clean.lastIndexOf('.');
                    String table = (dotIdx >= 0) ? clean.substring(dotIdx + 1) : clean;
                    tables.add(stripQuotes(table));
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract target tables: " + e.getMessage(), e);
        }
        return tables;
    }

    /**
     * Returns the accumulated DDL prefix (DROP TABLE / CREATE TABLE) from the last {@link #stream} call.
     * Statements are joined with "; " separator.
     */
    String getDdlPrefix() {
        return ddlPrefix;
    }

    /**
     * Extracts the table name from the CREATE TABLE statement in the DDL prefix.
     * Returns null if no CREATE TABLE is present.
     */
    String getDdlTargetTable() {
        if (ddlPrefix == null || ddlPrefix.isEmpty()) {
            return null;
        }
        // Search for CREATE TABLE within the concatenated DDL prefix
        Pattern createTableNoAnchor = Pattern.compile(
            "(?is)\\bCREATE\\s+TABLE\\s+(IF\\s+NOT\\s+EXISTS\\s+)?(?<table>[\\w.\"'`]+)\\s*\\("
        );
        Matcher m = createTableNoAnchor.matcher(ddlPrefix);
        if (m.find()) {
            String rawTable = m.group("table");
            String clean = stripQuotes(rawTable.trim());
            int dotIdx = clean.lastIndexOf('.');
            return (dotIdx >= 0) ? clean.substring(dotIdx + 1) : clean;
        }
        return null;
    }

    /**
     * SQL-specific type inference using syntax information from parsing.
     * Unlike CsvStreamReader which infers from string content, this uses
     * the token-level knowledge of whether a value was quoted, numeric, etc.
     */
    List<String> inferDdlTypesFromTokens(List<String> columns, List<Map<String, Object>> sampleRows,
                                          Map<String, String> columnTypes) {
        List<String> ddlTypes = new ArrayList<>();
        for (String col : columns) {
            if (columnTypes != null && columnTypes.containsKey(col)) {
                ddlTypes.add(columnTypes.get(col));
                continue;
            }
            String inferred = "VARCHAR(255)";
            for (Map<String, Object> row : sampleRows) {
                Object val = row.get(col);
                if (val == null) continue;
                if (val instanceof Long) {
                    inferred = "BIGINT";
                    break;
                }
                if (val instanceof Double) {
                    inferred = "DOUBLE";
                    break;
                }
                if (val instanceof Boolean) {
                    inferred = "BOOLEAN";
                    break;
                }
                // String values keep VARCHAR(255) default
            }
            ddlTypes.add(inferred);
        }
        return ddlTypes;
    }

    // ── internal helpers ──────────────────────────────────────────────

    /**
     * Parses comma-separated column identifiers, stripping optional quotes.
     */
    List<String> parseColumnList(String colsStr) {
        List<String> cols = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < colsStr.length(); i++) {
            char c = colsStr.charAt(i);
            if (c == ',') {
                cols.add(buf.toString().trim());
                buf.setLength(0);
            } else {
                buf.append(c);
            }
        }
        if (!buf.isEmpty()) {
            cols.add(buf.toString().trim());
        }
        // Strip surrounding quotes from each column name
        for (int i = 0; i < cols.size(); i++) {
            cols.set(i, stripQuotes(cols.get(i)));
        }
        return cols;
    }

    /**
     * Splits the VALUES section into individual tuples: "(1,2),(3,4)" -> ["1,2", "3,4"]
     */
    List<String> splitValueTuples(String valuesSection) {
        List<String> tuples = new ArrayList<>();
        int depth = 0;
        StringBuilder buf = new StringBuilder();
        boolean inQuote = false;
        char quoteChar = '\0';

        for (int i = 0; i < valuesSection.length(); i++) {
            char c = valuesSection.charAt(i);

            if (inQuote) {
                buf.append(c);
                if (c == quoteChar) {
                    // Check for escaped quote
                    if (i + 1 < valuesSection.length() && valuesSection.charAt(i + 1) == quoteChar) {
                        buf.append(valuesSection.charAt(i + 1));
                        i++;
                    } else {
                        inQuote = false;
                    }
                }
                continue;
            }

            if (c == '\'' || c == '"' || c == '`') {
                inQuote = true;
                quoteChar = c;
                buf.append(c);
            } else if (c == '(') {
                depth++;
                if (depth == 1) {
                    buf.setLength(0); // start new tuple
                } else {
                    buf.append(c);
                }
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    tuples.add(buf.toString().trim());
                    buf.setLength(0);
                } else {
                    buf.append(c);
                }
            } else if (depth > 0) {
                buf.append(c);
            }
        }
        return tuples;
    }

    /**
     * Parses a comma-separated value list inside parentheses, respecting quotes and nesting.
     */
    List<String> parseValueList(String valuesStr) {
        List<String> values = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        boolean inQuote = false;
        char quoteChar = '\0';
        int parenDepth = 0;

        for (int i = 0; i < valuesStr.length(); i++) {
            char c = valuesStr.charAt(i);

            if (inQuote) {
                buf.append(c);
                if (c == quoteChar) {
                    if (i + 1 < valuesStr.length() && valuesStr.charAt(i + 1) == quoteChar) {
                        buf.append(valuesStr.charAt(i + 1));
                        i++;
                    } else {
                        inQuote = false;
                    }
                }
                continue;
            }

            if (c == '\'' || c == '"' || c == '`') {
                inQuote = true;
                quoteChar = c;
                buf.append(c);
            } else if (c == '(') {
                parenDepth++;
                buf.append(c);
            } else if (c == ')') {
                parenDepth--;
                buf.append(c);
            } else if (c == ',' && parenDepth == 0) {
                values.add(buf.toString().trim());
                buf.setLength(0);
            } else {
                buf.append(c);
            }
        }
        if (!buf.isEmpty()) {
            values.add(buf.toString().trim());
        }
        return values;
    }

    /**
     * Parses a single SQL value token into a Java object.
     */
    Object parseSqlValue(String raw, String columnName, Map<String, String> columnTypes) {
        // If column type is explicitly overridden, keep as string
        if (columnTypes != null && columnTypes.containsKey(columnName)) {
            return raw.isEmpty() ? null : raw;
        }

        if (raw.isEmpty()) {
            return null;
        }

        // NULL keyword
        if (raw.equalsIgnoreCase("NULL")) {
            return null;
        }

        // Boolean
        if (raw.equalsIgnoreCase("TRUE")) {
            return true;
        }
        if (raw.equalsIgnoreCase("FALSE")) {
            return false;
        }

        // Quoted string
        if ((raw.startsWith("'") && raw.endsWith("'"))
            || (raw.startsWith("\"") && raw.endsWith("\""))
            || (raw.startsWith("`") && raw.endsWith("`"))) {
            String inner = raw.substring(1, raw.length() - 1);
            // Handle escaped quotes
            if (raw.startsWith("'")) {
                inner = inner.replace("''", "'");
            } else if (raw.startsWith("\"")) {
                inner = inner.replace("\\\"", "\"");
                inner = inner.replace("\"\"", "\"");
            }
            return inner;
        }

        // Unquoted number
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
        }

        // Fallback: return as string
        return raw;
    }

    private String stripQuotes(String s) {
        if (s == null || s.length() < 2) return s;
        char first = s.charAt(0);
        char last = s.charAt(s.length() - 1);
        if ((first == '\'' || first == '"' || first == '`') && first == last) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
