package com.datatalk.sql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlSqlStatementSplitterTest {

    private final MySqlSqlStatementSplitter splitter = new MySqlSqlStatementSplitter();

    @Test
    void ignores_semicolons_inside_mysql_literals_identifiers_and_comments() {
        assertThat(splitter.split("""
            SELECT 'a;\\'b' AS single_quote, "x;y" AS double_quote, `semi;col` FROM demo;
            SELECT 2 # ; stays inside hash comment
            ;
            /* ; stays inside block comment */
            SELECT 3;
            """))
            .containsExactly(
                "SELECT 'a;\\'b' AS single_quote, \"x;y\" AS double_quote, `semi;col` FROM demo",
                "SELECT 2 # ; stays inside hash comment",
                "/* ; stays inside block comment */\nSELECT 3"
            );
    }

    @Test
    void supports_delimiter_scripts_for_stored_programs() {
        assertThat(splitter.split("""
            DELIMITER //
            CREATE PROCEDURE AddData()
            BEGIN
              INSERT INTO test_data(value) VALUES ('Hello; there');
              SELECT `semi;col` FROM test_data;
            END //
            DELIMITER ;
            CALL AddData();
            """))
            .containsExactly(
                """
            CREATE PROCEDURE AddData()
            BEGIN
              INSERT INTO test_data(value) VALUES ('Hello; there');
              SELECT `semi;col` FROM test_data;
            END""",
                "CALL AddData()"
            );
    }
}
