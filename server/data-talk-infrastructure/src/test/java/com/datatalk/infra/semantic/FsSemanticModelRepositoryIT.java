package com.datatalk.infra.semantic;

import com.datatalk.domain.semantic.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class FsSemanticModelRepositoryIT {

    @TempDir Path home;
    FsSemanticModelRepository repo;

    @BeforeEach
    void setUp() {
        repo = new FsSemanticModelRepository(home);
    }

    private SemanticModel ordersModel() {
        return new SemanticModel(
            "orders", 1, "Orders domain",
            List.of(new Entity("orders", "fact",
                new Entity.Physical(null, null, "orders"),
                List.of("id"), List.of(), "orders fact table")),
            List.of(new Dimension("status", "orders", "status", "categorical",
                null, "状态", "Status", "")),
            List.of(new Measure("gmv", "orders", "sum", "amount", null,
                "销售额", "GMV", "")),
            List.of(),
            new LinkedHashMap<>(),
            List.of(),
            Instant.parse("2026-05-01T00:00:00Z"),
            "test"
        );
    }

    private String ordersYaml() {
        return """
            name: orders
            version: 1
            description: Orders domain
            authored_by: test
            entities:
              - name: orders
                type: fact
                physical:
                  table: orders
                primary_key: [id]
                foreign_keys: []
                description: orders fact table
            dimensions:
              - name: status
                entity: orders
                expr: status
                type: categorical
                label_zh: 状态
                label_en: Status
                description: ''
            measures:
              - name: gmv
                entity: orders
                agg: sum
                expr: amount
                label_zh: 销售额
                label_en: GMV
                description: ''
            metrics: []
            literal_mappings: {}
            verified_query_refs: []
            last_modified: '2026-05-01T00:00:00Z'
            """;
    }

    @Test
    void listDomains_returns_empty_when_directory_missing() {
        assertThat(repo.listDomains("ghost")).isEmpty();
    }

    @Test
    void saveDomain_then_listDomains_returns_saved_domain() {
        repo.saveDomain("conn1", ordersModel(), ordersYaml());

        assertThat(repo.listDomains("conn1")).containsExactly("orders");
    }

    @Test
    void saveDomain_then_loadDomain_returns_parsed_model() {
        repo.saveDomain("conn1", ordersModel(), ordersYaml());

        Optional<SemanticModel> loaded = repo.loadDomain("conn1", "orders");

        assertThat(loaded).isPresent();
        assertThat(loaded.get().name()).isEqualTo("orders");
        assertThat(loaded.get().measures()).hasSize(1);
        assertThat(loaded.get().measures().get(0).name()).isEqualTo("gmv");
    }

    @Test
    void loadDomain_returns_empty_when_yaml_missing() {
        assertThat(repo.loadDomain("conn1", "orders")).isEmpty();
    }

    @Test
    void appendPatch_then_listPatches_roundtrips_AddLiteralMapping() {
        repo.saveDomain("conn1", ordersModel(), ordersYaml());
        PatchOp patch = new PatchOp.AddLiteralMapping("ADD_LITERAL_MAPPING",
            "status", Map.of("已完成", "COMPLETED"), "ai", Instant.now());

        repo.appendPatch("conn1", "orders", patch);

        List<PatchOp> patches = repo.listPatches("conn1", "orders");
        assertThat(patches).hasSize(1);
        assertThat(patches.get(0)).isInstanceOf(PatchOp.AddLiteralMapping.class);
        var alm = (PatchOp.AddLiteralMapping) patches.get(0);
        assertThat(alm.dimension()).isEqualTo("status");
        assertThat(alm.map()).containsEntry("已完成", "COMPLETED");
    }

    @Test
    void appendPatch_then_listPatches_roundtrips_AddVerifiedQuery() {
        repo.saveDomain("conn1", ordersModel(), ordersYaml());
        VerifiedQuery vq = new VerifiedQuery("vq_1", "GMV", "SELECT 1", "orders",
            3, Instant.now(), "user", Instant.now(), false);
        PatchOp patch = new PatchOp.AddVerifiedQuery("ADD_VERIFIED_QUERY", vq, "user", Instant.now());

        repo.appendPatch("conn1", "orders", patch);

        List<PatchOp> patches = repo.listPatches("conn1", "orders");
        assertThat(patches).hasSize(1);
        assertThat(patches.get(0)).isInstanceOf(PatchOp.AddVerifiedQuery.class);
        var avq = (PatchOp.AddVerifiedQuery) patches.get(0);
        assertThat(avq.payload().id()).isEqualTo("vq_1");
    }

    @Test
    void appendPatch_then_listPatches_roundtrips_IncHitCount() {
        repo.saveDomain("conn1", ordersModel(), ordersYaml());
        PatchOp patch = new PatchOp.IncHitCount("INC_HIT_COUNT", "vq_1", 4, "user", Instant.now());

        repo.appendPatch("conn1", "orders", patch);

        List<PatchOp> patches = repo.listPatches("conn1", "orders");
        assertThat(patches).hasSize(1);
        var inc = (PatchOp.IncHitCount) patches.get(0);
        assertThat(inc.vqId()).isEqualTo("vq_1");
        assertThat(inc.delta()).isEqualTo(4);
    }

    @Test
    void appendPatch_creates_directory_when_missing() {
        PatchOp patch = new PatchOp.IncHitCount("INC_HIT_COUNT", "vq_x", 1, "u", Instant.now());

        repo.appendPatch("ghost_conn", "orders", patch);

        assertThat(repo.listPatches("ghost_conn", "orders")).hasSize(1);
    }

    @Test
    void recordVerifiedQuery_then_listVerifiedQueries_returns_vq_sorted_by_hits() {
        VerifiedQuery vqA = new VerifiedQuery("vq_a", "q1", "SELECT 1", "orders",
            1, Instant.now(), "user", Instant.now(), false);
        VerifiedQuery vqB = new VerifiedQuery("vq_b", "q2", "SELECT 2", "orders",
            10, Instant.now(), "user", Instant.now(), false);
        repo.recordVerifiedQuery("conn1", vqA);
        repo.recordVerifiedQuery("conn1", vqB);

        List<VerifiedQuery> top1 = repo.listVerifiedQueries("conn1", 1);
        assertThat(top1).hasSize(1);
        assertThat(top1.get(0).id()).isEqualTo("vq_b");

        List<VerifiedQuery> all = repo.listVerifiedQueries("conn1", 10);
        assertThat(all).extracting(VerifiedQuery::id).containsExactly("vq_b", "vq_a");
    }

    @Test
    void countVerifiedQueriesByConnection_returns_total() {
        VerifiedQuery vq = new VerifiedQuery("vq_a", "q1", "SELECT 1", "orders",
            1, Instant.now(), "user", Instant.now(), false);
        repo.recordVerifiedQuery("conn1", vq);
        repo.recordVerifiedQuery("conn1", vq);

        assertThat(repo.countVerifiedQueriesByConnection("conn1")).isEqualTo(2);
        assertThat(repo.countVerifiedQueriesByConnection("ghost")).isEqualTo(0);
    }

    @Test
    void savePending_then_listPending_returns_pending_domain() {
        repo.savePending("conn1", "new_domain", "name: new_domain\nversion: 1\n");

        assertThat(repo.listPending("conn1")).containsExactly("new_domain");
    }

    @Test
    void acceptPending_promotes_to_active_when_no_existing_yaml() {
        repo.savePending("conn1", "orders", ordersYaml());

        repo.acceptPending("conn1", "orders");

        assertThat(repo.listPending("conn1")).isEmpty();
        assertThat(repo.listDomains("conn1")).containsExactly("orders");
    }

    @Test
    void acceptPending_bumps_version_when_yaml_already_exists() {
        repo.saveDomain("conn1", ordersModel(), ordersYaml());
        repo.savePending("conn1", "orders", ordersYaml().replace("version: 1", "version: 1"));

        repo.acceptPending("conn1", "orders");

        SemanticModel active = repo.loadDomain("conn1", "orders").orElseThrow();
        assertThat(active.version()).isEqualTo(2);
    }

    @Test
    void rejectPending_moves_pending_into_trash() {
        repo.savePending("conn1", "to_reject", "name: to_reject\nversion: 1\n");

        repo.rejectPending("conn1", "to_reject");

        assertThat(repo.listPending("conn1")).isEmpty();
        Path trash = home.resolve(".data-talk/_trash/semantic");
        assertThat(Files.isDirectory(trash)).isTrue();
        long matchCount;
        try (var s = Files.walk(trash)) {
            matchCount = s.filter(p -> p.getFileName().toString().equals("to_reject.model.yaml")).count();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertThat(matchCount).isEqualTo(1L);
    }

    @Test
    void moveToTrash_renames_connection_dir_into_trash() throws Exception {
        repo.saveDomain("conn1", ordersModel(), ordersYaml());
        Path conn1Dir = home.resolve(".data-talk/semantic/conn1");
        assertThat(Files.exists(conn1Dir)).isTrue();

        repo.moveToTrash("conn1", 12345L);

        assertThat(Files.exists(conn1Dir)).isFalse();
        Path trashTarget = home.resolve(".data-talk/_trash/semantic/12345-conn1");
        assertThat(Files.exists(trashTarget)).isTrue();
        assertThat(Files.exists(trashTarget.resolve("orders.model.yaml"))).isTrue();
    }

    @Test
    void moveToTrash_is_noop_when_connection_dir_missing() {
        // Should not throw
        repo.moveToTrash("ghost", 1L);
    }

    @Test
    void deleteAllByConnection_removes_active_and_trash_dirs() throws Exception {
        repo.saveDomain("conn1", ordersModel(), ordersYaml());
        repo.moveToTrash("conn1", 100L);
        // Save again into active
        repo.saveDomain("conn1", ordersModel(), ordersYaml());
        Path activeDir = home.resolve(".data-talk/semantic/conn1");
        Path trashDir = home.resolve(".data-talk/_trash/semantic/100-conn1");
        assertThat(Files.exists(activeDir)).isTrue();
        assertThat(Files.exists(trashDir)).isTrue();

        repo.deleteAllByConnection("conn1");

        assertThat(Files.exists(activeDir)).isFalse();
        assertThat(Files.exists(trashDir)).isFalse();
    }

    @Test
    void markStale_sets_stale_flag_on_matching_vq() {
        VerifiedQuery vq = new VerifiedQuery("vq_stale", "q", "SELECT 1", "orders",
            1, Instant.now(), "user", Instant.now(), false);
        VerifiedQuery vqKeep = new VerifiedQuery("vq_keep", "q2", "SELECT 2", "orders",
            2, Instant.now(), "user", Instant.now(), false);
        repo.recordVerifiedQuery("conn1", vq);
        repo.recordVerifiedQuery("conn1", vqKeep);

        repo.markStale("conn1", "vq_stale");

        List<VerifiedQuery> vqs = repo.listVerifiedQueries("conn1", 10);
        assertThat(vqs).hasSize(2);
        var staleEntry = vqs.stream().filter(q -> q.id().equals("vq_stale")).findFirst().orElseThrow();
        var liveEntry = vqs.stream().filter(q -> q.id().equals("vq_keep")).findFirst().orElseThrow();
        assertThat(staleEntry.stale()).isTrue();
        assertThat(liveEntry.stale()).isFalse();
    }

    @Test
    void compactPatches_rewrites_yaml_and_truncates_patches() throws Exception {
        repo.saveDomain("conn1", ordersModel(), ordersYaml());
        repo.appendPatch("conn1", "orders",
            new PatchOp.IncHitCount("INC_HIT_COUNT", "vq_x", 1, "u", Instant.now()));
        assertThat(repo.listPatches("conn1", "orders")).hasSize(1);

        SemanticModel compactedModel = ordersModel();
        repo.compactPatches("conn1", "orders", compactedModel);

        assertThat(repo.listPatches("conn1", "orders")).isEmpty();
        // YAML still parses afterwards
        assertThat(repo.loadDomain("conn1", "orders")).isPresent();
    }
}
