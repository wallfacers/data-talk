package com.datatalk.adapter.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.sql.init.mode=never",
    "spring.datasource.url=jdbc:h2:mem:discovery-test;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.sqlite-datasource.url=jdbc:sqlite::memory:",
    "spring.sqlite-datasource.driver-class-name=org.sqlite.JDBC",
    "datatalk.master-key-hex=0000000000000000000000000000000000000000000000000000000000000000",
    "datatalk.opencode.base-url=http://localhost:4096",
    "datatalk.opencode.callback-base=http://localhost:8080"
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

    @Test
    void actionsPayloadCarriesRiskLevelAndCategory() throws Exception {
        mvc.perform(get("/api/actions"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.actions[?(@.id == 'datatalk.execute_sql')].riskLevel")
               .value(hasItem("L1")))
           .andExpect(jsonPath("$.actions[?(@.id == 'datatalk.execute_sql')].category")
               .value(hasItem("QUERY")))
           .andExpect(jsonPath("$.actions[?(@.id == 'datatalk.read_schema')].category")
               .value(hasItem("METADATA")));
    }
}
