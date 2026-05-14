package com.datatalk.adapter.ingestion;

import com.datatalk.application.ingestion.IngestionHeartbeatSweeper;
import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.domain.ingestion.IngestionJob;
import com.datatalk.domain.ingestion.PayloadFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link IngestionHeartbeatSweeper} flips writing/fetching rows whose
 * heartbeat is older than the TTL to {@code failed}, leaves fresh rows alone,
 * and ignores already-terminal rows.
 */
@SpringBootTest
class IngestionHeartbeatSweeperIT {

    @Autowired IngestionHeartbeatSweeper sweeper;
    @Autowired IngestionJobRepository repo;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate jdbc;

    @Test
    void flipsRowsWithStaleHeartbeat() {
        jdbc.update("DELETE FROM ingestion_job WHERE id LIKE 'hb_sweep_%'");

        long now = System.currentTimeMillis();
        long stale = now - 10 * 60 * 1000L; // 10 min old
        long fresh = now - 30 * 1000L;       // 30s old (well under 5 min TTL)

        repo.save(job("hb_sweep_stale_writing", "writing", stale));
        repo.save(job("hb_sweep_stale_fetching", "fetching", stale));
        repo.save(job("hb_sweep_fresh_writing", "writing", fresh));
        repo.save(job("hb_sweep_stale_pending", "pending", stale)); // not active — should NOT be swept
        repo.save(job("hb_sweep_null_writing", "writing", null));    // null heartbeat — swept (no proof of life)

        sweeper.sweepDeadHeartbeats();

        assertThat(repo.findById("hb_sweep_stale_writing").orElseThrow().status()).isEqualTo("failed");
        assertThat(repo.findById("hb_sweep_stale_fetching").orElseThrow().status()).isEqualTo("failed");
        assertThat(repo.findById("hb_sweep_null_writing").orElseThrow().status()).isEqualTo("failed");

        assertThat(repo.findById("hb_sweep_fresh_writing").orElseThrow().status()).isEqualTo("writing");
        assertThat(repo.findById("hb_sweep_stale_pending").orElseThrow().status()).isEqualTo("pending");

        String reason = jdbc.queryForObject(
            "SELECT error_message FROM ingestion_job WHERE id='hb_sweep_stale_writing'",
            String.class);
        assertThat(reason).contains("heartbeat");

        jdbc.update("DELETE FROM ingestion_job WHERE id LIKE 'hb_sweep_%'");
    }

    private IngestionJob job(String id, String status, Long heartbeatAt) {
        return new IngestionJob(id, id, "https://x", "GET", Map.of(), Map.of(), null,
            null, null, PayloadFormat.JSON, null,
            status, null, null, null, null,
            0, 0, 0L, null,
            "ai", null, null, heartbeatAt,
            1000L, 1000L, null, null);
    }
}
