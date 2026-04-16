package com.datatalk.adapter.channel;

import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChannelControllerIT {

    @LocalServerPort int port;
    @Autowired SessionRepository sessions;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate datatalkJdbc;

    WebClient client;

    @BeforeEach
    void setUp() {
        datatalkJdbc.update("DELETE FROM messages");
        datatalkJdbc.update("DELETE FROM events");
        datatalkJdbc.update("DELETE FROM sessions");
        sessions.upsert(new SessionRecord("s-1", null, "T", false, null, 100L, 100L));
        client = WebClient.create("http://localhost:" + port);
    }

    @Test
    void sendMessageReturnsSseStreamWithAtLeastConnected() throws Exception {
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
}
