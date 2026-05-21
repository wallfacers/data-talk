package com.datatalk.application.coverage.tidb;

import com.datatalk.application.coverage.mysqlprotocol.AbstractMySqlTargetResolutionReuseTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;

import javax.sql.DataSource;
import java.util.Set;

/**
 * Verifies that target-database resolution (USE, SHOW DATABASES)
 * works identically on TiDB as on MySQL.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TiDbTargetResolutionReuseIT extends AbstractMySqlTargetResolutionReuseTest {

    private GenericContainer<?> container;
    private DataSource dataSource;

    @BeforeAll
    void setUp() {
        container = TiDbContainerSupport.startContainer();
        dataSource = TiDbContainerSupport.dataSourceFor(container);
    }

    @AfterAll
    void tearDown() {
        if (container != null) container.stop();
    }

    @Override
    protected String kindUnderTest() { return "tidb"; }

    @Override
    protected DataSource dataSource() { return dataSource; }

    @Override
    protected String testDatabaseName() { return TiDbContainerSupport.TEST_DATABASE; }

    @Override
    protected Set<String> expectedSystemDatabases() {
        return Set.of("INFORMATION_SCHEMA", "mysql", "PERFORMANCE_SCHEMA", "METRICS_SCHEMA", "sys");
    }
}
