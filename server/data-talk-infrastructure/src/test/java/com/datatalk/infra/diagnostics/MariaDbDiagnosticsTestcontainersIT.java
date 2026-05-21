package com.datatalk.infra.diagnostics;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Manual smoke test for MariaDB diagnostics provider compatibility.
 *
 * <p>MariaDB reuses MySqlDiagnosticsProvider via {@code supportedDriverTypes("mariadb")}.
 * This test validates that the MySQL provider works correctly against a real MariaDB container.
 *
 * <p>Enable when running manually with {@code -Dtest=MariaDbDiagnosticsTestcontainersIT}
 * and Docker available.
 *
 * <p>Day-2 EXPLAIN: reuses MySQL EXPLAIN parsing.
 * Day-2 INDEX_HINTS: real BTREE recommendations via SqlColumnExtractor.
 */
@Disabled("manual smoke - run with -Dtest=MariaDbDiagnosticsTestcontainersIT and Docker available")
class MariaDbDiagnosticsTestcontainersIT extends DiagnosticsTestcontainersIT {

    @Override
    protected String containerImage() {
        return "mariadb:11.4";
    }

    @Override
    protected void setupSchema(String jdbcUrl, String user, String password) throws Exception {
        // TODO(impl-time): create orders(id, user_id, amount) + index, insert 100 rows
    }

    @Test
    void explain_runsAgainstRealMariaDbContainer() {
        // TODO(impl-time): start container, MySqlDiagnosticsProvider.explain, assert plan
    }

    @Test
    void indexHints_recommendsBtreeAgainstRealMariaDb() {
        // TODO(impl-time): full scan SQL → recommendations contain BTREE
    }
}