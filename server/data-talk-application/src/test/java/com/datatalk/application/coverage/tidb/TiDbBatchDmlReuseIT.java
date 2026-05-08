package com.datatalk.application.coverage.tidb;

import com.datatalk.application.coverage.mysqlprotocol.AbstractMySqlBatchDmlReuseTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;

import javax.sql.DataSource;

/**
 * Verifies that JDBC batch DML (addBatch / executeBatch) works
 * identically on TiDB as on MySQL.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TiDbBatchDmlReuseIT extends AbstractMySqlBatchDmlReuseTest {

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
}
