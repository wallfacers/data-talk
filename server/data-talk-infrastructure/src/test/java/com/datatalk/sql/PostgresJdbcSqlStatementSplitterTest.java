package com.datatalk.sql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresJdbcSqlStatementSplitterTest {

    private final PostgresJdbcSqlStatementSplitter splitter = new PostgresJdbcSqlStatementSplitter();

    @Test
    void splits_scripts_with_untagged_dollar_quoted_strings() {
        assertThat(splitter.split("SELECT $$a;b$$; SELECT 1;"))
            .containsExactly("SELECT $$a;b$$", "SELECT 1");
    }

    @Test
    void splits_scripts_with_tagged_dollar_quoted_strings() {
        assertThat(splitter.split("SELECT $tag$a;b$tag$; SELECT 1;"))
            .containsExactly("SELECT $tag$a;b$tag$", "SELECT 1");
    }

    @Test
    void splits_do_blocks_without_breaking_inner_semicolons() {
        assertThat(splitter.split("DO $$ BEGIN PERFORM 1; PERFORM 2; END $$; SELECT 1;"))
            .containsExactly("DO $$ BEGIN PERFORM 1; PERFORM 2; END $$", "SELECT 1");
    }

    @Test
    void splits_create_function_scripts_without_breaking_function_bodies() {
        assertThat(splitter.split("""
            CREATE OR REPLACE FUNCTION demo()
            RETURNS integer
            AS $fn$
            BEGIN
              RETURN 1;
            END
            $fn$
            LANGUAGE plpgsql;
            SELECT 1;
            """))
            .containsExactly(
                """
            CREATE OR REPLACE FUNCTION demo()
            RETURNS integer
            AS $fn$
            BEGIN
              RETURN 1;
            END
            $fn$
            LANGUAGE plpgsql""",
                "SELECT 1"
            );
    }
}
