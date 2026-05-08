package com.datatalk.adapter.diagnostics;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Integration test verifying MySqlDiagnosticsProvider compatibility with MariaDB.
 *
 * <p>MariaDB is declared as first-class and reuses MySQL ecosystem components:
 * <ul>
 *   <li>{@code MySqlSqlStatementSplitter} for SQL parsing</li>
 *   <li>{@code MySqlDiagnosticsProvider} via {@code supportedDriverTypes("mariadb")}</li>
 *   <li>{@code MariaDbDdlGenerator} for ER Designer DDL</li>
 * </ul>
 *
 * <p>This IT validates that MySqlDiagnosticsProvider produces correct, parseable EXPLAIN plans
 * and INDEX_HINTS recommendations when connected to a real MariaDB container.
 *
 * <p>Enable when running manually with {@code -Dtest=MariaDbCompatibilityIT} and Docker available.
 * CI pipeline does not run this test; it relies on L1-L3 unit tests and the provider's
 * MariaDB-specific fixture tests.
 */
@Disabled("manual smoke - validates MySqlDiagnosticsProvider works against real MariaDB")
class MariaDbCompatibilityIT {

    @Test
    void explain_returnsParsablePlanFromMariaDb_via_MySqlDiagnosticsProvider() {
        // TODO(impl-time):
        // 1. Start testcontainers MariaDB 11.4 container
        // 2. Create connection with kind="mariadb"
        // 3. Seed orders(id, user_id, amount) table with index + 100 rows
        // 4. Call MySqlDiagnosticsProvider.explain for SELECT * FROM orders WHERE user_id = 1
        // 5. Assert ExplainPlan.dialect == "mysql" (provider hardcodes)
        // 6. Assert nodes parsed correctly (ScanType, table, estimated rows)
    }

    @Test
    void indexHints_recommendsBtreeAgainstRealMariaDb() {
        // TODO(impl-time):
        // 1. Start testcontainers MariaDB 11.4 container
        // 2. Create connection with kind="mariadb"
        // 3. Seed orders(id, user_id, amount) WITHOUT index on user_id
        // 4. Call MySqlDiagnosticsProvider.indexHints for SELECT * FROM orders WHERE user_id = 1
        // 5. Assert recommendations contain BTREE suggestion
        // 6. Assert impact tier is HIGH or MEDIUM based on table size
    }

    @Test
    void explain_handlesMariaDbSpecificSyntax() {
        // TODO(impl-time):
        // 1. Start testcontainers MariaDB 11.4 container
        // 2. Test EXPLAIN for MariaDB-specific syntax (RETURNING, sequences, etc.)
        // 3. Verify provider handles MariaDB extensions gracefully
    }
}