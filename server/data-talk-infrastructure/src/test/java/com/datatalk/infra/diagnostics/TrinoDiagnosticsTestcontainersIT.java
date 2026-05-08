package com.datatalk.infra.diagnostics;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Manual smoke test for Trino diagnostics provider against real Trino container.
 *
 * <p>Enable when running manually with {@code -Dtest=TrinoDiagnosticsTestcontainersIT}
 * and Docker available.
 *
 * <p>Day-2 EXPLAIN: {@code EXPLAIN (TYPE LOGICAL) <sql>}, reuses TRINO_GRAMMAR.
 * Day-2 INDEX_HINTS: unsupported (Trino is federated).
 */
@Disabled("manual smoke - run with -Dtest=TrinoDiagnosticsTestcontainersIT and Docker available")
class TrinoDiagnosticsTestcontainersIT extends DiagnosticsTestcontainersIT {

    @Override
    protected String containerImage() {
        return "trinodb/trino:451";
    }

    @Override
    protected void setupSchema(String jdbcUrl, String user, String password) throws Exception {
        // TODO(impl-time): configure connector, create orders table, insert 100 rows
    }

    @Test
    void explain_runsAgainstRealTrinoContainer() {
        // TODO(impl-time): start container, real provider.explain via TRINO_GRAMMAR, assert plan
    }

    @Test
    void indexHints_returnsUnsupportedForFederated() {
        // TODO(impl-time): full scan SQL → unsupported with Trino federated reason
    }
}