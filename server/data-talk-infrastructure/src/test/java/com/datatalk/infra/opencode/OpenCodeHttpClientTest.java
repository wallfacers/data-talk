package com.datatalk.infra.opencode;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenCodeHttpClientTest {

    WireMockServer wm;
    OpenCodeHttpClient client;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
        client = new OpenCodeHttpClient("http://localhost:" + wm.port(), new ObjectMapper());
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void createSessionPostsAndParsesResponse() {
        wm.stubFor(post(urlEqualTo("/session"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"oc-1\"}")));

        String id = client.createSession();

        assertThat(id).isEqualTo("oc-1");
        wm.verify(postRequestedFor(urlEqualTo("/session")));
    }

    @Test
    void registerToolSendsNameAndCallback() {
        wm.stubFor(post(urlPathEqualTo("/plugin/register-tool"))
            .willReturn(aResponse().withStatus(204)));

        client.registerTool("datatalk.demo.echo", "echo",
            Map.of("type", "object"),
            "http://localhost:8080/api/opencode-tool/datatalk.demo.echo");

        String expectedJson = "{\"name\":\"datatalk.demo.echo\",\"description\":\"echo\",\"parameters\":{\"type\":\"object\"},\"callbackUrl\":\"http://localhost:8080/api/opencode-tool/datatalk.demo.echo\"}";
        wm.verify(postRequestedFor(urlPathEqualTo("/plugin/register-tool"))
            .withRequestBody(equalToJson(expectedJson, true, true)));
    }

    @Test
    void listProviders_returns_connected_and_all() {
        wm.stubFor(get(urlPathEqualTo("/provider"))
            .willReturn(okJson("""
                {"all":[{"id":"openai","name":"OpenAI","models":{"gpt-5":{"id":"gpt-5","name":"GPT-5"}}}],
                 "default":{"openai":"gpt-5"},
                 "connected":["openai"]}
                """)));
        var node = client.listProviders();
        assertThat(node.get("connected").get(0).asText()).isEqualTo("openai");
        assertThat(node.get("all").get(0).get("id").asText()).isEqualTo("openai");
    }

    @Test
    void getProviderAuth_returns_methods_per_provider() {
        wm.stubFor(get(urlPathEqualTo("/provider/auth"))
            .willReturn(okJson("""
                {"openai":[{"type":"api","label":"API Key"}],
                 "anthropic":[{"type":"oauth"},{"type":"api","label":"API Key"}]}
                """)));
        var node = client.getProviderAuth();
        assertThat(node.get("openai").get(0).get("type").asText()).isEqualTo("api");
    }

    @Test
    void putAuth_posts_body_to_provider_endpoint() {
        wm.stubFor(put(urlEqualTo("/auth/openai"))
            .willReturn(aResponse().withStatus(200)));
        client.putAuth("openai", Map.of("type", "api", "key", "sk-test"));
        wm.verify(putRequestedFor(urlEqualTo("/auth/openai"))
            .withRequestBody(matchingJsonPath("$.type", equalTo("api")))
            .withRequestBody(matchingJsonPath("$.key", equalTo("sk-test"))));
    }

    @Test
    void putAuth_bubbles_upstream_4xx_as_runtime_with_status() {
        wm.stubFor(put(urlEqualTo("/auth/openai"))
            .willReturn(aResponse().withStatus(400).withBody("invalid key format")));
        assertThatThrownBy(() -> client.putAuth("openai",
                Map.of("type", "api", "key", "x")))
            .isInstanceOf(org.springframework.web.reactive.function.client.WebClientResponseException.class);
    }

    @Test
    void listMessagesReturnsOpenCodePayload() {
        wm.stubFor(get(urlPathEqualTo("/session/ses_abc/message"))
            .withQueryParam("limit", equalTo("100"))
            .willReturn(okJson("""
                [
                  {
                    "info": { "id": "msg_1", "role": "user", "sessionID": "ses_abc",
                              "time": { "created": 1776000000000 } },
                    "parts": [
                      { "type": "text", "text": "hi", "id": "prt_1",
                        "sessionID": "ses_abc", "messageID": "msg_1" }
                    ]
                  }
                ]
                """)));

        JsonNode node = client.listMessages("ses_abc", 100);

        assertThat(node.isArray()).isTrue();
        assertThat(node).hasSize(1);
        assertThat(node.get(0).path("info").path("id").asText()).isEqualTo("msg_1");
        assertThat(node.get(0).path("parts").get(0).path("text").asText()).isEqualTo("hi");
    }

    @Test
    void listMessagesReturnsEmptyArrayWhenSessionHasNoMessages() {
        wm.stubFor(get(urlPathEqualTo("/session/ses_empty/message"))
            .willReturn(okJson("[]")));

        JsonNode node = client.listMessages("ses_empty", null);

        assertThat(node.isArray()).isTrue();
        assertThat(node).hasSize(0);
    }
}
