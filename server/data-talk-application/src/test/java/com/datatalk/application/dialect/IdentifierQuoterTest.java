package com.datatalk.application.dialect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class IdentifierQuoterTest {

    @ParameterizedTest
    @ValueSource(strings = { "mysql", "mariadb", "tidb", "oceanbase",
                             "apache_doris", "starrocks", "clickhouse" })
    void backtickStyleForMySqlProtocolKinds(String kind) {
        assertThat(IdentifierQuoter.resolve(kind)).isEqualTo(QuoteStyle.BACKTICK);
        assertThat(IdentifierQuoter.quote("x", kind)).isEqualTo("`x`");
    }

    @ParameterizedTest
    @ValueSource(strings = { "postgresql", "postgres", "h2", "sqlite", "oracle",
                             "duckdb", "kingbase", "dameng", "gaussdb",
                             "hive", "apache_hive", "trino", "presto" })
    void doubleQuoteStyleForAnsiKinds(String kind) {
        assertThat(IdentifierQuoter.resolve(kind)).isEqualTo(QuoteStyle.DOUBLE_QUOTE);
        assertThat(IdentifierQuoter.quote("x", kind)).isEqualTo("\"x\"");
    }

    @ParameterizedTest
    @ValueSource(strings = { "sqlserver", "mssql" })
    void bracketStyleForSqlServer(String kind) {
        assertThat(IdentifierQuoter.resolve(kind)).isEqualTo(QuoteStyle.BRACKET);
        assertThat(IdentifierQuoter.quote("x", kind)).isEqualTo("[x]");
    }

    @ParameterizedTest
    @CsvSource({ "MYSQL,`x`", "MySql,`x`", "mysql,`x`",
                 "POSTGRESQL,\"x\"", "PostgreSQL,\"x\"",
                 "SQLSERVER,[x]", "SqlServer,[x]" })
    void caseInsensitive(String kind, String expected) {
        assertThat(IdentifierQuoter.quote("x", kind)).isEqualTo(expected);
    }

    @Test
    void backtickEscapesEmbeddedBacktick() {
        assertThat(IdentifierQuoter.quote("weird`name", "mysql"))
            .isEqualTo("`weird``name`");
    }

    @Test
    void doubleQuoteEscapesEmbeddedDoubleQuote() {
        assertThat(IdentifierQuoter.quote("weird\"name", "postgresql"))
            .isEqualTo("\"weird\"\"name\"");
    }

    @Test
    void bracketEscapesEmbeddedRightBracketOnly() {
        assertThat(IdentifierQuoter.quote("weird]name", "sqlserver"))
            .isEqualTo("[weird]]name]");
        // Left bracket should NOT be escaped
        assertThat(IdentifierQuoter.quote("[weird]name", "sqlserver"))
            .isEqualTo("[[weird]]name]");
    }

    @Test
    void nullKindFallsBackToDoubleQuoteWithoutThrowing() {
        assertThatNoException().isThrownBy(() -> IdentifierQuoter.quote("x", null));
        assertThat(IdentifierQuoter.quote("x", null)).isEqualTo("\"x\"");
        assertThat(IdentifierQuoter.resolve(null)).isEqualTo(QuoteStyle.DOUBLE_QUOTE);
    }

    @Test
    void blankKindFallsBackToDoubleQuote() {
        assertThat(IdentifierQuoter.quote("x", "")).isEqualTo("\"x\"");
        assertThat(IdentifierQuoter.quote("x", "   ")).isEqualTo("\"x\"");
    }

    @Test
    void unknownKindFallsBackToDoubleQuoteWithoutThrowing() {
        assertThatNoException().isThrownBy(() -> IdentifierQuoter.quote("x", "unknown_kind_xyz"));
        assertThat(IdentifierQuoter.quote("x", "unknown_kind_xyz")).isEqualTo("\"x\"");
        assertThat(IdentifierQuoter.resolve("unknown_kind_xyz")).isEqualTo(QuoteStyle.DOUBLE_QUOTE);
    }

    @Test
    void reservedKeywordIdentifiersAreSafeUnderBackticks() {
        // MySQL reserved keywords like `select`, `order`, `group` are common pain points.
        assertThat(IdentifierQuoter.quote("select", "mysql")).isEqualTo("`select`");
        assertThat(IdentifierQuoter.quote("order", "mysql")).isEqualTo("`order`");
        assertThat(IdentifierQuoter.quote("group", "mysql")).isEqualTo("`group`");
    }

    @Test
    void identifierWithSpaceIsQuoted() {
        assertThat(IdentifierQuoter.quote("user data", "mysql")).isEqualTo("`user data`");
        assertThat(IdentifierQuoter.quote("user data", "postgresql")).isEqualTo("\"user data\"");
        assertThat(IdentifierQuoter.quote("user data", "sqlserver")).isEqualTo("[user data]");
    }

    @Test
    void nullIdentifierThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> IdentifierQuoter.quote(null, "mysql")
        );
    }
}
