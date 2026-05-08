package com.datatalk.application.coverage.tidb;

import com.datatalk.application.coverage.mysqlprotocol.AbstractMySqlResultNormalizationReuseTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link com.datatalk.application.sql.JdbcResultValueNormalizer}
 * handles MySQL-protocol-specific column types on TiDB, plus a TiDB-specific
 * AUTO_RANDOM smoke test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TiDbResultNormalizationReuseIT extends AbstractMySqlResultNormalizationReuseTest {

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

    /**
     * TiDB-specific: AUTO_RANDOM columns should normalize as Long / BIGINT.
     */
    @Test
    void tidbAutoRandomNormalizesAsBigInt() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS auto_random_t");
            st.execute("CREATE TABLE auto_random_t (id BIGINT AUTO_RANDOM(5) PRIMARY KEY, v INT)");
            st.execute("INSERT INTO auto_random_t (v) VALUES (1), (2), (3)");
            try (ResultSet rs = st.executeQuery("SELECT id, v FROM auto_random_t")) {
                int rowCount = 0;
                while (rs.next()) {
                    Object id = rs.getObject(1);
                    assertThat(id).isInstanceOf(Long.class);
                    rowCount++;
                }
                assertThat(rowCount).isEqualTo(3);
            }
        }
    }
}
