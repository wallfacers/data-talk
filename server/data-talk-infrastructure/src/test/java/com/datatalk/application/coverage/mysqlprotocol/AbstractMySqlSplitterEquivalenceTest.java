package com.datatalk.application.coverage.mysqlprotocol;

import com.datatalk.sql.MySqlSqlStatementSplitter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the {@link MySqlSqlStatementSplitter} produces identical
 * splitting results for every MySQL-protocol-compatible kind.
 *
 * Concrete subclasses provide {@link #kindUnderTest()} for reporting only;
 * all assertions are in this base class.
 */
public abstract class AbstractMySqlSplitterEquivalenceTest {

    /** Identifier of the data-source kind being tested (e.g. "tidb", "mariadb"). */
    protected abstract String kindUnderTest();

    private final MySqlSqlStatementSplitter splitter = new MySqlSqlStatementSplitter();

    @Test
    void basicSelectScript() {
        var result = splitter.split("SELECT 1; SELECT 2;");
        assertThat(result)
                .as("kindUnderTest=%s — basic two-statement script", kindUnderTest())
                .hasSize(2)
                .containsExactly("SELECT 1", "SELECT 2");
    }

    @Test
    void delimiterCustomMarker() {
        var result = splitter.split("""
                DELIMITER //
                CREATE PROCEDURE p() BEGIN SELECT 1; END //
                DELIMITER ;
                CALL p();
                """);
        assertThat(result)
                .as("kindUnderTest=%s — DELIMITER // handling", kindUnderTest())
                .hasSize(2)
                .containsExactly(
                        "CREATE PROCEDURE p() BEGIN SELECT 1; END",
                        "CALL p()"
                );
    }

    @Test
    void commentsAreStripped() {
        var result = splitter.split("""
                SELECT 1; -- line comment
                # hash comment
                /* block comment */ SELECT 2;
                """);
        assertThat(result)
                .as("kindUnderTest=%s — comments preserved in output but not splitting", kindUnderTest())
                .hasSize(2)
                .anyMatch(s -> s.contains("SELECT 1"))
                .anyMatch(s -> s.contains("SELECT 2"));
    }

    @Test
    void quoteStylesAreRespected() {
        var result = splitter.split("""
                INSERT INTO t VALUES ('semi;colon', "another;semi", `back;tick`);
                SELECT 1;
                """);
        assertThat(result)
                .as("kindUnderTest=%s — semicolons inside quotes are not statement separators", kindUnderTest())
                .hasSize(2)
                .anyMatch(s -> s.contains("INSERT INTO t"))
                .anyMatch(s -> s.contains("SELECT 1"));
    }

    @Test
    void escapeSequencesInsideStrings() {
        var result = splitter.split("INSERT INTO t VALUES ('it\\'s; ok'); SELECT 1;");
        assertThat(result)
                .as("kindUnderTest=%s — escaped single-quote inside string", kindUnderTest())
                .hasSize(2)
                .anyMatch(s -> s.contains("INSERT INTO t"))
                .anyMatch(s -> s.contains("SELECT 1"));
    }

    @Test
    void mysqlHintCommentsAreNotStatementSeparators() {
        var result = splitter.split("SELECT /*+ BKA(t1) */ 1; SELECT 2;");
        assertThat(result)
                .as("kindUnderTest=%s — optimizer hint /*+ ... */ is not a delimiter", kindUnderTest())
                .hasSize(2)
                .containsExactly("SELECT /*+ BKA(t1) */ 1", "SELECT 2");
    }

    @Test
    void splitTableIsSingleStatement() {
        var result = splitter.split("SPLIT TABLE t AT (1000);");
        assertThat(result)
                .as("kindUnderTest=%s — SPLIT TABLE is a single statement", kindUnderTest())
                .hasSize(1)
                .first().asString().contains("SPLIT TABLE");
    }
}
