package com.datatalk.application.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class SqlExecutionPlanner {

    public sealed interface ExecutionUnit permits SingleStatement, DmlBatch {}

    public record SingleStatement(int statementIndex, String statementText) implements ExecutionUnit {}

    public record DmlBatch(
        int startIndex,
        int endIndex,
        List<String> statementTexts,
        Optional<String> rewrittenStatement
    ) implements ExecutionUnit {}

    public List<ExecutionUnit> plan(List<String> statements) {
        List<ExecutionUnit> units = new ArrayList<>();
        int index = 0;
        while (index < statements.size()) {
            List<InsertValuesStatement> insertGroup = collectInsertValuesGroup(statements, index);
            if (insertGroup.size() > 1) {
                units.add(toRewrittenInsertBatch(index, insertGroup));
                index += insertGroup.size();
                continue;
            }

            if (isDml(statements.get(index))) {
                int endExclusive = collectDmlRun(statements, index);
                if (endExclusive - index > 1) {
                    units.add(toJdbcBatch(index, statements.subList(index, endExclusive)));
                } else {
                    units.add(new SingleStatement(index + 1, statements.get(index)));
                }
                index = endExclusive;
                continue;
            }

            units.add(new SingleStatement(index + 1, statements.get(index)));
            index++;
        }
        return units;
    }

    private int collectDmlRun(List<String> statements, int start) {
        int index = start;
        while (index < statements.size() && isDml(statements.get(index))) {
            if (index > start && collectInsertValuesGroup(statements, index).size() > 1) {
                break;
            }
            index++;
        }
        return index;
    }

    private List<InsertValuesStatement> collectInsertValuesGroup(List<String> statements, int start) {
        InsertValuesStatement first = parseInsertValues(statements.get(start));
        if (first == null) {
            return List.of();
        }

        List<InsertValuesStatement> group = new ArrayList<>();
        group.add(first);
        for (int index = start + 1; index < statements.size(); index++) {
            InsertValuesStatement current = parseInsertValues(statements.get(index));
            if (current == null || !current.normalizedPrefix().equals(first.normalizedPrefix())) {
                break;
            }
            group.add(current);
        }
        return group;
    }

    private DmlBatch toRewrittenInsertBatch(int startIndex, List<InsertValuesStatement> inserts) {
        List<String> originalStatements = inserts.stream()
            .map(InsertValuesStatement::originalStatement)
            .toList();
        String rewritten = inserts.get(0).prefix() + " VALUES " + String.join(", ", inserts.stream()
            .map(InsertValuesStatement::valuesList)
            .toList());
        return new DmlBatch(
            startIndex + 1,
            startIndex + inserts.size(),
            originalStatements,
            Optional.of(rewritten)
        );
    }

    private DmlBatch toJdbcBatch(int startIndex, List<String> statements) {
        return new DmlBatch(
            startIndex + 1,
            startIndex + statements.size(),
            List.copyOf(statements),
            Optional.empty()
        );
    }

    private boolean isDml(String statement) {
        String keyword = firstKeyword(statement);
        return "INSERT".equals(keyword) || "UPDATE".equals(keyword) || "DELETE".equals(keyword);
    }

    private InsertValuesStatement parseInsertValues(String statement) {
        String keyword = firstKeyword(statement);
        if (!"INSERT".equals(keyword)) {
            return null;
        }

        int valuesStart = findKeywordOutsideQuotedText(statement, "VALUES");
        if (valuesStart < 0) {
            return null;
        }

        int valuesEnd = valuesStart + "VALUES".length();
        String prefix = statement.substring(0, valuesStart).trim();
        String valuesList = statement.substring(valuesEnd).trim();
        if (prefix.isEmpty() || valuesList.isEmpty() || !isPureValuesList(valuesList)) {
            return null;
        }

        return new InsertValuesStatement(
            statement,
            prefix,
            normalizeSqlPrefix(prefix),
            valuesList
        );
    }

    private static String firstKeyword(String statement) {
        int index = skipIgnorablePrefix(statement, 0);
        StringBuilder keyword = new StringBuilder();
        while (index < statement.length()) {
            char ch = statement.charAt(index);
            if (!Character.isLetter(ch)) {
                break;
            }
            keyword.append(Character.toUpperCase(ch));
            index++;
        }
        return keyword.toString();
    }

    private static int findKeywordOutsideQuotedText(String statement, String keyword) {
        ScanState state = new ScanState();
        for (int index = 0; index < statement.length(); index++) {
            char ch = statement.charAt(index);
            char next = index + 1 < statement.length() ? statement.charAt(index + 1) : '\0';
            int consumed = state.consume(ch, next);
            if (consumed > 0) {
                index += consumed;
                continue;
            }
            if (state.clear() && isKeywordAt(statement, index, keyword)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isPureValuesList(String valuesList) {
        ScanState state = new ScanState();
        int depth = 0;
        boolean sawTuple = false;
        boolean expectingTuple = true;

        for (int index = 0; index < valuesList.length(); index++) {
            char ch = valuesList.charAt(index);
            char next = index + 1 < valuesList.length() ? valuesList.charAt(index + 1) : '\0';
            int consumed = state.consume(ch, next);
            if (consumed > 0) {
                index += consumed;
                continue;
            }
            if (!state.clear()) {
                continue;
            }

            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (ch == '(') {
                if (depth == 0) {
                    if (!expectingTuple) {
                        return false;
                    }
                    sawTuple = true;
                    expectingTuple = false;
                }
                depth++;
                continue;
            }
            if (ch == ')') {
                if (depth <= 0) {
                    return false;
                }
                depth--;
                continue;
            }
            if (depth == 0) {
                if (ch == ',') {
                    if (!sawTuple || expectingTuple) {
                        return false;
                    }
                    expectingTuple = true;
                    continue;
                }
                return false;
            }
        }

        return sawTuple && depth == 0 && !expectingTuple && state.clear();
    }

    private static boolean isKeywordAt(String statement, int index, String keyword) {
        if (index > 0 && isIdentifierPart(statement.charAt(index - 1))) {
            return false;
        }
        if (!statement.regionMatches(true, index, keyword, 0, keyword.length())) {
            return false;
        }
        int after = index + keyword.length();
        return after >= statement.length() || !isIdentifierPart(statement.charAt(after));
    }

    private static boolean isIdentifierPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '$';
    }

    private static int skipIgnorablePrefix(String statement, int start) {
        int index = start;
        while (index < statement.length()) {
            while (index < statement.length() && Character.isWhitespace(statement.charAt(index))) {
                index++;
            }
            if (index + 1 < statement.length() && statement.charAt(index) == '-' && statement.charAt(index + 1) == '-') {
                index += 2;
                while (index < statement.length() && statement.charAt(index) != '\n' && statement.charAt(index) != '\r') {
                    index++;
                }
                continue;
            }
            if (index < statement.length() && statement.charAt(index) == '#') {
                index++;
                while (index < statement.length() && statement.charAt(index) != '\n' && statement.charAt(index) != '\r') {
                    index++;
                }
                continue;
            }
            if (index + 1 < statement.length() && statement.charAt(index) == '/' && statement.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < statement.length() && !(statement.charAt(index) == '*' && statement.charAt(index + 1) == '/')) {
                    index++;
                }
                index = Math.min(statement.length(), index + 2);
                continue;
            }
            return index;
        }
        return index;
    }

    private static String normalizeSqlPrefix(String prefix) {
        return prefix.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private record InsertValuesStatement(
        String originalStatement,
        String prefix,
        String normalizedPrefix,
        String valuesList
    ) {}

    private static final class ScanState {
        private boolean inSingleQuote;
        private boolean inDoubleQuote;
        private boolean inBacktick;
        private boolean inLineComment;
        private boolean inBlockComment;

        int consume(char ch, char next) {
            if (inLineComment) {
                if (ch == '\n' || ch == '\r') {
                    inLineComment = false;
                }
                return 0;
            }
            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false;
                    return 1;
                }
                return 0;
            }
            if (inSingleQuote) {
                if (ch == '\\' && next != '\0') {
                    return 1;
                }
                if (ch == '\'' && next == '\'') {
                    return 1;
                }
                if (ch == '\'') {
                    inSingleQuote = false;
                }
                return 0;
            }
            if (inDoubleQuote) {
                if (ch == '\\' && next != '\0') {
                    return 1;
                }
                if (ch == '"' && next == '"') {
                    return 1;
                }
                if (ch == '"') {
                    inDoubleQuote = false;
                }
                return 0;
            }
            if (inBacktick) {
                if (ch == '`' && next == '`') {
                    return 1;
                }
                if (ch == '`') {
                    inBacktick = false;
                }
                return 0;
            }

            if (ch == '-' && next == '-') {
                inLineComment = true;
                return 1;
            }
            if (ch == '#') {
                inLineComment = true;
                return 0;
            }
            if (ch == '/' && next == '*') {
                inBlockComment = true;
                return 1;
            }
            if (ch == '\'') {
                inSingleQuote = true;
                return 0;
            }
            if (ch == '"') {
                inDoubleQuote = true;
                return 0;
            }
            if (ch == '`') {
                inBacktick = true;
                return 0;
            }
            return 0;
        }

        boolean clear() {
            return !inSingleQuote && !inDoubleQuote && !inBacktick && !inLineComment && !inBlockComment;
        }
    }
}
