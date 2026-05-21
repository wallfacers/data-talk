package com.datatalk.application.sql;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class InverseSqlGeneratorTest {

    // ── INSERT inverse ──────────────────────────────────────────────────

    @Test
    void insertWithSingleGeneratedKey_generatesDeleteByPk_mysqlBacktick() {
        String sql = InverseSqlGenerator.generate(
            "INSERT", "users", Set.of("id"),
            null,
            List.of(Map.of("id", 42)),
            "mysql"
        );
        assertThat(sql).isEqualTo("DELETE FROM `users` WHERE `id` = 42");
    }

    @Test
    void insertWithMultipleGeneratedKeys_generatesDeleteWithIn_postgresqlDoubleQuote() {
        String sql = InverseSqlGenerator.generate(
            "INSERT", "users", Set.of("id"),
            null,
            List.of(Map.of("id", 42), Map.of("id", 43), Map.of("id", 44)),
            "postgresql"
        );
        assertThat(sql).isEqualTo("DELETE FROM \"users\" WHERE \"id\" IN (42, 43, 44)");
    }

    @Test
    void insertInverse_sqlServerBracket() {
        String sql = InverseSqlGenerator.generate(
            "INSERT", "users", Set.of("id"),
            null,
            List.of(Map.of("id", 42)),
            "sqlserver"
        );
        assertThat(sql).isEqualTo("DELETE FROM [users] WHERE [id] = 42");
    }

    // ── UPDATE inverse ──────────────────────────────────────────────────

    @Test
    void updateWithSingleRow_quotesAllIdentifiers_mysql() {
        // Use LinkedHashMap to guarantee insertion order so the assertion is stable.
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("name", "Alice");
        row.put("age", 30);
        String sql = InverseSqlGenerator.generate(
            "UPDATE", "users", Set.of("id"),
            List.of(row),
            null,
            "mysql"
        );
        assertThat(sql).startsWith("UPDATE `users` SET ");
        assertThat(sql).contains("`name` = 'Alice'");
        assertThat(sql).contains("`age` = 30");
        assertThat(sql).contains("WHERE `id` = 1");
    }

    @Test
    void updateWithMultipleRows_generatesMultipleStatements_postgresql() {
        String sql = InverseSqlGenerator.generate(
            "UPDATE", "users", Set.of("id"),
            List.of(
                Map.of("id", 1, "name", "Alice"),
                Map.of("id", 2, "name", "Bob")
            ),
            null,
            "postgresql"
        );
        assertThat(sql).contains("SET \"name\" = 'Alice'");
        assertThat(sql).contains("SET \"name\" = 'Bob'");
        assertThat(sql).contains("WHERE \"id\" = 1");
        assertThat(sql).contains("WHERE \"id\" = 2");
        assertThat(sql).contains(";\n");
    }

    // ── DELETE inverse ──────────────────────────────────────────────────

    @Test
    void deleteWithSingleRow_generatesInsertValues_mysql() {
        String sql = InverseSqlGenerator.generate(
            "DELETE", "users", Set.of("id"),
            List.of(Map.of("id", 1, "name", "Alice")),
            null,
            "mysql"
        );
        assertThat(sql).isEqualTo("INSERT INTO `users` (`id`, `name`) VALUES (1, 'Alice')");
    }

    @Test
    void deleteWithMultipleRows_generatesMultiValueInsert_sqlserver() {
        String sql = InverseSqlGenerator.generate(
            "DELETE", "users", Set.of("id"),
            List.of(
                Map.of("id", 1, "name", "Alice"),
                Map.of("id", 2, "name", "Bob")
            ),
            null,
            "sqlserver"
        );
        assertThat(sql).isEqualTo("INSERT INTO [users] ([id], [name]) VALUES (1, 'Alice'), (2, 'Bob')");
    }

    // ── Value formatting (unchanged behavior) ───────────────────────────

    @Test
    void nullValues_generateUnquotedNull() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 1);
        row.put("name", null);
        String sql = InverseSqlGenerator.generate(
            "UPDATE", "users", Set.of("id"),
            List.of(row),
            null,
            "mysql"
        );
        assertThat(sql).contains("`name` = NULL");
        assertThat(sql).contains("WHERE `id` = 1");
    }

    @Test
    void stringWithSingleQuote_isEscaped() {
        String sql = InverseSqlGenerator.generate(
            "DELETE", "users", Set.of("id"),
            List.of(Map.of("id", 1, "name", "O'Brien")),
            null,
            "mysql"
        );
        assertThat(sql).contains("'O''Brien'");
    }

    @Test
    void booleanValues_areFormattedAs01() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 1);
        row.put("active", true);
        row.put("deleted", false);
        String sql = InverseSqlGenerator.generate(
            "UPDATE", "flags", Set.of("id"),
            List.of(row),
            null,
            "mysql"
        );
        assertThat(sql).contains("`active` = 1");
        assertThat(sql).contains("`deleted` = 0");
    }

    // ── Special-character identifier coverage (BUG-0066 follow-up) ──────

    @Test
    void tableNameWithSpace_quotedCorrectlyAcrossDialects() {
        // Use LinkedHashMap to keep deterministic ordering on Java 9+
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("name", "Alice");

        assertThat(InverseSqlGenerator.generate("DELETE", "user data", Set.of("id"),
                List.of(row), null, "mysql"))
            .startsWith("INSERT INTO `user data` ");
        assertThat(InverseSqlGenerator.generate("DELETE", "user data", Set.of("id"),
                List.of(row), null, "postgresql"))
            .startsWith("INSERT INTO \"user data\" ");
        assertThat(InverseSqlGenerator.generate("DELETE", "user data", Set.of("id"),
                List.of(row), null, "sqlserver"))
            .startsWith("INSERT INTO [user data] ");
    }

    @Test
    void columnNameIsReservedKeyword_quotedSafely() {
        // MySQL reserved keyword `select` would explode unquoted.
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("select", "x"); // reserved keyword as column name

        String sql = InverseSqlGenerator.generate(
            "UPDATE", "orders", Set.of("id"),
            List.of(row),
            null,
            "mysql"
        );
        assertThat(sql).contains("`select` = 'x'");
    }

    @Test
    void columnNameContainingBacktickIsEscaped_mysql() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("weird`col", "x");

        String sql = InverseSqlGenerator.generate(
            "UPDATE", "t", Set.of("id"),
            List.of(row),
            null,
            "mysql"
        );
        assertThat(sql).contains("`weird``col` = 'x'");
    }

    @Test
    void columnNameContainingRightBracketIsEscaped_sqlserver() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("weird]col", "x");

        String sql = InverseSqlGenerator.generate(
            "UPDATE", "t", Set.of("id"),
            List.of(row),
            null,
            "sqlserver"
        );
        assertThat(sql).contains("[weird]]col] = 'x'");
    }

    // ── Fallback behavior ───────────────────────────────────────────────

    @Test
    void nullKindFallsBackToDoubleQuoteWithoutThrowing() {
        assertThatNoException().isThrownBy(() -> InverseSqlGenerator.generate(
            "DELETE", "users", Set.of("id"),
            List.of(Map.of("id", 1, "name", "Alice")),
            null,
            null
        ));
        String sql = InverseSqlGenerator.generate(
            "DELETE", "users", Set.of("id"),
            List.of(Map.of("id", 1, "name", "Alice")),
            null,
            null
        );
        assertThat(sql).isEqualTo("INSERT INTO \"users\" (\"id\", \"name\") VALUES (1, 'Alice')");
    }
}
