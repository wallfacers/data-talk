package com.datatalk.infra.channel;

import com.datatalk.application.channel.ChannelService;
import com.datatalk.application.channel.JsonRpcCodec;
import com.datatalk.application.channel.RpcRequest;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.application.session.SseEmitterSubscriber;
import com.datatalk.domain.event.DtEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Streamable HTTP endpoint. POST with a {@code send_message} body returns an
 * SSE stream; other methods return plain JSON acks.
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/channel")
public class ChannelController {

    private static final Logger log = LoggerFactory.getLogger(ChannelController.class);

    /** POST turn 流最长存活时间——单 turn 最长运行时间。 */
    private static final long POST_STREAM_TIMEOUT_MS = 10L * 60_000L;
    /** GET 订阅流 timeout——0 = Tomcat 不超时；存活由心跳探活 + 客户端断连决定。 */
    private static final long GET_STREAM_TIMEOUT_MS = 0L;
    /** POST 线程等待 OpenCode turn 完成的上限，与 POST emitter timeout 对齐。 */
    private static final long TURN_WAIT_TIMEOUT_MS = POST_STREAM_TIMEOUT_MS;
    /** Brief grace so the final session.status:idle frame reaches the wire before close. */
    private static final long FINAL_FRAME_GRACE_MS = 50L;

    private final JsonRpcCodec codec;
    private final ChannelService svc;
    private final SessionBusRegistry buses;
    private final ObjectMapper om;
    private final SseHeartbeatScheduler heartbeat;
    private final long heartbeatIntervalMs;

    public ChannelController(JsonRpcCodec codec, ChannelService svc,
                             SessionBusRegistry buses, ObjectMapper om,
                             SseHeartbeatScheduler heartbeat,
                             @Value("${app.sse.heartbeat-interval-ms:30000}") long heartbeatIntervalMs) {
        this.codec = codec;
        this.svc = svc;
        this.buses = buses;
        this.om = om;
        this.heartbeat = heartbeat;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
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
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(GET_STREAM_TIMEOUT_MS);
        ScheduledFuture<?> hb = heartbeat.register(emitter, heartbeatIntervalMs);
        SseEmitterSubscriber sub = new SseEmitterSubscriber(
            new EmitterOutputStream(emitter), om, "connected");
        String clientId = "read-" + System.nanoTime();

        // Publish connected and subscribe
        bus.publish(new DtEvent.Connected(sessionId, 1));
        bus.subscribe(clientId, lastEventId == null ? 0L : lastEventId, sub);

        // 断连清理：取消心跳 + 摘订阅 + 触发 bus 驱逐（onError 路径新补）
        Runnable onDisconnect = () -> {
            hb.cancel(false);
            bus.unsubscribe(clientId);
            buses.onUnsubscribe(sessionId);
        };
        emitter.onCompletion(onDisconnect);
        emitter.onTimeout(onDisconnect);
        emitter.onError(ex -> onDisconnect.run());

        return emitter;
    }

    private ResponseBodyEmitter stream(String sessionId,
                                       RpcRequest.SendMessage m,
                                       Long lastEventId) {
        SessionBus bus = buses.getOrCreate(sessionId);
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(POST_STREAM_TIMEOUT_MS);
        ScheduledFuture<?> hb = heartbeat.register(emitter, heartbeatIntervalMs);
        SseEmitterSubscriber sub = new SseEmitterSubscriber(
            new EmitterOutputStream(emitter), om, "connected");
        String clientId = "post-" + System.nanoTime();
        String watcherId = "post-watch-" + System.nanoTime();

        // Latch that fires once OpenCode signals the turn is done (session.idle /
        // session.error / session.status:idle) or the client goes away.
        CountDownLatch turnDone = new CountDownLatch(1);
        AtomicBoolean clientGone = new AtomicBoolean(false);

        Runnable onDisconnect = () -> {
            hb.cancel(false);
            clientGone.set(true);
            turnDone.countDown();
            bus.unsubscribe(clientId);
            bus.unsubscribe(watcherId);
            buses.onUnsubscribe(sessionId);
        };
        emitter.onCompletion(onDisconnect);
        emitter.onTimeout(onDisconnect);
        emitter.onError(ex -> onDisconnect.run());

        // Subscribe the turn watcher FIRST (cursor = current seq) so we don't miss
        // a fast-arriving idle from OpenCode's /event stream. We then publish the
        // connected frame and attach the main SSE writer.
        long cursor = bus.latestEventId();
        bus.subscribe(watcherId, cursor, ne -> {
            if (isTurnDoneSignal(ne.event())) turnDone.countDown();
        });
        bus.publish(new DtEvent.Connected(sessionId, 1));
        bus.subscribe(clientId, lastEventId == null ? 0L : lastEventId, sub);

        Thread t = new Thread(() -> {
            try {
                svc.sendMessage(sessionId, m.params().parts());
                // Block until OpenCode reports the turn finished, the client hangs
                // up, or the hard upper bound elapses. The short-poll loop also
                // notices broken subscribers (failed SSE writes) in between.
                long deadline = System.currentTimeMillis() + TURN_WAIT_TIMEOUT_MS;
                while (!clientGone.get() && !sub.isBroken()) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) break;
                    if (turnDone.await(Math.min(remaining, 200L), TimeUnit.MILLISECONDS)) break;
                }
                if (!clientGone.get() && !sub.isBroken()) {
                    // Emit a final idle so clients keyed to session.status:idle
                    // always see it (OpenCode's session.idle already went out, but
                    // this preserves the legacy frame the UI watches for).
                    bus.publish(new DtEvent.SessionStatus("idle", Map.of()));
                    try { Thread.sleep(FINAL_FRAME_GRACE_MS); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
                try { emitter.complete(); } catch (IllegalStateException ignored) { /* already closed */ }
            } catch (Exception e) {
                try { emitter.completeWithError(e); } catch (IllegalStateException ignored) { /* already closed */ }
                log.warn("[channel] POST stream failed for session={}", sessionId, e);
            }
            // onCompletion/onTimeout/onError already unsubscribe clientId+watcherId.
        }, "channel-stream-" + sessionId);
        t.setDaemon(true);
        t.start();

        return emitter;
    }

    /** Events that mean "this turn is done" — used to close the POST SSE stream. */
    static boolean isTurnDoneSignal(DtEvent e) {
        return e instanceof DtEvent.SessionIdle
            || e instanceof DtEvent.SessionError
            || (e instanceof DtEvent.SessionStatus s && "idle".equals(s.status()));
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
