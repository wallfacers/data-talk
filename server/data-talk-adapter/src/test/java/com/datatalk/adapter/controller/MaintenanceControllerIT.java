package com.datatalk.adapter.controller;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.fileartifact.SessionWorkdirRoot;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.mode=never")
class MaintenanceControllerIT {

    @Autowired
    WebApplicationContext ctx;

    @Autowired
    FileArtifactRepository repo;

    @Autowired
    SessionWorkdirRoot workdirRoot;

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
    void storageOverview_returns_200_with_breakdown() throws Exception {
        mvc.perform(get("/api/maintenance/storage-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workdir").isString())
                .andExpect(jsonPath("$.breakdown.opencodeInfra").exists())
                .andExpect(jsonPath("$.breakdown.workspaces").exists())
                .andExpect(jsonPath("$.breakdown.trash").exists());
    }

    @Test
    void orphanedFiles_returns_200_and_list() throws Exception {
        mvc.perform(get("/api/maintenance/orphaned-files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void cleanupTrash_returns_200() throws Exception {
        mvc.perform(post("/api/maintenance/cleanup-trash"))
                .andExpect(status().isOk());
    }

    @Test
    void reattach_returns_404_for_unknown_fid() throws Exception {
        mvc.perform(post("/api/maintenance/files/fa_unknown/reattach")
                        .contentType("application/json")
                        .content("{\"connectionId\":\"conn_new\"}"))
                .andExpect(status().isNotFound());
    }

    private FileArtifact stub(String id, FileArtifactStatus status, String sessionId, String connectionId) {
        return new FileArtifact(
                id, FileArtifactScope.WORKSPACE, status, FileArtifactKind.OTHER,
                sessionId, connectionId, "f.md", "/tmp/f.md",
                100L, null, null, null,
                Instant.now(), Instant.now(), Instant.now(),
                Map.of());
    }
}
