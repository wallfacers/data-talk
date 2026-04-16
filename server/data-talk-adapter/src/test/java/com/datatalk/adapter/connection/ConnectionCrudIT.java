package com.datatalk.adapter.connection;

import com.datatalk.application.connection.ConnectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConnectionCrudIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate datatalkJdbc;
    @Autowired ConnectionService connSvc;

    @BeforeEach
    void clean() { connSvc.deleteAll(); }

    @Test
    void createListRoundTrip() throws Exception {
        String body = om.writeValueAsString(Map.of(
            "id", "c1",
            "kind", "postgresql",
            "host", "localhost",
            "port", 5432,
            "database", "demo",
            "username", "alice",
            "password", "secret123"
        ));

        mvc.perform(post("/api/connections").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        mvc.perform(get("/api/connections"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connections[0].id").value("c1"))
            .andExpect(jsonPath("$.connections[0].username").value("alice"));

        // password never leaks
        mvc.perform(get("/api/connections"))
            .andExpect(jsonPath("$.connections[0].password").doesNotExist())
            .andExpect(jsonPath("$.connections[0].passwordEnc").doesNotExist());
    }
}
