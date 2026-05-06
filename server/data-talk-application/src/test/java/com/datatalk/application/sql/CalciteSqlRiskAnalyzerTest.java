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
        assertThat(result.reason()).isEqualTo("oracle_truncate");
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
}
