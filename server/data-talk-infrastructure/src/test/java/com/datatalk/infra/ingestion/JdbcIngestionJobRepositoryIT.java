package com.datatalk.infra.ingestion;

import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.domain.ingestion.IngestionJob;
import com.datatalk.domain.ingestion.PayloadFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class JdbcIngestionJobRepositoryIT {
    @Autowired IngestionJobRepository repo;

    @Test void saveAndFindRoundTrip() {
        var j = new IngestionJob("ing_t1", "https://x", "GET",
            Map.of("Accept", "application/json"), Map.of("days", "7"), null,
            null, null, PayloadFormat.JSON, null,
            "pending", null, null, null, null,
            0, 0, 0L, 1000L, 1000L, null, null);
        repo.save(j);
        var found = repo.findById("ing_t1").orElseThrow();
        assertThat(found.sourceUrl()).isEqualTo("https://x");
        assertThat(found.payloadFormat()).isEqualTo(PayloadFormat.JSON);
        assertThat(found.status()).isEqualTo("pending");
    }

    @Test void statusUpdateRoundTrips() {
        var j = new IngestionJob("ing_t2", "https://y", "GET",
            Map.of(), Map.of(), null, null, null, PayloadFormat.JSON, null,
            "pending", null, null, null, null, 0, 0, 0L, 1000L, 1000L, null, null);
        repo.save(j);
        repo.updateStatus("ing_t2", "fetching", null, 2000L);
        assertThat(repo.findById("ing_t2").orElseThrow().status()).isEqualTo("fetching");
    }
}
