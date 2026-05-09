package com.datatalk.application.coverage.dameng;

import com.datatalk.sql.GenericSqlStatementSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kind-private equivalence test: dameng uses the same GenericSqlStatementSplitter
 * instance as Oracle Day-1. Validates the 5-case canonical splitter contract
 * on representative dameng statements.
 *
 * Per spec sec.10, no shared abstract base is extracted because dameng is the
 * only Wave C Oracle-like kind.
 */
class DamengSplitterEquivalenceTest {

    private final GenericSqlStatementSplitter splitter = new GenericSqlStatementSplitter();

    @Test
    void singleStatement() {
        List<String> parts = splitter.split("SELECT * FROM ALL_TABLES");
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).trim()).isEqualTo("SELECT * FROM ALL_TABLES");
    }

    @Test
    void semicolonDelimitedMultiStatement() {
        List<String> parts = splitter.split("SELECT 1 FROM DUAL; SELECT 2 FROM DUAL;");
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).trim()).isEqualTo("SELECT 1 FROM DUAL");
        assertThat(parts.get(1).trim()).isEqualTo("SELECT 2 FROM DUAL");
    }

    @Test
    void stringLiteralSemicolonEscaped() {
        // Semicolon inside a single-quoted string literal must NOT split
        List<String> parts = splitter.split("INSERT INTO t VALUES ('a;b'); SELECT 1");
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).trim()).isEqualTo("INSERT INTO t VALUES ('a;b')");
    }

    @Test
    void commentBlockSemicolonIgnored() {
        // Semicolon inside /* */ comment must NOT split
        List<String> parts = splitter.split("SELECT 1 /* a;b */ FROM DUAL; SELECT 2");
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).trim()).startsWith("SELECT 1");
    }

    @Test
    void emptyStatementsAreFiltered() {
        // Leading/trailing/repeated semicolons collapse to no extra statements
        List<String> parts = splitter.split(";;SELECT 1 FROM DUAL;;;");
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).trim()).isEqualTo("SELECT 1 FROM DUAL");
    }
}
