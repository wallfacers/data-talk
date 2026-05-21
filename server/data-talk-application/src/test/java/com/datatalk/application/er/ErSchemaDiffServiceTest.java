package com.datatalk.application.er;

import com.datatalk.domain.er.ErColumnMeta;
import com.datatalk.domain.er.ErDesignerColumn;
import com.datatalk.domain.er.ErDesignerIndex;
import com.datatalk.domain.er.ErDesignerPayload;
import com.datatalk.domain.er.ErDesignerRelation;
import com.datatalk.domain.er.ErDesignerTable;
import com.datatalk.domain.er.ErDesignerUnique;
import com.datatalk.domain.er.ErGraph;
import com.datatalk.domain.er.ErRelation;
import com.datatalk.domain.er.ErSchemaDiff;
import com.datatalk.domain.er.ErTableMeta;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErSchemaDiffServiceTest {

    @Test
    void emptyTargetReturnsAllDraftTablesAsAdded() {
        var service = new ErSchemaDiffService(new StubDiscovery(new ErGraph(List.of(), List.of(), "empty", List.of())));
        var draftTable = table("t1", "users", List.of(), List.of(), List.of());
        var draft = payload(List.of(draftTable), List.of());

        var diffs = service.diff(draft, "c1");

        assertThat(diffs).containsExactly(new ErSchemaDiff.TableAdded(draftTable));
    }

    @Test
    void detectsColumnAddsDropsAndTypeChanges() {
        var realCols = List.of(
            new ErColumnMeta("id", "BIGINT", false, true, false, true, null, null),
            new ErColumnMeta("legacy_field", "VARCHAR(50)", true, false, false, false, null, null),
            new ErColumnMeta("amount", "DECIMAL(10, 2)", false, false, false, false, null, null)
        );
        var realTable = new ErTableMeta("users", null, realCols, List.of());
        var service = new ErSchemaDiffService(new StubDiscovery(new ErGraph(List.of(realTable), List.of(), "x", List.of())));
        var draftCols = List.of(
            column("c1", "id", "BIGINT"),
            column("c2", "email", "VARCHAR(255)"),
            column("c3", "amount", "DECIMAL(12,2)")
        );
        var draft = payload(List.of(table("t1", "users", draftCols, List.of(), List.of())), List.of());

        var diffs = service.diff(draft, "c1");

        assertThat(diffs).anySatisfy(d -> assertThat(d).isEqualTo(new ErSchemaDiff.ColumnAdded("users", column("c2", "email", "VARCHAR(255)"))));
        assertThat(diffs).anySatisfy(d -> assertThat(d).isEqualTo(new ErSchemaDiff.ColumnDropped("users", "legacy_field")));
        assertThat(diffs).anySatisfy(d -> assertThat(d).isEqualTo(new ErSchemaDiff.ColumnTypeChanged("users", "amount", "DECIMAL(10, 2)", "DECIMAL(12,2)")));
    }

    @Test
    void detectsDroppedTables() {
        var service = new ErSchemaDiffService(new StubDiscovery(new ErGraph(
            List.of(new ErTableMeta("old_orders", null, List.of(), List.of())),
            List.of(), "x", List.of())));
        var draft = payload(List.of(), List.of());

        assertThat(service.diff(draft, "c1")).containsExactly(new ErSchemaDiff.TableDropped("old_orders"));
    }

    @Test
    void resolvesDesignerRelationIdsAndDetectsNewForeignKey() {
        var users = new ErTableMeta("users", null, List.of(new ErColumnMeta("id", "BIGINT", false, true, false, true, null, null)), List.of());
        var orders = new ErTableMeta("orders", null, List.of(new ErColumnMeta("user_id", "BIGINT", false, false, false, false, null, null)), List.of());
        var service = new ErSchemaDiffService(new StubDiscovery(new ErGraph(List.of(users, orders), List.of(), "x", List.of())));
        var draftUsers = table("t-users", "users", List.of(column("c-id", "id", "BIGINT")), List.of(), List.of());
        var draftOrders = table("t-orders", "orders", List.of(column("c-user", "user_id", "BIGINT")), List.of(), List.of());
        var rel = new ErDesignerRelation("r1", "t-orders", "c-user", "t-users", "c-id", "many_to_one", "database_fk");
        var draft = payload(List.of(draftUsers, draftOrders), List.of(rel));

        var diffs = service.diff(draft, "c1");

        assertThat(diffs).contains(new ErSchemaDiff.ConstraintAdded(
            new ErDesignerRelation("r1", "orders", "user_id", "users", "id", "many_to_one", "database_fk")));
    }

    @Test
    void detectsNewIndexesAndUniques() {
        var realTable = new ErTableMeta("orders", null, List.of(new ErColumnMeta("created_at", "TIMESTAMP", true, false, false, false, null, null)), List.of());
        var service = new ErSchemaDiffService(new StubDiscovery(new ErGraph(List.of(realTable), List.of(), "x", List.of())));
        var draftTable = table(
            "t1", "orders", List.of(column("c1", "created_at", "TIMESTAMP")),
            List.of(new ErDesignerIndex("idx_orders_created_at", List.of("created_at"))),
            List.of(new ErDesignerUnique(List.of("created_at")))
        );

        var diffs = service.diff(payload(List.of(draftTable), List.of()), "c1");

        assertThat(diffs).contains(new ErSchemaDiff.IndexAdded("orders", new ErDesignerIndex("idx_orders_created_at", List.of("created_at"))));
        assertThat(diffs).contains(new ErSchemaDiff.ConstraintAdded(new ErSchemaDiff.UniqueConstraint("orders", List.of("created_at"))));
    }

    private static ErDesignerPayload payload(List<ErDesignerTable> tables, List<ErDesignerRelation> relations) {
        return new ErDesignerPayload("mysql", "c1", null, null, tables, relations);
    }

    private static ErDesignerTable table(
        String id,
        String name,
        List<ErDesignerColumn> columns,
        List<ErDesignerIndex> indexes,
        List<ErDesignerUnique> uniques
    ) {
        return new ErDesignerTable(id, name, null, columns, indexes, uniques);
    }

    private static ErDesignerColumn column(String id, String name, String type) {
        return new ErDesignerColumn(id, name, type, true, false, false, null, null);
    }

    private record StubDiscovery(ErGraph graph) implements ErRelationDiscoveryService {
        @Override
        public ErGraph discover(String connectionId, List<String> tables, int neighborDepth) {
            return graph;
        }
    }
}
