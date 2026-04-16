package com.datatalk.application.session;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

@Component
public class PendingCallRegistry {

    private final Map<String, Entry> byCallId = new ConcurrentHashMap<>();

    private final ScheduledExecutorService watchdog =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pending-call-watchdog");
            t.setDaemon(true);
            return t;
        });

    public void register(String callId, CompletableFuture<Object> future, int timeoutMs) {
        ScheduledFuture<?> timer = watchdog.schedule(() -> {
            Entry e = byCallId.remove(callId);
            if (e != null) e.future.completeExceptionally(new TimeoutException("action.timeout after " + timeoutMs + "ms"));
        }, timeoutMs, TimeUnit.MILLISECONDS);
        byCallId.put(callId, new Entry(future, timer));
    }

    public boolean complete(String callId, Object output) {
        Entry e = byCallId.remove(callId);
        if (e == null) return false;
        e.timer.cancel(false);
        e.future.complete(output);
        return true;
    }

    public boolean fail(String callId, Throwable error) {
        Entry e = byCallId.remove(callId);
        if (e == null) return false;
        e.timer.cancel(false);
        e.future.completeExceptionally(error);
        return true;
    }

    public boolean cancel(String callId, String reason) {
        Entry e = byCallId.remove(callId);
        if (e == null) return false;
        e.timer.cancel(false);
        e.future.completeExceptionally(new CancelledException(reason));
        return true;
    }

    public boolean hasPending(String callId) {
        return byCallId.containsKey(callId);
    }

    private record Entry(CompletableFuture<Object> future, ScheduledFuture<?> timer) {}

    public static class CancelledException extends RuntimeException {
        public CancelledException(String reason) {
            super(reason);
        }
    }
}
