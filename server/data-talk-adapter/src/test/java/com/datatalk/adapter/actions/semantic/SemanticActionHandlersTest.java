package com.datatalk.adapter.actions.semantic;

import com.datatalk.application.semantic.SemanticModelRepository;
import com.datatalk.application.semantic.VerifiedQueryRouter;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.semantic.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SemanticActionHandlersTest {

    private final SemanticModelRepository repo = mock(SemanticModelRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final VerifiedQueryRouter router = mock(VerifiedQueryRouter.class);

    private ActionContext ctx(String connectionId) {
        return new ActionContext("ses_1", "call_1", connectionId, "oc_1");
    }

    private SemanticModel sampleModel() {
        return new SemanticModel(
            "orders", 1, "Orders domain",
            List.of(new Entity("orders", "fact",
                new Entity.Physical(null, null, "orders"),
                List.of("id"), List.of(), "orders fact")),
            List.of(new Dimension("status", "orders", "status", "categorical",
                null, "状态", "Status", "")),
            List.of(new Measure("gmv", "orders", "sum", "amount", null,
                "销售额", "GMV", "")),
            List.of(new Metric("repurchase_rate", "ratio", "repeat_buyers", "all_buyers",
                null, null, "复购率", "Repurchase Rate", "")),
            Map.of(),
            List.of(),
            Instant.now(),
            "test"
        );
    }

    // ---- SemanticLookupActionHandler ----

    @Test
    void semanticLookup_finds_measures_by_query_substring() throws Exception {
        when(repo.listDomains("conn1")).thenReturn(List.of("orders"));
        when(repo.loadDomain("conn1", "orders")).thenReturn(Optional.of(sampleModel()));
        var handler = new SemanticLookupActionHandler(repo);

        Map result = handler.handle(ctx("conn1"), Map.of("query", "GMV"))
            .toCompletableFuture().get();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertThat(matches).anySatisfy(m -> {
            assertThat(m).containsEntry("kind", "measure");
            assertThat(m).containsEntry("name", "gmv");
        });
    }

    @Test
    void semanticLookup_filters_by_kind() throws Exception {
        when(repo.listDomains("conn1")).thenReturn(List.of("orders"));
        when(repo.loadDomain("conn1", "orders")).thenReturn(Optional.of(sampleModel()));
        var handler = new SemanticLookupActionHandler(repo);

        Map result = handler.handle(ctx("conn1"),
            Map.of("query", "状", "kind", "dimension")).toCompletableFuture().get();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertThat(matches).allSatisfy(m -> assertThat(m).containsEntry("kind", "dimension"));
        assertThat(matches).extracting("name").contains("status");
    }

    @Test
    void semanticLookup_returns_empty_matches_for_no_hit() throws Exception {
        when(repo.listDomains("conn1")).thenReturn(List.of("orders"));
        when(repo.loadDomain("conn1", "orders")).thenReturn(Optional.of(sampleModel()));
        var handler = new SemanticLookupActionHandler(repo);

        Map result = handler.handle(ctx("conn1"), Map.of("query", "nonexistent_xyz"))
            .toCompletableFuture().get();

        assertThat((List<?>) result.get("matches")).isEmpty();
        assertThat(result).containsEntry("total", 0);
    }

    // ---- VerifiedQueryFindActionHandler ----

    @Test
    void verifiedQueryFind_returns_L0_hit_and_increments_count() throws Exception {
        VerifiedQuery vq = new VerifiedQuery("vq_1", "本月销售额", "SELECT 1", "orders",
            5, Instant.now(), "user", Instant.now(), false);
        when(router.findExact("conn1", "本月销售额")).thenReturn(Optional.of(vq));
        var handler = new VerifiedQueryFindActionHandler(repo, router);

        Map result = handler.handle(ctx("conn1"),
            Map.of("question", "本月销售额")).toCompletableFuture().get();

        assertThat(result).containsEntry("hit", true);
        assertThat(result).containsEntry("layer", "L0");
        verify(repo).incHit("conn1", "vq_1");
    }

    @Test
    void verifiedQueryFind_returns_L1_when_exact_miss_but_normalized_hits() throws Exception {
        VerifiedQuery vq = new VerifiedQuery("vq_2", "show MRR", "SELECT 2", "orders",
            3, Instant.now(), "user", Instant.now(), false);
        when(router.findExact(any(), any())).thenReturn(Optional.empty());
        when(router.findNormalized("conn1", "  SHOW   mrr  ")).thenReturn(Optional.of(vq));
        var handler = new VerifiedQueryFindActionHandler(repo, router);

        Map result = handler.handle(ctx("conn1"),
            Map.of("question", "  SHOW   mrr  ")).toCompletableFuture().get();

        assertThat(result).containsEntry("hit", true);
        assertThat(result).containsEntry("layer", "L1");
        verify(repo).incHit("conn1", "vq_2");
    }

    @Test
    void verifiedQueryFind_returns_L2_candidates_when_no_exact_match() throws Exception {
        VerifiedQuery vq = new VerifiedQuery("vq_x", "近 30 天 GMV", "SELECT 9", "orders",
            10, Instant.now(), "user", Instant.now(), false);
        when(router.findExact(any(), any())).thenReturn(Optional.empty());
        when(router.findNormalized(any(), any())).thenReturn(Optional.empty());
        when(router.topKByHits(eq("conn1"), anyInt())).thenReturn(List.of(vq));
        var handler = new VerifiedQueryFindActionHandler(repo, router);

        Map result = handler.handle(ctx("conn1"),
            Map.of("question", "新问题", "topK", 5)).toCompletableFuture().get();

        assertThat(result).containsEntry("hit", false);
        assertThat(result).containsEntry("layer", "L2");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) result.get("candidates");
        assertThat(candidates).hasSize(1);
        verify(repo, never()).incHit(any(), any());
    }

    @Test
    void verifiedQueryFind_returns_NONE_when_no_data() throws Exception {
        when(router.findExact(any(), any())).thenReturn(Optional.empty());
        when(router.findNormalized(any(), any())).thenReturn(Optional.empty());
        when(router.topKByHits(any(), anyInt())).thenReturn(List.of());
        var handler = new VerifiedQueryFindActionHandler(repo, router);

        Map result = handler.handle(ctx("conn1"),
            Map.of("question", "anything")).toCompletableFuture().get();

        assertThat(result).containsEntry("hit", false);
        assertThat(result).containsEntry("layer", "NONE");
    }

    // ---- VerifiedQueryRecordActionHandler ----

    @Test
    void verifiedQueryRecord_records_vq_and_publishes_event() throws Exception {
        when(repo.recordVerifiedQuery(eq("conn1"), any(VerifiedQuery.class)))
            .thenAnswer(inv -> ((VerifiedQuery) inv.getArgument(1)).id());
        var handler = new VerifiedQueryRecordActionHandler(repo, events);

        Map result = handler.handle(ctx("conn1"),
            Map.of("question", "Q1", "sql", "SELECT 1", "modelRef", "orders"))
            .toCompletableFuture().get();

        assertThat(result.get("id")).asString().startsWith("vq_orders_");
        verify(repo).recordVerifiedQuery(eq("conn1"), any(VerifiedQuery.class));
        verify(events).publishEvent(any(DtEvent.VerifiedQueryRecorded.class));
    }

    @Test
    void verifiedQueryRecord_rejects_when_no_active_connection() throws Exception {
        var handler = new VerifiedQueryRecordActionHandler(repo, events);

        Map result = handler.handle(ctx(null),
            Map.of("question", "Q", "sql", "SELECT 1", "modelRef", "orders"))
            .toCompletableFuture().get();

        assertThat(result).containsEntry("error", "NO_ACTIVE_CONNECTION");
        verifyNoInteractions(events);
        verify(repo, never()).recordVerifiedQuery(any(), any());
    }

    // ---- SemanticProposeChangeActionHandler ----

    @Test
    void proposeChange_writes_pending_and_publishes_event() throws Exception {
        when(repo.savePending(eq("conn1"), eq("orders"), any()))
            .thenReturn("/tmp/path");
        var handler = new SemanticProposeChangeActionHandler(repo, events);

        Map result = handler.handle(ctx("conn1"), Map.of(
            "domain", "orders",
            "yaml_text", "name: orders\nversion: 1\n",
            "reason", "initial setup"
        )).toCompletableFuture().get();

        assertThat(result).containsEntry("status", "pending_review");
        assertThat(result).containsEntry("domain", "orders");
        verify(repo).savePending("conn1", "orders", "name: orders\nversion: 1\n");
        verify(events).publishEvent(any(DtEvent.SemanticPendingCreated.class));
    }

    @Test
    void proposeChange_sanitizes_domain_name() throws Exception {
        when(repo.savePending(any(), any(), any())).thenReturn("/tmp/path");
        var handler = new SemanticProposeChangeActionHandler(repo, events);

        handler.handle(ctx("conn1"), Map.of(
            "domain", "Orders/Premium#1",
            "yaml_text", "name: x\n",
            "reason", "r"
        )).toCompletableFuture().get();

        verify(repo).savePending(eq("conn1"), eq("orders_premium_1"), any());
    }

    @Test
    void proposeChange_rejects_empty_yaml_with_schema_error() throws Exception {
        var handler = new SemanticProposeChangeActionHandler(repo, events);

        Map result = handler.handle(ctx("conn1"), Map.of(
            "domain", "orders",
            "yaml_text", "",
            "reason", "r"
        )).toCompletableFuture().get();

        assertThat(result).containsEntry("error", "SCHEMA_VALIDATION_FAILED");
        verifyNoInteractions(events);
        verify(repo, never()).savePending(any(), any(), any());
    }

    @Test
    void proposeChange_rejects_when_no_active_connection() throws Exception {
        var handler = new SemanticProposeChangeActionHandler(repo, events);

        Map result = handler.handle(ctx(null), Map.of(
            "domain", "orders",
            "yaml_text", "name: orders\n",
            "reason", "r"
        )).toCompletableFuture().get();

        assertThat(result).containsEntry("error", "NO_ACTIVE_CONNECTION");
        verifyNoInteractions(events);
    }

    // ---- LiteralMappingAddActionHandler ----

    @Test
    void literalMappingAdd_appends_patch() throws Exception {
        var handler = new LiteralMappingAddActionHandler(repo);

        Map result = handler.handle(ctx("conn1"), Map.of(
            "domain", "orders",
            "dimension", "order_status",
            "natural", "已完成",
            "dbValue", "COMPLETED"
        )).toCompletableFuture().get();

        assertThat(result).containsEntry("dimension", "order_status");
        verify(repo).appendPatch(eq("conn1"), eq("orders"), any(PatchOp.AddLiteralMapping.class));
    }

    @Test
    void literalMappingAdd_rejects_when_no_active_connection() throws Exception {
        var handler = new LiteralMappingAddActionHandler(repo);

        Map result = handler.handle(ctx(""), Map.of(
            "domain", "orders",
            "dimension", "x",
            "natural", "a",
            "dbValue", "A"
        )).toCompletableFuture().get();

        assertThat(result).containsEntry("error", "NO_ACTIVE_CONNECTION");
        verify(repo, never()).appendPatch(any(), any(), any());
    }

    // ---- SkillCreateActionHandler ----

    @Test
    void skillCreate_writes_pending_and_appends_authored_by_when_missing() throws Exception {
        when(repo.savePending(eq("conn1"), eq("subscriptions"), any())).thenReturn("/tmp/path");
        var handler = new SkillCreateActionHandler(repo, events);

        handler.handle(ctx("conn1"), Map.of(
            "name", "subscriptions",
            "yaml_text", "name: subscriptions\nversion: 1\ndescription: SaaS\n"
        )).toCompletableFuture().get();

        org.mockito.ArgumentCaptor<String> yamlCap = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repo).savePending(eq("conn1"), eq("subscriptions"), yamlCap.capture());
        assertThat(yamlCap.getValue()).contains("authored_by: ai_inferred");
        verify(events).publishEvent(any(DtEvent.SemanticPendingCreated.class));
    }

    @Test
    void skillCreate_does_not_double_append_authored_by() throws Exception {
        when(repo.savePending(any(), any(), any())).thenReturn("/tmp/path");
        var handler = new SkillCreateActionHandler(repo, events);

        handler.handle(ctx("conn1"), Map.of(
            "name", "subs",
            "yaml_text", "name: subs\nversion: 1\nauthored_by: human\n"
        )).toCompletableFuture().get();

        org.mockito.ArgumentCaptor<String> yamlCap = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repo).savePending(any(), any(), yamlCap.capture());
        String yaml = yamlCap.getValue();
        // Only one authored_by line
        long count = yaml.lines().filter(l -> l.contains("authored_by:")).count();
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void skillCreate_rejects_empty_yaml() throws Exception {
        var handler = new SkillCreateActionHandler(repo, events);

        Map result = handler.handle(ctx("conn1"), Map.of(
            "name", "x",
            "yaml_text", "   "
        )).toCompletableFuture().get();

        assertThat(result).containsEntry("error", "SCHEMA_VALIDATION_FAILED");
        verify(repo, never()).savePending(any(), any(), any());
    }

    @Test
    void skillCreate_rejects_when_no_active_connection() throws Exception {
        var handler = new SkillCreateActionHandler(repo, events);

        Map result = handler.handle(ctx(null), Map.of(
            "name", "x", "yaml_text", "name: x\n"
        )).toCompletableFuture().get();

        assertThat(result).containsEntry("error", "NO_ACTIVE_CONNECTION");
    }
}
