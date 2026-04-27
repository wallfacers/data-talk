package com.datatalk.infra.diagnostics;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SqlColumnExtractor {

    private static final Set<String> SQL_KEYWORDS = Set.of(
        "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "IS", "NULL",
        "LIKE", "BETWEEN", "EXISTS", "JOIN", "ON", "LEFT", "RIGHT", "INNER",
        "OUTER", "CROSS", "FULL", "GROUP", "ORDER", "BY", "HAVING", "LIMIT",
        "AS", "DESC", "ASC", "DISTINCT", "ALL", "ANY", "SOME", "TRUE", "FALSE",
        "CASE", "WHEN", "THEN", "ELSE", "END", "UNION", "SET", "INTO", "WITH",
        "OFFSET", "FETCH", "NEXT", "ROWS", "ONLY", "CAST", "COUNT", "SUM",
        "AVG", "MIN", "MAX", "COALESCE", "IF", "USING", "NATURAL", "WINDOW",
        "OVER", "PARTITION", "RECURSIVE", "TABLE", "INDEX", "VIEW", "SCHEMA",
        "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CONSTRAINT", "DEFAULT",
        "CHECK", "UNIQUE", "VALUES", "INSERT", "UPDATE", "DELETE", "CREATE",
        "ALTER", "DROP", "GRANT", "REVOKE", "BEGIN", "COMMIT", "ROLLBACK",
        "TRANSACTION", "TOP", "APPLY", "LATERAL"
    );

    static List<String> extract(String sql, String tableName) {
        if (sql == null || sql.isBlank() || tableName == null || tableName.isBlank()) {
            return List.of();
        }

        String shortTable = tableName.contains(".")
            ? tableName.substring(tableName.lastIndexOf(".") + 1)
            : tableName;

        Set<String> columns = new LinkedHashSet<>();

        String alias = findAlias(sql, shortTable);

        // Qualified column references: table.col, alias.col, or schema.table.col
        String fullPat = Pattern.quote(tableName);
        String shortPat = Pattern.quote(shortTable);
        String aliasAlt = alias != null ? "|" + Pattern.quote(alias) : "";
        Pattern qualified = Pattern.compile(
            "`?(?:" + fullPat + "|" + shortPat + aliasAlt + ")`?\\.`?(\\w+)`?",
            Pattern.CASE_INSENSITIVE
        );
        Matcher qm = qualified.matcher(sql);
        while (qm.find()) {
            addIfColumn(columns, qm.group(1));
        }

        if (!columns.isEmpty()) return List.copyOf(columns);

        // Fallback: unqualified columns from WHERE clause
        String whereClause = extractWhereClause(sql);
        if (whereClause != null) {
            Pattern colRef = Pattern.compile(
                "\\b(\\w+)\\s*(?:=|!=|<>|>=?|<=?|\\s+LIKE\\b|\\s+IN\\s*\\(|\\s+BETWEEN\\b|\\s+IS\\b)",
                Pattern.CASE_INSENSITIVE
            );
            Matcher cm = colRef.matcher(whereClause);
            while (cm.find()) {
                addIfColumn(columns, cm.group(1));
            }
        }

        return List.copyOf(columns);
    }

    private static String findAlias(String sql, String tableName) {
        Pattern p = Pattern.compile(
            "\\bFROM\\s+" + Pattern.quote(tableName) + "\\s+(?:AS\\s+)?(\\w+)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(sql);
        if (m.find()) {
            String candidate = m.group(1);
            if (!SQL_KEYWORDS.contains(candidate.toUpperCase()) && isValidIdentifier(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String extractWhereClause(String sql) {
        int start = keywordIndex(sql, "WHERE");
        if (start < 0) return null;
        start += 5;

        int end = sql.length();
        for (String kw : List.of("GROUP BY", "ORDER BY", "HAVING", "LIMIT")) {
            int idx = keywordIndex(sql.substring(start), kw);
            if (idx >= 0) {
                end = Math.min(end, start + idx);
            }
        }
        return sql.substring(start, end);
    }

    private static int keywordIndex(String sql, String keyword) {
        Pattern p = Pattern.compile("\\b" + keyword.replace(" ", "\\s+") + "\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        return m.find() ? m.start() : -1;
    }

    private static void addIfColumn(Set<String> columns, String name) {
        if (isValidIdentifier(name) && !SQL_KEYWORDS.contains(name.toUpperCase())) {
            columns.add(name);
        }
    }

    private static boolean isValidIdentifier(String name) {
        return !name.isEmpty() && Character.isLetter(name.charAt(0));
    }
}
