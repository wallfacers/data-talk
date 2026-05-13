package com.datatalk.infra.ingestion;

import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.domain.ingestion.IngestionJob;
import com.datatalk.domain.ingestion.PayloadFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class JdbcIngestionJobRepositoryIT {
    @Autowired IngestionJobRepository repo;

    @Test void saveAndFindRoundTrip() {
        var j = new IngestionJob("ing_t1", "round-trip test", "https://x", "GET",
            Map.of("Accept", "application/json"), Map.of("days", "7"), null,
            null, null, PayloadFormat.JSON, null,
            "pending", null, null, null, null,
            0, 0, 0L, null,
            "ai", "sess_t1", "AI · sample", 1500L,
            1000L, 1000L, null, null);
        repo.save(j);
        var found = repo.findById("ing_t1").orElseThrow();
        assertThat(found.sourceUrl()).isEqualTo("https://x");
        assertThat(found.payloadFormat()).isEqualTo(PayloadFormat.JSON);
        assertThat(found.status()).isEqualTo("pending");
        assertThat(found.name()).isEqualTo("round-trip test");
        assertThat(found.createdByKind()).isEqualTo("ai");
        assertThat(found.createdBySessionId()).isEqualTo("sess_t1");
        assertThat(found.createdByLabel()).isEqualTo("AI · sample");
        assertThat(found.heartbeatAt()).isEqualTo(1500L);
    }

    @Test void statusUpdateRoundTrips() {
        var j = new IngestionJob("ing_t2", "status update test", "https://y", "GET",
            Map.of(), Map.of(), null, null, null, PayloadFormat.JSON, null,
            "pending", null, null, null, null,
            0, 0, 0L, null,
            "ai", null, null, null,
            1000L, 1000L, null, null);
        repo.save(j);
        repo.updateStatus("ing_t2", "fetching", null, 2000L);
        assertThat(repo.findById("ing_t2").orElseThrow().status()).isEqualTo("fetching");
    }

    @Test void updateHeartbeatRefreshesColumn() {
        var j = new IngestionJob("ing_hb", "heartbeat test", "https://z", "GET",
            Map.of(), Map.of(), null, null, null, PayloadFormat.JSON, null,
            "fetching", null, null, null, null,
            0, 0, 0L, null,
            "ai", null, null, 1000L,
            1000L, 1000L, null, null);
        repo.save(j);
        repo.updateHeartbeat("ing_hb", 5000L);
        assertThat(repo.findById("ing_hb").orElseThrow().heartbeatAt()).isEqualTo(5000L);
    }

    @Test void batchFailByStatusFlipsAllMatching() {
        var a = new IngestionJob("ing_bf_a", "a", "https://a", "GET", Map.of(), Map.of(), null,
            null, null, PayloadFormat.JSON, null, "fetching", null, null, null, null,
            0, 0, 0L, null, "ai", null, null, null, 1000L, 1000L, null, null);
        var b = new IngestionJob("ing_bf_b", "b", "https://b", "GET", Map.of(), Map.of(), null,
            null, null, PayloadFormat.JSON, null, "writing", null, null, null, null,
            0, 0, 0L, null, "ai", null, null, null, 1000L, 1000L, null, null);
        var c = new IngestionJob("ing_bf_c", "c", "https://c", "GET", Map.of(), Map.of(), null,
            null, null, PayloadFormat.JSON, null, "completed", null, null, null, null,
            0, 0, 0L, null, "ai", null, null, null, 1000L, 1000L, null, null);
        repo.save(a); repo.save(b); repo.save(c);

        int n = repo.batchFailByStatus(List.of("fetching", "writing"), "restart sweep", 9000L);
        assertThat(n).isEqualTo(2);
        assertThat(repo.findById("ing_bf_a").orElseThrow().status()).isEqualTo("failed");
        assertThat(repo.findById("ing_bf_b").orElseThrow().status()).isEqualTo("failed");
        assertThat(repo.findById("ing_bf_c").orElseThrow().status()).isEqualTo("completed");
    }

    @Test void batchFailIfHeartbeatBeforeRespectsDeadline() {
        var fresh = new IngestionJob("ing_hb_fresh", "fresh", "https://f", "GET", Map.of(), Map.of(),
            null, null, null, PayloadFormat.JSON, null, "writing", null, null, null, null,
            0, 0, 0L, null, "ai", null, null, 10000L, 1000L, 1000L, null, null);
        var stale = new IngestionJob("ing_hb_stale", "stale", "https://s", "GET", Map.of(), Map.of(),
            null, null, null, PayloadFormat.JSON, null, "writing", null, null, null, null,
            0, 0, 0L, null, "ai", null, null, 1000L, 1000L, 1000L, null, null);
        repo.save(fresh); repo.save(stale);

        int n = repo.batchFailIfHeartbeatBefore(List.of("writing"), 5000L, "heartbeat lost", 9000L);
        assertThat(n).isEqualTo(1);
        assertThat(repo.findById("ing_hb_fresh").orElseThrow().status()).isEqualTo("writing");
        assertThat(repo.findById("ing_hb_stale").orElseThrow().status()).isEqualTo("failed");
    }
}
