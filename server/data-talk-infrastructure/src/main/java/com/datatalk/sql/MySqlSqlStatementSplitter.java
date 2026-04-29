package com.datatalk.sql;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class MySqlSqlStatementSplitter {

    private static final String DEFAULT_DELIMITER = ";";
    private static final String DELIMITER_DIRECTIVE = "DELIMITER";

    public List<String> split(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String delimiter = DEFAULT_DELIMITER;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBacktick = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            if (!inSingleQuote
                && !inDoubleQuote
                && !inBacktick
                && !inLineComment
                && !inBlockComment
                && isLineStart(sql, i)) {
                int lineEnd = findLineEnd(sql, i);
                String nextDelimiter = parseDelimiterDirective(sql.substring(i, lineEnd));
                if (nextDelimiter != null) {
                    delimiter = nextDelimiter;
                    i = skipLineBreak(sql, lineEnd) - 1;
                    continue;
                }
            }

            char ch = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                current.append(ch);
                if (ch == '\n' || ch == '\r') {
                    inLineComment = false;
                }
                continue;
            }

            if (inBlockComment) {
                current.append(ch);
                if (ch == '*' && next == '/') {
                    current.append(next);
                    i++;
                    inBlockComment = false;
                }
                continue;
            }

            if (inSingleQuote) {
                current.append(ch);
                if (ch == '\\' && next != '\0') {
                    current.append(next);
                    i++;
                    continue;
                }
                if (ch == '\'' && next == '\'') {
                    current.append(next);
                    i++;
                    continue;
                }
                if (ch == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }

            if (inDoubleQuote) {
                current.append(ch);
                if (ch == '\\' && next != '\0') {
                    current.append(next);
                    i++;
                    continue;
                }
                if (ch == '"' && next == '"') {
                    current.append(next);
                    i++;
                    continue;
                }
                if (ch == '"') {
                    inDoubleQuote = false;
                }
                continue;
            }

            if (inBacktick) {
                current.append(ch);
                if (ch == '`' && next == '`') {
                    current.append(next);
                    i++;
                    continue;
                }
                if (ch == '`') {
                    inBacktick = false;
                }
                continue;
            }

            if (startsWithDelimiter(sql, i, delimiter)) {
                pushStatement(statements, current);
                current.setLength(0);
                i += delimiter.length() - 1;
                continue;
            }

            if (ch == '-' && next == '-') {
                current.append(ch).append(next);
                i++;
                inLineComment = true;
                continue;
            }
            if (ch == '#') {
                current.append(ch);
                inLineComment = true;
                continue;
            }
            if (ch == '/' && next == '*') {
                current.append(ch).append(next);
                i++;
                inBlockComment = true;
                continue;
            }
            if (ch == '\'') {
                current.append(ch);
                inSingleQuote = true;
                continue;
            }
            if (ch == '"') {
                current.append(ch);
                inDoubleQuote = true;
                continue;
            }
            if (ch == '`') {
                current.append(ch);
                inBacktick = true;
                continue;
            }

            current.append(ch);
        }

        pushStatement(statements, current);
        return statements;
    }

    private static boolean isLineStart(String sql, int index) {
        if (index == 0) return true;
        char previous = sql.charAt(index - 1);
        return previous == '\n' || previous == '\r';
    }

    private static int findLineEnd(String sql, int index) {
        int i = index;
        while (i < sql.length()) {
            char ch = sql.charAt(i);
            if (ch == '\n' || ch == '\r') {
                return i;
            }
            i++;
        }
        return i;
    }

    private static int skipLineBreak(String sql, int lineEnd) {
        if (lineEnd >= sql.length()) return lineEnd;
        if (sql.charAt(lineEnd) == '\r' && lineEnd + 1 < sql.length() && sql.charAt(lineEnd + 1) == '\n') {
            return lineEnd + 2;
        }
        return lineEnd + 1;
    }

    private static String parseDelimiterDirective(String line) {
        String trimmed = line.trim();
        if (trimmed.length() < DELIMITER_DIRECTIVE.length()) {
            return null;
        }
        String prefix = trimmed.substring(0, DELIMITER_DIRECTIVE.length()).toUpperCase(Locale.ROOT);
        if (!DELIMITER_DIRECTIVE.equals(prefix)) {
            return null;
        }
        if (trimmed.length() > DELIMITER_DIRECTIVE.length()) {
            char boundary = trimmed.charAt(DELIMITER_DIRECTIVE.length());
            if (Character.isLetterOrDigit(boundary) || boundary == '_') {
                return null;
            }
        }

        String nextDelimiter = trimmed.substring(DELIMITER_DIRECTIVE.length()).trim();
        return nextDelimiter.isEmpty() ? DEFAULT_DELIMITER : nextDelimiter;
    }

    private static boolean startsWithDelimiter(String sql, int index, String delimiter) {
        return !delimiter.isEmpty() && sql.startsWith(delimiter, index);
    }

    private static void pushStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
    }
}
