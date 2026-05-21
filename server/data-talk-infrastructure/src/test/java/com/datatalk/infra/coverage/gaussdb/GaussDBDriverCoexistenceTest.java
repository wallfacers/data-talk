package com.datatalk.infra.jdbc;

import org.junit.jupiter.api.Test;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class GaussDBDriverCoexistenceTest {

    @Test
    void gaussdbDriverIsRegistered() {
        boolean found = false;
        var drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver d = drivers.nextElement();
            if (d.getClass().getName().equals("com.huawei.gaussdb.jdbc.Driver")) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void gaussdbDriverAcceptsGaussDbUrl() throws Exception {
        // GaussDB driver uses jdbc:gaussdb:// prefix
        Driver driver = DriverManager.getDriver("jdbc:gaussdb://host:8000/db");
        assertThat(driver.getClass().getName()).isEqualTo("com.huawei.gaussdb.jdbc.Driver");
    }

    @Test
    void standardPgUrlAcceptedByPostgresDriver() throws Exception {
        // Standard PG URL on port 5432 is accepted by org.postgresql.Driver
        Driver driver = DriverManager.getDriver("jdbc:postgresql://host:5432/db");
        assertThat(driver.acceptsURL("jdbc:postgresql://host:5432/db")).isTrue();
    }

    @Test
    void kingbaseDriverDoesNotAcceptGaussDbUrl() throws Exception {
        Driver kingbase = (Driver) Class.forName("com.kingbase8.Driver").getDeclaredConstructor().newInstance();
        assertThat(kingbase.acceptsURL("jdbc:gaussdb://host:8000/db")).isFalse();
    }

    @Test
    void gaussdbDriverDoesNotAcceptKingbaseUrl() throws Exception {
        Driver gaussdb = (Driver) Class.forName("com.huawei.gaussdb.jdbc.Driver").getDeclaredConstructor().newInstance();
        assertThat(gaussdb.acceptsURL("jdbc:kingbase8://host:54321/db")).isFalse();
    }

    @Test
    void onlyOnePostgresDriverInstanceRegistered() {
        List<Driver> pgDrivers = new ArrayList<>();
        var drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver d = drivers.nextElement();
            if (d.getClass().getName().equals("org.postgresql.Driver")) {
                pgDrivers.add(d);
            }
        }
        assertThat(pgDrivers).hasSize(1);
    }
}
