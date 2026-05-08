package com.datatalk.infra.diagnostics;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Manual smoke test for Presto diagnostics provider against real Presto container.
 *
 * <p>Enable when running manually with {@code -Dtest=PrestoDiagnosticsTestcontainersIT}
 * and Docker available.
 *
 * <p>Day-2 EXPLAIN: {@code EXPLAIN (TYPE LOGICAL) <sql>}, dash-prefixed tree via TRINO_GRAMMAR,
 * federated pushdown warning.
 * Day-2 INDEX_HINTS: unsupported (Presto is federated, indexes on underlying connector).
 */
@Disabled("manual smoke - run with -Dtest=PrestoDiagnosticsTestcontainersIT and Docker available")
class PrestoDiagnosticsTestcontainersIT extends DiagnosticsTestcontainersIT {

    @Override
    protected String containerImage() {
        return "prestodb/presto:0.285";
    }

    @Override
    protected void setupSchema(String jdbcUrl, String user, String password) throws Exception {
        // TODO(impl-time): configure connector, create orders table, insert 100 rows
    }

    @Test
    void explain_runsAgainstRealPrestoContainer() {
        // TODO(impl-time): start container, real provider.explain, assert dash-prefixed tree
    }

    @Test
    void indexHints_returnsUnsupportedForFederated() {
        // TODO(impl-time): full scan SQL → unsupported with Presto federated connector reason
    }
}