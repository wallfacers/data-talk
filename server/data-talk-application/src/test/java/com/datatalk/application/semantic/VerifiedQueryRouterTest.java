package com.datatalk.application.semantic;

import com.datatalk.domain.semantic.VerifiedQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerifiedQueryRouterTest {

    private final SemanticModelRepository repo = mock(SemanticModelRepository.class);
    private final VerifiedQueryRouter router = new VerifiedQueryRouter(repo);

    private VerifiedQuery vq(String id, String question) {
        return new VerifiedQuery(id, question, "SELECT 1", "orders", 0,
            null, "user", Instant.now(), false);
    }

    private VerifiedQuery vq(String id, String question, int hitCount) {
        return new VerifiedQuery(id, question, "SELECT 1", "orders", hitCount,
            null, "user", Instant.now(), false);
    }

    private VerifiedQuery staleVq(String id, String question) {
        return new VerifiedQuery(id, question, "SELECT 1", "orders", 0,
            null, "user", Instant.now(), true);
    }

    @Test
    void findExact_returns_match_for_identical_question() {
        when(repo.listVerifiedQueries(eq("conn1"), anyInt()))
            .thenReturn(List.of(vq("vq_1", "本月销售额")));

        var found = router.findExact("conn1", "本月销售额");

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo("vq_1");
    }

    @Test
    void findExact_returns_empty_when_case_differs() {
        when(repo.listVerifiedQueries(eq("conn1"), anyInt()))
            .thenReturn(List.of(vq("vq_1", "show MRR")));

        assertThat(router.findExact("conn1", "Show MRR")).isEmpty();
    }

    @Test
    void findExact_ignores_stale_entries() {
        when(repo.listVerifiedQueries(eq("conn1"), anyInt()))
            .thenReturn(List.of(staleVq("vq_1", "本月销售额")));

        assertThat(router.findExact("conn1", "本月销售额")).isEmpty();
    }

    @Test
    void findNormalized_matches_after_trim_and_case_fold() {
        when(repo.listVerifiedQueries(eq("conn1"), anyInt()))
            .thenReturn(List.of(vq("vq_1", "Show MRR")));

        assertThat(router.findNormalized("conn1", "  show mrr  ")).isPresent();
    }

    @Test
    void findNormalized_substitutes_synonyms_GMV_to_销售额() {
        when(repo.listVerifiedQueries(eq("conn1"), anyInt()))
            .thenReturn(List.of(vq("vq_1", "本月 销售额")));

        var found = router.findNormalized("conn1", "本月 GMV");

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo("vq_1");
    }

    @Test
    void findNormalized_handles_full_width_punctuation() {
        when(repo.listVerifiedQueries(eq("conn1"), anyInt()))
            .thenReturn(List.of(vq("vq_1", "近 7 天 销售额(按渠道)")));

        var found = router.findNormalized("conn1", "近 7 天 GMV（按渠道）");

        assertThat(found).isPresent();
    }

    @Test
    void findNormalized_ignores_stale() {
        when(repo.listVerifiedQueries(eq("conn1"), anyInt()))
            .thenReturn(List.of(staleVq("vq_1", "Show MRR")));

        assertThat(router.findNormalized("conn1", "show mrr")).isEmpty();
    }

    @Test
    void topKByHits_returns_sorted_descending_excluding_stale() {
        when(repo.listVerifiedQueries(eq("conn1"), anyInt()))
            .thenReturn(List.of(
                vq("vq_a", "q1", 3),
                vq("vq_b", "q2", 10),
                staleVq("vq_stale", "stale"),
                vq("vq_c", "q3", 1)
            ));

        var top2 = router.topKByHits("conn1", 2);

        assertThat(top2).hasSize(2);
        assertThat(top2.get(0).id()).isEqualTo("vq_b");
        assertThat(top2.get(1).id()).isEqualTo("vq_a");
    }

    @Test
    void findExact_returns_empty_for_null_connectionId() {
        assertThat(router.findExact(null, "q")).isEmpty();
    }

    @Test
    void findNormalized_returns_empty_for_null_question() {
        assertThat(router.findNormalized("conn1", null)).isEmpty();
    }

    @Test
    void normalize_strips_diacritics_and_lowercases() {
        assertThat(VerifiedQueryRouter.normalize("  HELLO  WORLD  "))
            .isEqualTo("hello world");
    }

    @Test
    void noArgConstructor_returns_empty_for_all_methods() {
        VerifiedQueryRouter bare = new VerifiedQueryRouter();
        assertThat(bare.findExact("c", "q")).isEmpty();
        assertThat(bare.findNormalized("c", "q")).isEmpty();
        assertThat(bare.topKByHits("c", 5)).isEmpty();
    }
}
