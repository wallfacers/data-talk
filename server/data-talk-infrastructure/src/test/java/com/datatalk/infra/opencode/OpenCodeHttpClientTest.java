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
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
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

    @Test
    void abortReturnsTrueWhenOpenCodeRespondsTrue() {
        wm.stubFor(post(urlEqualTo("/session/ses_1/abort"))
            .willReturn(okJson("true")));

        boolean aborted = client.abort("ses_1");

        assertThat(aborted).isTrue();
        wm.verify(postRequestedFor(urlEqualTo("/session/ses_1/abort")));
    }

    @Test
    void abortReturnsFalseWhenOpenCodeRespondsFalse() {
        wm.stubFor(post(urlEqualTo("/session/ses_2/abort"))
            .willReturn(okJson("false")));

        boolean aborted = client.abort("ses_2");

        assertThat(aborted).isFalse();
        wm.verify(postRequestedFor(urlEqualTo("/session/ses_2/abort")));
    }

    @Test
    void abortTreatsEmptyBodyAsSuccessForCompatibility() {
        wm.stubFor(post(urlEqualTo("/session/ses_3/abort"))
            .willReturn(aResponse().withStatus(204)));

        boolean aborted = client.abort("ses_3");

        assertThat(aborted).isTrue();
        wm.verify(postRequestedFor(urlEqualTo("/session/ses_3/abort")));
    }

    @Test
    void patchConfigPostsJsonFragment() {
        wm.stubFor(patch(urlEqualTo("/config"))
            .willReturn(okJson("{\"mcp\":{\"datatalk\":{\"type\":\"remote\"}}}")));

        JsonNode node = client.patchConfig(Map.of(
            "mcp", Map.of("datatalk", Map.of("type", "remote"))
        ));

        assertThat(node.path("mcp").path("datatalk").path("type").asText()).isEqualTo("remote");
        wm.verify(patchRequestedFor(urlEqualTo("/config"))
            .withRequestBody(equalToJson("""
                {"mcp":{"datatalk":{"type":"remote"}}}
                """, true, true)));
    }

    @Test
    void addMcpServerPostsNameAndConfig() {
        wm.stubFor(post(urlEqualTo("/mcp"))
            .willReturn(okJson("""
                {"datatalk":{"status":"connected"}}
                """)));

        JsonNode node = client.addMcpServer("datatalk", Map.of(
            "type", "remote",
            "url", "http://127.0.0.1:8080/mcp"
        ));

        assertThat(node.path("datatalk").path("status").asText()).isEqualTo("connected");
        wm.verify(postRequestedFor(urlEqualTo("/mcp"))
            .withRequestBody(equalToJson("""
                {"name":"datatalk","config":{"type":"remote","url":"http://127.0.0.1:8080/mcp"}}
                """, true, true)));
    }

    @Test
    void getMcpStatusReturnsStatusMap() {
        wm.stubFor(get(urlEqualTo("/mcp"))
            .willReturn(okJson("""
                {"datatalk":{"status":"connected"}}
                """)));

        JsonNode node = client.getMcpStatus();

        assertThat(node.path("datatalk").path("status").asText()).isEqualTo("connected");
        wm.verify(getRequestedFor(urlEqualTo("/mcp")));
    }

    @Test
    void getSessionStatuses_returns_busy_entry() {
        wm.stubFor(get(urlEqualTo("/session/status"))
            .willReturn(okJson("""
                {"oc-1":{"type":"busy"},"oc-2":{"type":"retry","attempt":1,"message":"x","next":2}}
                """)));

        JsonNode node = client.getSessionStatuses();

        assertThat(node.path("oc-1").path("type").asText()).isEqualTo("busy");
        assertThat(node.path("oc-2").path("type").asText()).isEqualTo("retry");
        wm.verify(getRequestedFor(urlEqualTo("/session/status")));
    }

    @Test
    void getSessionStatuses_returns_empty_when_no_busy_sessions() {
        wm.stubFor(get(urlEqualTo("/session/status"))
            .willReturn(okJson("{}")));

        JsonNode node = client.getSessionStatuses();

        assertThat(node.isObject()).isTrue();
        assertThat(node.size()).isZero();
    }

    @Test
    void getSessionStatuses_fail_open_on_5xx() {
        wm.stubFor(get(urlEqualTo("/session/status"))
            .willReturn(aResponse().withStatus(503)));

        JsonNode node = client.getSessionStatuses();

        assertThat(node.isObject()).isTrue();
        assertThat(node.size()).isZero();
    }

    @Test
    void getSessionStatuses_fail_open_on_connection_error() {
        wm.stop();
        JsonNode node = client.getSessionStatuses();
        assertThat(node.isObject()).isTrue();
        assertThat(node.size()).isZero();
    }

    @Test
    void listQuestions_returns_pending_request_array() {
        wm.stubFor(get(urlEqualTo("/question"))
            .willReturn(okJson("""
                [
                  {"id":"qst_1","sessionID":"ses_abc",
                   "questions":[{"question":"Continue?","header":"Confirm",
                                 "options":[{"label":"Yes","description":"go"}],
                                 "multiple":false,"custom":true}]}
                ]
                """)));

        JsonNode node = client.listQuestions();

        assertThat(node.isArray()).isTrue();
        assertThat(node.get(0).path("id").asText()).isEqualTo("qst_1");
        assertThat(node.get(0).path("sessionID").asText()).isEqualTo("ses_abc");
        wm.verify(getRequestedFor(urlEqualTo("/question")));
    }

    @Test
    void questionReply_posts_answers_as_string_array_of_arrays() {
        wm.stubFor(post(urlEqualTo("/question/qst_1/reply"))
            .willReturn(okJson("true")));

        boolean ok = client.replyQuestion("qst_1",
            java.util.List.of(java.util.List.of("Yes"), java.util.List.of("A", "B")));

        assertThat(ok).isTrue();
        wm.verify(postRequestedFor(urlEqualTo("/question/qst_1/reply"))
            .withRequestBody(equalToJson("""
                {"answers":[["Yes"],["A","B"]]}
                """, true, true)));
    }

    @Test
    void questionReject_posts_to_reject_endpoint() {
        wm.stubFor(post(urlEqualTo("/question/qst_2/reject"))
            .willReturn(okJson("true")));

        boolean ok = client.rejectQuestion("qst_2");

        assertThat(ok).isTrue();
        wm.verify(postRequestedFor(urlEqualTo("/question/qst_2/reject")));
    }
}
