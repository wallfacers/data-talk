package com.datatalk.application.coverage.oceanbase;

import com.datatalk.application.coverage.mysqlprotocol.AbstractMySqlResultNormalizationReuseTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;

import javax.sql.DataSource;

/**
 * Verifies that {@link com.datatalk.application.sql.JdbcResultValueNormalizer}
 * handles MySQL-protocol-specific column types on OceanBase MySQL-mode
 * without error.
 */
@Disabled("Requires running OceanBase CE container")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OceanBaseNormalizerReuseIT extends AbstractMySqlResultNormalizationReuseTest {

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
}
