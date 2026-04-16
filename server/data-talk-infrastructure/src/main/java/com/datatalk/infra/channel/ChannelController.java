package com.datatalk.infra.channel;

import com.datatalk.application.channel.ChannelService;
import com.datatalk.application.channel.JsonRpcCodec;
import com.datatalk.application.channel.RpcRequest;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.application.session.SseEmitterSubscriber;
import com.datatalk.domain.event.DtEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.Map;

/**
 * Streamable HTTP endpoint. POST with a {@code send_message} body returns an
 * SSE stream; other methods return plain JSON acks.
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/channel")
public class ChannelController {

    private final JsonRpcCodec codec;
    private final ChannelService svc;
    private final SessionBusRegistry buses;
    private final ObjectMapper om;

    public ChannelController(JsonRpcCodec codec, ChannelService svc,
                             SessionBusRegistry buses, ObjectMapper om) {
        this.codec = codec;
        this.svc = svc;
        this.buses = buses;
        this.om = om;
    }

    @PostMapping
    public ResponseEntity<?> post(@PathVariable String sessionId,
                                  @RequestBody String rawBody,
                                  @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId) {
        RpcRequest req = codec.decodeRequest(rawBody);
        return switch (req) {
            case RpcRequest.SendMessage m -> stream(sessionId, m, lastEventId);
            case RpcRequest.ActionResult ar -> {
                svc.completeActionResult(ar.params().callId(), ar.params().ok(),
                    ar.params().output(), ar.params().error());
                yield ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(codec.encodeAck(ar.id()));
            }
            case RpcRequest.Abort a -> {
                svc.abort(sessionId);
                yield ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(codec.encodeAck(a.id()));
            }
            case RpcRequest.Hello h -> ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(codec.encodeAck(h.id()));
        };
    }

    @GetMapping
    public ResponseEntity<StreamingResponseBody> subscribe(
        @PathVariable String sessionId,
        @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId
    ) {
        SessionBus bus = buses.getOrCreate(sessionId);
        StreamingResponseBody body = os -> {
            SseEmitterSubscriber sub = new SseEmitterSubscriber(os, om, "connected");
            String clientId = "read-" + System.nanoTime();
            bus.publish(new DtEvent.Connected(sessionId, 1));
            bus.subscribe(clientId, lastEventId == null ? 0L : lastEventId, sub);
            try {
                while (!sub.isBroken()) {
                    try {
                        Thread.sleep(1_000);
                        bus.publish(new DtEvent.Heartbeat(System.currentTimeMillis()));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                bus.unsubscribe(clientId);
            }
        };
        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("text/event-stream"))
            .body(body);
    }

    private ResponseEntity<StreamingResponseBody> stream(String sessionId,
                                                         RpcRequest.SendMessage m,
                                                         Long lastEventId) {
        SessionBus bus = buses.getOrCreate(sessionId);
        StreamingResponseBody body = os -> {
            SseEmitterSubscriber sub = new SseEmitterSubscriber(os, om, "connected");
            String clientId = "post-" + System.nanoTime();
            bus.publish(new DtEvent.Connected(sessionId, 1));
            bus.subscribe(clientId, lastEventId == null ? 0L : lastEventId, sub);
            try {
                svc.sendMessage(sessionId, m.params().parts());
                // Hold the stream briefly so tests observe initial frames.
                // In production this will stay open until OpenCode completes
                // (Task 23 wires that up). For Plan A, close after short grace.
                for (int i = 0; i < 20 && !sub.isBroken(); i++) {
                    try { Thread.sleep(50); } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                bus.publish(new DtEvent.SessionStatus("idle", Map.of()));
                try { Thread.sleep(50); } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                bus.unsubscribe(clientId);
            }
        };
        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("text/event-stream"))
            .body(body);
    }
}
