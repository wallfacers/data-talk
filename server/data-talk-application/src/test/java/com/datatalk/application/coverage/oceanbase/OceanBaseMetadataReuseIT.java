package com.datatalk.application.coverage.oceanbase;

import com.datatalk.application.coverage.mysqlprotocol.AbstractMySqlMetadataReuseTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;

import javax.sql.DataSource;

/**
 * Verifies that JDBC {@link java.sql.DatabaseMetaData} queries required by
 * DataTalk work identically on OceanBase MySQL-mode as on MySQL.
 */
@Disabled("Requires running OceanBase CE container")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OceanBaseMetadataReuseIT extends AbstractMySqlMetadataReuseTest {

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
}
