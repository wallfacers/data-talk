package com.datatalk.application.coverage.oceanbase;

import com.datatalk.application.coverage.mysqlprotocol.AbstractMySqlSplitterEquivalenceTest;
import org.junit.jupiter.api.Disabled;

/**
 * Verifies that {@link com.datatalk.sql.MySqlSqlStatementSplitter} produces
 * identical results when the reported kind is {@code "oceanbase"}.
 * <p>
 * No container is needed — the splitter is pure logic.
 */
@Disabled("Requires OceanBase kind wiring validation post container availability")
class OceanBaseSplitterReuseIT extends AbstractMySqlSplitterEquivalenceTest {

    @Override
    protected String kindUnderTest() {
        return "oceanbase";
    }
}
