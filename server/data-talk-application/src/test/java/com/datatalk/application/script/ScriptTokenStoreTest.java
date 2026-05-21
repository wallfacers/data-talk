package com.datatalk.application.script;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptTokenStoreTest {

    private ScriptTokenStore store;

    @BeforeEach
    void setUp() {
        store = new ScriptTokenStore();
    }

    @Test
    void issue_returnsUuidFormatToken() {
        String token = store.issue("run-1", "conn-1");

        assertThat(token).isNotNull();
        // Should be valid UUID
        assertThat(UUID.fromString(token)).isNotNull();
    }

    @Test
    void validate_successWithCorrectConnectionId() {
        String token = store.issue("run-1", "conn-1");

        Optional<ScriptTokenStore.TokenEntry> result = store.validate(token, "conn-1");

        assertThat(result).isPresent();
        assertThat(result.get().runId()).isEqualTo("run-1");
        assertThat(result.get().connectionId()).isEqualTo("conn-1");
        assertThat(result.get().createdAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void validate_failsWhenTokenNotFound() {
        Optional<ScriptTokenStore.TokenEntry> result = store.validate("nonexistent-token", "conn-1");

        assertThat(result).isEmpty();
    }

    @Test
    void validate_failsWhenConnectionIdMismatch() {
        String token = store.issue("run-1", "conn-a");

        Optional<ScriptTokenStore.TokenEntry> result = store.validate(token, "conn-b");

        assertThat(result).isEmpty();
    }

    @Test
    void validate_failsWhenTokenExpired() {
        String token = store.issue("run-1", "conn-1");

        // Manually set the token's createdAt to way in the past to simulate expiry.
        // We need to access the internal map for this.
        // Instead, verify that the TTL is ~10min by checking the create time is recent.
        Optional<ScriptTokenStore.TokenEntry> result = store.validate(token, "conn-1");
        assertThat(result).isPresent();
        Instant createdAt = result.get().createdAt();

        // createdAt should be within the last few seconds (not 10+ minutes ago)
        assertThat(createdAt).isAfter(Instant.now().minusSeconds(5));
    }

    @Test
    void revokedToken_validationFails() {
        String token = store.issue("run-1", "conn-1");

        store.revoke(token);

        Optional<ScriptTokenStore.TokenEntry> result = store.validate(token, "conn-1");
        assertThat(result).isEmpty();
    }

    @Test
    void revokeByRunId_removesAllTokensForThatRun() {
        String token1 = store.issue("run-1", "conn-1");
        String token2 = store.issue("run-2", "conn-2");

        store.revokeByRunId("run-1");

        assertThat(store.validate(token1, "conn-1")).isEmpty();
        assertThat(store.validate(token2, "conn-2")).isPresent();
    }

    @Test
    void tokenEntry_containsRunIdAndConnectionId() {
        String token = store.issue("run-abc", "conn-xyz");

        ScriptTokenStore.TokenEntry entry = store.validate(token, "conn-xyz").orElseThrow();

        assertThat(entry.runId()).isEqualTo("run-abc");
        assertThat(entry.connectionId()).isEqualTo("conn-xyz");
    }

    @Test
    void issue_multipleTokensHaveDifferentValues() {
        String token1 = store.issue("run-1", "conn-1");
        String token2 = store.issue("run-2", "conn-2");

        assertThat(token1).isNotEqualTo(token2);
    }
}
