package com.datatalk.infra.diagnostics;

import org.junit.jupiter.api.Disabled;

/**
 * Abstract base class for L4/L5 testcontainers-based integration tests.
 *
 * <p>These tests are {@code @Disabled} by default and only run manually with Docker available.
 * CI pipeline relies on L1-L3 unit tests; these IT classes provide optional smoke tests
 * against real database containers when a developer wants to verify provider behavior
 * against an actual engine.
 *
 * <p>Subclasses must override:
 * <ul>
 *   <li>{@link #containerImage()} — testcontainers image name (e.g., "pingcap/tidb:v7.5.0")</li>
 *   <li>{@link #setupSchema(String, String, String)} — seed test schema after container starts</li>
 * </ul>
 *
 * <p>Day-2 diagnostics implementation uses this hook for:
 * <ul>
 *   <li>TiDB, MariaDB, DuckDB, ClickHouse, Doris, StarRocks, Presto, Trino, Hive, SQL Server, SQLite</li>
 *   <li>EXPLAIN plan parsing validation against real output</li>
 *   <li>INDEX_HINTS recommendation verification when supported</li>
 * </ul>
 */
@Disabled("manual smoke - enable when running against real container")
public abstract class DiagnosticsTestcontainersIT {

    /**
     * Override to provide testcontainers image name.
     *
     * @return Docker image name (e.g., "pingcap/tidb:v7.5.0", "mariadb:11.4")
     */
    protected abstract String containerImage();

    /**
     * Override to seed schema after container starts.
     *
     * <p>Typical setup: create {@code orders(id, user_id, amount)} table with index,
     * insert 100 sample rows for EXPLAIN and INDEX_HINTS tests.
     *
     * @param jdbcUrl  JDBC connection URL from container
     * @param user     Database username
     * @param password Database password
     * @throws Exception on schema setup failure
     */
    protected abstract void setupSchema(String jdbcUrl, String user, String password) throws Exception;
}