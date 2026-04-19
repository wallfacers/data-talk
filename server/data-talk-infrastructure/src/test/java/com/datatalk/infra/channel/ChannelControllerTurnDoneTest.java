package com.datatalk.infra.channel;

import com.datatalk.application.session.SessionBus;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.event.NumberedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the POST channel stream close condition.
 *
 * <p>The controller replaces a hard-coded 1s {@code Thread.sleep} loop with a
 * watcher subscriber that listens for "turn done" events on the per-session
 * {@link SessionBus}. This test pins the classification rule and proves the
 * end-to-end bus wiring (publish → flush → subscribe callback) fires the latch
 * in the right cases.</p>
 */
class ChannelControllerTurnDoneTest {

    SessionBus bus;

    @BeforeEach
    void setUp() {
        bus = new SessionBus("s-1", 0L, 500, Duration.ofMinutes(5),
            Duration.ofMillis(16), Clock.systemUTC(), new ObjectMapper(),
            (sid, eid, type, payload, ts) -> {});
    }

    @AfterEach
    void tearDown() {
        bus.close();
    }

    @Test
    void classifierAcceptsIdleErrorAndIdleStatus() {
        assertThat(ChannelController.isTurnDoneSignal(new DtEvent.SessionIdle("s-1"))).isTrue();
        assertThat(ChannelController.isTurnDoneSignal(new DtEvent.SessionError("s-1", "x"))).isTrue();
        assertThat(ChannelController.isTurnDoneSignal(new DtEvent.SessionStatus("idle", Map.of()))).isTrue();
    }

    @Test
    void classifierRejectsBusyAndUnrelatedEvents() {
        assertThat(ChannelController.isTurnDoneSignal(new DtEvent.SessionStatus("busy", Map.of()))).isFalse();
        assertThat(ChannelController.isTurnDoneSignal(new DtEvent.Heartbeat(1L))).isFalse();
        assertThat(ChannelController.isTurnDoneSignal(new DtEvent.Connected("s-1", 1))).isFalse();
        assertThat(ChannelController.isTurnDoneSignal(new DtEvent.MessagePartDelta("p1", "text", "hi"))).isFalse();
    }

    @Test
    void watcherLatchFiresWhenBusPublishesSessionIdle() throws Exception {
        CountDownLatch turnDone = new CountDownLatch(1);
        bus.subscribe("watcher", bus.latestEventId(), (NumberedEvent ne) -> {
            if (ChannelController.isTurnDoneSignal(ne.event())) turnDone.countDown();
        });

        // A burst of non-terminal events should NOT release the latch.
        bus.publish(new DtEvent.SessionStatus("busy", Map.of()));
        bus.publish(new DtEvent.MessagePartDelta("p1", "text", "hi"));
        bus.publish(new DtEvent.Heartbeat(42L));
        assertThat(turnDone.await(150, TimeUnit.MILLISECONDS)).isFalse();

        // The idle signal released the POST thread within one flush window.
        bus.publish(new DtEvent.SessionIdle("s-1"));
        assertThat(turnDone.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void watcherLatchFiresOnSessionStatusIdleAsFallback() throws Exception {
        CountDownLatch turnDone = new CountDownLatch(1);
        bus.subscribe("watcher", bus.latestEventId(), (NumberedEvent ne) -> {
            if (ChannelController.isTurnDoneSignal(ne.event())) turnDone.countDown();
        });

        bus.publish(new DtEvent.SessionStatus("idle", Map.of()));
        assertThat(turnDone.await(1, TimeUnit.SECONDS)).isTrue();
    }
}
