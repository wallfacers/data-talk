package com.datatalk.infra.diagnostics;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Manual smoke test for StarRocks diagnostics provider against real StarRocks container.
 *
 * <p>Enable when running manually with {@code -Dtest=StarrocksDiagnosticsTestcontainersIT}
 * and Docker available.
 *
 * <p>Day-2 EXPLAIN: {@code EXPLAIN <sql>}, reuses DORIS_GRAMMAR.
 * Day-2 INDEX_HINTS: unsupported (StarRocks uses sort key / bitmap / bloom filter).
 */
@Disabled("manual smoke - run with -Dtest=StarrocksDiagnosticsTestcontainersIT and Docker available")
class StarrocksDiagnosticsTestcontainersIT extends DiagnosticsTestcontainersIT {

    @Override
    protected String containerImage() {
        return "starrocks/allin1:3.2";
    }

    @Override
    protected void setupSchema(String jdbcUrl, String user, String password) throws Exception {
        // TODO(impl-time): create orders(id, user_id, amount) OLAP table, insert 100 rows
    }

    @Test
    void explain_runsAgainstRealStarrocksContainer() {
        // TODO(impl-time): start container, real provider.explain via DORIS_GRAMMAR, assert plan
    }

    @Test
    void indexHints_returnsUnsupportedForSortKeyBitmap() {
        // TODO(impl-time): full scan SQL → unsupported with StarRocks sort-key/bitmap reason
    }
}