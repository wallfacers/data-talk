package com.datatalk.sql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSqlStatementSplittersTest {

    private final DefaultSqlStatementSplitters splitters = new DefaultSqlStatementSplitters(
        new PostgresJdbcSqlStatementSplitter(),
        new MySqlSqlStatementSplitter(),
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
    void routes_mysql_to_the_mysql_splitter() {
        assertThat(splitters.split("mysql", """
            DELIMITER //
            CREATE PROCEDURE AddData()
            BEGIN
              SELECT 'a;b';
            END //
            DELIMITER ;
            CALL AddData();
            """))
            .containsExactly(
                """
            CREATE PROCEDURE AddData()
            BEGIN
              SELECT 'a;b';
            END""",
                "CALL AddData()"
            );
    }

    @Test
    void routes_other_kinds_to_the_generic_splitter() {
        assertThat(splitters.split("sqlite", "SELECT $$a;b$$; SELECT 1;"))
            .containsExactly("SELECT $$a", "b$$", "SELECT 1");
    }

    @Test
    void sqliteScriptsKeepPragmasAndQuotedSemicolonsTogether() {
        assertThat(splitters.split("sqlite", """
            PRAGMA table_info('users');
            SELECT 'semi;colon';
            VACUUM;
            """))
            .containsExactly(
                "PRAGMA table_info('users')",
                "SELECT 'semi;colon'",
                "VACUUM"
            );
    }

    @Test
    void sqliteScriptsDoNotSplitInsideComments() {
        assertThat(splitters.split("sqlite", """
            -- bootstrap; keep comment attached
            PRAGMA foreign_keys = ON;
            SELECT "semi;colon";
            """))
            .containsExactly(
                """
            -- bootstrap; keep comment attached
            PRAGMA foreign_keys = ON""",
                "SELECT \"semi;colon\""
            );
    }
}
