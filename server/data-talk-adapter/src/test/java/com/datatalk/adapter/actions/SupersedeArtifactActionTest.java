package com.datatalk.adapter.actions;

import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class SupersedeArtifactActionTest {

    @Autowired
    SupersedeArtifactAction action;

    @Autowired
    ArtifactRepository artifacts;

    @Autowired
    @Qualifier("datatalkJdbc")
    JdbcTemplate datatalkJdbc;

    @BeforeEach
    void clean() {
        datatalkJdbc.update("DELETE FROM artifacts");
        datatalkJdbc.update("DELETE FROM sessions");
        datatalkJdbc.update("DELETE FROM connections");
        datatalkJdbc.update("""
            INSERT INTO connections(id, name, kind, host, port, username, password_enc, created_at)
            VALUES('c-default', 'Default Connection', 'mysql', 'h', 3306, 'u', x'00', 0)
            """);
        datatalkJdbc.update("""
            INSERT INTO sessions(id, connection_id, title, created_at, updated_at)
            VALUES('s-1', 'c-default', 't', 0, 0)
            """);
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsOkForExistingArtifacts() throws Exception {
        String aid = "art-sup-" + System.nanoTime();
        String bid = "art-sup-old-" + System.nanoTime();
        artifacts.insert(new ArtifactRecord(aid, 1, "s-1", "table", "c-1",
            "INLINE:[]", 2, null, null, false, 0L, null, null));
        artifacts.insert(new ArtifactRecord(bid, 1, "s-1", "chart", "c-2",
            "INLINE:{}", 2, null, null, false, 0L, null, null));

        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-s1", null, "oc-1"),
            Map.of("newArtifactId", aid, "oldArtifactId", bid)
        ).toCompletableFuture().get();

        assertThat(out.get("ok")).isEqualTo(true);
    }

    @Test
    void failsForMissingArtifact() {
        String aid = "art-sup-n-" + System.nanoTime();
        artifacts.insert(new ArtifactRecord(aid, 1, "s-1", "table", "c-1",
            "INLINE:[]", 2, null, null, false, 0L, null, null));

        assertThat(
            action.handle(new ActionContext("s-1", "c", null, "oc"),
                Map.of("newArtifactId", aid, "oldArtifactId", "nonexistent-" + System.nanoTime()))
                .toCompletableFuture()
        ).isCompletedExceptionally();
    }
}
