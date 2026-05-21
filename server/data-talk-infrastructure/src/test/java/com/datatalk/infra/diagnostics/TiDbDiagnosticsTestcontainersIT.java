package com.datatalk.infra.diagnostics;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Manual smoke test for TiDB diagnostics provider against real TiDB container.
 *
 * <p>Enable when running manually with {@code -Dtest=TiDbDiagnosticsTestcontainersIT}
 * and Docker available.
 *
 * <p>Day-2 EXPLAIN: real EXPLAIN output, tabular parsed via TiDB TabularLayout.
 * Day-2 INDEX_HINTS: real BTREE recommendations via SqlColumnExtractor,
 * impact tier by estRows.
 * LOCK/POOL/SPACE/TERMINATE/OPTIMIZE remain structured unsupported (Day-3 includes
 * Statement Summary / ADMIN SHOW DDL).
 */
@Disabled("manual smoke - run with -Dtest=TiDbDiagnosticsTestcontainersIT and Docker available")
class TiDbDiagnosticsTestcontainersIT extends DiagnosticsTestcontainersIT {

    @Override
    protected String containerImage() {
        return "pingcap/tidb:v7.5.0";
    }

    @Override
    protected void setupSchema(String jdbcUrl, String user, String password) throws Exception {
        // TODO(impl-time): create orders(id, user_id, amount) + index, insert 100 rows
    }

    @Test
    void explain_runsAgainstRealTidbContainer() {
        // TODO(impl-time): start container, real provider.explain, assert plan via TabularLayout
    }

    @Test
    void indexHints_recommendsBtreeAgainstRealTidb() {
        // TODO(impl-time): full scan SQL → recommendations contain BTREE, impact tier by estRows
    }
}