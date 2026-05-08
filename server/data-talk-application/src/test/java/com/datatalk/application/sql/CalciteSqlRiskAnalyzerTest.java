package com.datatalk.application.sql;

import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CalciteSqlRiskAnalyzerTest {

    private final CalciteSqlRiskAnalyzer analyzer = new CalciteSqlRiskAnalyzer((kind, sql) ->
        java.util.Arrays.stream(sql.split(";"))
            .map(String::trim)
            .filter(part -> !part.isEmpty())
            .toList()
    );

    @Test
    void classifiesSelectAsL1() {
        SqlRiskAnalysis analysis = analyzer.analyze("SELECT * FROM orders", Category.QUERY);

        assertThat(analysis.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(analysis.fallbackUsed()).isFalse();
        assertThat(analysis.requiresStrongConfirmation()).isFalse();
    }

    @Test
    void classifiesInsertAsL2() {
        SqlRiskAnalysis analysis = analyzer.analyze(
            "INSERT INTO orders(id, status) VALUES (1, 'new')", Category.MUTATION);

        assertThat(analysis.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(analysis.fallbackUsed()).isFalse();
        assertThat(analysis.requiresStrongConfirmation()).isFalse();
    }

    @Test
    void classifiesUpdateWithWhereAsL2() {
        SqlRiskAnalysis analysis = analyzer.analyze(
            "UPDATE orders SET status = 'done' WHERE id = 1", Category.MUTATION);

        assertThat(analysis.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(analysis.fallbackUsed()).isFalse();
        assertThat(analysis.requiresStrongConfirmation()).isFalse();
    }

    @Test
    void classifiesUpdateWithoutWhereAsL3() {
        SqlRiskAnalysis analysis = analyzer.analyze(
            "UPDATE orders SET status = 'done'", Category.MUTATION);

        assertThat(analysis.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(analysis.fallbackUsed()).isFalse();
        assertThat(analysis.requiresStrongConfirmation()).isTrue();
    }

    @Test
    void classifiesWithUpdateAsL3() {
        // Calcite's default SQL parser does not understand WITH ... UPDATE,
        // so this lands in the parse-failure fallback path. For Category.MUTATION
        // that fallback escalates to L3 unconditionally — the safe choice when
        // the AST cannot be inspected.
        SqlRiskAnalysis analysis = analyzer.analyze(
            "WITH stale AS (SELECT id FROM orders) UPDATE orders SET status = 'x' WHERE id IN (SELECT id FROM stale)",
            Category.MUTATION);

        assertThat(analysis.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(analysis.fallbackUsed()).isFalse();
        assertThat(analysis.requiresStrongConfirmation()).isTrue();
    }

    @Test
    void classifiesDropAsL3() {
        SqlRiskAnalysis analysis = analyzer.analyze("DROP TABLE orders", Category.DDL);

        assertThat(analysis.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(analysis.fallbackUsed()).isFalse();
        assertThat(analysis.requiresStrongConfirmation()).isTrue();
    }

    @Test
    void queryParseFailureFallsBackWithoutRisk() {
        SqlRiskAnalysis analysis = analyzer.analyze("SELECT FROM", Category.QUERY);

        assertThat(analysis.riskLevel()).isNull();
        assertThat(analysis.fallbackUsed()).isTrue();
        assertThat(analysis.requiresStrongConfirmation()).isFalse();
    }

    @Test
    void mutationParseFailureEscalatesToL3() {
        SqlRiskAnalysis analysis = analyzer.analyze("UPDATE", Category.MUTATION);

        assertThat(analysis.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(analysis.fallbackUsed()).isFalse();
        assertThat(analysis.requiresStrongConfirmation()).isTrue();
    }

    @Test
    void deleteWithWhereIsMedium() {
        var result = analyzer.analyze("DELETE FROM users WHERE id = 1", Category.QUERY);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(result.reason()).isEqualTo("delete_with_where");
        assertThat(result.affectedObjects()).containsExactly("users");
    }

    @Test
    void deleteWithoutWhereIsHigh() {
        var result = analyzer.analyze("DELETE FROM users", Category.QUERY);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("delete_without_where");
        assertThat(result.affectedObjects()).containsExactly("users");
    }

    @Test
    void updateWithWhereExposesAffectedObjects() {
        var result = analyzer.analyze("UPDATE orders SET status = 'paid' WHERE id = 9", Category.MUTATION);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(result.affectedObjects()).containsExactly("orders");
    }

    @Test
    void dropTableExposesAffectedObjects() {
        var result = analyzer.analyze("DROP TABLE temp_log", Category.MUTATION);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.affectedObjects()).containsExactly("temp_log");
    }

    @Test
    void multiStatementBatchUsesHighestRiskAndUnionAffectedObjects() {
        var result = analyzer.analyze(
            "UPDATE orders SET note = 'x' WHERE id = 1; DELETE FROM logs;",
            Category.MUTATION
        );
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.affectedObjects()).containsExactlyInAnyOrder("orders", "logs");
    }

    @Test
    void createViewLandsInDdlFallback() {
        // Calcite's default SQL parser does not parse CREATE VIEW; the path is
        // covered by the DDL parse-failure fallback (L3). The dedicated
        // CREATE_VIEW classifier branch with affectedObjects extraction stays
        // wired so that future parser upgrades (or dialect-aware splitters)
        // get the L2 + extracted-objects treatment for free.
        var result = analyzer.analyze(
            "CREATE VIEW active_users AS SELECT id FROM users WHERE active = TRUE",
            Category.DDL);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).startsWith("parse_failed:");
    }

    @Test
    void sqliteExplainQueryPlanIsReadOnly() {
        var result = analyzer.analyze("EXPLAIN QUERY PLAN SELECT * FROM users", Category.QUERY, "sqlite");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.reason()).isEqualTo("explain_query_plan");
    }

    @Test
    void sqlitePragmaTableInfoIsReadOnly() {
        var result = analyzer.analyze("PRAGMA table_info(users)", Category.QUERY, "sqlite");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.reason()).isEqualTo("pragma_table_info");
    }

    @Test
    void sqliteAttachDetachAndVacuumAreHighRiskEvenFromQueryCategory() {
        assertThat(analyzer.analyze("ATTACH DATABASE 'other.db' AS other", Category.QUERY, "sqlite").riskLevel())
            .isEqualTo(RiskLevel.L3);
        assertThat(analyzer.analyze("DETACH DATABASE other", Category.QUERY, "sqlite").riskLevel())
            .isEqualTo(RiskLevel.L3);
        assertThat(analyzer.analyze("VACUUM", Category.QUERY, "sqlite").riskLevel())
            .isEqualTo(RiskLevel.L3);
    }

    @Test
    void sqliteReadOnlyPragmasRemainLowRisk() {
        var result = analyzer.analyze("PRAGMA database_list", Category.QUERY, "sqlite");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.reason()).isEqualTo("pragma_database_list");
    }

    @Test
    void sqliteWritePragmasRemainHighRisk() {
        var result = analyzer.analyze("PRAGMA journal_mode = WAL", Category.QUERY, "sqlite");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("sqlite_file_or_maintenance_command");
    }

    @Test
    void sqliteHighRiskMaintenanceCommandsStayHighInsideMultiStatementScripts() {
        var result = analyzer.analyze("SELECT 1; VACUUM;", Category.QUERY, "sqlite");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("sqlite_file_or_maintenance_command");
    }

    @Test
    void nonSqliteKindsDoNotApplySqliteMaintenanceRules() {
        var result = analyzer.analyze("VACUUM", Category.QUERY, "postgresql");

        assertThat(result.riskLevel()).isNull();
        assertThat(result.fallbackUsed()).isTrue();
    }

    @Test
    void mariadbSelectIsL1() {
        var result = analyzer.analyze("SELECT * FROM orders", Category.QUERY, "mariadb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.fallbackUsed()).isFalse();
    }

    @Test
    void mariadbDeleteWithoutWhereIsL3() {
        var result = analyzer.analyze("DELETE FROM logs", Category.QUERY, "mariadb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("delete_without_where");
    }

    @Test
    void mariadbInsertIsL2() {
        var result = analyzer.analyze("INSERT INTO orders(id) VALUES (1)", Category.MUTATION, "mariadb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L2);
    }

    @Test
    void mariadbDoesNotApplySqliteMaintenanceRules() {
        var result = analyzer.analyze("VACUUM", Category.QUERY, "mariadb");

        assertThat(result.riskLevel()).isNull();
        assertThat(result.fallbackUsed()).isTrue();
    }

    // --- Oracle risk classification ---

    @Test
    void oracle_explainPlanIsL1() {
        var result = analyzer.analyze("EXPLAIN PLAN FOR SELECT * FROM employees", Category.QUERY, "oracle");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.reason()).isEqualTo("explain_plan");
    }

    @Test
    void oracle_mergeIsHighRisk() {
        var result = analyzer.analyze(
            "MERGE INTO employees e USING new_employees n ON (e.id = n.id) WHEN MATCHED THEN UPDATE SET e.name = n.name",
            Category.MUTATION, "oracle");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("oracle_merge");
    }

    @Test
    void oracle_callIsHighRisk() {
        var result = analyzer.analyze("CALL my_procedure()", Category.QUERY, "oracle");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("oracle_call");
    }

    @Test
    void oracle_beginBlockIsHighRisk() {
        var result = analyzer.analyze("BEGIN DBMS_OUTPUT.PUT_LINE('hello'); END;", Category.QUERY, "oracle");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("oracle_plsql_block");
    }

    @Test
    void oracle_declareBlockIsHighRisk() {
        var result = analyzer.analyze("DECLARE v_name VARCHAR2(100); BEGIN SELECT name INTO v_name FROM users; END;", Category.QUERY, "oracle");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("oracle_plsql_block");
    }

    @Test
    void oracle_truncateIsHighRisk() {
        var result = analyzer.analyze("TRUNCATE TABLE temp_data", Category.DDL, "oracle");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("truncate");
    }

    @Test
    void oracle_selectIsL1() {
        var result = analyzer.analyze("SELECT * FROM employees WHERE department_id = 10", Category.QUERY, "oracle");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.fallbackUsed()).isFalse();
    }

    @Test
    void oracle_insertIsL2() {
        var result = analyzer.analyze("INSERT INTO employees(id, name) VALUES (1, 'Alice')", Category.MUTATION, "oracle");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L2);
    }

    @Test
    void oracle_doesNotApplySqliteMaintenanceRules() {
        var result = analyzer.analyze("VACUUM", Category.QUERY, "oracle");

        assertThat(result.riskLevel()).isNull();
        assertThat(result.fallbackUsed()).isTrue();
    }

    // --- SQL Server risk classification ---

    @Test
    void sqlserver_mergeIsHighRisk() {
        var result = analyzer.analyze(
            "MERGE INTO employees AS t USING new_employees AS s ON (t.id = s.id) WHEN MATCHED THEN UPDATE SET t.name = s.name",
            Category.MUTATION, "sqlserver");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("sqlserver_merge");
    }

    @Test
    void sqlserver_execIsHighRisk() {
        var result = analyzer.analyze("EXEC sp_who", Category.QUERY, "sqlserver");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("sqlserver_exec");
    }

    @Test
    void sqlserver_executeIsHighRisk() {
        var result = analyzer.analyze("EXECUTE sp_helpdb", Category.QUERY, "sqlserver");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("sqlserver_exec");
    }

    @Test
    void sqlserver_backupIsHighRisk() {
        var result = analyzer.analyze("BACKUP DATABASE mydb TO DISK = 'backup.bak'", Category.DDL, "sqlserver");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("sqlserver_backup_restore");
    }

    @Test
    void sqlserver_restoreIsHighRisk() {
        var result = analyzer.analyze("RESTORE DATABASE mydb FROM DISK = 'backup.bak'", Category.DDL, "sqlserver");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("sqlserver_backup_restore");
    }

    @Test
    void sqlserver_dbccIsHighRisk() {
        var result = analyzer.analyze("DBCC CHECKDB(mydb)", Category.QUERY, "sqlserver");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("sqlserver_dbcc");
    }

    @Test
    void sqlserver_killIsHighRisk() {
        var result = analyzer.analyze("KILL 53", Category.QUERY, "sqlserver");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("sqlserver_kill");
    }

    @Test
    void sqlserver_denyIsHighRisk() {
        var result = analyzer.analyze("DENY SELECT ON employees TO public", Category.DDL, "sqlserver");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("sqlserver_deny");
    }

    @Test
    void sqlserver_selectIsL1() {
        var result = analyzer.analyze("SELECT * FROM employees WHERE department_id = 10", Category.QUERY, "sqlserver");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.fallbackUsed()).isFalse();
    }

    @Test
    void sqlserver_doesNotApplySqliteMaintenanceRules() {
        var result = analyzer.analyze("VACUUM", Category.QUERY, "sqlserver");

        assertThat(result.riskLevel()).isNull();
        assertThat(result.fallbackUsed()).isTrue();
    }

    // --- DuckDB risk classification ---

    @Test
    void duckdb_explainIsL1() {
        var result = analyzer.analyze("EXPLAIN SELECT * FROM users", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.reason()).isEqualTo("explain");
    }

    @Test
    void duckdb_describeIsL1() {
        var result = analyzer.analyze("DESCRIBE users", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.reason()).isEqualTo("describe");
    }

    @Test
    void duckdb_pragmaDatabaseListIsL1() {
        var result = analyzer.analyze("PRAGMA database_list", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.reason()).isEqualTo("pragma_database_list");
    }

    @Test
    void duckdb_pragmaTableInfoIsL1() {
        var result = analyzer.analyze("PRAGMA table_info('t')", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.reason()).isEqualTo("pragma_table_info");
    }

    @Test
    void duckdb_pragmaStorageInfoIsL1() {
        var result = analyzer.analyze("PRAGMA storage_info('t')", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.reason()).isEqualTo("pragma_storage_info");
    }

    @Test
    void duckdb_configPragmaIsHighRisk() {
        var result = analyzer.analyze("PRAGMA memory_limit='1GB'", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("duckdb_config_pragma");
    }

    @Test
    void duckdb_copyIsHighRisk() {
        var result = analyzer.analyze("COPY users TO 'export.csv'", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("duckdb_copy");
    }

    @Test
    void duckdb_exportDatabaseIsHighRisk() {
        var result = analyzer.analyze("EXPORT DATABASE 'backup_dir'", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("duckdb_export_import");
    }

    @Test
    void duckdb_importDatabaseIsHighRisk() {
        var result = analyzer.analyze("IMPORT DATABASE 'backup_dir'", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("duckdb_export_import");
    }

    @Test
    void duckdb_attachIsHighRisk() {
        var result = analyzer.analyze("ATTACH 'other.db' AS other", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("duckdb_attach");
    }

    @Test
    void duckdb_detachIsHighRisk() {
        var result = analyzer.analyze("DETACH other", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("duckdb_attach");
    }

    @Test
    void duckdb_installIsHighRisk() {
        var result = analyzer.analyze("INSTALL httpfs", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("duckdb_extension");
    }

    @Test
    void duckdb_loadIsHighRisk() {
        var result = analyzer.analyze("LOAD httpfs", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("duckdb_extension");
    }

    @Test
    void duckdb_createSecretIsHighRisk() {
        var result = analyzer.analyze("CREATE SECRET my_secret (TYPE s3, KEY_ID 'id', SECRET 'secret')", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("duckdb_secret");
    }

    @Test
    void duckdb_readCsvIsHighRisk() {
        var result = analyzer.analyze("SELECT * FROM read_csv('data.csv')", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("duckdb_file_access");
    }

    @Test
    void duckdb_readParquetIsHighRisk() {
        var result = analyzer.analyze("SELECT * FROM read_parquet('data.parquet')", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("duckdb_file_access");
    }

    @Test
    void duckdb_globIsHighRisk() {
        var result = analyzer.analyze("SELECT * FROM glob('*.csv')", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("duckdb_file_access");
    }

    @Test
    void duckdb_standardSelectFallsThroughToCalcite() {
        var result = analyzer.analyze("SELECT * FROM orders WHERE id = 1", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.fallbackUsed()).isFalse();
    }

    @Test
    void duckdb_standardInsertFallsThroughToCalcite() {
        var result = analyzer.analyze("INSERT INTO orders(id) VALUES (1)", Category.MUTATION, "duckdb");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L2);
    }

    @Test
    void duckdb_doesNotApplySqliteMaintenanceRules() {
        // VACUUM is SQLite-specific; DuckDB should not inherit SQLite rules
        var result = analyzer.analyze("VACUUM", Category.QUERY, "duckdb");

        assertThat(result.riskLevel()).isNull();
        assertThat(result.fallbackUsed()).isTrue();
    }

    // --- ClickHouse risk classification ---

    // L1 safe: SELECT, WITH, SHOW, DESCRIBE, EXPLAIN
    @Test
    void clickhouse_selectIsL1() {
        var result = analyzer.analyze("SELECT * FROM users", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.fallbackUsed()).isFalse();
    }

    @Test
    void clickhouse_withSelectIsL1() {
        var result = analyzer.analyze("WITH cte AS (SELECT 1) SELECT * FROM cte", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.fallbackUsed()).isFalse();
    }

    @Test
    void clickhouse_showTablesIsL1() {
        var result = analyzer.analyze("SHOW TABLES", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
    }

    @Test
    void clickhouse_describeIsL1() {
        var result = analyzer.analyze("DESCRIBE users", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.reason()).isEqualTo("clickhouse_describe");
    }

    @Test
    void clickhouse_explainIsL1() {
        var result = analyzer.analyze("EXPLAIN SELECT * FROM users", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.reason()).isEqualTo("clickhouse_explain");
    }

    // L2 mutation: bounded INSERT (single table, no subquery source), safe CREATE TABLE
    @Test
    void clickhouse_boundedInsertIsL2() {
        var result = analyzer.analyze("INSERT INTO users(id, name) VALUES (1, 'Alice')", Category.MUTATION, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L2);
    }

    @Test
    void clickhouse_createTableIsL2() {
        var result = analyzer.analyze(
            "CREATE TABLE users (id UInt64, name String) ENGINE = MergeTree ORDER BY id",
            Category.DDL, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(result.reason()).isEqualTo("clickhouse_create_table");
    }

    @Test
    void clickhouse_createTableWithMaterializedColumnIsL2() {
        var result = analyzer.analyze(
            "CREATE TABLE users (id UInt64, name String, hash UInt64 AS cityHash64(name)) ENGINE = MergeTree ORDER BY id",
            Category.DDL, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(result.reason()).isEqualTo("clickhouse_create_table");
    }

    @Test
    void clickhouse_createTableAsSelectIsL2() {
        var result = analyzer.analyze(
            "CREATE TABLE users_copy AS SELECT * FROM users",
            Category.DDL, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(result.reason()).isEqualTo("clickhouse_create_table");
    }

    @Test
    void clickhouse_createTableAsSelectFromExternalFunctionIsL3() {
        var result = analyzer.analyze(
            "CREATE TABLE external_copy AS SELECT * FROM s3('https://bucket/data.parquet')",
            Category.DDL, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_external_access");
    }

    // L3 destructive: DROP, TRUNCATE, broad ALTER, RENAME, GRANT/REVOKE, CREATE USER/ROLE, dictionaries
    @Test
    void clickhouse_dropTableIsL3() {
        var result = analyzer.analyze("DROP TABLE users", Category.DDL, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
    }

    @Test
    void clickhouse_truncateIsL3() {
        var result = analyzer.analyze("TRUNCATE TABLE users", Category.DDL, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_truncate");
    }

    @Test
    void clickhouse_alterIsL3() {
        var result = analyzer.analyze("ALTER TABLE users ADD COLUMN email String", Category.DDL, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_alter");
    }

    @Test
    void clickhouse_renameIsL3() {
        var result = analyzer.analyze("RENAME TABLE users TO customers", Category.DDL, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_rename");
    }

    @Test
    void clickhouse_grantIsL3() {
        var result = analyzer.analyze("GRANT SELECT ON users TO reader", Category.DDL, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_grant");
    }

    @Test
    void clickhouse_revokeIsL3() {
        var result = analyzer.analyze("REVOKE SELECT ON users FROM reader", Category.DDL, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_revoke");
    }

    @Test
    void clickhouse_createUserIsL3() {
        var result = analyzer.analyze("CREATE USER admin IDENTIFIED BY 'secret'", Category.DDL, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_create_user");
    }

    @Test
    void clickhouse_createRoleIsL3() {
        var result = analyzer.analyze("CREATE ROLE analyst", Category.DDL, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_create_role");
    }

    @Test
    void clickhouse_createDictionaryIsL3() {
        var result = analyzer.analyze(
            "CREATE DICTIONARY my_dict (id UInt64, name String) SOURCE(CLICKHOUSE(host 'localhost' port 9000))",
            Category.DDL, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_create_dictionary");
    }

    // Hard reject: KILL QUERY, SYSTEM, OPTIMIZE, ATTACH, DETACH
    @Test
    void clickhouse_killQueryIsL3() {
        var result = analyzer.analyze("KILL QUERY WHERE query_id = 'abc'", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_kill");
    }

    @Test
    void clickhouse_systemIsL3() {
        var result = analyzer.analyze("SYSTEM FLUSH LOGS", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_system");
    }

    @Test
    void clickhouse_optimizeIsL3() {
        var result = analyzer.analyze("OPTIMIZE TABLE users FINAL", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_optimize");
    }

    @Test
    void clickhouse_attachIsL3() {
        var result = analyzer.analyze("ATTACH TABLE users", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_attach");
    }

    @Test
    void clickhouse_detachIsL3() {
        var result = analyzer.analyze("DETACH TABLE users", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_detach");
    }

    // Hard reject: SELECT-shaped file/network functions
    @Test
    void clickhouse_remoteFunctionIsL3() {
        var result = analyzer.analyze("SELECT * FROM remote('host:9000', 'db', 'table', 'user', 'pass')", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_external_access");
    }

    @Test
    void clickhouse_remoteSecureFunctionIsL3() {
        var result = analyzer.analyze("SELECT * FROM remoteSecure('host:9000', 'db', 'table')", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_external_access");
    }

    @Test
    void clickhouse_urlFunctionIsL3() {
        var result = analyzer.analyze("SELECT * FROM url('http://example.com/data.csv')", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_external_access");
    }

    @Test
    void clickhouse_s3FunctionIsL3() {
        var result = analyzer.analyze("SELECT * FROM s3('https://bucket/data.parquet')", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_external_access");
    }

    @Test
    void clickhouse_s3ClusterFunctionIsL3() {
        var result = analyzer.analyze("SELECT * FROM s3Cluster('my_cluster', 'https://bucket/data.parquet')", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_external_access");
    }

    @Test
    void clickhouse_fileFunctionIsL3() {
        var result = analyzer.analyze("SELECT * FROM file('data.csv')", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_external_access");
    }

    @Test
    void clickhouse_hdfsFunctionIsL3() {
        var result = analyzer.analyze("SELECT * FROM hdfs('hdfs://namenode/data')", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_external_access");
    }

    @Test
    void clickhouse_postgresqlFunctionIsL3() {
        var result = analyzer.analyze("SELECT * FROM postgresql('host:5432', 'db', 'table', 'user', 'pass')", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_external_access");
    }

    @Test
    void clickhouse_mysqlFunctionIsL3() {
        var result = analyzer.analyze("SELECT * FROM mysql('host:3306', 'db', 'table', 'user', 'pass')", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_external_access");
    }

    @Test
    void clickhouse_mongodbFunctionIsL3() {
        var result = analyzer.analyze("SELECT * FROM mongodb('host:27017', 'db', 'collection', 'user', 'pass')", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_external_access");
    }

    @Test
    void clickhouse_odbcFunctionIsL3() {
        var result = analyzer.analyze("SELECT * FROM odbc('dsn', 'table')", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_external_access");
    }

    @Test
    void clickhouse_jdbcFunctionIsL3() {
        var result = analyzer.analyze("SELECT * FROM jdbc('jdbc:url', 'table')", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_external_access");
    }

    @Test
    void clickhouse_clusterFunctionIsL3() {
        var result = analyzer.analyze("SELECT * FROM cluster('my_cluster', 'db', 'table')", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_external_access");
    }

    @Test
    void clickhouse_clusterAllReplicasFunctionIsL3() {
        var result = analyzer.analyze("SELECT * FROM clusterAllReplicas('my_cluster', 'db', 'table')", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("clickhouse_external_access");
    }

    @Test
    void clickhouse_doesNotApplySqliteMaintenanceRules() {
        var result = analyzer.analyze("VACUUM", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isNull();
        assertThat(result.fallbackUsed()).isTrue();
    }

    @Test
    void clickhouse_standardSelectFallsThroughToCalcite() {
        var result = analyzer.analyze("SELECT * FROM orders WHERE id = 1", Category.QUERY, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.fallbackUsed()).isFalse();
    }

    @Test
    void clickhouse_standardInsertFallsThroughToCalcite() {
        var result = analyzer.analyze("INSERT INTO orders(id) VALUES (1)", Category.MUTATION, "clickhouse");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L2);
    }

    // --- Doris risk classification ---
    // L1 safe
    @Test
    void doris_selectIsL1() {
        assertThat(analyzer.analyze("SELECT * FROM t", Category.QUERY, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    @Test
    void doris_showTablesIsL1() {
        assertThat(analyzer.analyze("SHOW TABLES", Category.QUERY, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    @Test
    void doris_describeIsL1() {
        assertThat(analyzer.analyze("DESCRIBE t", Category.QUERY, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    @Test
    void doris_explainIsL1() {
        assertThat(analyzer.analyze("EXPLAIN SELECT * FROM t", Category.QUERY, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    // L2 mutation
    @Test
    void doris_boundedInsertIsL2() {
        assertThat(analyzer.analyze("INSERT INTO t VALUES (1)", Category.MUTATION, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    @Test
    void doris_createTableIsL2() {
        assertThat(analyzer.analyze("CREATE TABLE t (id INT)", Category.DDL, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    @Test
    void doris_createIndexIsL2() {
        assertThat(analyzer.analyze("CREATE INDEX idx ON t(id)", Category.DDL, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    @Test
    void doris_analyzeIsL2() {
        assertThat(analyzer.analyze("ANALYZE TABLE t", Category.QUERY, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    // L3 destructive
    @Test
    void doris_dropTableIsL3() {
        assertThat(analyzer.analyze("DROP TABLE t", Category.DDL, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void doris_truncateIsL3() {
        assertThat(analyzer.analyze("TRUNCATE TABLE t", Category.DDL, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void doris_alterIsL3() {
        assertThat(analyzer.analyze("ALTER TABLE t ADD COLUMN c INT", Category.DDL, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void doris_alterSystemIsL3() {
        assertThat(analyzer.analyze("ALTER SYSTEM DECOMMISSION BACKEND 'host:9050'", Category.QUERY, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void doris_grantIsL3() {
        assertThat(analyzer.analyze("GRANT SELECT ON db.t TO user", Category.DDL, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void doris_revokeIsL3() {
        assertThat(analyzer.analyze("REVOKE SELECT ON db.t FROM user", Category.DDL, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void doris_createUserIsL3() {
        assertThat(analyzer.analyze("CREATE USER test IDENTIFIED BY 'pw'", Category.DDL, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void doris_createRoleIsL3() {
        assertThat(analyzer.analyze("CREATE ROLE analyst", Category.DDL, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void doris_loadLabelIsL3() {
        assertThat(analyzer.analyze("LOAD LABEL label1 (DATA INFILE('file') INTO TABLE t)", Category.QUERY, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void doris_routineLoadIsL3() {
        assertThat(analyzer.analyze("ROUTINE LOAD db.label ON t FROM kafka", Category.QUERY, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void doris_exportIsL3() {
        assertThat(analyzer.analyze("EXPORT TABLE t TO 'hdfs://path'", Category.QUERY, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void doris_adminIsL3() {
        assertThat(analyzer.analyze("ADMIN SET FRONTEND CONFIG ('key' = 'val')", Category.QUERY, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void doris_deleteWithoutWhereIsL3() {
        assertThat(analyzer.analyze("DELETE FROM t", Category.QUERY, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void doris_deleteWithWhereIsL2() {
        assertThat(analyzer.analyze("DELETE FROM t WHERE id = 1", Category.QUERY, "apache_doris").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    @Test
    void doris_doesNotApplyClickhouseRules() {
        // Doris should not use ClickHouse-specific rules
        var result = analyzer.analyze("SYSTEM RELOAD DICTIONARY d", Category.QUERY, "apache_doris");
        // Should be parsed by generic Calcite, not ClickHouse rules
        assertThat(result.riskLevel()).isNotEqualTo(RiskLevel.L1);
    }

    // --- StarRocks risk classification ---
    @Test
    void starrocks_selectIsL1() {
        assertThat(analyzer.analyze("SELECT * FROM t", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    @Test
    void starrocks_showTablesIsL1() {
        assertThat(analyzer.analyze("SHOW TABLES", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    @Test
    void starrocks_describeIsL1() {
        assertThat(analyzer.analyze("DESCRIBE t", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    @Test
    void starrocks_explainIsL1() {
        assertThat(analyzer.analyze("EXPLAIN SELECT * FROM t", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    // L2
    @Test
    void starrocks_insertIsL2() {
        assertThat(analyzer.analyze("INSERT INTO t VALUES (1)", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    @Test
    void starrocks_createTableIsL2() {
        assertThat(analyzer.analyze("CREATE TABLE t (id INT)", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    @Test
    void starrocks_createIndexIsL2() {
        assertThat(analyzer.analyze("CREATE INDEX idx ON t(id)", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    @Test
    void starrocks_analyzeIsL2() {
        assertThat(analyzer.analyze("ANALYZE TABLE t", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    // L3
    @Test
    void starrocks_dropTableIsL3() {
        assertThat(analyzer.analyze("DROP TABLE t", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_truncateIsL3() {
        assertThat(analyzer.analyze("TRUNCATE TABLE t", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_alterIsL3() {
        assertThat(analyzer.analyze("ALTER TABLE t ADD COLUMN c INT", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_grantIsL3() {
        assertThat(analyzer.analyze("GRANT SELECT ON db.t TO user", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_revokeIsL3() {
        assertThat(analyzer.analyze("REVOKE SELECT ON db.t FROM user", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_createUserIsL3() {
        assertThat(analyzer.analyze("CREATE USER test IDENTIFIED BY 'pw'", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_createRoleIsL3() {
        assertThat(analyzer.analyze("CREATE ROLE analyst", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_createCatalogIsL3() {
        assertThat(analyzer.analyze("CREATE EXTERNAL CATALOG hive PROPERTIES ('type' = 'hive')", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_dropCatalogIsL3() {
        assertThat(analyzer.analyze("DROP CATALOG hive", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_loadLabelIsL3() {
        assertThat(analyzer.analyze("LOAD LABEL label1 (DATA INFILE('file') INTO TABLE t)", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_routineLoadIsL3() {
        assertThat(analyzer.analyze("ROUTINE LOAD db.label ON t FROM kafka", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_streamLoadIsL3() {
        assertThat(analyzer.analyze("STREAM LOAD label1 INTO t FROM 'file'", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_brokerLoadIsL3() {
        assertThat(analyzer.analyze("BROKER LOAD label1 INTO t FROM 'hdfs://path'", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_cancelLoadIsL3() {
        assertThat(analyzer.analyze("CANCEL LOAD label1", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_exportIsL3() {
        assertThat(analyzer.analyze("EXPORT TABLE t TO 'hdfs://path'", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_adminIsL3() {
        assertThat(analyzer.analyze("ADMIN SET FRONTEND CONFIG ('key' = 'val')", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_setGlobalIsL3() {
        assertThat(analyzer.analyze("SET GLOBAL query_timeout = 300", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_setPasswordIsL3() {
        assertThat(analyzer.analyze("SET PASSWORD FOR user = 'newpw'", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_killIsL3() {
        assertThat(analyzer.analyze("KILL QUERY 12345", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_killTabSeparatedIsL3() {
        assertThat(analyzer.analyze("KILL\tQUERY 12345", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_renameIsL3() {
        assertThat(analyzer.analyze("RENAME TABLE t TO t2", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_submitTaskIsL3() {
        assertThat(analyzer.analyze("SUBMIT TASK AS CREATE TABLE t AS SELECT 1", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_cancelTaskIsL3() {
        assertThat(analyzer.analyze("CANCEL TASK 123", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_insertOverwriteIsL3() {
        assertThat(analyzer.analyze("INSERT OVERWRITE t SELECT * FROM s", Category.QUERY, "starrocks").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_unrecognizedFallbackIsL3() {
        // Any unrecognised StarRocks statement must default to L3, not null.
        var result = analyzer.analyze("RESUME ROUTINE LOAD FOR db.label", Category.QUERY, "starrocks");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void starrocks_doesNotApplyDorisRules() {
        var result = analyzer.analyze("DECOMMISSION BACKEND 'host:9050'", Category.QUERY, "starrocks");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
    }

    // --- Trino risk classification ---
    @Test
    void trino_selectIsL1() {
        assertThat(analyzer.analyze("SELECT * FROM t", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    @Test
    void trino_showTablesIsL1() {
        assertThat(analyzer.analyze("SHOW TABLES", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    @Test
    void trino_describeIsL1() {
        assertThat(analyzer.analyze("DESCRIBE t", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    @Test
    void trino_explainIsL1() {
        assertThat(analyzer.analyze("EXPLAIN SELECT * FROM t", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    // L2
    @Test
    void trino_insertIsL2() {
        assertThat(analyzer.analyze("INSERT INTO t VALUES (1)", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    @Test
    void trino_createTableIsL2() {
        assertThat(analyzer.analyze("CREATE TABLE t (id INT)", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    @Test
    void trino_createViewIsL2() {
        assertThat(analyzer.analyze("CREATE VIEW v AS SELECT 1", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    @Test
    void trino_createMaterializedViewIsL2() {
        assertThat(analyzer.analyze("CREATE MATERIALIZED VIEW mv AS SELECT 1", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    @Test
    void trino_updateIsL2() {
        assertThat(analyzer.analyze("UPDATE t SET x = 1 WHERE id = 1", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    @Test
    void trino_deleteIsL2() {
        assertThat(analyzer.analyze("DELETE FROM t WHERE id = 1", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    // L3
    @Test
    void trino_dropIsL3() {
        assertThat(analyzer.analyze("DROP TABLE t", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void trino_truncateIsL3() {
        assertThat(analyzer.analyze("TRUNCATE TABLE t", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void trino_alterIsL3() {
        assertThat(analyzer.analyze("ALTER TABLE t ADD COLUMN x INT", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void trino_grantIsL3() {
        assertThat(analyzer.analyze("GRANT SELECT ON t TO user", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void trino_revokeIsL3() {
        assertThat(analyzer.analyze("REVOKE SELECT ON t FROM user", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void trino_createUserIsL3() {
        assertThat(analyzer.analyze("CREATE USER alice", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void trino_createRoleIsL3() {
        assertThat(analyzer.analyze("CREATE ROLE admin", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void trino_callIsL3() {
        assertThat(analyzer.analyze("CALL system.runtime.kill_query('query_id')", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void trino_setSessionIsL3() {
        assertThat(analyzer.analyze("SET SESSION join_distribution = 'AUTOMATIC'", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void trino_resetSessionIsL3() {
        assertThat(analyzer.analyze("RESET SESSION join_distribution", Category.QUERY, "trino").riskLevel()).isEqualTo(RiskLevel.L3);
    }

    // --- TiDB risk classification ---

    // L1: read-only introspection
    @Test
    void tidb_show_placement_is_l1() {
        assertThat(analyzer.analyze("SHOW PLACEMENT FOR DATABASE analytics", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    @Test
    void tidb_show_table_regions_is_l1() {
        assertThat(analyzer.analyze("SHOW TABLE t1 REGIONS", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    @Test
    void tidb_show_split_regions_is_l1() {
        assertThat(analyzer.analyze("SHOW SPLIT REGIONS", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    @Test
    void tidb_show_stats_is_l1() {
        assertThat(analyzer.analyze("SHOW STATS_HEALTHY", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    @Test
    void tidb_show_generic_is_l1() {
        assertThat(analyzer.analyze("SHOW DATABASES", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    @Test
    void tidb_admin_show_ddl_is_l1() {
        assertThat(analyzer.analyze("ADMIN SHOW DDL", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    @Test
    void tidb_admin_show_ddl_jobs_is_l1() {
        assertThat(analyzer.analyze("ADMIN SHOW DDL JOBS", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L1);
    }

    // L2: bounded write or heavy read
    @Test
    void tidb_split_table_is_l2() {
        assertThat(analyzer.analyze("SPLIT TABLE t1 BETWEEN (0) AND (1000) REGIONS 8", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    @Test
    void tidb_recover_table_is_l2() {
        assertThat(analyzer.analyze("RECOVER TABLE t1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    @Test
    void tidb_alter_table_compact_is_l2() {
        assertThat(analyzer.analyze("ALTER TABLE t1 COMPACT", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    @Test
    void tidb_admin_check_table_is_l2() {
        assertThat(analyzer.analyze("ADMIN CHECK TABLE t1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L2);
    }

    // L3: destructive or cluster-affecting
    @Test
    void tidb_admin_cancel_ddl_is_l3() {
        assertThat(analyzer.analyze("ADMIN CANCEL DDL JOBS 42", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_admin_pause_ddl_is_l3() {
        assertThat(analyzer.analyze("ADMIN PAUSE DDL JOBS 42", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_admin_resume_ddl_is_l3() {
        assertThat(analyzer.analyze("ADMIN RESUME DDL JOBS 42", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_admin_unrecognized_is_l3() {
        assertThat(analyzer.analyze("ADMIN RECOVER INDEX t1 idx_a", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_backup_database_is_l3() {
        assertThat(analyzer.analyze("BACKUP DATABASE analytics TO 's3://x/y'", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_restore_database_is_l3() {
        assertThat(analyzer.analyze("RESTORE DATABASE analytics FROM 's3://x/y'", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_import_into_is_l3() {
        assertThat(analyzer.analyze("IMPORT INTO t1 FROM 's3://x/data.csv'", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_load_data_infile_is_l3() {
        assertThat(analyzer.analyze("LOAD DATA INFILE '/tmp/x.csv' INTO TABLE t1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_flashback_cluster_is_l3() {
        assertThat(analyzer.analyze("FLASHBACK CLUSTER TO TIMESTAMP '2026-01-01 00:00:00'", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_flashback_database_is_l3() {
        assertThat(analyzer.analyze("FLASHBACK DATABASE analytics", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_flashback_table_is_l3() {
        assertThat(analyzer.analyze("FLASHBACK TABLE analytics.t1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_alter_placement_policy_is_l3() {
        assertThat(analyzer.analyze("ALTER PLACEMENT POLICY p FOLLOWERS=3", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_create_placement_policy_is_l3() {
        assertThat(analyzer.analyze("CREATE PLACEMENT POLICY p FOLLOWERS=2", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_drop_placement_policy_is_l3() {
        assertThat(analyzer.analyze("DROP PLACEMENT POLICY p", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_kill_tidb_is_l3() {
        assertThat(analyzer.analyze("KILL TIDB 12345", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_set_global_is_l3() {
        assertThat(analyzer.analyze("SET GLOBAL tidb_gc_life_time = '24h'", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_session_set_is_not_l3() {
        assertThat(analyzer.analyze("SET tidb_isolation_read_engines = 'tikv'", Category.QUERY, "tidb").riskLevel()).isNotEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_batch_on_insert_is_l3() {
        assertThat(analyzer.analyze("BATCH ON id LIMIT 1000 INSERT INTO t2 SELECT * FROM t1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_batch_on_update_is_l3() {
        assertThat(analyzer.analyze("BATCH ON id LIMIT 1000 UPDATE t1 SET v = v + 1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_batch_on_delete_is_l3() {
        assertThat(analyzer.analyze("BATCH ON id LIMIT 1000 DELETE FROM t1 WHERE v < 0", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }

    // Standard SQL falls through to Calcite generic
    @Test
    void tidb_select_is_l1() {
        assertThat(analyzer.analyze("SELECT * FROM t1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L1);
    }
    @Test
    void tidb_insert_is_l2() {
        assertThat(analyzer.analyze("INSERT INTO t1 VALUES (1)", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    @Test
    void tidb_update_with_where_is_l2() {
        assertThat(analyzer.analyze("UPDATE t1 SET v=1 WHERE id=1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    @Test
    void tidb_delete_with_where_is_l2() {
        assertThat(analyzer.analyze("DELETE FROM t1 WHERE id=1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L2);
    }
    @Test
    void tidb_drop_table_is_l3() {
        assertThat(analyzer.analyze("DROP TABLE t1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_truncate_is_l3() {
        assertThat(analyzer.analyze("TRUNCATE TABLE t1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_alter_is_l3() {
        assertThat(analyzer.analyze("ALTER TABLE t1 ADD COLUMN c INT", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
    @Test
    void tidb_grant_is_l3() {
        assertThat(analyzer.analyze("GRANT SELECT ON *.* TO u@'%'", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
    }
}
