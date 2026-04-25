package com.datatalk.adapter.smoke;

import com.datatalk.DataTalkApplication;
import com.datatalk.application.ai.AiUserPrefsRepository;
import com.datatalk.application.channel.ChannelService;
import com.datatalk.application.opencode.OpenCodeBridgeStatus;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.session.SessionService;
import com.datatalk.domain.part.TextPart;
import com.datatalk.infra.opencode.OpenCodeHttpClient;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfEnvironmentVariable(named = "DATATALK_REAL_OPENCODE_E2E", matches = "true")
@EnabledIfEnvironmentVariable(named = "DATATALK_REAL_OPENCODE_MODEL", matches = ".+")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = DataTalkApplication.class,
    properties = {
        "datatalk.mcp.enabled=true",
        "datatalk.opencode.serve.enabled=true",
        "datatalk.opencode.required=true",
        "datatalk.opencode.serve.strip-proxy-env=false"
    }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RealOpenCodeMcpBridgeIT {

    private static final String MODEL_ENV = "DATATALK_REAL_OPENCODE_MODEL";
    private static final String LIST_CONNECTIONS_ACTION = "datatalk.list_connections";
    private static final String LIST_CONNECTIONS_PROMPT = """
        Call the datatalk_list_connections tool exactly once, then answer with the number of connections. Do not call any other tools.
        """;

    static Path configDir;

    @Autowired AiUserPrefsRepository userPrefs;
    @Autowired ChannelService channelService;
    @Autowired SessionService sessionService;
    @Autowired OpenCodeBridgeStatus bridgeStatus;
    @Autowired OpenCodeHttpClient openCodeHttpClient;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate datatalkJdbc;

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) throws IOException {
        configDir = Files.createTempDirectory("datatalk-real-opencode-");
        registry.add("datatalk.mcp.config-dir", () -> configDir.toString());
        registry.add("datatalk.opencode.serve.base-port", () -> "4196");
        registry.add("datatalk.opencode.serve.port-retries", () -> "50");
    }

    @AfterAll
    static void cleanupConfigDir() throws IOException {
        if (configDir == null || !Files.exists(configDir)) {
            return;
        }
        try (var paths = Files.walk(configDir)) {
            paths.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
        }
    }

    @BeforeEach
    void cleanDatabase() {
        datatalkJdbc.update("DELETE FROM events");
        datatalkJdbc.update("DELETE FROM action_invocations");
        datatalkJdbc.update("DELETE FROM artifacts");
        datatalkJdbc.update("DELETE FROM query_results");
        datatalkJdbc.update("DELETE FROM session_data_contexts");
        datatalkJdbc.update("DELETE FROM synthetic_session_messages");
        datatalkJdbc.update("DELETE FROM sessions");
        datatalkJdbc.update("DELETE FROM connections");
    }

    @Test
    void realOpenCodeToolCallInjectsBridgeFieldsThroughPlugin() {
        String model = System.getenv(MODEL_ENV);
        assumeTrue(model != null && !model.isBlank(), MODEL_ENV + " must be set for real OpenCode E2E");

        userPrefs.setCurrentModel(model);
        SessionRecord session = sessionService.create(null, "real opencode mcp bridge").record();
        channelService.sendMessage(session.id(), List.of(new TextPart(LIST_CONNECTIONS_PROMPT)));

        try {
            await()
                .atMost(Duration.ofSeconds(90))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(completedListConnectionsRows(session.id())).hasSize(1));
        } catch (ConditionTimeoutException e) {
            throw new AssertionError("Timed out waiting for real OpenCode MCP bridge call.\n"
                + diagnostics(session.id()), e);
        }

        Map<String, Object> row = completedListConnectionsRows(session.id()).get(0);
        assertThat(String.valueOf(row.get("input_json")))
            .doesNotContain("__dtOpenCodeSessionId")
            .doesNotContain("__dtCallId")
            .doesNotContain("__dtBridgeNonce");
        assertThat(sessionOpenCodeSid(session.id())).isNotBlank();
        assertThat(bridgeStatus.snapshot().status()).isEqualTo("ok");
    }

    private List<Map<String, Object>> completedListConnectionsRows(String sessionId) {
        return datatalkJdbc.queryForList("""
            SELECT call_id, action_id, status, input_json, output_json, error_json, started_at, ended_at
            FROM action_invocations
            WHERE session_id = ? AND action_id = ? AND status = 'completed'
            ORDER BY started_at
            """, sessionId, LIST_CONNECTIONS_ACTION);
    }

    private String diagnostics(String sessionId) {
        OpenCodeBridgeStatus.Snapshot snapshot = bridgeStatus.snapshot();
        return """
            OpenCode base URL: %s
            OpenCode bridge status: %s
            OpenCode bridge reason: %s
            session opencode_sid: %s
            recent action_invocations rows: %s
            recent events rows: %s
            """.formatted(
                openCodeHttpClient.getBaseUrl(),
                snapshot.status(),
                snapshot.reason(),
                sessionOpenCodeSid(sessionId),
                recentActionInvocations(sessionId),
                recentEvents(sessionId)
            );
    }

    private String sessionOpenCodeSid(String sessionId) {
        List<String> rows = datatalkJdbc.queryForList(
            "SELECT opencode_sid FROM sessions WHERE id = ?",
            String.class,
            sessionId
        );
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    private List<Map<String, Object>> recentActionInvocations(String sessionId) {
        return datatalkJdbc.queryForList("""
            SELECT call_id, action_id, status, input_json, error_json, started_at, ended_at
            FROM action_invocations
            WHERE session_id = ?
            ORDER BY started_at DESC
            LIMIT 10
            """, sessionId);
    }

    private List<Map<String, Object>> recentEvents(String sessionId) {
        return datatalkJdbc.queryForList("""
            SELECT event_id, event_type, payload_json, ts
            FROM events
            WHERE session_id = ?
            ORDER BY event_id DESC
            LIMIT 10
            """, sessionId);
    }
}
