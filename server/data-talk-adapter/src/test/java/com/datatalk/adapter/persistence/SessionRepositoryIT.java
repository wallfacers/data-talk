package com.datatalk.adapter.persistence;

import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SessionRepositoryIT {

    @Autowired SessionRepository repo;
    @Autowired JdbcTemplate datatalkJdbc;

    @Test
    void savesAndLoadsSession() {
        String id = "sess-saveload-" + System.nanoTime();
        SessionRecord s = new SessionRecord(id, null, "Untitled", false, null, 100L, 100L);
        repo.upsert(s);
        Optional<SessionRecord> found = repo.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().title()).isEqualTo("Untitled");
        assertThat(found.get().hasEverSent()).isFalse();
    }

    @Test
    void markHasEverSentFlipsFlag() {
        String id = "sess-mark-" + System.nanoTime();
        repo.upsert(new SessionRecord(id, null, "T", false, null, 100L, 100L));
        repo.markHasEverSent(id, 200L);
        assertThat(repo.findById(id).orElseThrow().hasEverSent()).isTrue();
    }

    @Test
    void findByIdReturnsEmptyForUnknown() {
        assertThat(repo.findById("nope-" + System.nanoTime())).isEmpty();
    }
}
