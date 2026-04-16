package com.datatalk.adapter.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "datatalk.persistence.enabled=false",
    "spring.sql.init.mode=never",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
class DiscoveryControllerIT {

    @Autowired
    MockMvc mvc;

    @Test
    void listsActionsReturnsArray() throws Exception {
        mvc.perform(get("/api/actions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actions").isArray());
    }

    @Test
    void listsOntologyReturnsArray() throws Exception {
        mvc.perform(get("/api/ontology"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.objects").isArray());
    }

    @Test
    void getUnknownActionReturns404() throws Exception {
        mvc.perform(get("/api/actions/does.not.exist"))
            .andExpect(status().isNotFound());
    }
}
