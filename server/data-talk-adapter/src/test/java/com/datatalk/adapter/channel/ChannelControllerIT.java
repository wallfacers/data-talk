package com.datatalk.adapter.channel;

import com.datatalk.application.opencode.OcEvent;
import com.datatalk.application.opencode.OpenCodeEventTranslator;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.application.opencode.SessionInfo;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.part.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChannelControllerIT {

    static WireMockServer oc;

    @DynamicPropertySource
    static void wireOpenCodeBaseUrl(DynamicPropertyRegistry reg) {
        oc = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        oc.start();
        reg.add("datatalk.opencode.base-url", () -> "http://localhost:" + oc.port());
    }

    @AfterAll
    static void stopOpenCode() {
        oc.stop();
    }

    @LocalServerPort int port;
    @Autowired SessionRepository sessions;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate datatalkJdbc;
    @Autowired OpenCodeEventTranslator translator;
    @Autowired OpenCodeSessionMap ocSessionMap;
    @Autowired SessionBusRegistry buses;

    WebClient client;

    @BeforeEach
    void setUp() {
        datatalkJdbc.update("DELETE FROM events");
        datatalkJdbc.update("DELETE FROM action_invocations");
        datatalkJdbc.update("DELETE FROM artifacts");
        datatalkJdbc.update("DELETE FROM query_results");
        datatalkJdbc.update("DELETE FROM sessions");
        sessions.upsert(new SessionRecord("s-1", null, "T", false, "ses_test", 100L, 100L, false));
        ocSessionMap.bind("s-1", "ses_test");
        oc.resetAll();
        client = WebClient.create("http://localhost:" + port);
    }

    @Test
    void sendMessageReturnsSseStreamWithAtLeastConnected() throws Exception {
        // Mock OpenCode POST /session/{id}/message — forwardUserMessage 调用此端点
        oc.stubFor(post(urlEqualTo("/session/ses_test/message"))
            .willReturn(aResponse().withStatus(204)));

        String body = om.writeValueAsString(Map.of(
            "jsonrpc", "2.0",
            "id", "r1",
            "method", "send_message",
            "params", Map.of("parts", List.of(Map.of(
                "type", "text",
                "id", "p1",
                "sessionID", "s-1",
                "messageID", "ignored-server-generates",
                "text", "hello",
                "metadata", Map.of()
            )))
        ));

        // 模拟 OpenCode 通过 OpenCodeEventLoop 推送事件：
        // 等待 POST 完成后，发布 message.created 和 message.part.created
        new Thread(() -> {
            try { Thread.sleep(150); } catch (InterruptedException ignored) { return; }
            var bus = buses.getOrCreate("s-1");
            // message.created
            Message msg = new Message("msg_1", "s-1", Message.Role.USER, List.of(), System.currentTimeMillis(), null, null);
            bus.publish(new DtEvent.MessageCreated(msg));
            // message.part.created
            ObjectNode part = om.createObjectNode();
            part.put("type", "text");
            part.put("id", "prt_1");
            part.put("sessionID", "ses_test");
            part.put("messageID", "msg_1");
            part.put("text", "hello");
            bus.publish(new DtEvent.MessagePartCreated(part));
            // session.idle 标记 turn 结束
            bus.publish(new DtEvent.SessionIdle("s-1"));
        }).start();

        List<String> lines = new CopyOnWriteArrayList<>();
        client.post()
            .uri("/api/sessions/s-1/channel")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(String.class)
            .take(Duration.ofSeconds(2))
            .doOnNext(lines::add)
            .blockLast(Duration.ofSeconds(3));

        String all = String.join("\n", lines);
        assertThat(all).contains("event: connected");
        assertThat(all).contains("event: message.created");
        assertThat(all).contains("event: message.part.created");
    }

    @Test
    void sessionUpdatedFromTranslatorSyncsTitle() {
        ocSessionMap.bind("s-1", "ses_oc1");
        var info = new SessionInfo("ses_oc1", "AI 标题", 2L);
        translator.translate("s-1", new OcEvent.SessionUpdated(info));

        SessionRecord reloaded = sessions.findById("s-1").orElseThrow();
        assertThat(reloaded.title()).isEqualTo("AI 标题");
        assertThat(reloaded.titleLocked()).isFalse();
    }

    @Test
    void sessionUpdatedRespectsTitleLocked() {
        // 覆盖 s-1 为锁定状态
        sessions.upsert(new SessionRecord("s-1", null, "手动命名", false, null, 100L, 100L, true));
        ocSessionMap.bind("s-1", "ses_oc1");

        var info = new SessionInfo("ses_oc1", "AI 标题", 2L);
        translator.translate("s-1", new OcEvent.SessionUpdated(info));

        SessionRecord reloaded = sessions.findById("s-1").orElseThrow();
        assertThat(reloaded.title()).isEqualTo("手动命名");
        assertThat(reloaded.titleLocked()).isTrue();
    }

    @Test
    void subscribeReceivesSessionMetaUpdated() throws Exception {
        new Thread(() -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) { return; }
            buses.getOrCreate("s-1").publish(
                new DtEvent.SessionMetaUpdated(
                    "s-1", "AI 标题", false, 2L));
        }).start();

        List<String> lines = new CopyOnWriteArrayList<>();
        client.get()
            .uri("/api/sessions/s-1/channel")
            .retrieve()
            .bodyToFlux(String.class)
            .take(Duration.ofSeconds(2))
            .doOnNext(lines::add)
            .blockLast(Duration.ofSeconds(3));

        String all = String.join("\n", lines);
        assertThat(all).contains("event: connected");
        assertThat(all).contains("session.meta.updated");
        assertThat(all).contains("\"title\":\"AI 标题\"");
    }

    @Test
    void emitsHeartbeatFramesWhileIdle() {
        StringBuilder raw = new StringBuilder();

        client.get()
            .uri("/api/sessions/s-1/channel")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchangeToFlux(resp -> resp.bodyToFlux(DataBuffer.class))
            .take(Duration.ofMillis(800))
            .doOnNext(db -> {
                byte[] bytes = new byte[db.readableByteCount()];
                db.read(bytes);
                DataBufferUtils.release(db);
                raw.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            })
            .blockLast(Duration.ofSeconds(2));

        // test yml 配置 heartbeat-interval-ms=200，800ms 窗口应收到 ≥2 次
        // 心跳帧字面量为 ":\n\n" —— 行首冒号后立即换行，区分于 "event: x\n" 类业务帧
        String s = raw.toString();
        int count = 0;
        int idx = 0;
        while ((idx = s.indexOf(":\n\n", idx)) != -1) {
            count++;
            idx += 3;
        }
        assertThat(count).isGreaterThanOrEqualTo(2);
    }

    @Test
    void abortRpcForwardsToOpenCodeAbortEndpoint() throws Exception {
        oc.stubFor(post(urlEqualTo("/session/ses_test/abort"))
            .willReturn(aResponse().withStatus(204)));

        String body = om.writeValueAsString(Map.of(
            "jsonrpc", "2.0",
            "id", "abort-1",
            "method", "abort",
            "params", Map.of()
        ));

        String response = client.post()
            .uri("/api/sessions/s-1/channel")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(3));

        assertThat(response).contains("\"id\":\"abort-1\"");
        assertThat(response).contains("\"aborted\":true");
        oc.verify(1, postRequestedFor(urlEqualTo("/session/ses_test/abort")));
    }

    @Test
    void sendMessageWithImageDataUriForwardsAsFilePartToOpenCode() throws Exception {
        // Verifies the new batch-image path end-to-end through the controller:
        // a file_upload part carrying a base64 data URI must reach OpenCode's
        // /session/{id}/message endpoint as {type:"file", url:"data:..."},
        // with no datatalk_file_read hint text and no DataTalk-specific fields.
        oc.stubFor(post(urlEqualTo("/session/ses_test/message"))
            .willReturn(aResponse().withStatus(204)));

        String dataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB";
        String body = om.writeValueAsString(Map.of(
            "jsonrpc", "2.0",
            "id", "img-1",
            "method", "send_message",
            "params", Map.of("parts", List.of(
                Map.of(
                    "type", "text",
                    "id", "p-t",
                    "sessionID", "s-1",
                    "messageID", "ignored",
                    "text", "what is this?",
                    "metadata", Map.of()
                ),
                Map.of(
                    "type", "file_upload",
                    "id", "p-img",
                    "sessionID", "s-1",
                    "messageID", "ignored",
                    "fileId", "file-img-1",
                    "filename", "screenshot.png",
                    "mimeType", "image/png",
                    "sizeBytes", 12345,
                    "analysis", Map.of(),
                    "url", dataUri
                )
            ))
        ));

        // No echo wiring needed — the image path skips the echo registry entirely;
        // we only need to publish a SessionIdle to terminate the SSE stream cleanly.
        new Thread(() -> {
            try { Thread.sleep(150); } catch (InterruptedException ignored) { return; }
            buses.getOrCreate("s-1").publish(new DtEvent.SessionIdle("s-1"));
        }).start();

        client.post()
            .uri("/api/sessions/s-1/channel")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(String.class)
            .take(Duration.ofSeconds(2))
            .blockLast(Duration.ofSeconds(3));

        // OpenCode must have received exactly one POST with the FilePart in body
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
            oc.verify(1, postRequestedFor(urlEqualTo("/session/ses_test/message"))));

        List<com.github.tomakehurst.wiremock.verification.LoggedRequest> reqs =
            oc.findAll(postRequestedFor(urlEqualTo("/session/ses_test/message")));
        assertThat(reqs).hasSize(1);
        String forwarded = reqs.get(0).getBodyAsString();
        JsonNode parts = om.readTree(forwarded).get("parts");
        assertThat(parts).isNotNull().hasSize(2);
        assertThat(parts.get(0).get("type").asText()).isEqualTo("text");
        assertThat(parts.get(1).get("type").asText()).isEqualTo("file");
        assertThat(parts.get(1).get("mime").asText()).isEqualTo("image/png");
        assertThat(parts.get(1).get("filename").asText()).isEqualTo("screenshot.png");
        assertThat(parts.get(1).get("url").asText()).startsWith("data:image/png;base64,");
        // No DataTalk-specific fields leak through Zod barrier
        assertThat(parts.get(1).has("fileId")).isFalse();
        assertThat(parts.get(1).has("sizeBytes")).isFalse();
        assertThat(parts.get(1).has("analysis")).isFalse();
        // Crucially: no datatalk_file_read hint anywhere in the forwarded body
        assertThat(forwarded).doesNotContain("datatalk_file_read");
    }
}
