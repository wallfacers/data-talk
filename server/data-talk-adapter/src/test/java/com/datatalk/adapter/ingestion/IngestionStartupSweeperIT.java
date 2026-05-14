package com.datatalk.adapter.ingestion;

import com.datatalk.application.ingestion.IngestionStartupSweeper;
import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.domain.ingestion.IngestionJob;
import com.datatalk.domain.ingestion.PayloadFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link IngestionStartupSweeper} flips every non-terminal status to
 * {@code failed} when invoked. Spring already runs it once on context boot;
 * the test inserts a fresh batch and invokes the method directly to validate
 * the contract end-to-end against the real JDBC repository.
 */
@SpringBootTest
class IngestionStartupSweeperIT {

    @Autowired IngestionStartupSweeper sweeper;
    @Autowired IngestionJobRepository repo;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate jdbc;

    @Test
    void flipsAllNonTerminalStatusesToFailed() {
        // Clean any pre-existing test rows.
        jdbc.update("DELETE FROM ingestion_job WHERE id LIKE 'startup_sweep_%'");

        repo.save(job("startup_sweep_pending", "pending"));
        repo.save(job("startup_sweep_fetching", "fetching"));
        repo.save(job("startup_sweep_mapping", "mapping"));
        repo.save(job("startup_sweep_confirmed", "confirmed"));
        repo.save(job("startup_sweep_writing", "writing"));
        repo.save(job("startup_sweep_completed", "completed"));
        repo.save(job("startup_sweep_failed", "failed"));
        repo.save(job("startup_sweep_cancelled", "cancelled"));

        sweeper.sweepStaleJobs();

        assertThat(repo.findById("startup_sweep_pending").orElseThrow().status()).isEqualTo("failed");
        assertThat(repo.findById("startup_sweep_fetching").orElseThrow().status()).isEqualTo("failed");
        assertThat(repo.findById("startup_sweep_mapping").orElseThrow().status()).isEqualTo("failed");
        assertThat(repo.findById("startup_sweep_confirmed").orElseThrow().status()).isEqualTo("failed");
        assertThat(repo.findById("startup_sweep_writing").orElseThrow().status()).isEqualTo("failed");

        // Terminal statuses must remain untouched.
        assertThat(repo.findById("startup_sweep_completed").orElseThrow().status()).isEqualTo("completed");
        assertThat(repo.findById("startup_sweep_failed").orElseThrow().status()).isEqualTo("failed");
        assertThat(repo.findById("startup_sweep_cancelled").orElseThrow().status()).isEqualTo("cancelled");

        // errorMessage on the flipped rows references the restart reason.
        String reason = jdbc.queryForObject(
            "SELECT error_message FROM ingestion_job WHERE id='startup_sweep_writing'",
            String.class);
        assertThat(reason).contains("server restarted");

        jdbc.update("DELETE FROM ingestion_job WHERE id LIKE 'startup_sweep_%'");
    }

    private IngestionJob job(String id, String status) {
        return new IngestionJob(id, id, "https://x", "GET", Map.of(), Map.of(), null,
            null, null, PayloadFormat.JSON, null,
            status, null, null, null, null,
            0, 0, 0L, null,
            "ai", null, null, null,
            1000L, 1000L, null, null);
    }
}
