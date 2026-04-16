package com.datatalk.application.channel;

/** Outbound JSON-RPC response. Only used for non-streaming methods. */
public record RpcResponse(String jsonrpc, String id, Object result, Object error) {
    public static RpcResponse ok(String id) {
        return new RpcResponse("2.0", id, java.util.Map.of(), null);
    }
    public static RpcResponse ok(String id, Object result) {
        return new RpcResponse("2.0", id, result, null);
    }
    public static RpcResponse err(String id, Object error) {
        return new RpcResponse("2.0", id, null, error);
    }
}
