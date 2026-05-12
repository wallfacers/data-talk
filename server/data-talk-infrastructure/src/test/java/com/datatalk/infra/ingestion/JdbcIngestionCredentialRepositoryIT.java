package com.datatalk.infra.ingestion;

import com.datatalk.application.ingestion.repository.IngestionCredentialRepository;
import com.datatalk.domain.ingestion.AuthScheme;
import com.datatalk.domain.ingestion.IngestionCredential;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class JdbcIngestionCredentialRepositoryIT {
    @Autowired IngestionCredentialRepository repo;

    @Test void saveAndFindRoundTrip() {
        var c = new IngestionCredential("cred_t1", "test-bearer", AuthScheme.BEARER,
            Map.of(), "vault_x", 1000L, 1000L);
        repo.save(c);
        var found = repo.findById("cred_t1");
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("test-bearer");
        assertThat(found.get().scheme()).isEqualTo(AuthScheme.BEARER);
        repo.deleteById("cred_t1");
    }

    @Test void findByNameWorks() {
        var c = new IngestionCredential("cred_t2", "name-key", AuthScheme.API_KEY_HEADER,
            Map.of("headerName", "X-Api-Key"), "vault_y", 2000L, 2000L);
        repo.save(c);
        assertThat(repo.findByName("name-key")).isPresent();
        repo.deleteById("cred_t2");
    }

    @Test void countReferencingJobsZeroWhenNoJobs() {
        var c = new IngestionCredential("cred_t3", "unused", AuthScheme.NONE, Map.of(), null, 0L, 0L);
        repo.save(c);
        assertThat(repo.countReferencingJobs("cred_t3")).isEqualTo(0);
        repo.deleteById("cred_t3");
    }
}
