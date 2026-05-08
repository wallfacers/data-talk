package com.datatalk.application.coverage.tidb;

import com.datatalk.application.coverage.mysqlprotocol.AbstractMySqlSplitterEquivalenceTest;

/**
 * Verifies that {@link com.datatalk.sql.MySqlSqlStatementSplitter} produces
 * identical results when the reported kind is {@code "tidb"}.
 * <p>
 * No container is needed — the splitter is pure logic.
 */
class TiDbSplitterEquivalenceIT extends AbstractMySqlSplitterEquivalenceTest {

    @Override
    protected String kindUnderTest() {
        return "tidb";
    }
}
