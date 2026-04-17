package com.datatalk.adapter.controller;

import com.datatalk.application.ai.OpenCodeProviderClient;
import com.datatalk.infra.opencode.OpenCodeHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiSettingsControllerIT {

    static WireMockServer oc;

    @LocalServerPort int port;

    WebTestClient web;

    @Autowired ObjectMapper objectMapper;

    @TestConfiguration
    static class Override {
        @Bean @Primary
        OpenCodeProviderClient openCodeProviderClient(ObjectMapper om) {
            return new OpenCodeHttpClient("http://localhost:" + oc.port(), om);
        }
    }

    @BeforeAll
    static void up() {
        oc = new WireMockServer(wireMockConfig().dynamicPort());
        oc.start();
    }

    @AfterAll
    static void down() {
        oc.stop();
    }

    @BeforeEach
    void setup() {
        web = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        oc.resetAll();
    }

    @Test
    void list_providers_transparently_proxies() {
        oc.stubFor(get("/provider").willReturn(okJson("""
            {"all":[{"id":"openai","name":"OpenAI","models":{}}],
             "connected":["openai"]}
            """)));
        web.get().uri("/api/ai/providers").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.connected[0]").isEqualTo("openai");
    }

    @Test
    void provider_auth_proxies() {
        oc.stubFor(get("/provider/auth").willReturn(okJson("""
            {"openai":[{"type":"api"}]}
            """)));
        web.get().uri("/api/ai/providers/auth").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.openai[0].type").isEqualTo("api");
    }

    @Test
    void put_credentials_forwards_body() {
        oc.stubFor(put("/auth/openai").willReturn(aResponse().withStatus(200)));
        web.put().uri("/api/ai/providers/openai/credentials")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"type":"api","key":"sk-test"}
                """)
            .exchange()
            .expectStatus().isNoContent();
        oc.verify(putRequestedFor(urlEqualTo("/auth/openai"))
            .withRequestBody(matchingJsonPath("$.key", equalTo("sk-test"))));
    }

    @Test
    void opencode_down_returns_503() {
        oc.stubFor(get("/provider").willReturn(aResponse().withStatus(500)));
        web.get().uri("/api/ai/providers").exchange()
            .expectStatus().isEqualTo(503)
            .expectBody().jsonPath("$.error").isEqualTo("OPENCODE_UNAVAILABLE");
    }
}
