package com.datatalk.adapter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class IngestionControllerCredentialTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void createListReadDeleteRoundTrip() throws Exception {
        String body = om.writeValueAsString(Map.of(
            "name", "ctl-test-1", "authScheme", "bearer",
            "configNonSecret", Map.of(), "secret", "tok_abc"));
        var create = mvc.perform(post("/api/ingestion/credentials")
                .contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andReturn();
        String id = om.readTree(create.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(get("/api/ingestion/credentials"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.id=='" + id + "')]").exists());

        mvc.perform(delete("/api/ingestion/credentials/" + id))
            .andExpect(status().isNoContent());
    }

    @Test
    void createWithNoneSchemeAcceptsNullSecret() throws Exception {
        String body = om.writeValueAsString(Map.of(
            "name", "ctl-test-2", "authScheme", "none", "configNonSecret", Map.of()));
        mvc.perform(post("/api/ingestion/credentials")
                .contentType("application/json").content(body))
            .andExpect(status().isOk());
    }
}
