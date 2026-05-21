package com.datatalk.application.sql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SqlExecutionPlannerTest {

    private final SqlExecutionPlanner planner = new SqlExecutionPlanner();

    @Test
    void rewrites_consecutive_same_prefix_insert_values_statements() {
        List<SqlExecutionPlanner.ExecutionUnit> units = planner.plan(List.of(
            "INSERT INTO t(id, name) VALUES (1, 'a')",
            "INSERT INTO t(id, name) VALUES (2, 'b')",
            "SELECT id, name FROM t ORDER BY id"
        ));

        assertThat(units).hasSize(2);
        assertThat(units.get(0)).isInstanceOfSatisfying(SqlExecutionPlanner.DmlBatch.class, unit -> {
            assertThat(unit.startIndex()).isEqualTo(1);
            assertThat(unit.endIndex()).isEqualTo(2);
            assertThat(unit.statementTexts()).containsExactly(
                "INSERT INTO t(id, name) VALUES (1, 'a')",
                "INSERT INTO t(id, name) VALUES (2, 'b')"
            );
            assertThat(unit.rewrittenStatement()).hasValue(
                "INSERT INTO t(id, name) VALUES (1, 'a'), (2, 'b')"
            );
        });
        assertThat(units.get(1)).isInstanceOfSatisfying(SqlExecutionPlanner.SingleStatement.class, unit -> {
            assertThat(unit.statementIndex()).isEqualTo(3);
            assertThat(unit.statementText()).isEqualTo("SELECT id, name FROM t ORDER BY id");
        });
    }

    @Test
    void keeps_insert_select_as_jdbc_batch_candidate_without_rewrite() {
        List<SqlExecutionPlanner.ExecutionUnit> units = planner.plan(List.of(
            "INSERT INTO archive SELECT * FROM live WHERE id = 1",
            "INSERT INTO archive SELECT * FROM live WHERE id = 2"
        ));

        assertThat(units).hasSize(1);
        assertThat(units.get(0)).isInstanceOfSatisfying(SqlExecutionPlanner.DmlBatch.class, unit -> {
            assertThat(unit.startIndex()).isEqualTo(1);
            assertThat(unit.endIndex()).isEqualTo(2);
            assertThat(unit.rewrittenStatement()).isEmpty();
            assertThat(unit.statementTexts()).containsExactly(
                "INSERT INTO archive SELECT * FROM live WHERE id = 1",
                "INSERT INTO archive SELECT * FROM live WHERE id = 2"
            );
        });
    }

    @Test
    void select_statements_break_dml_batches_to_preserve_result_ordering() {
        List<SqlExecutionPlanner.ExecutionUnit> units = planner.plan(List.of(
            "UPDATE t SET name = 'a' WHERE id = 1",
            "SELECT name FROM t WHERE id = 1",
            "DELETE FROM t WHERE id = 2",
            "UPDATE t SET name = 'c' WHERE id = 3"
        ));

        assertThat(units).hasSize(3);
        assertThat(units.get(0)).isInstanceOfSatisfying(SqlExecutionPlanner.SingleStatement.class, unit -> {
            assertThat(unit.statementIndex()).isEqualTo(1);
        });
        assertThat(units.get(1)).isInstanceOfSatisfying(SqlExecutionPlanner.SingleStatement.class, unit -> {
            assertThat(unit.statementIndex()).isEqualTo(2);
        });
        assertThat(units.get(2)).isInstanceOfSatisfying(SqlExecutionPlanner.DmlBatch.class, unit -> {
            assertThat(unit.startIndex()).isEqualTo(3);
            assertThat(unit.endIndex()).isEqualTo(4);
            assertThat(unit.rewrittenStatement()).isEmpty();
        });
    }
}
