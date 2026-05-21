package com.datatalk.infra.diagnostics;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Manual smoke test for SQL Server diagnostics provider against real SQL Server container.
 *
 * <p>Enable when running manually with {@code -Dtest=SqlServerDiagnosticsTestcontainersIT}
 * and Docker available.
 *
 * <p>Day-2 EXPLAIN: {@code SET SHOWPLAN_XML ON/OFF}, XXE-safe XML parsing.
 * Day-2 INDEX_HINTS: real BTREE recommendations, impact tier by estimated rows.
 */
@Disabled("manual smoke - run with -Dtest=SqlServerDiagnosticsTestcontainersIT and Docker available")
class SqlServerDiagnosticsTestcontainersIT extends DiagnosticsTestcontainersIT {

    @Override
    protected String containerImage() {
        return "mcr.microsoft.com/mssql/server:2022-latest";
    }

    @Override
    protected void setupSchema(String jdbcUrl, String user, String password) throws Exception {
        // TODO(impl-time): create orders(id, user_id, amount) + index, insert 100 rows
    }

    @Test
    void explain_runsAgainstRealSqlServerContainer() {
        // TODO(impl-time): start container, real provider.explain via SHOWPLAN_XML, assert plan
    }

    @Test
    void indexHints_recommendsBtreeAgainstRealSqlServer() {
        // TODO(impl-time): full scan SQL → recommendations contain BTREE, impact tier by rows
    }
}