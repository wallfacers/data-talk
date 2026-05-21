package com.datatalk.infra.diagnostics;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Manual smoke test for Apache Doris diagnostics provider against real Doris container.
 *
 * <p>Enable when running manually with {@code -Dtest=DorisDiagnosticsTestcontainersIT}
 * and Docker available.
 *
 * <p>Day-2 EXPLAIN: {@code EXPLAIN <sql>}, PLAN FRAGMENT parsed via DORIS_GRAMMAR,
 * ScanType from PREAGGREGATION/PREDICATES/ROLLUP.
 * Day-2 INDEX_HINTS: unsupported (Apache Doris uses ROLLUP / MV / inverted indexes).
 */
@Disabled("manual smoke - run with -Dtest=DorisDiagnosticsTestcontainersIT and Docker available")
class DorisDiagnosticsTestcontainersIT extends DiagnosticsTestcontainersIT {

    @Override
    protected String containerImage() {
        return "apache/doris:doris-2.1.0";
    }

    @Override
    protected void setupSchema(String jdbcUrl, String user, String password) throws Exception {
        // TODO(impl-time): create orders(id, user_id, amount) OLAP table, insert 100 rows
    }

    @Test
    void explain_runsAgainstRealDorisContainer() {
        // TODO(impl-time): start container, real provider.explain, assert PLAN FRAGMENT parsed
    }

    @Test
    void indexHints_returnsUnsupportedForRollupMv() {
        // TODO(impl-time): full scan SQL → unsupported with Doris ROLLUP/MV reason
    }
}