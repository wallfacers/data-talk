package com.datatalk.infra.jdbc;

import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Oracle and Dameng JDBC drivers do not interfere with each other.
 * Each driver must only accept URLs with its own protocol prefix.
 */
class DamengDriverCoexistenceTest {

    @Test
    void damengDriverAcceptsOwnUrl() throws Exception {
        var dmDriver = DriverManager.getDriver("jdbc:dm://localhost:5236");
        assertThat(dmDriver).isNotNull();
        assertThat(dmDriver.acceptsURL("jdbc:dm://localhost:5236")).isTrue();
    }

    @Test
    void damengDriverRejectsOracleUrl() throws Exception {
        var dmDriver = DriverManager.getDriver("jdbc:dm://localhost:5236");
        assertThat(dmDriver.acceptsURL("jdbc:oracle:thin:@//localhost:1521/ORCL")).isFalse();
    }

    @Test
    void oracleDriverAcceptsOwnUrl() throws Exception {
        var oracleDriver = DriverManager.getDriver("jdbc:oracle:thin:@//localhost:1521/ORCL");
        assertThat(oracleDriver).isNotNull();
        assertThat(oracleDriver.acceptsURL("jdbc:oracle:thin:@//localhost:1521/ORCL")).isTrue();
    }

    @Test
    void oracleDriverRejectsDamengUrl() throws Exception {
        var oracleDriver = DriverManager.getDriver("jdbc:oracle:thin:@//localhost:1521/ORCL");
        assertThat(oracleDriver.acceptsURL("jdbc:dm://localhost:5236")).isFalse();
    }

    @Test
    void driversAreDistinctInstances() throws Exception {
        var dmDriver = DriverManager.getDriver("jdbc:dm://localhost:5236");
        var oracleDriver = DriverManager.getDriver("jdbc:oracle:thin:@//localhost:1521/ORCL");
        assertThat(dmDriver).isNotSameAs(oracleDriver);
        assertThat(dmDriver.getClass().getName()).isNotEqualTo(oracleDriver.getClass().getName());
    }
}
