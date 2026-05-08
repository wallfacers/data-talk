package com.datatalk.infra.diagnostics;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Manual smoke test for SQLite diagnostics provider against real SQLite file.
 *
 * <p>SQLite is embedded, so no Docker container needed — just use a temp file.
 * Enable when running manually with {@code -Dtest=SqliteDiagnosticsTestcontainersIT}.
 *
 * <p>Day-2 EXPLAIN: {@code EXPLAIN QUERY PLAN <sql>} with id/parent columns.
 * Day-2 INDEX_HINTS: real BTREE recommendations, MEDIUM impact tier.
 */
@Disabled("manual smoke - run with -Dtest=SqliteDiagnosticsTestcontainersIT and a real SQLite file")
class SqliteDiagnosticsTestcontainersIT extends DiagnosticsTestcontainersIT {

    @Override
    protected String containerImage() {
        // SQLite is embedded; no Docker image needed.
        return "embedded:sqlite";
    }

    @Override
    protected void setupSchema(String jdbcUrl, String user, String password) throws Exception {
        // TODO(impl-time): create orders(id, user_id, amount) + index, insert 100 rows
    }

    @Test
    void explain_runsAgainstRealSqliteFile() {
        // TODO(impl-time): create temp SQLite file, real provider.explain, assert plan
    }

    @Test
    void indexHints_recommendsBtreeAgainstRealSqlite() {
        // TODO(impl-time): full scan SQL → recommendations contain BTREE, MEDIUM impact
    }
}