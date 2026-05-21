package com.datatalk.application.semantic;

import com.datatalk.domain.semantic.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SemanticModelLoaderTest {

    private final SemanticModelRepository repo = mock(SemanticModelRepository.class);
    private final SemanticModelLoader loader = new SemanticModelLoader(repo);

    private SemanticModel baseModel() {
        return new SemanticModel(
            "orders", 1, "Orders domain",
            List.of(new Entity("orders", "fact",
                new Entity.Physical(null, null, "orders"),
                List.of("id"), List.of(), "fact table")),
            List.of(),
            List.of(new Measure("gmv", "orders", "sum", "amount", null,
                "销售额", "GMV", "Gross Merchandise Value")),
            List.of(),
            new LinkedHashMap<>(),
            List.of(),
            Instant.now(),
            "test"
        );
    }

    @Test
    void loadDomain_returns_empty_when_repository_returns_empty() {
        when(repo.loadDomain("conn1", "missing")).thenReturn(Optional.empty());

        assertThat(loader.loadDomain("conn1", "missing")).isEmpty();
    }

    @Test
    void loadDomain_returns_base_unchanged_when_no_patches() {
        SemanticModel base = baseModel();
        when(repo.loadDomain("conn1", "orders")).thenReturn(Optional.of(base));
        when(repo.listPatches("conn1", "orders")).thenReturn(List.of());

        Optional<SemanticModel> result = loader.loadDomain("conn1", "orders");

        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(base);
    }

    @Test
    void loadDomain_applies_AddLiteralMapping_patches() {
        SemanticModel base = baseModel();
        when(repo.loadDomain("conn1", "orders")).thenReturn(Optional.of(base));
        when(repo.listPatches("conn1", "orders")).thenReturn(List.of(
            new PatchOp.AddLiteralMapping("ADD_LITERAL_MAPPING",
                "order_status",
                Map.of("已完成", "COMPLETED", "进行中", "PROCESSING"),
                "ai", Instant.now())
        ));

        SemanticModel merged = loader.loadDomain("conn1", "orders").orElseThrow();

        assertThat(merged.literalMappings()).containsKey("order_status");
        assertThat(merged.literalMappings().get("order_status"))
            .containsEntry("已完成", "COMPLETED")
            .containsEntry("进行中", "PROCESSING");
    }

    @Test
    void loadDomain_applies_AddVerifiedQuery_patches_into_refs() {
        SemanticModel base = baseModel();
        VerifiedQuery vq = new VerifiedQuery(
            "vq_orders_001", "本月销售额", "SELECT SUM(amount) FROM orders WHERE month = 5",
            "orders", 0, null, "user", Instant.now(), false);
        when(repo.loadDomain("conn1", "orders")).thenReturn(Optional.of(base));
        when(repo.listPatches("conn1", "orders")).thenReturn(List.of(
            new PatchOp.AddVerifiedQuery("ADD_VERIFIED_QUERY", vq, "user", Instant.now())
        ));

        SemanticModel merged = loader.loadDomain("conn1", "orders").orElseThrow();

        assertThat(merged.verifiedQueryRefs()).hasSize(1);
        assertThat(merged.verifiedQueryRefs().get(0).id()).isEqualTo("vq_orders_001");
    }

    @Test
    void loadDomain_IncHitCount_patches_dont_mutate_model() {
        SemanticModel base = baseModel();
        when(repo.loadDomain("conn1", "orders")).thenReturn(Optional.of(base));
        when(repo.listPatches("conn1", "orders")).thenReturn(List.of(
            new PatchOp.IncHitCount("INC_HIT_COUNT", "vq_xx", 3, "user", Instant.now())
        ));

        SemanticModel merged = loader.loadDomain("conn1", "orders").orElseThrow();

        assertThat(merged.literalMappings()).isEmpty();
        assertThat(merged.verifiedQueryRefs()).isEmpty();
    }

    @Test
    void loadDomain_merges_multiple_patches_in_order() {
        SemanticModel base = baseModel();
        VerifiedQuery vqA = new VerifiedQuery("vq_a", "q1", "sql1", "orders", 0, null, "u", Instant.now(), false);
        VerifiedQuery vqB = new VerifiedQuery("vq_b", "q2", "sql2", "orders", 0, null, "u", Instant.now(), false);
        when(repo.loadDomain("conn1", "orders")).thenReturn(Optional.of(base));
        when(repo.listPatches("conn1", "orders")).thenReturn(List.of(
            new PatchOp.AddLiteralMapping("ADD_LITERAL_MAPPING", "status",
                Map.of("a", "A"), "u", Instant.now()),
            new PatchOp.AddVerifiedQuery("ADD_VERIFIED_QUERY", vqA, "u", Instant.now()),
            new PatchOp.AddLiteralMapping("ADD_LITERAL_MAPPING", "status",
                Map.of("b", "B"), "u", Instant.now()),
            new PatchOp.AddVerifiedQuery("ADD_VERIFIED_QUERY", vqB, "u", Instant.now())
        ));

        SemanticModel merged = loader.loadDomain("conn1", "orders").orElseThrow();

        assertThat(merged.literalMappings().get("status"))
            .containsEntry("a", "A")
            .containsEntry("b", "B");
        assertThat(merged.verifiedQueryRefs())
            .extracting(SemanticModel.VerifiedQueryRef::id)
            .containsExactly("vq_a", "vq_b");
    }

    @Test
    void noArgConstructor_returns_empty_on_load() {
        SemanticModelLoader bare = new SemanticModelLoader();
        assertThat(bare.loadDomain("any", "any")).isEmpty();
        // Must not throw
        bare.compact("any", "any", baseModel());
    }

    @Test
    void compact_delegates_to_repository() {
        SemanticModel m = baseModel();
        loader.compact("conn1", "orders", m);

        verify(repo).compactPatches("conn1", "orders", m);
    }
}
