package com.datatalk.adapter.ontology;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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

    @Test
    void objectDisplayNamesFollowAcceptLanguage() throws Exception {
        mvc.perform(get("/api/ontology/datatalk.connection")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Connection"));

        mvc.perform(get("/api/ontology/datatalk.connection")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("数据源连接"));
    }
}
