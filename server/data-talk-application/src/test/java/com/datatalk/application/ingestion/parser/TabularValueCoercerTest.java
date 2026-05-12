package com.datatalk.application.ingestion.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TabularValueCoercerTest {

    // ───────── null / empty ─────────

    @Test
    void nullStaysNull() {
        assertThat(TabularValueCoercer.coerce(null)).isNull();
    }

    @Test
    void emptyStringBecomesNull() {
        assertThat(TabularValueCoercer.coerce("")).isNull();
        assertThat(TabularValueCoercer.coerce("   ")).isNull();
    }

    // ───────── boolean ─────────

    @Test
    void booleanLiteralsCoerceToBoolean() {
        assertThat(TabularValueCoercer.coerce("true")).isEqualTo(Boolean.TRUE);
        assertThat(TabularValueCoercer.coerce("False")).isEqualTo(Boolean.FALSE);
        assertThat(TabularValueCoercer.coerce("TRUE")).isEqualTo(Boolean.TRUE);
    }

    // ───────── integers ─────────

    @Test
    void smallIntegerStaysInteger() {
        assertThat(TabularValueCoercer.coerce("42")).isEqualTo(Integer.valueOf(42));
        assertThat(TabularValueCoercer.coerce("-7")).isEqualTo(Integer.valueOf(-7));
        assertThat(TabularValueCoercer.coerce("0")).isEqualTo(Integer.valueOf(0));
    }

    @Test
    void integerBoundaryStaysInteger() {
        assertThat(TabularValueCoercer.coerce(String.valueOf(Integer.MAX_VALUE)))
            .isEqualTo(Integer.MAX_VALUE);
        assertThat(TabularValueCoercer.coerce(String.valueOf(Integer.MIN_VALUE)))
            .isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    void valuesAboveIntegerMaxPromoteToLong() {
        // BUG-0023: > 2^31 - 1 must promote to Long so TypeInferrer votes INTEGER_64.
        Object coerced = TabularValueCoercer.coerce("3000000000");
        assertThat(coerced).isInstanceOf(Long.class);
        assertThat(coerced).isEqualTo(3_000_000_000L);
    }

    @Test
    void exact2to31PromotesToLong() {
        // 2147483648 is exactly Integer.MAX_VALUE + 1.
        Object coerced = TabularValueCoercer.coerce("2147483648");
        assertThat(coerced).isInstanceOf(Long.class);
        assertThat(coerced).isEqualTo(2_147_483_648L);
    }

    // ───────── decimal ─────────

    @Test
    void decimalLiteralCoercesToDouble() {
        assertThat(TabularValueCoercer.coerce("100.50")).isEqualTo(100.50);
        assertThat(TabularValueCoercer.coerce("-3.14")).isEqualTo(-3.14);
    }

    @Test
    void integerWithoutDecimalDoesNotBecomeDouble() {
        // Pure integer literal should stay Integer, not Double.
        assertThat(TabularValueCoercer.coerce("100")).isInstanceOf(Integer.class);
    }

    // ───────── ambiguous / locale ─────────

    @Test
    void localeFormattedNumberStaysString() {
        // Thousands separator must NOT be misinterpreted as a decimal point.
        assertThat(TabularValueCoercer.coerce("1,234")).isEqualTo("1,234");
        assertThat(TabularValueCoercer.coerce("1.234,56")).isEqualTo("1.234,56");
    }

    @Test
    void alphanumericStaysString() {
        assertThat(TabularValueCoercer.coerce("abc")).isEqualTo("abc");
        assertThat(TabularValueCoercer.coerce("42abc")).isEqualTo("42abc");
        assertThat(TabularValueCoercer.coerce("v1")).isEqualTo("v1");
    }

    @Test
    void leadingZeroIntegerStillCoerces() {
        // "007" parses as 7 by Long.parseLong — accepted but loses leading zeros.
        // This is acceptable for typed columns; users wanting string-preserving must
        // configure mapping explicitly.
        assertThat(TabularValueCoercer.coerce("007")).isEqualTo(Integer.valueOf(7));
    }
}
