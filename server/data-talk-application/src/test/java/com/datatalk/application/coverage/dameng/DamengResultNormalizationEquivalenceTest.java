package com.datatalk.application.coverage.dameng;

import com.datatalk.application.sql.JdbcResultValueNormalizer;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Clob;
import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Kind-private equivalence test: dameng reuses JdbcResultValueNormalizer
 * (Oracle baseline). Verifies correctness for Dameng-typical column types:
 * CHAR (trailing spaces preserved), VARCHAR2, NUMBER, DATE, CLOB.
 */
class DamengResultNormalizationEquivalenceTest {

    // ====== CHAR — trailing spaces preserved (Oracle/semantics) ======

    @Test
    void charTrailingSpacePreserved() {
        // Dameng CHAR is fixed-width; trailing spaces are preserved.
        // The normalizer passes strings through unchanged.
        Object value = JdbcResultValueNormalizer.normalize("ABC   ");
        assertThat(value).isEqualTo("ABC   ");
    }

    // ====== VARCHAR2 — passes through as String ======

    @Test
    void varchar2StringPassesThrough() {
        Object value = JdbcResultValueNormalizer.normalize("hello");
        assertThat(value).isEqualTo("hello");
    }

    // ====== NUMBER — BigDecimal preserved ======

    @Test
    void numberAsBigDecimal() {
        BigDecimal bd = new BigDecimal("123.456");
        Object value = JdbcResultValueNormalizer.normalize(bd);
        assertThat(value).isInstanceOf(BigDecimal.class);
        assertThat(value).isEqualTo(bd);
    }

    @Test
    void integerScaleNumberConvertsToLongWithinSafeRange() {
        BigDecimal bd = new BigDecimal("42");
        Object value = JdbcResultValueNormalizer.normalize(bd);
        // scale=0 BigDecimal within safe integer range -> Long
        assertThat(value).isEqualTo(42L);
    }

    // ====== DATE — Timestamp with time portion preserved ======

    @Test
    void dateTimestampPreservesTimePortion() {
        Timestamp ts = Timestamp.valueOf("2026-05-09 14:30:45");
        Object value = JdbcResultValueNormalizer.normalize(ts);
        assertThat(value.toString()).contains("14:30:45");
    }

    // ====== CLOB — content preview via unwrapClob ======

    @Test
    void clobPreview() throws Exception {
        Clob clob = mock(Clob.class);
        when(clob.length()).thenReturn(100L);
        when(clob.getSubString(1, 100)).thenReturn("This is a CLOB preview...");
        when(clob.getCharacterStream()).thenReturn(
            new StringReader("This is a CLOB preview..."));
        // The normalizer reads up to MAX_LOB_LENGTH (64KB)
        Object value = JdbcResultValueNormalizer.normalize(clob);
        assertThat(value.toString()).contains("CLOB preview");
    }

    // ====== Large NUMBER beyond safe integer range — preserved as string ======

    @Test
    void largeNumberPreservedAsString() {
        BigInteger large = JS_SAFE_INTEGER_MAX.add(BigInteger.ONE);
        Object value = JdbcResultValueNormalizer.normalize(large);
        assertThat(value).isInstanceOf(String.class);
        assertThat(value).isEqualTo(large.toString());
    }

    private static final BigInteger JS_SAFE_INTEGER_MAX =
        BigInteger.valueOf(9_007_199_254_740_991L);
}
