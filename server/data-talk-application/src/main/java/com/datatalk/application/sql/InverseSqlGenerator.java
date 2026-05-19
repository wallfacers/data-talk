package com.datatalk.application.sql;

import com.datatalk.application.dialect.IdentifierQuoter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class InverseSqlGenerator {

    public static String generate(
        String operation,
        String tableName,
        Set<String> pkColumns,
        List<Map<String, Object>> beforeState,
        List<Map<String, Object>> generatedKeys,
        String connectionKind
    ) {
        return switch (operation) {
            case "INSERT" -> generateInsertInverse(tableName, pkColumns, generatedKeys, connectionKind);
            case "UPDATE" -> generateUpdateInverse(tableName, pkColumns, beforeState, connectionKind);
            case "DELETE" -> generateDeleteInverse(tableName, beforeState, connectionKind);
            default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
        };
    }

    private static String generateInsertInverse(String tableName, Set<String> pkColumns,
                                                 List<Map<String, Object>> generatedKeys,
                                                 String connectionKind) {
        if (generatedKeys == null || generatedKeys.isEmpty()) {
            throw new IllegalStateException("INSERT inverse requires generated keys");
        }
        List<String> pkValues = generatedKeys.stream()
            .map(row -> formatValue(row.values().iterator().next()))
            .toList();
        String pkCol = pkColumns.isEmpty() ? "id" : pkColumns.iterator().next();
        String quotedTable = IdentifierQuoter.quote(tableName, connectionKind);
        String quotedPk = IdentifierQuoter.quote(pkCol, connectionKind);
        if (pkValues.size() == 1) {
            return "DELETE FROM " + quotedTable + " WHERE " + quotedPk + " = " + pkValues.get(0);
        }
        return "DELETE FROM " + quotedTable + " WHERE " + quotedPk
            + " IN (" + String.join(", ", pkValues) + ")";
    }

    private static String generateUpdateInverse(String tableName, Set<String> pkColumns,
                                                 List<Map<String, Object>> beforeState,
                                                 String connectionKind) {
        if (beforeState == null || beforeState.isEmpty()) {
            throw new IllegalStateException("UPDATE inverse requires before-state");
        }
        String quotedTable = IdentifierQuoter.quote(tableName, connectionKind);
        List<String> statements = new ArrayList<>();
        for (Map<String, Object> row : beforeState) {
            List<String> setClauses = new ArrayList<>();
            List<String> pkConditions = new ArrayList<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String col = entry.getKey();
                String quotedCol = IdentifierQuoter.quote(col, connectionKind);
                if (pkColumns.contains(col)) {
                    pkConditions.add(quotedCol + " = " + formatValue(entry.getValue()));
                } else {
                    setClauses.add(quotedCol + " = " + formatValue(entry.getValue()));
                }
            }
            if (setClauses.isEmpty()) {
                continue;
            }
            statements.add("UPDATE " + quotedTable + " SET " + String.join(", ", setClauses)
                + " WHERE " + String.join(" AND ", pkConditions));
        }
        return String.join(";\n", statements);
    }

    private static String generateDeleteInverse(String tableName, List<Map<String, Object>> beforeState,
                                                 String connectionKind) {
        if (beforeState == null || beforeState.isEmpty()) {
            throw new IllegalStateException("DELETE inverse requires before-state");
        }
        List<String> columns = beforeState.get(0).keySet().stream().sorted().toList();
        List<String> valueRows = beforeState.stream()
            .map(row -> columns.stream()
                .map(col -> formatValue(row.get(col)))
                .collect(Collectors.joining(", ", "(", ")")))
            .toList();
        String quotedTable = IdentifierQuoter.quote(tableName, connectionKind);
        String quotedColumns = columns.stream()
            .map(c -> IdentifierQuoter.quote(c, connectionKind))
            .collect(Collectors.joining(", "));
        return "INSERT INTO " + quotedTable + " (" + quotedColumns + ") VALUES "
            + String.join(", ", valueRows);
    }

    static String formatValue(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean b) return b ? "1" : "0";
        String s = value.toString();
        return "'" + s.replace("'", "''") + "'";
    }
}
