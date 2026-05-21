package com.datatalk.application.opencode;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class OpenCodeBridgeStatus {

    private final Clock clock;
    private final AtomicReference<String> bridgeNonce = new AtomicReference<>("");
    private final AtomicReference<Snapshot> snapshot;

    public OpenCodeBridgeStatus(Clock clock) {
        this.clock = clock;
        this.snapshot = new AtomicReference<>(new Snapshot("ok", now(), "OpenCode bridge ready", null));
    }

    public String bridgeNonce() {
        return bridgeNonce.get();
    }

    public void rotateNonce(String nonce) {
        bridgeNonce.set(nonce == null ? "" : nonce);
    }

    public Snapshot snapshot() {
        return snapshot.get();
    }

    public void markOk(String message) {
        snapshot.set(new Snapshot("ok", now(), message, null));
    }

    public void markDegraded(String reason, String message) {
        snapshot.set(new Snapshot("degraded", now(), message, reason));
    }

    private String now() {
        return Instant.now(clock).toString();
    }

    public record Snapshot(String status, String timestamp, String message, String reason) {}
}
