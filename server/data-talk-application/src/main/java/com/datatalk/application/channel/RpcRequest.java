package com.datatalk.application.channel;

import com.datatalk.domain.event.ErrorInfo;
import com.datatalk.domain.part.Part;

import java.util.List;
import java.util.Map;

/** Inbound JSON-RPC request over Streamable HTTP. See spec §3.3. */
public sealed interface RpcRequest {

    String id();

    record SendMessage(String id, SendMessageParams params) implements RpcRequest {}
    record ActionResult(String id, ActionResultParams params) implements RpcRequest {}
    record Abort(String id) implements RpcRequest {}
    record Hello(String id, HelloParams params) implements RpcRequest {}

    record SendMessageParams(List<Part> parts) {}
    record ActionResultParams(String callId, boolean ok, Object output, ErrorInfo error) {}
    record HelloParams(int clientRev, Long lastEventId) {}
}
