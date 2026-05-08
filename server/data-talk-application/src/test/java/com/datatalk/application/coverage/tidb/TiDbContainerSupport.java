package com.datatalk.application.coverage.tidb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;

/**
 * Shared Testcontainers fixture for TiDB integration tests.
 * Starts a single-node TiDB (pingcap/tidb) container on port 4000
 * and provides a {@link DataSource} backed by HikariCP.
 */
public final class TiDbContainerSupport {

    public static final String IMAGE = "pingcap/tidb:v7.5.5";
    public static final String TEST_DATABASE = "reuse_test";

    private TiDbContainerSupport() {}

    public static GenericContainer<?> startContainer() {
        GenericContainer<?> container = new GenericContainer<>(IMAGE)
            .withExposedPorts(4000)
            .waitingFor(Wait.forLogMessage(".*server is running.*\\n", 1)
                .withStartupTimeout(Duration.ofMinutes(3)));
        container.start();
        return container;
    }

    public static DataSource dataSourceFor(GenericContainer<?> container) {
        Integer mappedPort = container.getMappedPort(4000);
        String url = "jdbc:mysql://" + container.getHost() + ":" + mappedPort
            + "/" + TEST_DATABASE
            + "?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true";
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername("root");
        cfg.setPassword("");
        cfg.setMaximumPoolSize(2);
        cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
        HikariDataSource ds = new HikariDataSource(cfg);
        bootstrapTestDatabase(ds);
        return ds;
    }

    private static void bootstrapTestDatabase(DataSource ds) {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS " + TEST_DATABASE);
            st.execute("USE " + TEST_DATABASE);
            st.execute("SET GLOBAL foreign_key_checks = 1");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to bootstrap TiDB test database", e);
        }
    }
}
