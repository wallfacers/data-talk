package com.datatalk.domain.er;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DialectTest {

    @Test
    void fromConnectionKind_mapsKnown() {
        assertThat(Dialect.fromConnectionKind("mysql")).contains(Dialect.MYSQL);
        assertThat(Dialect.fromConnectionKind("MYSQL")).contains(Dialect.MYSQL);
        assertThat(Dialect.fromConnectionKind("postgresql")).contains(Dialect.POSTGRESQL);
        assertThat(Dialect.fromConnectionKind("postgres")).contains(Dialect.POSTGRESQL);
        assertThat(Dialect.fromConnectionKind("h2")).contains(Dialect.H2);
        assertThat(Dialect.fromConnectionKind("sqlite")).contains(Dialect.SQLITE);
    }

    @Test
    void fromConnectionKind_mapsWaveA() {
        assertThat(Dialect.fromConnectionKind("mariadb")).contains(Dialect.MARIADB);
        assertThat(Dialect.fromConnectionKind("oracle")).contains(Dialect.ORACLE);
        assertThat(Dialect.fromConnectionKind("sqlserver")).contains(Dialect.SQLSERVER);
    }

    @Test
    void fromConnectionKind_returnsEmptyForUnsupported() {
        assertThat(Dialect.fromConnectionKind("clickhouse")).isEmpty();
        assertThat(Dialect.fromConnectionKind("mongodb")).isEmpty();
        assertThat(Dialect.fromConnectionKind("mssql")).isEmpty();
        assertThat(Dialect.fromConnectionKind(null)).isEmpty();
        assertThat(Dialect.fromConnectionKind("")).isEmpty();
    }
}
