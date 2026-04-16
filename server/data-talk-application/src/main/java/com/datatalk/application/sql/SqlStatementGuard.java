package com.datatalk.application.sql;

import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lightweight MVP guard. Accepts only statements whose first keyword
 * (after stripping comments/whitespace) is SELECT or WITH, and rejects
 * any input containing a second statement separator.
 */
@Component
public class SqlStatementGuard {

    private static final Set<String> ALLOWED_FIRST_KEYWORDS = Set.of("SELECT", "WITH");
    private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern FIRST_KEYWORD = Pattern.compile("^\\s*(\\w+)");

    public void assertSelectOnly(String sql) {
        String cleaned = BLOCK_COMMENT.matcher(LINE_COMMENT.matcher(sql).replaceAll(""))
            .replaceAll("").trim();
        if (cleaned.isEmpty()) throw forbidden("empty SQL");

        if (cleaned.endsWith(";")) cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        if (cleaned.contains(";")) {
            throw forbidden("multiple statements not allowed");
        }

        var matcher = FIRST_KEYWORD.matcher(cleaned);
        if (!matcher.find()) throw forbidden("cannot determine statement type");
        String head = matcher.group(1).toUpperCase();
        if (!ALLOWED_FIRST_KEYWORDS.contains(head)) {
            throw forbidden("only SELECT / WITH allowed, got: " + head);
        }
    }

    private DataTalkException forbidden(String msg) {
        return new DataTalkException(DataTalkErrorCodes.SQL_FORBIDDEN,
            "MVP only permits read queries: " + msg, false);
    }
}
