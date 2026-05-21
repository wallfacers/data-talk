package com.datatalk.infra.diagnostics;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Manual smoke test for Hive diagnostics provider against real Hive container.
 *
 * <p>Enable when running manually with {@code -Dtest=HiveDiagnosticsTestcontainersIT}
 * and Docker available.
 *
 * <p>Day-2 EXPLAIN: {@code EXPLAIN <sql>}, STAGE PLANS parsed via HIVE_GRAMMAR,
 * partition check warning.
 * Day-2 INDEX_HINTS: unsupported (Hive uses partitioning / bucketing).
 */
@Disabled("manual smoke - run with -Dtest=HiveDiagnosticsTestcontainersIT and Docker available")
class HiveDiagnosticsTestcontainersIT extends DiagnosticsTestcontainersIT {

    @Override
    protected String containerImage() {
        return "apache/hive:4.0.0";
    }

    @Override
    protected void setupSchema(String jdbcUrl, String user, String password) throws Exception {
        // TODO(impl-time): create orders(id, user_id, amount) table, insert 100 rows
    }

    @Test
    void explain_runsAgainstRealHiveContainer() {
        // TODO(impl-time): start container, real provider.explain, assert STAGE PLANS parsed
    }

    @Test
    void indexHints_returnsUnsupportedForPartitioning() {
        // TODO(impl-time): full scan SQL → unsupported with Hive partitioning/bucketing reason
    }
}