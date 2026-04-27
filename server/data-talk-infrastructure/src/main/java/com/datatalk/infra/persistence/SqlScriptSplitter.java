package com.datatalk.infra.persistence;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits SQLite migration scripts into executable statements.
 * SQLite trigger bodies contain semicolon-terminated inner statements, so a
 * plain String.split(";") corrupts CREATE TRIGGER statements.
 */
public final class SqlScriptSplitter {

    private SqlScriptSplitter() {
    }

    public static List<String> split(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inTrigger = false;

        for (String line : sql.split("\\R", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() && current.isEmpty()) {
                continue;
            }

            if (!inTrigger && startsCreateTrigger(trimmed)) {
                inTrigger = true;
            }

            current.append(line).append('\n');

            if (inTrigger) {
                if (trimmed.equalsIgnoreCase("END;")) {
                    addStatement(statements, current);
                    inTrigger = false;
                }
                continue;
            }

            if (trimmed.endsWith(";")) {
                addStatement(statements, current);
            }
        }

        addStatement(statements, current);
        return statements;
    }

    private static boolean startsCreateTrigger(String trimmed) {
        String upper = trimmed.toUpperCase();
        return upper.startsWith("CREATE TRIGGER ") || upper.startsWith("CREATE TRIGGER IF NOT EXISTS ");
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        if (!statement.isEmpty()) {
            statements.add(stripTrailingSemicolon(statement));
        }
        current.setLength(0);
    }

    private static String stripTrailingSemicolon(String statement) {
        return statement.endsWith(";")
            ? statement.substring(0, statement.length() - 1).trim()
            : statement;
    }
}
