package com.datatalk.adapter.actions;

import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RenderChartActionTest {

    @Autowired RenderChartAction action;
    @Autowired ArtifactRepository artifacts;
    @Autowired SessionRepository sessions;
    @Autowired JdbcTemplate datatalkJdbc;

    @BeforeEach
    void clean() {
        datatalkJdbc.update("DELETE FROM artifacts");
        datatalkJdbc.update("DELETE FROM sessions");
        sessions.upsert(new SessionRecord("s-1", null, "T", true, null, 0L, 0L, false));
        artifacts.insert(new ArtifactRecord("art-src", 1, "s-1", "table", "c-1",
            "INLINE:[]", 2, null, null, false, 0L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsNewArtifactAndReturnsId() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-r1", null, "oc-1"),
            Map.of(
                "sourceArtifactId", "art-src",
                "echartsOption", Map.of(
                    "xAxis", Map.of("type", "category", "data", List.of("a","b","c")),
                    "yAxis", Map.of("type", "value"),
                    "series", List.of(Map.of("type", "line", "data", List.of(1,2,3)))
                )
            )
        ).toCompletableFuture().get();

        assertThat(out.get("artifactId")).isNotNull();
        assertThat(out.get("version")).isEqualTo(1);
    }

    @Test
    void rejectsMissingEchartsOption() {
        assertThat(
            action.handle(new ActionContext("s-1","c","q","oc"),
                Map.of("sourceArtifactId","art-src"))
                .toCompletableFuture()
        ).isCompletedExceptionally();
    }
}
