package com.datatalk.application.sql;

import com.datatalk.domain.action.RiskLevel;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class KingbaseRiskClassifierTest {
    private final CalciteSqlRiskAnalyzer analyzer = new CalciteSqlRiskAnalyzer(
        (kind, sql) -> java.util.Arrays.stream(sql.split(";"))
            .map(String::trim)
            .filter(part -> !part.isEmpty())
            .toList()
    );

    // Channel 1 hits — L3
    @Test void dropTableSysUsersIsL3() { assertThat(analyzer.classifyKingbaseSpecific("DROP TABLE SYS_USERS")).hasValue(RiskLevel.L3); }
    @Test void alterTableSysAuditTrailIsL3() { assertThat(analyzer.classifyKingbaseSpecific("ALTER TABLE SYS_AUDIT_TRAIL ADD COLUMN x INT")).hasValue(RiskLevel.L3); }
    @Test void truncateTableSysLogIsL3() { assertThat(analyzer.classifyKingbaseSpecific("TRUNCATE TABLE SYS_LOG")).hasValue(RiskLevel.L3); }
    @Test void dropTableSyscrtPackagesIsL3() { assertThat(analyzer.classifyKingbaseSpecific("DROP TABLE SYSCRT_PACKAGES")).hasValue(RiskLevel.L3); }
    @Test void alterTableSysauditRulesIsL3() { assertThat(analyzer.classifyKingbaseSpecific("ALTER TABLE SYSAUDIT_RULES ADD COLUMN y INT")).hasValue(RiskLevel.L3); }
    @Test void selectSysKillIsL3() { assertThat(analyzer.classifyKingbaseSpecific("SELECT SYS_KILL(123)")).hasValue(RiskLevel.L3); }
    @Test void flashbackTableIsL3() { assertThat(analyzer.classifyKingbaseSpecific("FLASHBACK TABLE my_table TO TIMESTAMP '2025-01-01'")).hasValue(RiskLevel.L3); }
    @Test void leadingWhitespaceStillMatchesChannel1() { assertThat(analyzer.classifyKingbaseSpecific("   DROP TABLE SYS_USERS")).hasValue(RiskLevel.L3); }

    // Channel 1 boundaries — NOT matched
    @Test void updateMySysUserNotMatched() { assertThat(analyzer.classifyKingbaseSpecific("UPDATE my_sys_user SET x = 1")).isEmpty(); }
    @Test void selectFromSysUsersViewNotMatched() { assertThat(analyzer.classifyKingbaseSpecific("SELECT * FROM sys_users_view")).isEmpty(); }
    @Test void insertIntoFlashbackLogNotMatched() { assertThat(analyzer.classifyKingbaseSpecific("INSERT INTO flashback_log VALUES (1)")).isEmpty(); }
    @Test void selectColumnSysKillAuditNotMatched() { assertThat(analyzer.classifyKingbaseSpecific("SELECT sys_kill_audit FROM x")).isEmpty(); }

    // Channel 2 hits
    @Test void kbbackupTriggersUnsupported() { assertThat(analyzer.detectKingbaseUnsupported("KBBACKUP DATABASE my_db FILE='/backup/...'")).hasValue(KingbaseUnsupportedReason.KB_BACKUP_RESTORE_CLI); }
    @Test void kbrestoreTriggersUnsupported() { assertThat(analyzer.detectKingbaseUnsupported("KBRESTORE DATABASE my_db FROM '/backup/...'")).hasValue(KingbaseUnsupportedReason.KB_BACKUP_RESTORE_CLI); }
    @Test void declareBlockTriggersUnsupported() { assertThat(analyzer.detectKingbaseUnsupported("DECLARE v_x INT := 1; BEGIN NULL; END;")).hasValue(KingbaseUnsupportedReason.ORACLE_PLSQL_BLOCK); }
    @Test void beginBlockTriggersUnsupported() { assertThat(analyzer.detectKingbaseUnsupported("BEGIN DBMS_OUTPUT.PUT_LINE('x'); END;")).hasValue(KingbaseUnsupportedReason.ORACLE_PLSQL_BLOCK); }

    // Channel 2 boundaries
    @Test void kbbackupInfoFunctionNotTriggered() { assertThat(analyzer.detectKingbaseUnsupported("SELECT KBBACKUP_INFO()")).isEmpty(); }
    @Test void insertIntoBeginLogNotTriggered() { assertThat(analyzer.detectKingbaseUnsupported("INSERT INTO begin_log VALUES (1)")).isEmpty(); }
    @Test void insertIntoDeclareAuditNotTriggered() { assertThat(analyzer.detectKingbaseUnsupported("INSERT INTO declare_audit VALUES (1)")).isEmpty(); }
    @Test void pgStyleDoBlockNotTriggered() { assertThat(analyzer.detectKingbaseUnsupported("DO $$ BEGIN RAISE NOTICE 'x'; END $$;")).isEmpty(); }
}
