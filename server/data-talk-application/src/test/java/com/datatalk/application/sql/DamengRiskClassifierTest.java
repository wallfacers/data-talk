package com.datatalk.application.sql;

import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dameng dual-channel risk classifier tests:
 * - Channel 1: 5 anchored L3 admin command patterns
 * - Channel 2: 3 anchored dialect_unsupported patterns
 *
 * 29 test cases total.
 */
class DamengRiskClassifierTest {

    private final CalciteSqlRiskAnalyzer analyzer = new CalciteSqlRiskAnalyzer(
        (kind, sql) -> java.util.Arrays.stream(sql.split(";"))
            .map(String::trim)
            .filter(part -> !part.isEmpty())
            .toList()
    );

    // ====== Channel 1 (L3 admin_command) ======

    @Test void createTablespaceIsL3() {
        assertThat(channel1("CREATE TABLESPACE ts1 DATAFILE 'ts1.dbf' SIZE 100M"))
            .hasValue(RiskLevel.L3);
    }

    @Test void alterTablespaceIsL3() {
        assertThat(channel1("ALTER TABLESPACE ts1 ADD DATAFILE 'ts2.dbf'"))
            .hasValue(RiskLevel.L3);
    }

    @Test void dropTablespaceIsL3() {
        assertThat(channel1("DROP TABLESPACE ts1"))
            .hasValue(RiskLevel.L3);
    }

    @Test void alterUserIsL3() {
        assertThat(channel1("ALTER USER scott IDENTIFIED BY new_pw"))
            .hasValue(RiskLevel.L3);
    }

    @Test void dropRoleIsL3() {
        assertThat(channel1("DROP ROLE admin_role"))
            .hasValue(RiskLevel.L3);
    }

    @Test void grantIsL3() {
        assertThat(channel1("GRANT SELECT ON t1 TO scott"))
            .hasValue(RiskLevel.L3);
    }

    @Test void revokeIsL3() {
        assertThat(channel1("REVOKE SELECT ON t1 FROM scott"))
            .hasValue(RiskLevel.L3);
    }

    @Test void dropTableIsL3() {
        assertThat(channel1("DROP TABLE t1"))
            .hasValue(RiskLevel.L3);
    }

    @Test void dropViewIsL3() {
        assertThat(channel1("DROP VIEW v1"))
            .hasValue(RiskLevel.L3);
    }

    @Test void dropIndexIsL3() {
        assertThat(channel1("DROP INDEX idx1"))
            .hasValue(RiskLevel.L3);
    }

    @Test void dropSequenceIsL3() {
        assertThat(channel1("DROP SEQUENCE s1"))
            .hasValue(RiskLevel.L3);
    }

    @Test void dropSynonymIsL3() {
        assertThat(channel1("DROP SYNONYM syn1"))
            .hasValue(RiskLevel.L3);
    }

    // ---- Channel 1 boundary cases (must NOT match) ----

    @Test void insertDropAuditIsNotL3() {
        assertThat(channel1("INSERT INTO drop_audit VALUES (1)"))
            .isEmpty();
    }

    @Test void selectFromTablespaceHistoryIsNotL3() {
        assertThat(channel1("SELECT * FROM tablespace_history"))
            .isEmpty();
    }

    @Test void insertIntoGrantLogIsNotL3() {
        assertThat(channel1("INSERT INTO grant_log VALUES (1)"))
            .isEmpty();
    }

    @Test void selectFromAllRolesIsNotL3() {
        assertThat(channel1("SELECT role_name FROM all_roles"))
            .isEmpty();
    }

    @Test void insertIntoDropAuditIsNotL3() {
        assertThat(channel1("INSERT INTO drop_audit_log VALUES (1)"))
            .isEmpty();
    }

    @Test void leadingWhitespaceStillMatchesChannel1() {
        assertThat(channel1("   CREATE TABLESPACE ts1 DATAFILE 'x.dbf' SIZE 100M"))
            .hasValue(RiskLevel.L3);
    }

    // ====== Channel 2 (dialect_unsupported) ======

    // ---- PL/SQL BLOCK ----
    @Test void declareBlockIsUnsupported() {
        assertThat(channel2("DECLARE v_x INT; BEGIN v_x := 1; END;"))
            .hasValue(CalciteSqlRiskAnalyzer.DamengUnsupportedReason.PLSQL_BLOCK);
    }

    @Test void beginBlockIsUnsupported() {
        assertThat(channel2("BEGIN DBMS_OUTPUT.PUT_LINE('x'); END;"))
            .hasValue(CalciteSqlRiskAnalyzer.DamengUnsupportedReason.PLSQL_BLOCK);
    }

    @Test void leadingWhitespacePlsqlStillMatches() {
        assertThat(channel2("   BEGIN NULL; END;"))
            .hasValue(CalciteSqlRiskAnalyzer.DamengUnsupportedReason.PLSQL_BLOCK);
    }

    @Test void insertBeginLogIsNotPlsql() {
        assertThat(channel2("INSERT INTO begin_log VALUES (1)"))
            .isEmpty();
    }

    // ---- PROCEDURE / FUNCTION / TRIGGER / PACKAGE DDL ----
    @Test void createProcedureIsUnsupported() {
        assertThat(channel2("CREATE PROCEDURE p1 AS BEGIN NULL; END;"))
            .hasValue(CalciteSqlRiskAnalyzer.DamengUnsupportedReason.PROCEDURE_DDL);
    }

    @Test void createOrReplaceFunctionIsUnsupported() {
        assertThat(channel2("CREATE OR REPLACE FUNCTION f1 RETURN INT AS BEGIN RETURN 1; END;"))
            .hasValue(CalciteSqlRiskAnalyzer.DamengUnsupportedReason.PROCEDURE_DDL);
    }

    @Test void dropTriggerIsUnsupported() {
        assertThat(channel2("DROP TRIGGER t1"))
            .hasValue(CalciteSqlRiskAnalyzer.DamengUnsupportedReason.PROCEDURE_DDL);
    }

    @Test void alterPackageBodyIsUnsupported() {
        assertThat(channel2("ALTER PACKAGE BODY pkg COMPILE"))
            .hasValue(CalciteSqlRiskAnalyzer.DamengUnsupportedReason.PROCEDURE_DDL);
    }

    // ---- EXP / IMP ----
    @Test void expCommandIsUnsupported() {
        assertThat(channel2("EXP scott/tiger@dm FILE=demo.dmp"))
            .hasValue(CalciteSqlRiskAnalyzer.DamengUnsupportedReason.EXP_IMP_COMMAND);
    }

    @Test void impCommandIsUnsupported() {
        assertThat(channel2("IMP scott/tiger@dm FILE=demo.dmp"))
            .hasValue(CalciteSqlRiskAnalyzer.DamengUnsupportedReason.EXP_IMP_COMMAND);
    }

    @Test void selectExpFunctionIsNotMatched() {
        assertThat(channel2("SELECT EXP(2) FROM DUAL"))
            .isEmpty();
    }

    @Test void selectImpToDateIsNotMatched() {
        assertThat(channel2("SELECT IMP_TO_DATE('2026-01-01') FROM DUAL"))
            .isEmpty();
    }

    // ====== Helpers ======

    private java.util.Optional<RiskLevel> channel1(String sql) {
        return java.util.Optional.ofNullable(analyzer.classifyDamengSpecific(sql))
            .map(SqlRiskAnalysis::riskLevel);
    }

    private java.util.Optional<CalciteSqlRiskAnalyzer.DamengUnsupportedReason> channel2(String sql) {
        return analyzer.detectDamengUnsupported(sql);
    }
}
