package com.datatalk.infra.channel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;

import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SseHeartbeatSchedulerTest {

    private SseHeartbeatScheduler scheduler;

    @BeforeEach
    void setUp() { scheduler = new SseHeartbeatScheduler(); }

    @AfterEach
    void tearDown() { scheduler.destroy(); }

    @Test
    void emitsHeartbeatAtInterval() {
        ResponseBodyEmitter emitter = mock(ResponseBodyEmitter.class);

        scheduler.register(emitter, 50L);

        await().atMost(ofMillis(400)).untilAsserted(() ->
            verify(emitter, atLeast(3)).send(any(), eq(MediaType.APPLICATION_OCTET_STREAM))
        );
    }

    @Test
    void cancelStopsFurtherEmissions() throws Exception {
        ResponseBodyEmitter emitter = mock(ResponseBodyEmitter.class);

        ScheduledFuture<?> future = scheduler.register(emitter, 50L);
        future.cancel(false);
        Thread.sleep(200);

        // cancel 后最多允许 1 次已入队的 tick 完成后再停
        verify(emitter, atMost(1)).send(any(), eq(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Test
    void sendExceptionCancelsFuture() throws Exception {
        ResponseBodyEmitter emitter = mock(ResponseBodyEmitter.class);
        doThrow(new IOException("broken pipe"))
            .when(emitter).send(any(), eq(MediaType.APPLICATION_OCTET_STREAM));

        ScheduledFuture<?> future = scheduler.register(emitter, 20L);

        await().atMost(ofMillis(300)).until(future::isCancelled);
    }

    @Test
    void registerAfterDestroyThrows() {
        scheduler.destroy();
        ResponseBodyEmitter emitter = mock(ResponseBodyEmitter.class);

        assertThatThrownBy(() -> scheduler.register(emitter, 50L))
            .isInstanceOf(RejectedExecutionException.class);
    }
}