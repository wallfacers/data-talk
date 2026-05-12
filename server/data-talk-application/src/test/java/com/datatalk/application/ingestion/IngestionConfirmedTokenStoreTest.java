package com.datatalk.application.ingestion;

import com.datatalk.domain.ingestion.IngestionTokenInvalidException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestionConfirmedTokenStoreTest {

    @Test
    void issueAndConsumeOnce() {
        var store = new IngestionConfirmedTokenStore();
        var token = store.issue("job1", "hash1");
        assertThat(token.tokenId()).startsWith("ict_");
        assertThat(token.expiresAt()).isGreaterThan(0);
        store.consume(token.tokenId(), "job1", "hash1");
    }

    @Test
    void consumeTwiceThrows() {
        var store = new IngestionConfirmedTokenStore();
        var token = store.issue("job1", "hash1");
        store.consume(token.tokenId(), "job1", "hash1");
        assertThatThrownBy(() -> store.consume(token.tokenId(), "job1", "hash1"))
            .isInstanceOf(IngestionTokenInvalidException.class)
            .satisfies(ex -> assertThat(((IngestionTokenInvalidException) ex).reason())
                .isEqualTo(IngestionTokenInvalidException.Reason.ALREADY_CONSUMED));
    }

    @Test
    void mismatchJobIdThrows() {
        var store = new IngestionConfirmedTokenStore();
        var token = store.issue("job1", "hash1");
        assertThatThrownBy(() -> store.consume(token.tokenId(), "jobX", "hash1"))
            .isInstanceOf(IngestionTokenInvalidException.class)
            .satisfies(ex -> assertThat(((IngestionTokenInvalidException) ex).reason())
                .isEqualTo(IngestionTokenInvalidException.Reason.JOB_MISMATCH));
    }

    @Test
    void mismatchHashThrows() {
        var store = new IngestionConfirmedTokenStore();
        var token = store.issue("job1", "hash1");
        assertThatThrownBy(() -> store.consume(token.tokenId(), "job1", "hashX"))
            .isInstanceOf(IngestionTokenInvalidException.class)
            .satisfies(ex -> assertThat(((IngestionTokenInvalidException) ex).reason())
                .isEqualTo(IngestionTokenInvalidException.Reason.MAPPING_HASH_MISMATCH));
    }

    @Test
    void expiredTokenThrows() {
        long[] clock = {0};
        var store = new IngestionConfirmedTokenStore(() -> clock[0]);
        var token = store.issue("job1", "hash1");

        clock[0] = 6 * 60 * 1000; // advance 6 minutes past TTL
        assertThatThrownBy(() -> store.consume(token.tokenId(), "job1", "hash1"))
            .isInstanceOf(IngestionTokenInvalidException.class)
            .satisfies(ex -> assertThat(((IngestionTokenInvalidException) ex).reason())
                .isEqualTo(IngestionTokenInvalidException.Reason.EXPIRED));
    }

    @Test
    void notFoundThrows() {
        var store = new IngestionConfirmedTokenStore();
        assertThatThrownBy(() -> store.consume("nonexistent", "job1", "hash1"))
            .isInstanceOf(IngestionTokenInvalidException.class)
            .satisfies(ex -> assertThat(((IngestionTokenInvalidException) ex).reason())
                .isEqualTo(IngestionTokenInvalidException.Reason.NOT_FOUND));
    }
}
