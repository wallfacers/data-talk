package com.datatalk.adapter.smoke;

import com.datatalk.DataTalkApplication;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = DataTalkApplication.class
)
class EndToEndSmokeIT {

    static WireMockServer openCode;

    @LocalServerPort int port;
    @Autowired ObjectMapper om;
    @Autowired SessionRepository sessions;
    @Autowired OpenCodeSessionMap map;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate datatalkJdbc;

    @DynamicPropertySource
    static void wireOpenCodeBaseUrl(DynamicPropertyRegistry reg) {
        if (openCode == null) {
            openCode = new WireMockServer(WireMockConfiguration.options().dynamicPort());
            openCode.start();
            openCode.stubFor(post(urlPathEqualTo("/plugin/register-tool"))
                .willReturn(aResponse().withStatus(204)));
        }
        reg.add("datatalk.opencode.base-url", () -> "http://localhost:" + openCode.port());
        reg.add("datatalk.opencode.plugin-callback-base", () -> "http://localhost:8080");
    }

    @AfterAll
    void stop() {
        if (openCode != null) {
            openCode.stop();
        }
    }

    @Test
    void skipsToolRegistrationWhenEmbeddedServeDisabled() {
        await().during(java.time.Duration.ofSeconds(1))
            .atMost(java.time.Duration.ofSeconds(2))
            .untilAsserted(() -> openCode.verify(
                0,
                postRequestedFor(urlPathEqualTo("/plugin/register-tool"))
            ));
    }

    @Test
    void opencodeToolCallInvokesHandlerAndReturnsOutput() throws Exception {
        datatalkJdbc.update("DELETE FROM sessions");
        sessions.upsert(new SessionRecord("s-smoke", null, "T", true, "oc-smoke", 0L, 0L, false));
        map.bind("s-smoke", "oc-smoke");

        WebClient client = WebClient.create("http://localhost:" + port);
        String response = client.post()
            .uri("/api/opencode-tool/datatalk.demo.echo")
            .header("X-OpenCode-Call-Id", "call-smoke-1")
            .header("X-OpenCode-Session-Id", "oc-smoke")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("text", "hello"))
            .retrieve()
            .bodyToMono(String.class)
            .block();

        assertThat(response).contains("\"reversed\":\"olleh\"");
    }
}
