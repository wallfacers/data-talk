package com.datatalk.application.coverage.oceanbase;

import com.datatalk.application.coverage.mysqlprotocol.AbstractMySqlTargetResolutionReuseTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;

import javax.sql.DataSource;
import java.util.Set;

/**
 * Verifies that target-database resolution (USE, SHOW DATABASES)
 * works identically on OceanBase MySQL-mode as on MySQL.
 */
@Disabled("Requires running OceanBase CE container")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OceanBaseTargetResolutionReuseIT extends AbstractMySqlTargetResolutionReuseTest {

    private GenericContainer<?> container;
    private DataSource dataSource;

    @BeforeAll
    void setUp() {
        container = OceanBaseContainerSupport.startContainer();
        dataSource = OceanBaseContainerSupport.dataSourceFor(container);
    }

    @AfterAll
    void tearDown() {
        if (container != null) container.stop();
    }

    @Override
    protected String kindUnderTest() { return "oceanbase"; }

    @Override
    protected DataSource dataSource() { return dataSource; }

    @Override
    protected String testDatabaseName() { return OceanBaseContainerSupport.TEST_DATABASE; }

    @Override
    protected Set<String> expectedSystemDatabases() {
        return Set.of("oceanbase", "information_schema", "mysql", "SYS", "LBACSYS");
    }
}
