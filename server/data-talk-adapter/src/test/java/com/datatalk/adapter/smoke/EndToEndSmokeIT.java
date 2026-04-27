package com.datatalk.adapter.smoke;

import com.datatalk.DataTalkApplication;
import com.datatalk.application.opencode.McpNameMapper;
import com.datatalk.application.opencode.OpenCodeBridgeStatus;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/*
 * This smoke test verifies DataTalk's MCP endpoint and bootstrap/reconcile logic
 * without starting a real OpenCode process. Real OpenCode plugin injection is
 * covered by RealOpenCodeMcpBridgeIT when DATATALK_REAL_OPENCODE_E2E=true.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = DataTalkApplication.class
)
class EndToEndSmokeIT {

    private static final String RECONCILE_SCENARIO = "external-mcp-reconcile";

    static WireMockServer openCode;
    static Path configDir;

    @LocalServerPort int port;
    @Autowired ObjectMapper om;
    @Autowired SessionRepository sessions;
    @Autowired OpenCodeSessionMap map;
    @Autowired ActionRegistry registry;
    @Autowired SessionBusRegistry buses;
    @Autowired OpenCodeBridgeStatus bridgeStatus;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate datatalkJdbc;

    @DynamicPropertySource
    static void wireOpenCodeBaseUrl(DynamicPropertyRegistry reg) throws IOException {
        if (openCode == null) {
            openCode = new WireMockServer(WireMockConfiguration.options().dynamicPort());
            openCode.start();
            stubExternalReconcileFlow();
        }
        if (configDir == null) {
            configDir = Files.createTempDirectory("datatalk-smoke-opencode-");
        }
        reg.add("datatalk.opencode.base-url", () -> "http://localhost:" + openCode.port());
        reg.add("datatalk.mcp.enabled", () -> "true");
        reg.add("datatalk.mcp.config-dir", () -> configDir.toString());
        reg.add("datatalk.opencode.serve.enabled", () -> "false");
    }

    @AfterAll
    static void stop() throws IOException {
        if (openCode != null) {
            openCode.stop();
        }
        if (configDir != null && Files.exists(configDir)) {
            try (var paths = Files.walk(configDir)) {
                paths.sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
            }
        }
    }

    @BeforeEach
    void setUp() {
        datatalkJdbc.update("DELETE FROM events");
        datatalkJdbc.update("DELETE FROM action_invocations");
        datatalkJdbc.update("DELETE FROM artifacts");
        datatalkJdbc.update("DELETE FROM query_results");
        datatalkJdbc.update("DELETE FROM connections");
        datatalkJdbc.update("DELETE FROM sessions");
    }

    @Test
    void reconcilesExternalMcpBeforeStartingEventLoop() {
        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> openCode.verify(
                patchRequestedFor(urlPathEqualTo("/config"))
                    .withRequestBody(matchingJsonPath("$.mcp.datatalk.type", equalTo("remote")))
                    .withRequestBody(matchingJsonPath("$.mcp.datatalk.url",
                        matching("http://127\\.0\\.0\\.1:\\d+/mcp")))
            ));

        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> openCode.verify(
                postRequestedFor(urlPathEqualTo("/mcp"))
                    .withRequestBody(matchingJsonPath("$.name", equalTo("datatalk")))
                    .withRequestBody(matchingJsonPath("$.config.type", equalTo("remote")))
            ));

        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> openCode.verify(
                getRequestedFor(urlEqualTo("/mcp"))
            ));

        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> openCode.verify(
                getRequestedFor(urlPathEqualTo("/global/event"))
            ));

        assertThat(bridgeStatus.snapshot().status()).isEqualTo("ok");
        assertThat(bridgeStatus.snapshot().message()).isEqualTo("OpenCode MCP bridge ready");
    }

    @Test
    void writesManagedBootstrapArtifactsForExternalMcp() throws Exception {
        Path instructionsFile = configDir.resolve("AGENTS.md");
        Path pluginFile = configDir.resolve("plugins").resolve("datatalk-mcp-context.js");
        Path configFile = configDir.resolve("opencode.json");

        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(configFile).exists());

        JsonNode config = om.readTree(Files.readString(configFile));
        assertThat(config.path("mcp").path("datatalk").path("type").asText()).isEqualTo("remote");
        assertThat(config.path("mcp").path("datatalk").path("enabled").asBoolean()).isTrue();
        assertThat(config.path("mcp").path("datatalk").path("url").asText())
            .matches("http://127\\.0\\.0\\.1:\\d+/mcp");
        assertThat(config.path("instructions"))
            .extracting(JsonNode::asText)
            .contains(instructionsFile.toString());

        assertThat(Files.readString(instructionsFile))
            .contains("datatalk_execute_sql")
            .doesNotContain("datatalk.execute_sql");
        assertThat(Files.readString(pluginFile))
            .contains("TOOL_PREFIX = 'datatalk_'")
            .contains("__dtBridgeNonce")
            .contains(bridgeStatus.bridgeNonce());
    }

    @Test
    void mcpToolsListMatchesProductionRegistry() {
        JsonNode response = callMcp("req-list", "tools/list", Map.of());

        Set<String> actualNames = java.util.stream.StreamSupport.stream(
                response.path("result").path("tools").spliterator(), false)
            .map(tool -> tool.path("name").asText())
            .collect(java.util.stream.Collectors.toSet());
        Set<String> expectedNames = new McpNameMapper(registry.mcpExposed()).mcpToolNames();

        assertThat(actualNames).isEqualTo(expectedNames);
        assertThat(actualNames).doesNotContain("demo_echo");
    }

    @Test
    void mcpToolsCallBridgesSessionScopedServerActions() {
        seedSession("s-smoke-server", "oc-smoke-server");

        JsonNode response = callMcp("req-call-1", "tools/call", Map.of(
            "name", "list_connections",
            "arguments", bridgeArgs("oc-smoke-server", "call-smoke-1", Map.of())
        ));

        assertThat(response.path("error").isMissingNode()).isTrue();
        assertThat(response.path("result").path("structuredContent").path("connections").isArray()).isTrue();
    }

    @Test
    void mcpToolsCallRejectsClientActionsWithoutSubscriber() {
        seedSession("s-smoke-nosub", "oc-smoke-nosub");

        JsonNode response = callMcp("req-call-2", "tools/call", Map.of(
            "name", "ui_read",
            "arguments", bridgeArgs("oc-smoke-nosub", "call-smoke-2", Map.of())
        ));

        assertThat(response.path("error").path("code").asInt()).isEqualTo(-32004);
        assertThat(response.path("error").path("message").asText()).isEqualTo("no client subscriber");
    }

    @Test
    void mcpToolsCallTimesOutWhenClientSubscriberDoesNotReply() throws Exception {
        seedSession("s-smoke-timeout", "oc-smoke-timeout");
        SessionBus bus = buses.getOrCreate("s-smoke-timeout");
        CountDownLatch invoked = new CountDownLatch(1);
        bus.subscribe("client-smoke", 0L, event -> {
            if (event.event() instanceof DtEvent.ActionInvoke) {
                invoked.countDown();
            }
        });

        try {
            JsonNode response = callMcp("req-call-3", "tools/call", Map.of(
                "name", "ui_read",
                "arguments", bridgeArgs("oc-smoke-timeout", "call-smoke-3", Map.of())
            ));

            assertThat(invoked.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(response.path("error").path("code").asInt()).isEqualTo(-32003);
            assertThat(response.path("error").path("message").asText()).isEqualTo("client action timed out");
        } finally {
            bus.unsubscribe("client-smoke");
            buses.close("s-smoke-timeout");
        }
    }

    private JsonNode callMcp(String id, String method, Map<String, Object> params) {
        String body = WebClient.create("http://localhost:" + port)
            .post()
            .uri("/mcp")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "method", method,
                "params", params
            ))
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(10));

        try {
            return om.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse /mcp response", e);
        }
    }

    private Map<String, Object> bridgeArgs(String openCodeSessionId, String callId, Map<String, Object> input) {
        Map<String, Object> args = new LinkedHashMap<>(input);
        args.put("__dtOpenCodeSessionId", openCodeSessionId);
        args.put("__dtCallId", callId);
        args.put("__dtBridgeNonce", bridgeStatus.bridgeNonce());
        return args;
    }

    private void seedSession(String dataTalkSessionId, String openCodeSessionId) {
        sessions.upsert(new SessionRecord(dataTalkSessionId, null, "T", true, openCodeSessionId, 0L, 0L, false));
        map.bind(dataTalkSessionId, openCodeSessionId);
    }

    private static void stubExternalReconcileFlow() {
        openCode.stubFor(patch(urlEqualTo("/config"))
            .inScenario(RECONCILE_SCENARIO)
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("CONFIG_PATCHED")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"mcp":{"datatalk":{"status":"configured"}}}
                    """)));

        openCode.stubFor(post(urlEqualTo("/mcp"))
            .inScenario(RECONCILE_SCENARIO)
            .whenScenarioStateIs("CONFIG_PATCHED")
            .willSetStateTo("MCP_READY")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"datatalk":{"status":"connected"}}
                    """)));

        openCode.stubFor(get(urlEqualTo("/mcp"))
            .inScenario(RECONCILE_SCENARIO)
            .whenScenarioStateIs("MCP_READY")
            .willSetStateTo("MCP_PROBED")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"datatalk":{"status":"connected"}}
                    """)));

        openCode.stubFor(get(urlPathEqualTo("/global/event"))
            .inScenario(RECONCILE_SCENARIO)
            .whenScenarioStateIs("MCP_PROBED")
            .willSetStateTo("EVENT_LOOP_CONNECTED")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody(":\n\n")));

        openCode.stubFor(get(urlPathEqualTo("/global/event"))
            .inScenario(RECONCILE_SCENARIO)
            .whenScenarioStateIs("EVENT_LOOP_CONNECTED")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody(":\n\n")));
    }
}
