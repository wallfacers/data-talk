package com.datatalk.application.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionRunRegistryTest {

    @Test
    void registerThenIsActive() {
        var reg = new IngestionRunRegistry();
        try (var entry = reg.register("ing_1")) {
            assertThat(reg.isActive("ing_1")).isTrue();
            assertThat(reg.getCancelled("ing_1")).isNotNull();
            assertThat(reg.getCancelled("ing_1").get()).isFalse();
        }
        assertThat(reg.isActive("ing_1")).isFalse();
        assertThat(reg.getCancelled("ing_1")).isNull();
    }

    @Test
    void requestStopSetsFlagAndInterruptsWorker() throws Exception {
        var reg = new IngestionRunRegistry();
        var threadStartedLatch = new java.util.concurrent.CountDownLatch(1);
        var interruptedFlag = new java.util.concurrent.atomic.AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            try (var entry = reg.register("ing_2")) {
                threadStartedLatch.countDown();
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    interruptedFlag.set(true);
                }
            }
        }, "test-worker");
        worker.start();
        threadStartedLatch.await();

        boolean signalled = reg.requestStop("ing_2");
        assertThat(signalled).isTrue();

        worker.join(2_000);
        assertThat(worker.isAlive()).isFalse();
        assertThat(interruptedFlag.get()).isTrue();
        assertThat(reg.isActive("ing_2")).isFalse();
    }

    @Test
    void requestStopReturnsFalseWhenNoActiveJob() {
        var reg = new IngestionRunRegistry();
        assertThat(reg.requestStop("ing_missing")).isFalse();
        assertThat(reg.isActive("ing_missing")).isFalse();
        assertThat(reg.getCancelled("ing_missing")).isNull();
    }

    @Test
    void doubleRegisterReplacesPreviousHandle() {
        var reg = new IngestionRunRegistry();
        try (var first = reg.register("ing_dup")) {
            var initialFlag = reg.getCancelled("ing_dup");
            try (var second = reg.register("ing_dup")) {
                var secondFlag = reg.getCancelled("ing_dup");
                assertThat(secondFlag).isNotSameAs(initialFlag);
            }
        }
    }

    @Test
    void closeIsIdempotent() {
        var reg = new IngestionRunRegistry();
        var entry = reg.register("ing_idem");
        entry.close();
        entry.close();
        assertThat(reg.isActive("ing_idem")).isFalse();
    }
}
