package com.datatalk.infra.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 为 SSE {@link ResponseBodyEmitter} 提供定时心跳，发送 SSE 注释帧 ":\n\n"。
 * 用途：避免 idle 期连接被 servlet async timeout 杀掉，并主动探活客户端。
 * 心跳仅是传输层保活机制，不经 SessionBus、不占 eventId、不持久化。
 */
@Component
public class SseHeartbeatScheduler implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SseHeartbeatScheduler.class);
    private static final byte[] HEARTBEAT_BYTES = ":\n\n".getBytes(StandardCharsets.UTF_8);

    private final ScheduledExecutorService exec;

    public SseHeartbeatScheduler() {
        AtomicInteger seq = new AtomicInteger();
        this.exec = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "sse-heartbeat-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 为 emitter 注册周期心跳任务。调用方必须在 emitter 的
     * onCompletion / onTimeout / onError 回调中 cancel 返回的 future，
     * 否则 scheduler 会继续尝试对已关闭 emitter 写入直到 send 抛异常。
     */
    public ScheduledFuture<?> register(ResponseBodyEmitter emitter, long intervalMs) {
        AtomicReference<ScheduledFuture<?>> ref = new AtomicReference<>();
        ScheduledFuture<?> future = exec.scheduleAtFixedRate(() -> {
            try {
                emitter.send(HEARTBEAT_BYTES, MediaType.APPLICATION_OCTET_STREAM);
            } catch (Exception e) {
                // AsyncRequestNotUsableException 是客户端断连的预期信号（Broken pipe 等），静默取消；其它异常才值得 DEBUG。
                if (!(e instanceof AsyncRequestNotUsableException)) {
                    log.debug("Heartbeat send failed, cancelling task", e);
                }
                ScheduledFuture<?> self = ref.get();
                if (self != null) self.cancel(false);
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        ref.set(future);
        return future;
    }

    @Override
    public void destroy() {
        exec.shutdownNow();
    }
}