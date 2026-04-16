package com.datatalk.adapter.ontology;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OntologyRegistrationIT {

    @Autowired MockMvc mvc;

    @Test
    void allFourMvpObjectTypesAreRegistered() throws Exception {
        mvc.perform(get("/api/ontology"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.objects[*].id",
                org.hamcrest.Matchers.hasItems(
                    "datatalk.connection",
                    "datatalk.session",
                    "datatalk.artifact",
                    "datatalk.action_invocation"
                )));
    }
}
