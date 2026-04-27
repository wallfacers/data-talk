package com.datatalk.application.sql;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.sql.Array;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcResultValueNormalizerTest {

    @Test
    void keeps_safe_long_as_long() {
        Object normalized = JdbcResultValueNormalizer.normalize(9_007_199_254_740_991L);
        assertThat(normalized).isEqualTo(9_007_199_254_740_991L);
    }

    @Test
    void serializes_unsafe_long_as_string() {
        Object normalized = JdbcResultValueNormalizer.normalize(9_007_199_254_740_993L);
        assertThat(normalized).isEqualTo("9007199254740993");
    }

    @Test
    void serializes_unsafe_big_integer_as_string() {
        Object normalized = JdbcResultValueNormalizer.normalize(new BigInteger("9223372036854775807"));
        assertThat(normalized).isEqualTo("9223372036854775807");
    }

    @Test
    void unwraps_sql_array_to_java_array() throws SQLException {
        Array sqlArray = mock(Array.class);
        Long[] inner = {1L, 2L, 3L};
        when(sqlArray.getArray()).thenReturn(inner);

        Object normalized = JdbcResultValueNormalizer.normalize(sqlArray);
        assertThat(normalized).isSameAs(inner);
    }

    @Test
    void falls_back_to_string_when_array_unwrap_fails() throws SQLException {
        Array sqlArray = mock(Array.class);
        when(sqlArray.getArray()).thenThrow(new SQLException("not supported"));
        when(sqlArray.toString()).thenReturn("{1,2,3}");

        Object normalized = JdbcResultValueNormalizer.normalize(sqlArray);
        assertThat(normalized).isEqualTo("{1,2,3}");
    }

    @Test
    void unwraps_pgobject_value_via_reflection() {
        // Simulate PGobject without compile-time dependency
        Object pgObject = new FakePGobject("json", "[1,2,3]");
        Object normalized = JdbcResultValueNormalizer.normalize(pgObject);
        assertThat(normalized).isEqualTo("[1,2,3]");
    }

    @Test
    void unwraps_pgobject_jsonb_object() {
        Object pgObject = new FakePGobject("jsonb", "{\"dept_ids\":[1,2,3]}");
        Object normalized = JdbcResultValueNormalizer.normalize(pgObject);
        assertThat(normalized).isEqualTo("{\"dept_ids\":[1,2,3]}");
    }

    @SuppressWarnings("unused")
    public static class FakePGobject {
        private final String type;
        private final String value;

        public FakePGobject(String type, String value) {
            this.type = type;
            this.value = value;
        }

        public String getType() { return type; }
        public String getValue() { return value; }
    }
}
