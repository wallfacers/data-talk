package com.datatalk.application.session;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class PendingCallRegistryTest {

    private final PendingCallRegistry registry = new PendingCallRegistry();

    @Test
    void completesFutureOnResult() {
        CompletableFuture<Object> future = new CompletableFuture<>();
        registry.register("call-1", future, 5_000);
        registry.complete("call-1", "done");
        assertThat(future.join()).isEqualTo("done");
    }

    @Test
    void timesOutWhenWatchdogFires() {
        CompletableFuture<Object> future = new CompletableFuture<>();
        registry.register("call-2", future, 50);
        await().atMost(Duration.ofSeconds(1)).until(future::isDone);
        assertThatThrownBy(future::join).hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void cancelPreventsLaterCompletion() {
        CompletableFuture<Object> future = new CompletableFuture<>();
        registry.register("call-3", future, 5_000);
        registry.cancel("call-3", "user_abort");
        assertThat(future.isCompletedExceptionally()).isTrue();
    }

    @Test
    void lookupUnknownCallIdReturnsFalse() {
        assertThat(registry.complete("nope", "x")).isFalse();
    }
}
