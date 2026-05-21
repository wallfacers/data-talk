package com.datatalk.sql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenericSqlStatementSplitterTest {

    private final GenericSqlStatementSplitter splitter = new GenericSqlStatementSplitter();

    @Test
    void splits_plain_semicolon_separated_sql() {
        assertThat(splitter.split("SELECT 1; SELECT 2;"))
            .containsExactly("SELECT 1", "SELECT 2");
    }

    @Test
    void ignores_semicolons_inside_single_and_double_quotes() {
        assertThat(splitter.split("SELECT ';' AS semi, \";\" AS quoted; SELECT 2;"))
            .containsExactly("SELECT ';' AS semi, \";\" AS quoted", "SELECT 2");
    }

    @Test
    void ignores_semicolons_inside_comments() {
        assertThat(splitter.split("""
            SELECT 1 -- ; stays inside comment
            ;
            /* ; stays inside block comment */
            SELECT 2;
            """))
            .containsExactly(
                "SELECT 1 -- ; stays inside comment",
                "/* ; stays inside block comment */\nSELECT 2"
            );
    }
}
