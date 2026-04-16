package com.datatalk.application.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonRpcCodec {

    private final ObjectMapper om;

    public JsonRpcCodec(ObjectMapper om) { this.om = om; }

    public RpcRequest decodeRequest(String json) {
        try {
            JsonNode node = om.readTree(json);
            String id = node.path("id").asText();
            String method = node.path("method").asText();
            JsonNode params = node.path("params");
            return switch (method) {
                case "send_message"  -> new RpcRequest.SendMessage(id,
                    om.treeToValue(params, RpcRequest.SendMessageParams.class));
                case "action_result" -> new RpcRequest.ActionResult(id,
                    om.treeToValue(params, RpcRequest.ActionResultParams.class));
                case "abort"         -> new RpcRequest.Abort(id);
                case "hello"         -> new RpcRequest.Hello(id,
                    om.treeToValue(params, RpcRequest.HelloParams.class));
                default -> throw new IllegalArgumentException("unknown RPC method: " + method);
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("malformed RPC: " + e.getMessage(), e);
        }
    }

    public String encodeAck(String id) {
        try {
            return om.writeValueAsString(RpcResponse.ok(id));
        } catch (Exception e) {
            throw new IllegalStateException("cannot encode RpcResponse", e);
        }
    }

    public String encodeResult(String id, Object result) {
        try {
            return om.writeValueAsString(RpcResponse.ok(id, result));
        } catch (Exception e) {
            throw new IllegalStateException("cannot encode RpcResponse", e);
        }
    }

    public String encodeError(String id, Object error) {
        try {
            return om.writeValueAsString(RpcResponse.err(id, error));
        } catch (Exception e) {
            throw new IllegalStateException("cannot encode RpcResponse", e);
        }
    }
}
