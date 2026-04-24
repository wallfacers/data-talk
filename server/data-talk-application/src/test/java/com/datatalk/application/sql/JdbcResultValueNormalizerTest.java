package com.datatalk.application.sql;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

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
}
