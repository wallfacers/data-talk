package com.datatalk.adapter.controller;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.dto.SessionDataContextUpdateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    ConnectionService connections;

    @Autowired
    SessionRepository sessions;

    @Autowired
    SessionDataContextService sessionDataContexts;

    private static final String V3_EMPTY_DASHBOARD = """
        {
          "schemaVersion": 3,
          "id": "dash_placeholder",
          "title": "Test Dashboard",
          "theme": "industry-ecommerce",
          "renderer": "bezel",
          "refresh": { "defaultIntervalMs": 10000, "pauseOnHidden": true },
          "parameters": [],
          "widgets": [],
          "layout": { "engine": "free", "template": "grid-equal" },
          "version": 999
        }
        """;

    @Test
    void fullLifecycle_promoteLoad() throws Exception {
        String promoteResponse = mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dashboard\": %s}".formatted(V3_EMPTY_DASHBOARD)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        JsonNode promoteJson = mapper.readTree(promoteResponse);
        String id = promoteJson.get("id").asText();
        assertThat(id).startsWith("dash_");
        assertThat(promoteJson.get("version").asInt()).isEqualTo(1);

        mvc.perform(get("/api/dashboards/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Test Dashboard"))
            .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void updatePersistsNewVersionAndJson() throws Exception {
        String promoteResponse = mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dashboard\": %s}".formatted(V3_EMPTY_DASHBOARD)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(promoteResponse).get("id").asText();

        String updated = V3_EMPTY_DASHBOARD.replace("\"Test Dashboard\"", "\"Updated Title\"");
        mvc.perform(post("/api/dashboards/" + id + "/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dashboard\": %s, \"baseVersion\": 1}".formatted(updated)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(2));

        // New JSON + version are persisted (GET reflects the update).
        mvc.perform(get("/api/dashboards/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(2))
            .andExpect(jsonPath("$.title").value("Updated Title"));

        // Version actually advanced: the now-stale baseVersion=1 must conflict.
        mvc.perform(post("/api/dashboards/" + id + "/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dashboard\": %s, \"baseVersion\": 1}".formatted(updated)))
            .andExpect(status().isConflict());
    }

    @Test
    void patchEndpointRemoved() throws Exception {
        mvc.perform(patch("/api/dashboards/dash_test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void getReturns404ForUnknownId() throws Exception {
        mvc.perform(get("/api/dashboards/dash_nonexistent"))
            .andExpect(status().isNotFound());
    }

    @Test
    void promoteEnrichesDefaultDatabaseSchemaFromSessionHeader() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        String connectionId = connections.create(
            "enrich-conn-" + suffix, "h2", "localhost", 0,
            "<unused-database-name>", "sa", "", 3000,
            null, null, null, null, null, null, null, null);
        String sessionId = "promote-enrich-sess-" + suffix;
        sessions.upsert(new SessionRecord(sessionId, connectionId, "promote enrich", false, null, 1L, 1L, false));
        sessionDataContexts.set(sessionId, new SessionDataContextUpdateRequest(
            connectionId, "test_store", "public", "schema"
        ));

        String body = """
            {
              "dashboard": {
                "schemaVersion": 3,
                "id": "dash_placeholder",
                "title": "Enrichment Test",
                "theme": "industry-ecommerce",
                "renderer": "bezel",
                "defaultConnectionId": "%s",
                "refresh": { "defaultIntervalMs": 10000, "pauseOnHidden": true },
                "parameters": [],
                "widgets": [],
                "layout": { "engine": "free", "template": "grid-equal" },
                "version": 999
              }
            }
            """.formatted(connectionId);

        String promoteResponse = mvc.perform(post("/api/dashboards/promote")
                .header(DashboardController.SESSION_ID_HEADER, sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(promoteResponse).get("id").asText();

        mvc.perform(get("/api/dashboards/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.defaultConnectionId").value(connectionId))
            .andExpect(jsonPath("$.defaultDatabase").value("test_store"))
            .andExpect(jsonPath("$.defaultSchema").value("public"));
    }

    @Test
    void promoteWithoutSessionHeaderSkipsEnrichment() throws Exception {
        mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dashboard\": %s}".formatted(V3_EMPTY_DASHBOARD)))
            .andExpect(status().isCreated());
    }

    @Test
    void widgetDataAcceptsNullOriginCorsPreflightForSandboxedIframe() throws Exception {
        mvc.perform(options("/api/dashboards/dash_anything/widgets/w_x/data")
                .header("Origin", "null")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "null"))
            .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")));
    }

    @Test
    void serveHtmlRewritesOriginPlaceholder() throws Exception {
        String promoteResponse = mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dashboard\": %s}".formatted(V3_EMPTY_DASHBOARD)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(promoteResponse).get("id").asText();

        var res = mvc.perform(get("/api/dashboards/" + id + "/html"))
            .andExpect(status().isOk())
            .andReturn().getResponse();

        assertThat(res.getContentType()).containsIgnoringCase("charset=UTF-8");
        String decoded = new String(res.getContentAsByteArray(), StandardCharsets.UTF_8);

        // Origin placeholder must be replaced
        assertThat(decoded).doesNotContain("__BEZEL_SERVER_ORIGIN__");
        assertThat(decoded).contains("http://localhost");
    }
}
