package com.datatalk.adapter.persistence;

import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ArtifactRepositoryIT {

    @Autowired ArtifactRepository repo;
    @Autowired SessionRepository sessRepo;

    @Test
    void insertAndList() {
        String sid = "sess-art-" + System.nanoTime();
        sessRepo.upsert(new SessionRecord(sid, null, "T", true, null, 100L, 100L, false));
        String aid = "art-insert-" + System.nanoTime();
        repo.insert(new ArtifactRecord(aid, 1, sid, "table", "call-1",
            "INLINE:{}", 20, null, null, false, 101L));
        List<ArtifactRecord> all = repo.findBySession(sid);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).kind()).isEqualTo("table");
    }

    @Test
    void findLatestByIdReturnsHighestVersion() {
        String sid = "sess-artl-" + System.nanoTime();
        sessRepo.upsert(new SessionRecord(sid, null, "T", true, null, 100L, 100L, false));
        String aid = "art-latest-" + System.nanoTime();
        repo.insert(new ArtifactRecord(aid, 1, sid, "chart", "call-1", "INLINE:{}", 2, null, null, false, 100L));
        repo.insert(new ArtifactRecord(aid, 2, sid, "chart", "call-2", "INLINE:{}", 2, aid, 1, false, 200L));
        assertThat(repo.findLatestById(aid).orElseThrow().version()).isEqualTo(2);
    }
}
