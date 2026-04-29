package com.datatalk.application.sql;

import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlStatementGuardTest {

    private SqlStatementGuard guard;

    @BeforeEach
    void setUp() {
        var source = new StaticMessageSource();
        source.addMessage("error.sql.forbidden", Locale.ENGLISH, "Only SELECT/WITH statements are allowed");
        source.addMessage("error.sql.empty_sql", Locale.ENGLISH, "empty SQL");
        source.addMessage("error.sql.multiple_statements", Locale.ENGLISH, "multiple statements not allowed");
        source.addMessage("error.sql.cannot_determine_type", Locale.ENGLISH, "cannot determine statement type");
        source.addMessage("error.sql.only_select_allowed", Locale.ENGLISH, "only SELECT / WITH allowed, got: {0}");
        source.addMessage("error.sql.mvp_only_read", Locale.ENGLISH, "MVP only permits read queries: {0}");
        guard = new SqlStatementGuard(new Translator(source));
    }

    @Test void acceptsSimpleSelect() {
        assertThatCode(() -> guard.assertSelectOnly("SELECT * FROM users")).doesNotThrowAnyException();
    }
    @Test void acceptsSelectWithWith() {
        assertThatCode(() -> guard.assertSelectOnly("WITH t AS (SELECT 1) SELECT * FROM t")).doesNotThrowAnyException();
    }
    @Test void rejectsInsert() {
        assertThatThrownBy(() -> guard.assertSelectOnly("INSERT INTO t VALUES(1)"))
            .isInstanceOf(DataTalkException.class)
            .matches(e -> ((DataTalkException) e).code().equals(DataTalkErrorCodes.SQL_FORBIDDEN));
    }
    @Test void rejectsDelete() {
        assertThatThrownBy(() -> guard.assertSelectOnly("DELETE FROM t WHERE 1=1"))
            .isInstanceOf(DataTalkException.class);
    }
    @Test void rejectsUpdate() {
        assertThatThrownBy(() -> guard.assertSelectOnly("UPDATE t SET x=1"))
            .isInstanceOf(DataTalkException.class);
    }
    @Test void rejectsDrop() {
        assertThatThrownBy(() -> guard.assertSelectOnly("DROP TABLE t"))
            .isInstanceOf(DataTalkException.class);
    }
    @Test void rejectsMultipleStatements() {
        assertThatThrownBy(() -> guard.assertSelectOnly("SELECT 1; DELETE FROM t"))
            .isInstanceOf(DataTalkException.class);
    }
    @Test void acceptsSelectWithTrailingComment() {
        // "SELECT 1; -- DELETE FROM t" strips to "SELECT 1;" — the semicolon is the
        // statement terminator, not a separator. Safe SQL, so the guard accepts it.
        assertThatCode(() -> guard.assertSelectOnly("SELECT 1; -- DELETE FROM t"))
            .doesNotThrowAnyException();
    }
    @Test void rejectsSemicolonSeparatingTwoStatements() {
        assertThatThrownBy(() -> guard.assertSelectOnly("SELECT 1 ; DELETE FROM t"))
            .isInstanceOf(DataTalkException.class);
    }
}
