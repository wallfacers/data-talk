package com.datatalk.infra.channel;

import com.datatalk.application.channel.ChannelService;
import com.datatalk.application.channel.JsonRpcCodec;
import com.datatalk.application.channel.RpcRequest;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.application.session.SseEmitterSubscriber;
import com.datatalk.domain.event.DtEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * Streamable HTTP endpoint. POST with a {@code send_message} body returns an
 * SSE stream; other methods return plain JSON acks.
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/channel")
public class ChannelController {

    /** SSE streams need long idle timeout for slow model responses. Spring's default 30s triggers AsyncRequestTimeoutException. */
    private static final long SSE_STREAM_TIMEOUT_MS = 10L * 60_000L; // 10 minutes

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

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Object post(@PathVariable String sessionId,
                       @RequestBody String rawBody,
                       @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId) {
        RpcRequest req = codec.decodeRequest(rawBody);
        return switch (req) {
            case RpcRequest.SendMessage m -> stream(sessionId, m, lastEventId);
            case RpcRequest.ActionResult ar -> {
                svc.completeActionResult(ar.params().callId(), ar.params().ok(),
                    ar.params().output(), ar.params().error());
                yield codec.encodeAck(ar.id());
            }
            case RpcRequest.Abort a -> {
                svc.abort(sessionId);
                yield codec.encodeAck(a.id());
            }
            case RpcRequest.Hello h -> codec.encodeAck(h.id());
        };
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseBodyEmitter subscribe(
        @PathVariable String sessionId,
        @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId
    ) {
        SessionBus bus = buses.getOrCreate(sessionId);
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(SSE_STREAM_TIMEOUT_MS);
        SseEmitterSubscriber sub = new SseEmitterSubscriber(
            new EmitterOutputStream(emitter), om, "connected");
        String clientId = "read-" + System.nanoTime();

        // Publish connected and subscribe
        bus.publish(new DtEvent.Connected(sessionId, 1));
        bus.subscribe(clientId, lastEventId == null ? 0L : lastEventId, sub);

        // Unsubscribe on disconnect; trigger eviction when last subscriber leaves
        Runnable onDisconnect = () -> { bus.unsubscribe(clientId); buses.onUnsubscribe(sessionId); };
        emitter.onCompletion(onDisconnect);
        emitter.onTimeout(onDisconnect);

        return emitter;
    }

    private ResponseBodyEmitter stream(String sessionId,
                                       RpcRequest.SendMessage m,
                                       Long lastEventId) {
        SessionBus bus = buses.getOrCreate(sessionId);
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(SSE_STREAM_TIMEOUT_MS);
        SseEmitterSubscriber sub = new SseEmitterSubscriber(
            new EmitterOutputStream(emitter), om, "connected");
        String clientId = "post-" + System.nanoTime();

        // Unsubscribe on disconnect; trigger eviction when last subscriber leaves
        Runnable onDisconnect = () -> { bus.unsubscribe(clientId); buses.onUnsubscribe(sessionId); };
        emitter.onCompletion(onDisconnect);
        emitter.onTimeout(onDisconnect);

        // Publish connected and subscribe
        bus.publish(new DtEvent.Connected(sessionId, 1));
        bus.subscribe(clientId, lastEventId == null ? 0L : lastEventId, sub);

        // Run the send + grace period on a background thread
        Thread t = new Thread(() -> {
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
                Thread.sleep(50);
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            // onCompletion/onTimeout callbacks already handled unsubscribe
        }, "channel-stream-" + sessionId);
        t.setDaemon(true);
        t.start();

        return emitter;
    }

    /** Wraps a {@link ResponseBodyEmitter} as an {@link java.io.OutputStream}. */
    private static final class EmitterOutputStream extends java.io.OutputStream {
        private final ResponseBodyEmitter emitter;
        EmitterOutputStream(ResponseBodyEmitter emitter) { this.emitter = emitter; }
        @Override
        public void write(int b) throws IOException {
            try { emitter.send(new byte[]{(byte) b}); }
            catch (Exception e) { throw new IOException(e); }
        }
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            try {
                byte[] copy = new byte[len];
                System.arraycopy(b, off, copy, 0, len);
                emitter.send(copy);
            } catch (Exception e) { throw new IOException(e); }
        }
        @Override
        public void flush() {
            // no-op; ResponseBodyEmitter flushes automatically
        }
    }
}
