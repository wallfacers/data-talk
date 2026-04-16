package com.datatalk.adapter.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SessionControllerIT {

    @Autowired MockMvc mvc;

    @Test
    void create_then_list_returns_session() throws Exception {
        String body = """
            {"connectionId":"conn-1","title":"冒烟测试"}
            """;

        String created = mvc.perform(post("/api/sessions")
                .contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.connectionId").value("conn-1"))
            .andExpect(jsonPath("$.title").value("冒烟测试"))
            .andExpect(jsonPath("$.hasEverSent").value(false))
            .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/sessions").param("connectionId", "conn-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].connectionId").value("conn-1"));
    }

    @Test
    void create_rejects_missing_connection() throws Exception {
        mvc.perform(post("/api/sessions")
                .contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void list_without_filter_returns_all() throws Exception {
        mvc.perform(post("/api/sessions").contentType("application/json")
                .content("{\"connectionId\":\"c-a\",\"title\":\"A\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/sessions").contentType("application/json")
                .content("{\"connectionId\":\"c-b\",\"title\":\"B\"}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/sessions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }
}
