package com.datatalk.application.coverage.oceanbase;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;

/**
 * Shared Testcontainers fixture for OceanBase integration tests.
 * Starts a single-node OceanBase CE container on port 2881
 * and provides a {@link DataSource} backed by HikariCP.
 */
public final class OceanBaseContainerSupport {

    public static final String IMAGE = "oceanbase/oceanbase-ce:4.2.1-lts";
    public static final String TEST_DATABASE = "reuse_test";

    private OceanBaseContainerSupport() {}

    public static GenericContainer<?> startContainer() {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(IMAGE))
            .withExposedPorts(2881)
            .withEnv("MODE", "slim")
            .waitingFor(Wait.forLogMessage(".*observer\\s+is\\s+ready.*", 1)
                .withStartupTimeout(Duration.ofSeconds(180)));
        container.start();
        return container;
    }

    public static DataSource dataSourceFor(GenericContainer<?> container) {
        String host = container.getHost();
        Integer mappedPort = container.getMappedPort(2881);
        String url = "jdbc:oceanbase://" + host + ":" + mappedPort
            + "/" + TEST_DATABASE
            + "?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true";
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername("root@sys");
        cfg.setPassword("");
        cfg.setMaximumPoolSize(2);
        cfg.setDriverClassName("com.oceanbase.jdbc.Driver");
        HikariDataSource ds = new HikariDataSource(cfg);
        bootstrapTestDatabase(ds);
        return ds;
    }

    private static void bootstrapTestDatabase(DataSource ds) {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS " + TEST_DATABASE);
            st.execute("USE " + TEST_DATABASE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to bootstrap OceanBase test database", e);
        }
    }
}
