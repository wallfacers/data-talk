package com.datatalk.application.opencode;

import com.datatalk.domain.part.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the wire contract against real OpenCode 1.4.7 {@code /event} samples.
 * Fixtures live in {@code resources/opencode-events-147/}; refresh them if a
 * future OpenCode release changes envelope shape.
 */
class OpenCodeEventLoopParseTest {

    private OpenCodeEventLoop loop;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper();
        loop = new OpenCodeEventLoop("http://test", om, null, null, null, null, null, null);
    }

    private String load(String name) throws IOException {
        try (var in = Objects.requireNonNull(
            getClass().getClassLoader().getResourceAsStream("opencode-events-147/" + name))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void serverConnected() throws Exception {
        OcEvent e = loop.parseOcEvent("server.connected", load("server-connected.json"));
        assertThat(e).isInstanceOf(OcEvent.ServerConnected.class);
    }

    @Test
    void sessionCreated() throws Exception {
        OcEvent e = loop.parseOcEvent("session.created", load("session-created.json"));
        assertThat(e).isInstanceOf(OcEvent.SessionCreated.class);
        OcEvent.SessionCreated c = (OcEvent.SessionCreated) e;
        assertThat(c.info().id()).isEqualTo("ses_25fabb782ffeZPB4zWXBff62AS");
        assertThat(c.info().title()).startsWith("New session -");
    }

    @Test
    void sessionUpdatedCarriesNewTitle() throws Exception {
        OcEvent e = loop.parseOcEvent("session.updated", load("session-updated.json"));
        assertThat(e).isInstanceOf(OcEvent.SessionUpdated.class);
        OcEvent.SessionUpdated u = (OcEvent.SessionUpdated) e;
        assertThat(u.info().title()).isEqualTo("SQL 查询问候");
    }

    @Test
    void sessionDeleted() throws Exception {
        OcEvent e = loop.parseOcEvent("session.deleted", load("session-deleted.json"));
        assertThat(e).isInstanceOf(OcEvent.SessionDeleted.class);
        assertThat(((OcEvent.SessionDeleted) e).info().id()).isEqualTo("ses_25fabb782ffeZPB4zWXBff62AS");
    }

    @Test
    void sessionIdleWithoutInfo() throws Exception {
        // OpenCode 1.4.7 sends session.idle without an `info` payload — only sessionID.
        OcEvent e = loop.parseOcEvent("session.idle", load("session-idle.json"));
        assertThat(e).isInstanceOf(OcEvent.SessionIdle.class);
        OcEvent.SessionIdle i = (OcEvent.SessionIdle) e;
        assertThat(i.info().id()).isEqualTo("ses_25fabb782ffeZPB4zWXBff62AS");
    }

    @Test
    void sessionStatusExtractsNestedType() throws Exception {
        // 1.4.7 wire: properties.status = {"type": "busy"} (object, not string).
        OcEvent e = loop.parseOcEvent("session.status", load("session-status-busy.json"));
        assertThat(e).isInstanceOf(OcEvent.SessionStatus.class);
        assertThat(((OcEvent.SessionStatus) e).status()).isEqualTo("busy");
    }

    @Test
    void sessionErrorExtractsMessageFromDataBag() throws Exception {
        // 1.4.7 wire: properties.error = {"name": ..., "data": {"message": "..."}}
        OcEvent e = loop.parseOcEvent("session.error", load("session-error.json"));
        assertThat(e).isInstanceOf(OcEvent.SessionError.class);
        OcEvent.SessionError se = (OcEvent.SessionError) e;
        assertThat(se.error()).isEqualTo("Model not found: openai/gpt-4o-mini.");
        assertThat(se.info().id()).isEqualTo("ses_25fabb782ffeZPB4zWXBff62AS");
    }

    @Test
    void messageUpdatedNormalizesRoleAndSessionId() throws Exception {
        // 1.4.7 wire: properties.info = { id, role: "user" (lowercase), sessionID, time: {created} }
        // No `parts` field — parts arrive via message.part.updated.
        OcEvent e = loop.parseOcEvent("message.updated", load("message-updated-user.json"));
        assertThat(e).isInstanceOf(OcEvent.MessageUpdated.class);
        Message m = ((OcEvent.MessageUpdated) e).message();
        assertThat(m.id()).isEqualTo("msg_da05448a30013tKskYObsKxSSw");
        assertThat(m.sessionId()).isEqualTo("ses_25fabb782ffeZPB4zWXBff62AS");
        assertThat(m.role()).isEqualTo(Message.Role.USER);
        assertThat(m.createdAt()).isEqualTo(1776511371427L);
        assertThat(m.parts()).isEmpty();
        assertThat(m.providerID()).isEqualTo("openai");
        assertThat(m.modelID()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void messageUpdatedAssistantExtractsFlatModel() throws Exception {
        // 1.4.7 wire: assistant 消息 info.{providerID, modelID} 扁平形态
        OcEvent e = loop.parseOcEvent("message.updated", load("message-updated-assistant.json"));
        assertThat(e).isInstanceOf(OcEvent.MessageUpdated.class);
        Message m = ((OcEvent.MessageUpdated) e).message();
        assertThat(m.role()).isEqualTo(Message.Role.ASSISTANT);
        assertThat(m.providerID()).isEqualTo("anthropic");
        assertThat(m.modelID()).isEqualTo("claude-opus-4-7");
    }

    @Test
    void messageUpdatedReturnsNullModelWhenFieldsMissing() throws Exception {
        // 旧协议 / 未知消息：provider/model 都不存在时，不抛且返回 null
        String json = """
            {"type":"message.updated","properties":{"info":{"id":"msg_x","role":"user","sessionID":"s1","time":{"created":0}}}}
            """;
        OcEvent e = loop.parseOcEvent("message.updated", json);
        Message m = ((OcEvent.MessageUpdated) e).message();
        assertThat(m.providerID()).isNull();
        assertThat(m.modelID()).isNull();
    }

    @Test
    void messagePartUpdatedParsesTextPart() throws Exception {
        // 1.4.7 wire: properties.part = {type, text, messageID, sessionID, id}
        OcEvent e = loop.parseOcEvent("message.part.updated", load("message-part-updated-text.json"));
        assertThat(e).isInstanceOf(OcEvent.MessagePartUpdated.class);
        JsonNode p = ((OcEvent.MessagePartUpdated) e).part();
        assertThat(p.path("id").asText()).isEqualTo("prt_da05448a40014UHfnwy01c40ZL");
        assertThat(p.path("text").asText()).isEqualTo("你好");
        assertThat(p.path("messageID").asText()).isEqualTo("msg_da05448a30013tKskYObsKxSSw");
    }

    @Test
    void messagePartDeltaReadsNestedFields() throws Exception {
        // Best-effort: OpenCode hasn't been observed emitting this in our env
        // (needs valid API key). Format assumed from code conventions; adjust
        // if wire reveals otherwise.
        String json = """
            {"type":"message.part.delta","properties":{"sessionID":"s1","partID":"p1","field":"text","delta":"hello"}}
            """;
        OcEvent e = loop.parseOcEvent("message.part.delta", json);
        assertThat(e).isInstanceOf(OcEvent.MessagePartDelta.class);
        OcEvent.MessagePartDelta d = (OcEvent.MessagePartDelta) e;
        assertThat(d.partId()).isEqualTo("p1");
        assertThat(d.field()).isEqualTo("text");
        assertThat(d.delta()).isEqualTo("hello");
    }

    @Test
    void messagePartRemovedReadsNestedPartId() throws Exception {
        String json = """
            {"type":"message.part.removed","properties":{"sessionID":"s1","partID":"p1"}}
            """;
        OcEvent e = loop.parseOcEvent("message.part.removed", json);
        assertThat(e).isInstanceOf(OcEvent.MessagePartRemoved.class);
        assertThat(((OcEvent.MessagePartRemoved) e).partId()).isEqualTo("p1");
    }
}
