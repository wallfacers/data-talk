package com.datatalk.application.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonRpcCodecTest {

    private final JsonRpcCodec codec = new JsonRpcCodec(new ObjectMapper());

    @Test
    void decodesSendMessageRequest() {
        String json = """
            {"jsonrpc":"2.0","id":"r1","method":"send_message",
             "params":{"parts":[{"type":"text","id":"p1","sessionID":"s1","messageID":"m1",
                                 "text":"hi","metadata":{}}]}}
            """;
        RpcRequest req = codec.decodeRequest(json);
        assertThat(req).isInstanceOf(RpcRequest.SendMessage.class);
        assertThat(req.id()).isEqualTo("r1");
    }

    @Test
    void decodesActionResultRequest() {
        String json = """
            {"jsonrpc":"2.0","id":"r2","method":"action_result",
             "params":{"callId":"c1","ok":true,"output":{"reversed":"abc"}}}
            """;
        RpcRequest req = codec.decodeRequest(json);
        assertThat(req).isInstanceOf(RpcRequest.ActionResult.class);
        RpcRequest.ActionResult ar = (RpcRequest.ActionResult) req;
        assertThat(ar.params().callId()).isEqualTo("c1");
        assertThat(ar.params().ok()).isTrue();
    }

    @Test
    void rejectsUnknownMethod() {
        String json = """
            {"jsonrpc":"2.0","id":"r3","method":"unknown","params":{}}
            """;
        assertThatThrownBy(() -> codec.decodeRequest(json))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown");
    }

    @Test
    void encodesAckResponse() throws Exception {
        String json = codec.encodeAck("r1");
        assertThat(json).contains("\"jsonrpc\":\"2.0\"")
            .contains("\"id\":\"r1\"").contains("\"result\":{}");
    }
}
