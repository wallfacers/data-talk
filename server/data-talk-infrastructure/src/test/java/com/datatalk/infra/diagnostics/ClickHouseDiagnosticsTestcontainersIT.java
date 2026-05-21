package com.datatalk.infra.diagnostics;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Manual smoke test for ClickHouse diagnostics provider against real ClickHouse container.
 *
 * <p>Enable when running manually with {@code -Dtest=ClickHouseDiagnosticsTestcontainersIT}
 * and Docker available.
 *
 * <p>Day-2 EXPLAIN: {@code EXPLAIN PLAN <sql>}, 2-space indented tree via CLICKHOUSE_GRAMMAR,
 * ScanType from Granules ratio.
 * Day-2 INDEX_HINTS: unsupported (ClickHouse uses ORDER BY primary key + data skipping indexes).
 */
@Disabled("manual smoke - run with -Dtest=ClickHouseDiagnosticsTestcontainersIT and Docker available")
class ClickHouseDiagnosticsTestcontainersIT extends DiagnosticsTestcontainersIT {

    @Override
    protected String containerImage() {
        return "clickhouse/clickhouse-server:24.3";
    }

    @Override
    protected void setupSchema(String jdbcUrl, String user, String password) throws Exception {
        // TODO(impl-time): create orders(id, user_id, amount) MergeTree engine, insert 100 rows
    }

    @Test
    void explain_runsAgainstRealClickHouseContainer() {
        // TODO(impl-time): start container, real provider.explain, assert 2-space indented tree
    }

    @Test
    void indexHints_returnsUnsupportedForPrimaryKeyModel() {
        // TODO(impl-time): full scan SQL → unsupported with ClickHouse ORDER BY/skipping reason
    }
}