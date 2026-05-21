package com.datatalk.application.sql;

import com.datatalk.domain.action.CallerKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BulkSqlGuardTest {

    private BulkSqlGuard guard;

    @BeforeEach
    void setUp() {
        // In-test naive splitter: split on `;`. The real dialect-aware splitters live in
        // infrastructure; the guard's logic does not depend on dialect specifics, only on
        // first-keyword detection per statement.
        SqlStatementSplitters splitters = (connectionKind, sql) -> {
            if (sql == null || sql.isBlank()) return List.of();
            return Arrays.stream(sql.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        };
        guard = new BulkSqlGuard(splitters);
    }

    @Test
    void userPath_unconditionallyPasses_even_for_100kbWrite() {
        String huge = "INSERT INTO td_orders VALUES (1);\n".repeat(2000); // ~60KB
        BulkSqlVerdict v = guard.evaluate(huge, CallerKind.USER, null, "conn-1", "mysql");
        assertThat(v.shouldReject()).isFalse();
    }

    @Test
    void aiPath_pureSelect_10kb_passes_becausePreconditionNotMet() {
        String select = "WITH cte AS (SELECT * FROM big_table_with_many_columns WHERE col = "
            + "'x'".repeat(500) + ") SELECT a.*, b.* FROM cte a JOIN cte b ON a.id = b.id";
        assertThat(select.getBytes().length).isGreaterThan(1000);
        BulkSqlVerdict v = guard.evaluate(select, CallerKind.AI, null, "conn-1", "mysql");
        assertThat(v.shouldReject()).isFalse();
    }

    @Test
    void aiPath_pureSelect_10kb_passes_evenWithSourceFileId_becauseNoWriteStatement() {
        String select = "SELECT * FROM t WHERE col IN (" + "1,".repeat(2500) + "0)";
        assertThat(select.getBytes().length).isGreaterThan(4096);
        // pure SELECT must pass even with sourceFileId, because the precondition
        // "contains a write statement" is unmet — there is no import_data alternative for SELECT
        BulkSqlVerdict v = guard.evaluate(select, CallerKind.AI, "f-1", "conn-1", "mysql");
        assertThat(v.shouldReject()).isFalse();
    }

    @Test
    void aiPath_selectPlusSmallInsert_triggersSize_whenOverThreshold() {
        String big = "SELECT * FROM t WHERE col IN (" + "1,".repeat(2500) + "0); INSERT INTO target VALUES (1)";
        assertThat(big.getBytes().length).isGreaterThan(4096);
        BulkSqlVerdict v = guard.evaluate(big, CallerKind.AI, null, "conn-1", "mysql");
        assertThat(v.shouldReject()).isTrue();
        assertThat(v.reason()).isEqualTo(BulkSqlGuard.REASON_SIZE);
    }

    @Test
    void aiPath_singleInsertOverSizeThreshold_triggersSize() {
        // single INSERT with many VALUES tuples — count = 1 but bytes > 4096
        StringBuilder sb = new StringBuilder("INSERT INTO td_orders VALUES ");
        for (int i = 0; i < 100; i++) {
            if (i > 0) sb.append(",");
            sb.append("(").append(i).append(", 'order_").append(i).append("_with_padding_to_inflate_bytes')");
        }
        String sql = sb.toString();
        assertThat(sql.getBytes().length).isGreaterThan(4096);
        BulkSqlVerdict v = guard.evaluate(sql, CallerKind.AI, null, "conn-1", "mysql");
        assertThat(v.shouldReject()).isTrue();
        assertThat(v.reason()).isEqualTo(BulkSqlGuard.REASON_SIZE);
        Map<String, Object> nextParams = v.nextActionParams();
        assertThat(nextParams).containsKey("target");
        assertThat(((Map<?, ?>) nextParams.get("target")).get("tableName")).isEqualTo("td_orders");
    }

    @Test
    void aiPath_21SmallIndependentInserts_underSizeButOverCount_triggersCount() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 21; i++) {
            sb.append("INSERT INTO td_orders VALUES (").append(i).append(");\n");
        }
        String sql = sb.toString();
        assertThat(sql.getBytes().length).isLessThan(4096);
        BulkSqlVerdict v = guard.evaluate(sql, CallerKind.AI, null, "conn-1", "mysql");
        assertThat(v.shouldReject()).isTrue();
        assertThat(v.reason()).isEqualTo(BulkSqlGuard.REASON_INSERT_COUNT);
    }

    @Test
    void aiPath_fiveInsertsWithSourceFileId_triggersOrigin_evenUnderOtherThresholds() {
        String sql = "INSERT INTO td_orders VALUES (1);\n".repeat(5);
        assertThat(sql.getBytes().length).isLessThan(4096);
        BulkSqlVerdict v = guard.evaluate(sql, CallerKind.AI, "f-123", "conn-1", "mysql");
        assertThat(v.shouldReject()).isTrue();
        assertThat(v.reason()).isEqualTo(BulkSqlGuard.REASON_FILE_ORIGIN);
        Map<String, Object> nextParams = v.nextActionParams();
        assertThat(((Map<?, ?>) nextParams.get("source")).get("fileId")).isEqualTo("f-123");
    }

    @Test
    void aiPath_pureSelectWithSourceFileId_passes() {
        // legitimate scenario: user uploads a SELECT-only file, AI reads + executes
        String sql = "SELECT * FROM orders ORDER BY id LIMIT 100";
        BulkSqlVerdict v = guard.evaluate(sql, CallerKind.AI, "f-select", "conn-1", "mysql");
        assertThat(v.shouldReject()).isFalse();
    }

    @Test
    void aiPath_smallUpdate_underAllThresholds_passes() {
        String sql = "UPDATE td_orders SET status = 'shipped' WHERE id = 42";
        BulkSqlVerdict v = guard.evaluate(sql, CallerKind.AI, null, "conn-1", "mysql");
        assertThat(v.shouldReject()).isFalse();
    }

    @Test
    void parseTargetTable_singleInsert_returnsTableName() {
        String table = guard.parseTargetTable("INSERT INTO td_orders VALUES (1)");
        assertThat(table).isEqualTo("td_orders");
    }

    @Test
    void parseTargetTable_caseInsensitive_returnsAsWritten() {
        String table = guard.parseTargetTable("insert into TD_ORDERS values (1)");
        assertThat(table).isEqualTo("TD_ORDERS");
    }

    @Test
    void parseTargetTable_multipleDistinctTables_returnsNull() {
        String sql = "INSERT INTO t1 VALUES (1); INSERT INTO t2 VALUES (2);";
        String table = guard.parseTargetTable(sql);
        assertThat(table).isNull();
    }

    @Test
    void parseTargetTable_quotedIdentifiers_areStripped() {
        String table = guard.parseTargetTable("INSERT INTO `td_orders` VALUES (1)");
        assertThat(table).isEqualTo("td_orders");
    }

    @Test
    void parseTargetTable_noInsert_returnsNull() {
        String table = guard.parseTargetTable("SELECT * FROM t");
        assertThat(table).isNull();
    }

    @Test
    void rejectResponse_includesConnectionIdInTarget() {
        String sql = "INSERT INTO td_orders VALUES (1);\n".repeat(5);
        BulkSqlVerdict v = guard.evaluate(sql, CallerKind.AI, "f-1", "conn-xyz", "mysql");
        assertThat(v.shouldReject()).isTrue();
        Map<String, Object> nextParams = v.nextActionParams();
        assertThat(((Map<?, ?>) nextParams.get("target")).get("connectionId")).isEqualTo("conn-xyz");
    }
}
