package com.datatalk.application.script;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ScriptTokenStore {

    private static final long TTL_MS = 10 * 60 * 1000; // 10 minutes

    private final ConcurrentHashMap<String, TokenEntry> tokens = new ConcurrentHashMap<>();

    public record TokenEntry(String runId, String connectionId, Instant createdAt) {}

    public String issue(String runId, String connectionId) {
        String token = java.util.UUID.randomUUID().toString();
        tokens.put(token, new TokenEntry(runId, connectionId, Instant.now()));
        return token;
    }

    public Optional<TokenEntry> validate(String token, String connectionId) {
        TokenEntry entry = tokens.get(token);
        if (entry == null) return Optional.empty();
        if (Instant.now().isAfter(entry.createdAt().plusMillis(TTL_MS))) {
            tokens.remove(token);
            return Optional.empty();
        }
        if (!entry.connectionId().equals(connectionId)) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    public void revoke(String token) {
        tokens.remove(token);
    }

    public void revokeByRunId(String runId) {
        tokens.entrySet().removeIf(e -> e.getValue().runId().equals(runId));
    }
}
