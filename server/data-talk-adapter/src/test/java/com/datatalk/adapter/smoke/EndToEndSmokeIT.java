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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
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
            openCode.stubFor(get(urlPathEqualTo("/global/event"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody("")));
        }
        reg.add("datatalk.opencode.base-url", () -> "http://localhost:" + openCode.port());
        reg.add("datatalk.opencode.plugin-callback-base", () -> "http://localhost:8080");
        reg.add("datatalk.opencode.register-external-on-startup", () -> "true");
    }

    @AfterAll
    void stop() {
        if (openCode != null) {
            openCode.stop();
        }
    }

    @Test
    void registersToolsAndStartsEventLoopAgainstExternalOpenCodeWhenEmbeddedServeDisabled() {
        await()
            .atMost(java.time.Duration.ofSeconds(2))
            .untilAsserted(() -> openCode.verify(
                postRequestedFor(urlPathEqualTo("/plugin/register-tool"))
            ));

        await()
            .atMost(java.time.Duration.ofSeconds(2))
            .untilAsserted(() -> openCode.verify(
                getRequestedFor(urlPathEqualTo("/global/event"))
            ));
    }

    @Test
    void registersKnownProductionActionIdsOnly() {
        await()
            .atMost(java.time.Duration.ofSeconds(2))
            .untilAsserted(() -> {
                List<String> requestBodies = openCode.findAll(postRequestedFor(urlPathEqualTo("/plugin/register-tool")))
                    .stream()
                    .map(request -> request.getBodyAsString())
                    .toList();
                assertThat(requestBodies).anyMatch(body -> body.contains("\"name\":\"datatalk.list_connections\""));
                assertThat(requestBodies).anyMatch(body -> body.contains("\"name\":\"datatalk.ui.read\""));
                assertThat(requestBodies).noneMatch(body -> body.contains("datatalk.demo.echo"));
            });
    }

    @Test
    void registersPreciseUiSchemasForModelUse() {
        await()
            .atMost(java.time.Duration.ofSeconds(2))
            .untilAsserted(() -> {
                String uiList = registrationBody("datatalk.ui.list");
                String uiPatch = registrationBody("datatalk.ui.patch");
                String uiExec = registrationBody("datatalk.ui.exec");

                assertThat(uiList)
                    .contains("workspace")
                    .contains("query_editor")
                    .contains("connectionId")
                    .contains("database");
                assertThat(uiPatch)
                    .contains("query_editor")
                    .contains("replace")
                    .contains("/content")
                    .contains("/connectionId")
                    .contains("/database")
                    .contains("/schema");
                assertThat(uiExec)
                    .contains("workspace")
                    .contains("query_editor")
                    .contains("choose_connection")
                    .contains("apply_text_edits")
                    .contains("set_context")
                    .contains("connection_id")
                    .contains("baseVersion")
                    .contains("preferredConnectionId");
            });
    }

    @Test
    void opencodeToolCallInvokesHandlerAndReturnsOutput() throws Exception {
        datatalkJdbc.update("DELETE FROM sessions");
        sessions.upsert(new SessionRecord("s-smoke", null, "T", true, "oc-smoke", 0L, 0L, false));
        map.bind("s-smoke", "oc-smoke");

        WebClient client = WebClient.create("http://localhost:" + port);
        String response = client.post()
            .uri("/api/opencode-tool/datatalk.list_connections")
            .header("X-OpenCode-Call-Id", "call-smoke-1")
            .header("X-OpenCode-Session-Id", "oc-smoke")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of())
            .retrieve()
            .bodyToMono(String.class)
            .block();

        assertThat(response).contains("\"connections\"");
    }

    private String registrationBody(String toolName) {
        Optional<String> body = openCode.findAll(postRequestedFor(urlPathEqualTo("/plugin/register-tool")))
            .stream()
            .map(request -> request.getBodyAsString())
            .filter(requestBody -> requestBody.contains("\"name\":\"" + toolName + "\""))
            .findFirst();

        assertThat(body)
            .as("registered tool %s should be pushed to OpenCode", toolName)
            .isPresent();
        return body.orElseThrow();
    }
}
