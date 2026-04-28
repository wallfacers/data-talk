package com.datatalk.application.sql;

import com.datatalk.application.i18n.Translator;
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

    private final Translator translator;

    public SqlStatementGuard(Translator translator) {
        this.translator = translator;
    }

    public void assertSelectOnly(String sql) {
        String cleaned = BLOCK_COMMENT.matcher(LINE_COMMENT.matcher(sql).replaceAll(""))
            .replaceAll("").trim();
        if (cleaned.isEmpty()) throw forbidden(translator.get("error.sql.empty_sql"));

        if (cleaned.endsWith(";")) cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        if (cleaned.contains(";")) {
            throw forbidden(translator.get("error.sql.multiple_statements"));
        }

        var matcher = FIRST_KEYWORD.matcher(cleaned);
        if (!matcher.find()) throw forbidden(translator.get("error.sql.cannot_determine_type"));
        String head = matcher.group(1).toUpperCase();
        if (!ALLOWED_FIRST_KEYWORDS.contains(head)) {
            throw forbidden(translator.get("error.sql.only_select_allowed", head));
        }
    }

    private DataTalkException forbidden(String msg) {
        return new DataTalkException(DataTalkErrorCodes.SQL_FORBIDDEN,
            translator.get("error.sql.mvp_only_read", msg), false);
    }
}
