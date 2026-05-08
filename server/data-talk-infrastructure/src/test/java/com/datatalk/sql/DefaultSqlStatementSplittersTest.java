package com.datatalk.sql;

import org.junit.jupiter.api.Test;

import java.util.List;

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
    void routes_mariadb_to_the_mysql_splitter() {
        assertThat(splitters.split("mariadb", """
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

    @Test
    void routes_oracle_to_generic_splitter() {
        // Oracle day-1 uses generic single-statement splitting.
        assertThat(splitters.split("oracle", "SELECT 'a;b'; SELECT 1;"))
            .containsExactly("SELECT 'a;b'", "SELECT 1");
    }

    @Test
    void routes_sqlserver_to_generic_splitter() {
        // SQL Server day-1 uses generic single-statement splitting.
        // Future: implement GO batch-aware splitting.
        assertThat(splitters.split("sqlserver", "SELECT 'a;b'; SELECT 1;"))
            .containsExactly("SELECT 'a;b'", "SELECT 1");
    }

    @Test
    void routes_duckdb_to_generic_splitter() {
        // DuckDB day-1 uses generic single-statement splitting.
        // DuckDB has no DELIMITER, no PL/SQL, no GO.
        assertThat(splitters.split("duckdb", "SELECT 'a;b'; SELECT 1;"))
            .containsExactly("SELECT 'a;b'", "SELECT 1");
    }

    @Test
    void routes_clickhouse_to_generic_splitter() {
        // ClickHouse uses generic splitter — handles comments, strings, FORMAT/SETTINGS clauses.
        assertThat(splitters.split("clickhouse", "SELECT 'a;b'; SELECT 1;"))
            .containsExactly("SELECT 'a;b'", "SELECT 1");
    }

    @Test
    void clickhouse_handlesCommentsAndStrings() {
        assertThat(splitters.split("clickhouse", """
            -- comment; with semicolon
            SELECT 'semi;colon';
            /* block; comment */
            SELECT 1;
            """))
            .containsExactly(
                """
                -- comment; with semicolon
                SELECT 'semi;colon'""",
                """
                /* block; comment */
                SELECT 1"""
            );
    }

    @Test
    void clickhouse_handlesFormatClause() {
        // FORMAT clause should not cause splitting issues
        assertThat(splitters.split("clickhouse", "SELECT 1 FORMAT TabSeparated; SELECT 2;"))
            .containsExactly("SELECT 1 FORMAT TabSeparated", "SELECT 2");
    }

    @Test
    void tidbRoutesToMysqlSplitter() {
        String sql = "DELIMITER //\nCREATE PROCEDURE p() BEGIN SELECT 1; END //\nDELIMITER ;";
        List<String> parts = splitters.split("tidb", sql);
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0)).contains("CREATE PROCEDURE");
    }

    @Test
    void routes_apache_doris_to_mysql_splitter() {
        var result = splitters.split("apache_doris",
            "SELECT 1; INSERT INTO t VALUES (1);");
        assertThat(result).hasSize(2);
    }

    @Test
    void doris_handles_delimiter() {
        var result = splitters.split("apache_doris",
            "DELIMITER //\nCREATE PROCEDURE p() BEGIN SELECT 1; END //\nDELIMITER ;");
        assertThat(result).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void routes_starrocks_to_generic_splitter() {
        var result = splitters.split("starrocks",
            "SELECT 1; INSERT INTO t VALUES (1);");
        assertThat(result).hasSize(2);
    }
}
