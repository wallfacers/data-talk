package com.datatalk.infra.jdbc;

import org.junit.jupiter.api.Test;
import java.sql.Driver;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

class KingbaseDriverCoexistenceTest {
    @Test
    void kingbase8DriverIsRegistered() {
        boolean found = false;
        var drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver d = drivers.nextElement();
            if (d.getClass().getName().equals("com.kingbase8.Driver")) { found = true; break; }
        }
        assertThat(found).isTrue();
    }

    @Test
    void kingbase8AcceptsKingbaseUrlOnly() throws Exception {
        Driver kingbase = DriverManager.getDriver("jdbc:kingbase8://h:54321/db");
        assertThat(kingbase.getClass().getName()).isEqualTo("com.kingbase8.Driver");
    }

    @Test
    void postgresDriverDoesNotAcceptKingbaseUrl() throws Exception {
        Driver pg = (Driver) Class.forName("org.postgresql.Driver").getDeclaredConstructor().newInstance();
        assertThat(pg.acceptsURL("jdbc:kingbase8://h:54321/db")).isFalse();
    }

    @Test
    void kingbase8DoesNotAcceptPostgresUrl() throws Exception {
        Driver kingbase = (Driver) Class.forName("com.kingbase8.Driver").getDeclaredConstructor().newInstance();
        assertThat(kingbase.acceptsURL("jdbc:postgresql://h:5432/db")).isFalse();
    }

    @Test
    void kingbase8DoesNotAcceptOpengaussUrl() throws Exception {
        Driver kingbase = (Driver) Class.forName("com.kingbase8.Driver").getDeclaredConstructor().newInstance();
        assertThat(kingbase.acceptsURL("jdbc:opengauss://h:5432/db")).isFalse();
    }
}
