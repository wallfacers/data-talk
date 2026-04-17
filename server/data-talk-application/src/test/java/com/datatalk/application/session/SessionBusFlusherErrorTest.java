package com.datatalk.application.session;

import com.datatalk.domain.event.DtEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SessionBusFlusherErrorTest {

    SessionBus bus;
    Clock clock = Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC);

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        // Attach a ListAppender to SessionBus logger to capture log output
        Logger logger = (Logger) LoggerFactory.getLogger(SessionBus.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        if (bus != null) bus.close();
        Logger logger = (Logger) LoggerFactory.getLogger(SessionBus.class);
        logger.detachAppender(listAppender);
    }

    @Test
    void flusherLogsErrorWhenPersisterThrowsRuntimeException() {
        SessionBus.Persister brokenPersister = (sid, eid, type, payload, ts) -> {
            throw new RuntimeException("database locked");
        };

        bus = new SessionBus("s-err", 0L, 500, Duration.ofMinutes(5),
            Duration.ofMillis(16), clock, new ObjectMapper(), brokenPersister);
        bus.subscribe("c-1", 0L, n -> {});

        // publish an event - persister will throw but bus should survive
        bus.publish(new DtEvent.Heartbeat(1000L));

        // wait for flush cycle and verify error was logged
        await().atMost(Duration.ofSeconds(2)).until(() ->
            listAppender.list.stream().anyMatch(e ->
                e.getFormattedMessage().contains("database locked")));

        // bus should still be alive - publish another event
        bus.publish(new DtEvent.Heartbeat(2000L));
        await().atMost(Duration.ofSeconds(2)).until(() ->
            listAppender.list.stream().filter(e ->
                e.getFormattedMessage().contains("database locked")).count() >= 2);
    }
}