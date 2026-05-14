package com.datatalk.application.sql;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InverseSqlGeneratorTest {

    @Test
    void insertWithSingleGeneratedKey_generatesDeleteById() {
        String sql = InverseSqlGenerator.generate(
            "INSERT", "users", Set.of("id"),
            null,
            List.of(Map.of("id", 42))
        );
        assertThat(sql).isEqualTo("DELETE FROM \"users\" WHERE \"id\" = 42");
    }

    @Test
    void insertWithMultipleGeneratedKeys_generatesDeleteWithIn() {
        String sql = InverseSqlGenerator.generate(
            "INSERT", "users", Set.of("id"),
            null,
            List.of(Map.of("id", 42), Map.of("id", 43), Map.of("id", 44))
        );
        assertThat(sql).isEqualTo("DELETE FROM \"users\" WHERE \"id\" IN (42, 43, 44)");
    }

    @Test
    void updateWithSingleRow_generatesUpdateSetOldValues() {
        String sql = InverseSqlGenerator.generate(
            "UPDATE", "users", Set.of("id"),
            List.of(Map.of("id", 1, "name", "Alice", "age", 30)),
            null
        );
        assertThat(sql).startsWith("UPDATE \"users\" SET ");
        assertThat(sql).contains("\"name\" = 'Alice'");
        assertThat(sql).contains("\"age\" = 30");
        assertThat(sql).contains("WHERE \"id\" = 1");
    }

    @Test
    void updateWithMultipleRows_generatesMultipleStatements() {
        String sql = InverseSqlGenerator.generate(
            "UPDATE", "users", Set.of("id"),
            List.of(
                Map.of("id", 1, "name", "Alice"),
                Map.of("id", 2, "name", "Bob")
            ),
            null
        );
        assertThat(sql).contains("UPDATE \"users\" SET \"name\" = 'Alice' WHERE \"id\" = 1");
        assertThat(sql).contains("UPDATE \"users\" SET \"name\" = 'Bob' WHERE \"id\" = 2");
        assertThat(sql).contains(";\n");
    }

    @Test
    void deleteWithSingleRow_generatesInsertValues() {
        String sql = InverseSqlGenerator.generate(
            "DELETE", "users", Set.of("id"),
            List.of(Map.of("id", 1, "name", "Alice")),
            null
        );
        assertThat(sql).isEqualTo("INSERT INTO \"users\" (\"id\", \"name\") VALUES (1, 'Alice')");
    }

    @Test
    void deleteWithMultipleRows_generatesMultiValueInsert() {
        String sql = InverseSqlGenerator.generate(
            "DELETE", "users", Set.of("id"),
            List.of(
                Map.of("id", 1, "name", "Alice"),
                Map.of("id", 2, "name", "Bob")
            ),
            null
        );
        assertThat(sql).isEqualTo("INSERT INTO \"users\" (\"id\", \"name\") VALUES (1, 'Alice'), (2, 'Bob')");
    }

    @Test
    void nullValues_generateUnquotedNull() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 1);
        row.put("name", null);
        String sql = InverseSqlGenerator.generate(
            "UPDATE", "users", Set.of("id"),
            List.of(row),
            null
        );
        assertThat(sql).contains("\"name\" = NULL");
        assertThat(sql).contains("WHERE \"id\" = 1");
    }

    @Test
    void stringWithSingleQuote_isEscaped() {
        String sql = InverseSqlGenerator.generate(
            "DELETE", "users", Set.of("id"),
            List.of(Map.of("id", 1, "name", "O'Brien")),
            null
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
            null
        );
        assertThat(sql).contains("\"active\" = 1");
        assertThat(sql).contains("\"deleted\" = 0");
    }
}
