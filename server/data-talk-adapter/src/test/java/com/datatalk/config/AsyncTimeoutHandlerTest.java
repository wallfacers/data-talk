package com.datatalk.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncTimeoutHandlerTest {

    private final AsyncTimeoutHandler handler = new AsyncTimeoutHandler();
    private final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    private Logger logger;
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(AsyncTimeoutHandler.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.setLevel(originalLevel);
        logger.detachAppender(listAppender);
        listAppender.stop();
        listAppender.list.clear();
    }

    @Test
    void logsAtDebugLevelInsteadOfRethrowing() {
        HttpServletRequest req = new MockHttpServletRequest("GET", "/api/sessions/abc/channel");

        handler.onAsyncTimeout(new AsyncRequestTimeoutException(), req);

        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent event = listAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
        assertThat(event.getFormattedMessage()).contains("GET");
        assertThat(event.getFormattedMessage()).contains("/api/sessions/abc/channel");
    }
}