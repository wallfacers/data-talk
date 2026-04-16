package com.datatalk.infra.opencode;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

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

        wm.verify(postRequestedFor(urlPathEqualTo("/plugin/register-tool"))
            .withRequestBody(equalToJson("""
                {"name":"datatalk.demo.echo","description":"echo",
                 "parameters":{"type":"object"},
                 "callbackUrl":"http://localhost:8080/api/opencode-tool/datatalk.demo.echo"}
                """, true, true)));
    }
}
