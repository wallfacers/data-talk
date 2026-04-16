package com.datatalk.adapter.persistence;

import com.datatalk.application.persistence.EventRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EventRepositoryIT {

    @Autowired EventRepository repo;
    @Autowired SessionRepository sessRepo;

    @Test
    void appendsAndRestoresMaxEventId() {
        String sid = "sess-evt-" + System.nanoTime();
        sessRepo.upsert(new SessionRecord(sid, null, "T", true, null, 100L, 100L));
        repo.append(sid, 1, "connected", "{\"sessionId\":\"" + sid + "\"}", 100L);
        repo.append(sid, 2, "heartbeat", "{\"ts\":101}", 101L);
        assertThat(repo.maxEventId(sid)).isEqualTo(2);
    }

    @Test
    void maxEventIdReturnsZeroWhenEmpty() {
        String sid = "sess-empty-" + System.nanoTime();
        sessRepo.upsert(new SessionRecord(sid, null, "T", true, null, 100L, 100L));
        assertThat(repo.maxEventId(sid)).isZero();
    }
}
