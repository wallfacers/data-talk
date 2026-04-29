package com.datatalk.adapter.controller;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.mode=never")
class FileArtifactControllerIT {

    private static final String SESSION_ID = "ses-file-controller-it";
    private static final String OTHER_SESSION_ID = "ses-file-controller-other";

    @Autowired
    WebApplicationContext ctx;

    @Autowired
    FileArtifactRepository repo;

    @Autowired
    @Qualifier("datatalkJdbc")
    JdbcTemplate jdbc;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM file_artifact");
        mvc = MockMvcBuilders.webAppContextSetup(ctx).build();
    }

    @Test
    void getSessionFilesReturnsSessionArtifacts() throws Exception {
        repo.insert(stub("file-controller-a1", FileArtifactStatus.TEMPORARY, SESSION_ID, null));
        repo.insert(stub("file-controller-a2", FileArtifactStatus.CANDIDATE, SESSION_ID, null));
        repo.insert(stub("file-controller-a3", FileArtifactStatus.TEMPORARY, OTHER_SESSION_ID, null));

        mvc.perform(get("/api/sessions/{sessionId}/files", SESSION_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].scope", everyItem(is("session"))))
            .andExpect(jsonPath("$[*].kind", everyItem(is("other"))))
            .andExpect(jsonPath("$[*].status", containsInAnyOrder("temporary", "candidate")));
    }

    @Test
    void postMarkCandidatePromotesStatus() throws Exception {
        repo.insert(stub("file-controller-candidate", FileArtifactStatus.TEMPORARY, SESSION_ID, null));

        mvc.perform(post("/api/files/{fileArtifactId}/mark-candidate", "file-controller-candidate"))
            .andExpect(status().isNoContent());

        assertThat(repo.findById("file-controller-candidate").orElseThrow().status())
            .isEqualTo(FileArtifactStatus.CANDIDATE);
    }

    private static FileArtifact stub(String id, FileArtifactStatus status, String sessionId, String connectionId) {
        Instant now = Instant.now();
        return new FileArtifact(
            id,
            FileArtifactScope.SESSION,
            status,
            FileArtifactKind.OTHER,
            sessionId,
            connectionId,
            id + ".md",
            "/abs/sessions/" + sessionId + "/" + id + ".md",
            100L,
            "text/markdown",
            null,
            null,
            now,
            now,
            null,
            Map.of()
        );
    }
}
