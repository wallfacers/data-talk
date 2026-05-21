package com.datatalk.application.sql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionOpLogBusTest {

    private ConnectionOpLogBus bus;

    @BeforeEach
    void setUp() {
        bus = new ConnectionOpLogBus("conn-1");
    }

    @Test
    void connectionId_returnsConstructorValue() {
        assertThat(bus.connectionId()).isEqualTo("conn-1");
    }

    @Test
    void hasSubscribers_returnsFalseWhenEmpty() {
        assertThat(bus.hasSubscribers()).isFalse();
    }

    @Test
    void subscribe_thenHasSubscribersReturnsTrue() {
        bus.subscribe("sub-1", s -> {});
        assertThat(bus.hasSubscribers()).isTrue();
    }

    @Test
    void publish_deliversToAllSubscribers() {
        CopyOnWriteArrayList<String> received1 = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<String> received2 = new CopyOnWriteArrayList<>();

        bus.subscribe("sub-1", received1::add);
        bus.subscribe("sub-2", received2::add);

        bus.publish("{\"event\":\"test\"}");

        assertThat(received1).containsExactly("{\"event\":\"test\"}");
        assertThat(received2).containsExactly("{\"event\":\"test\"}");
    }

    @Test
    void publish_deliversMultipleMessages() {
        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();
        bus.subscribe("sub-1", received::add);

        bus.publish("msg-1");
        bus.publish("msg-2");
        bus.publish("msg-3");

        assertThat(received).containsExactly("msg-1", "msg-2", "msg-3");
    }

    @Test
    void unsubscribe_removesSubscriber() {
        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();
        bus.subscribe("sub-1", received::add);
        bus.unsubscribe("sub-1");

        bus.publish("msg-after");

        assertThat(received).isEmpty();
        assertThat(bus.hasSubscribers()).isFalse();
    }

    @Test
    void unsubscribe_oneOfMany_remainingStillReceive() {
        CopyOnWriteArrayList<String> received1 = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<String> received2 = new CopyOnWriteArrayList<>();

        bus.subscribe("sub-1", received1::add);
        bus.subscribe("sub-2", received2::add);

        bus.unsubscribe("sub-1");
        assertThat(bus.hasSubscribers()).isTrue();

        bus.publish("msg-after");
        assertThat(received1).isEmpty();
        assertThat(received2).containsExactly("msg-after");
    }

    @Test
    void hasSubscribers_reflectsCurrentState() {
        assertThat(bus.hasSubscribers()).isFalse();

        bus.subscribe("sub-1", s -> {});
        assertThat(bus.hasSubscribers()).isTrue();

        bus.subscribe("sub-2", s -> {});
        assertThat(bus.hasSubscribers()).isTrue();

        bus.unsubscribe("sub-1");
        assertThat(bus.hasSubscribers()).isTrue();

        bus.unsubscribe("sub-2");
        assertThat(bus.hasSubscribers()).isFalse();
    }

    @Test
    void publish_continuesAfterSubscriberException() {
        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();

        bus.subscribe("sub-failing", s -> { throw new RuntimeException("boom"); });
        bus.subscribe("sub-ok", received::add);

        bus.publish("msg-1");

        assertThat(received).containsExactly("msg-1");
    }
}
