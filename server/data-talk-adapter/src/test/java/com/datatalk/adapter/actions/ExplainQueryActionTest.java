package com.datatalk.adapter.actions;

import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExplainQueryActionTest {

    @Test
    void serializePlan_includesAllFields() {
        ExplainNode child = new ExplainNode(
            "INDEX SCAN", "orders", ScanType.INDEX_RANGE,
            100, 0.5, "Using index", List.of()
        );
        ExplainPlan plan = new ExplainPlan(
            "mysql", "EXPLAIN SELECT * FROM orders",
            List.of(child),
            42.0,
            List.of("Using temporary")
        );

        var result = ExplainQueryAction.serializePlan(plan);

        assertThat(result.get("dialect")).isEqualTo("mysql");
        assertThat(result.get("rawText")).isEqualTo("EXPLAIN SELECT * FROM orders");
        assertThat(result.get("totalCostEstimate")).isEqualTo(42.0);

        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) result.get("warnings");
        assertThat(warnings).containsExactly("Using temporary");

        @SuppressWarnings("unchecked")
        List<Object> nodes = (List<Object>) result.get("nodes");
        assertThat(nodes).hasSize(1);
    }

    @Test
    void serializeNode_omitsNullTableAndExtra() {
        ExplainNode node = new ExplainNode(
            "SEQ SCAN", null, ScanType.FULL_SCAN,
            500, 10.0, null, List.of()
        );

        var result = ExplainQueryAction.serializeNode(node);

        assertThat(result).containsEntry("operation", "SEQ SCAN");
        assertThat(result).containsEntry("scanType", "FULL_SCAN");
        assertThat(result).containsEntry("rows", 500L);
        assertThat(result).containsEntry("cost", 10.0);
        assertThat(result).doesNotContainKey("table");
        assertThat(result).doesNotContainKey("extra");
        assertThat(result).doesNotContainKey("children");
    }

    @Test
    void serializeNode_includesChildren() {
        ExplainNode child = new ExplainNode(
            "INDEX SCAN", "items", ScanType.INDEX_SCAN,
            50, 1.0, null, List.of()
        );
        ExplainNode parent = new ExplainNode(
            "NESTED LOOP", null, ScanType.OTHER,
            100, 5.0, null, List.of(child)
        );

        var result = ExplainQueryAction.serializeNode(parent);

        assertThat(result).containsKey("children");
        @SuppressWarnings("unchecked")
        List<Object> children = (List<Object>) result.get("children");
        assertThat(children).hasSize(1);
    }
}
