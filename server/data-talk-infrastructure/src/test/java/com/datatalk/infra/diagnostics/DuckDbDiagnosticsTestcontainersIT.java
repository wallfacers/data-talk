package com.datatalk.infra.diagnostics;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Manual smoke test for DuckDB diagnostics provider against real DuckDB instance.
 *
 * <p>DuckDB is embedded; no Docker container needed — just use in-memory or temp file.
 * Enable when running manually with {@code -Dtest=DuckDbDiagnosticsTestcontainersIT}.
 *
 * <p>Day-2 EXPLAIN: {@code EXPLAIN <sql>}, simplified flat node list via DUCKDB_GRAMMAR.
 * Day-2 INDEX_HINTS: unsupported (DuckDB column store, points to zone map).
 */
@Disabled("manual smoke - run with -Dtest=DuckDbDiagnosticsTestcontainersIT and a real DuckDB instance")
class DuckDbDiagnosticsTestcontainersIT extends DiagnosticsTestcontainersIT {

    @Override
    protected String containerImage() {
        // DuckDB is embedded; no Docker image needed.
        return "embedded:duckdb";
    }

    @Override
    protected void setupSchema(String jdbcUrl, String user, String password) throws Exception {
        // TODO(impl-time): create orders(id, user_id, amount), insert 100 rows
    }

    @Test
    void explain_runsAgainstRealDuckDb() {
        // TODO(impl-time): create DuckDB instance, real provider.explain, assert flat node list
    }

    @Test
    void indexHints_returnsUnsupportedForColumnStore() {
        // TODO(impl-time): full scan SQL → unsupported with DuckDB column-store reason
    }
}