package com.datatalk.application.er;

import com.datatalk.domain.er.ErColumnMeta;
import com.datatalk.domain.er.ErDesignerColumn;
import com.datatalk.domain.er.ErDesignerPayload;
import com.datatalk.domain.er.ErDesignerRelation;
import com.datatalk.domain.er.ErDesignerTable;
import com.datatalk.domain.er.ErGraph;
import com.datatalk.domain.er.ErRelation;
import com.datatalk.domain.er.ErTableMeta;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ErDesignerSyncServiceTest {

    @Test
    void mergePreservesNonSyncedDraftTablesAndReplacesSyncedOnes() {
        ErDesignerTable draftUsers = new ErDesignerTable(
            "t_users", "users", null,
            List.of(new ErDesignerColumn("c_users_id", "id", "BIGINT", false, true, true, null, null)),
            List.of(), List.of()
        );
        ErDesignerTable draftDraftOnly = new ErDesignerTable(
            "t_drafts", "drafts", null,
            List.of(new ErDesignerColumn("c_drafts_id", "id", "BIGINT", false, true, true, null, null)),
            List.of(), List.of()
        );
        ErDesignerPayload draft = new ErDesignerPayload(
            "h2", "conn-1", null, null,
            List.of(draftUsers, draftDraftOnly),
            List.of()
        );

        ErTableMeta realUsers = new ErTableMeta(
            "users", null,
            List.of(
                new ErColumnMeta("id", "BIGINT", false, true, false, false, null, null),
                new ErColumnMeta("email", "VARCHAR(255)", true, false, false, false, null, null)
            ),
            List.of()
        );
        ErGraph graph = new ErGraph(List.of(realUsers), List.of(), "1 table", List.of());

        ErDesignerPayload merged = ErDesignerSyncService.merge(draft, graph, Set.of("users"));

        assertThat(merged.tables()).extracting(ErDesignerTable::name).containsExactly("users", "drafts");
        ErDesignerTable mergedUsers = merged.tables().stream().filter(t -> "users".equals(t.name())).findFirst().orElseThrow();
        assertThat(mergedUsers.id()).isEqualTo("t_users");
        // Existing column id preserved when name matches; new column gets a derived id.
        assertThat(mergedUsers.columns())
            .extracting(ErDesignerColumn::id, ErDesignerColumn::name)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("c_users_id", "id"),
                org.assertj.core.groups.Tuple.tuple("c_users_email", "email")
            );
    }

    @Test
    void mergeReplacesDatabaseFkRelationsWithRealOnesForSyncedTables() {
        ErDesignerTable draftUsers = new ErDesignerTable(
            "t_users", "users", null,
            List.of(new ErDesignerColumn("c_users_id", "id", "BIGINT", false, true, true, null, null)),
            List.of(), List.of()
        );
        ErDesignerTable draftOrders = new ErDesignerTable(
            "t_orders", "orders", null,
            List.of(
                new ErDesignerColumn("c_orders_id", "id", "BIGINT", false, true, true, null, null),
                new ErDesignerColumn("c_orders_user", "user_id", "BIGINT", false, false, false, null, null)
            ),
            List.of(), List.of()
        );
        // A stale draft FK with the right column but rebuild required from real DB on sync.
        ErDesignerRelation staleFk = new ErDesignerRelation(
            "r_old", "t_orders", "c_orders_user", "t_users", "c_users_id", "many_to_one", "database_fk"
        );
        ErDesignerPayload draft = new ErDesignerPayload(
            "h2", "conn-1", null, null,
            List.of(draftUsers, draftOrders),
            List.of(staleFk)
        );

        ErTableMeta realUsers = new ErTableMeta(
            "users", null,
            List.of(new ErColumnMeta("id", "BIGINT", false, true, false, false, null, null)),
            List.of()
        );
        ErTableMeta realOrders = new ErTableMeta(
            "orders", null,
            List.of(
                new ErColumnMeta("id", "BIGINT", false, true, false, false, null, null),
                new ErColumnMeta("user_id", "BIGINT", false, false, true, false, null, null)
            ),
            List.of(new ErRelation("orders", "user_id", "users", "id", "many_to_one", "schema_fk"))
        );
        ErGraph graph = new ErGraph(List.of(realUsers, realOrders), List.of(), "2 tables", List.of());

        ErDesignerPayload merged = ErDesignerSyncService.merge(draft, graph, Set.of("orders", "users"));

        assertThat(merged.relations()).hasSize(1);
        ErDesignerRelation rebuilt = merged.relations().get(0);
        assertThat(rebuilt.fromTableId()).isEqualTo("t_orders");
        assertThat(rebuilt.fromColumnId()).isEqualTo("c_orders_user");
        assertThat(rebuilt.toTableId()).isEqualTo("t_users");
        assertThat(rebuilt.toColumnId()).isEqualTo("c_users_id");
        assertThat(rebuilt.constraintMethod()).isEqualTo("database_fk");
        // Old stale FK id was dropped; new id is derived from the real-DB FK.
        assertThat(rebuilt.id()).isNotEqualTo("r_old");
    }

    @Test
    void mergePreservesCommentRefRelationsThroughIdRemap() {
        ErDesignerTable users = new ErDesignerTable(
            "t_users", "users", null,
            List.of(new ErDesignerColumn("c_users_email", "email", "VARCHAR(255)", true, false, false, null, null)),
            List.of(), List.of()
        );
        ErDesignerTable orders = new ErDesignerTable(
            "t_orders", "orders", null,
            List.of(new ErDesignerColumn("c_orders_email", "user_email", "VARCHAR(255)", true, false, false, null, null)),
            List.of(), List.of()
        );
        ErDesignerRelation virtualRel = new ErDesignerRelation(
            "r_virt_1", "t_orders", "c_orders_email", "t_users", "c_users_email",
            "many_to_one", "comment_ref"
        );
        ErDesignerPayload draft = new ErDesignerPayload(
            "h2", "conn-1", null, null, List.of(users, orders), List.of(virtualRel)
        );

        ErTableMeta realUsers = new ErTableMeta(
            "users", null,
            List.of(new ErColumnMeta("email", "VARCHAR(320)", true, false, false, false, null, null)),
            List.of()
        );
        ErGraph graph = new ErGraph(List.of(realUsers), List.of(), "1 table", List.of());

        ErDesignerPayload merged = ErDesignerSyncService.merge(draft, graph, Set.of("users"));

        assertThat(merged.relations()).hasSize(1);
        ErDesignerRelation kept = merged.relations().get(0);
        assertThat(kept.id()).isEqualTo("r_virt_1");
        assertThat(kept.constraintMethod()).isEqualTo("comment_ref");
        // Column id mapping: synced users.email kept the old draft column id since name matches.
        assertThat(kept.toColumnId()).isEqualTo("c_users_email");
    }
}
