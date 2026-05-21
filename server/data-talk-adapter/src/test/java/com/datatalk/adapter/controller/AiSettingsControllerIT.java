package com.datatalk.adapter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiSettingsControllerIT {

    static WireMockServer oc;

    @LocalServerPort int port;

    WebTestClient web;

    @Autowired ObjectMapper objectMapper;

    @DynamicPropertySource
    static void wireOpenCodeBaseUrl(DynamicPropertyRegistry registry) {
        if (oc == null) {
            oc = new WireMockServer(wireMockConfig().dynamicPort());
            oc.start();
        }
        registry.add("datatalk.opencode.base-url", () -> "http://localhost:" + oc.port());
    }

    @AfterAll
    static void down() {
        if (oc != null) {
            oc.stop();
        }
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
            .jsonPath("$.all[0].id").isEqualTo("openai")
            .jsonPath("$.connected").isArray()
            // connected may include auth.json entries, but must contain openai
            .jsonPath("$.connected[?(@ == 'openai')]").isNotEmpty();
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

    @Test
    void list_models_merges_prefs() {
        oc.stubFor(get("/provider").willReturn(okJson("""
            {"all":[{"id":"openai","name":"OpenAI","models":{
                "gpt-5":{"id":"gpt-5","name":"GPT-5"}}}],
             "connected":["openai"]}
            """)));
        web.get().uri("/api/ai/models").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.providers[0].id").isEqualTo("openai")
            .jsonPath("$.providers[0].connected").isEqualTo(true)
            .jsonPath("$.providers[0].models[0].enabled").isEqualTo(true);
    }

    @Test
    void patch_model_disables() {
        oc.stubFor(get("/provider").willReturn(okJson("""
            {"all":[{"id":"openai","name":"OpenAI","models":{"gpt-5":{"id":"gpt-5","name":"GPT-5"}}}],
             "connected":["openai"]}
            """)));
        web.patch().uri("/api/ai/models/openai/gpt-5")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"enabled\":false}")
            .exchange()
            .expectStatus().isNoContent();
        web.get().uri("/api/ai/models").exchange()
            .expectBody().jsonPath("$.providers[0].models[0].enabled").isEqualTo(false);
    }

    @Test
    void current_model_round_trip() {
        web.patch().uri("/api/ai/current-model")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"modelId\":\"openai/gpt-5\"}")
            .exchange().expectStatus().isNoContent();
        web.get().uri("/api/ai/current-model").exchange()
            .expectBody().jsonPath("$.modelId").isEqualTo("openai/gpt-5");
    }
}
