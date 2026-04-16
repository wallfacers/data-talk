package com.datatalk.adapter.actions;

import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SupersedeArtifactActionTest {

    @Autowired
    SupersedeArtifactAction action;

    @Autowired
    ArtifactRepository artifacts;

    @Test
    @SuppressWarnings("unchecked")
    void returnsOkForExistingArtifacts() throws Exception {
        String aid = "art-sup-" + System.nanoTime();
        String bid = "art-sup-old-" + System.nanoTime();
        artifacts.insert(new ArtifactRecord(aid, 1, "s-1", "table", "c-1",
            "INLINE:[]", 2, null, null, false, 0L));
        artifacts.insert(new ArtifactRecord(bid, 1, "s-1", "chart", "c-2",
            "INLINE:{}", 2, null, null, false, 0L));

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
            "INLINE:[]", 2, null, null, false, 0L));

        assertThat(
            action.handle(new ActionContext("s-1", "c", null, "oc"),
                Map.of("newArtifactId", aid, "oldArtifactId", "nonexistent-" + System.nanoTime()))
                .toCompletableFuture()
        ).isCompletedExceptionally();
    }
}
