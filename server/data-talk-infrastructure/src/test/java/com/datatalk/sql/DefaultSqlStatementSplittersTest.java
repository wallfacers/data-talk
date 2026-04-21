package com.datatalk.sql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSqlStatementSplittersTest {

    private final DefaultSqlStatementSplitters splitters = new DefaultSqlStatementSplitters(
        new PostgresJdbcSqlStatementSplitter(),
        new GenericSqlStatementSplitter()
    );

    @Test
    void routes_postgresql_kinds_to_the_postgres_splitter() {
        assertThat(splitters.split("postgresql", "SELECT $$a;b$$; SELECT 1;"))
            .containsExactly("SELECT $$a;b$$", "SELECT 1");
        assertThat(splitters.split("postgres", "SELECT $$a;b$$; SELECT 1;"))
            .containsExactly("SELECT $$a;b$$", "SELECT 1");
    }

    @Test
    void routes_other_kinds_to_the_generic_splitter() {
        assertThat(splitters.split("mysql", "SELECT $$a;b$$; SELECT 1;"))
            .containsExactly("SELECT $$a", "b$$", "SELECT 1");
    }
}
