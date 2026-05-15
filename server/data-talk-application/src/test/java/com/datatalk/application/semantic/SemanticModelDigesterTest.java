package com.datatalk.application.semantic;

import com.datatalk.domain.semantic.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SemanticModelDigesterTest {

    private final SemanticModelRepository repo = mock(SemanticModelRepository.class);
    private final SemanticModelDigester digester = new SemanticModelDigester(repo);

    private Measure measure(String name) {
        return new Measure(name, "orders", "sum", "amount", null,
            name + "_zh", name + "_en", "desc");
    }

    private SemanticModel modelWith(int measureCount, Map<String, Map<String, String>> mappings) {
        List<Measure> measures = new ArrayList<>();
        for (int i = 0; i < measureCount; i++) measures.add(measure("m" + i));
        return new SemanticModel(
            "orders", 1, "Orders domain",
            List.of(new Entity("orders", "fact",
                new Entity.Physical(null, null, "orders"),
                List.of("id"), List.of(), "")),
            List.of(),
            measures,
            List.of(),
            mappings,
            List.of(),
            Instant.now(),
            "test"
        );
    }

    @Test
    void digest_returns_sentinel_for_null_connection() {
        assertThat(digester.digest(null))
            .contains("<no semantic model — please bind a connection>");
    }

    @Test
    void digest_returns_sentinel_for_blank_connection() {
        assertThat(digester.digest("  "))
            .contains("<no semantic model — please bind a connection>");
    }

    @Test
    void digest_returns_sentinel_when_no_domains_for_connection() {
        when(repo.listDomains("conn1")).thenReturn(List.of());

        assertThat(digester.digest("conn1"))
            .contains("<no semantic model defined for this connection>");
    }

    @Test
    void digest_renders_snapshot_with_domains_measures_and_mappings() {
        when(repo.listDomains("conn1")).thenReturn(List.of("orders"));
        when(repo.loadDomain("conn1", "orders")).thenReturn(Optional.of(
            modelWith(3, Map.of("status", Map.of("已完成", "COMPLETED")))
        ));
        when(repo.listVerifiedQueries(eq("conn1"), anyInt())).thenReturn(List.of());

        String d = digester.digest("conn1");

        assertThat(d)
            .contains("## Semantic Model Snapshot")
            .contains("Current connection: conn1")
            .contains("Domains loaded: orders")
            .contains("Top measures:")
            .contains("m0_zh / m0_en → measures.m0")
            .contains("Common literal mappings:")
            .contains("已完成 → COMPLETED")
            .contains("User defaults:");
    }

    @Test
    void digest_renders_top_K_verified_queries() {
        when(repo.listDomains("conn1")).thenReturn(List.of("orders"));
        when(repo.loadDomain("conn1", "orders")).thenReturn(Optional.of(modelWith(1, Map.of())));
        when(repo.listVerifiedQueries(eq("conn1"), anyInt())).thenReturn(List.of(
            new VerifiedQuery("vq1", "本月销售额", "SELECT 1", "orders", 5, null, "u", Instant.now(), false),
            new VerifiedQuery("vq2", "近 30 天 DAU", "SELECT 2", "orders", 3, null, "u", Instant.now(), false)
        ));

        String d = digester.digest("conn1");

        assertThat(d)
            .contains("Top verified queries")
            .contains("\"本月销售额\" (hits: 5)")
            .contains("\"近 30 天 DAU\" (hits: 3)");
    }

    @Test
    void digest_truncates_long_question_to_120_chars_with_ellipsis() {
        String longQ = "a".repeat(200);
        when(repo.listDomains("conn1")).thenReturn(List.of("orders"));
        when(repo.loadDomain("conn1", "orders")).thenReturn(Optional.of(modelWith(1, Map.of())));
        when(repo.listVerifiedQueries(eq("conn1"), anyInt())).thenReturn(List.of(
            new VerifiedQuery("vq1", longQ, "SELECT 1", "orders", 1, null, "u", Instant.now(), false)
        ));

        String d = digester.digest("conn1");

        // 120 char truncation: 117 a's + "..." + " (hits..."
        assertThat(d).contains("a".repeat(117) + "...");
        assertThat(d).doesNotContain("a".repeat(121));
    }

    @Test
    void digest_caps_total_length_at_2000_chars() {
        // Build a model with many measures + many mappings + many VQs to exceed 2000
        List<Measure> measures = new ArrayList<>();
        for (int i = 0; i < 100; i++) measures.add(measure("measure_" + i));
        Map<String, Map<String, String>> mappings = new LinkedHashMap<>();
        for (int i = 0; i < 50; i++) {
            Map<String, String> inner = new LinkedHashMap<>();
            for (int j = 0; j < 10; j++) {
                inner.put("natural_value_" + i + "_" + j, "DB_VALUE_" + i + "_" + j);
            }
            mappings.put("dim_" + i, inner);
        }
        SemanticModel m = new SemanticModel(
            "orders", 1, "desc",
            List.of(new Entity("orders", "fact",
                new Entity.Physical(null, null, "orders"),
                List.of("id"), List.of(), "")),
            List.of(), measures, List.of(), mappings, List.of(),
            Instant.now(), "test");

        List<VerifiedQuery> vqs = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            vqs.add(new VerifiedQuery("vq_" + i, "Question number " + i + " about sales and revenue trends",
                "SELECT " + i, "orders", i, null, "u", Instant.now(), false));
        }

        when(repo.listDomains("conn1")).thenReturn(List.of("orders"));
        when(repo.loadDomain("conn1", "orders")).thenReturn(Optional.of(m));
        when(repo.listVerifiedQueries(eq("conn1"), anyInt())).thenReturn(vqs);

        String d = digester.digest("conn1");

        assertThat(d.length()).isLessThanOrEqualTo(2000);
        // Header and key sections survive even after cutting
        assertThat(d).contains("## Semantic Model Snapshot");
    }
}
