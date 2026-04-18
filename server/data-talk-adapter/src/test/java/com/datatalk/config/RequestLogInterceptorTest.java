package com.datatalk.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLogInterceptorTest {

    private final RequestLogInterceptor interceptor = new RequestLogInterceptor(1000);
    private final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) org.slf4j.LoggerFactory.getLogger(RequestLogInterceptor.class);
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        logger.detachAppender(listAppender);
        listAppender.stop();
        listAppender.list.clear();
    }

    @Test
    void preHandle_setsTraceIdAndStartTime() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sessions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(MDC.get("traceId")).isNotEmpty();
        assertThat(request.getAttribute("startTime")).isNotNull();
    }

    @Test
    void afterCompletion_logsInfo_forNormalRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sessions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new Object());
        response.setStatus(200);

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent event = listAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).contains("GET /api/sessions");
        assertThat(event.getFormattedMessage()).contains("→ 200");
        assertThat(event.getFormattedMessage()).contains("ms");
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void afterCompletion_logsSlowWarning_forSlowRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/sessions/1/channel");
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new Object());
        request.setAttribute("startTime", System.nanoTime() - 2_000_000_000L);
        response.setStatus(200);

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent event = listAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("[SLOW]");
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void afterCompletion_logsError_forErrorResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sessions/999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new Object());
        response.setStatus(404);

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent event = listAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).contains("[ERROR]");
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void afterCompletion_clearsMdc_evenWhenException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sessions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new Object());

        interceptor.afterCompletion(request, response, new Object(), new RuntimeException("test"));

        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent event = listAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).contains("[ERROR]");
        assertThat(event.getFormattedMessage()).contains("RuntimeException: test");
        assertThat(MDC.get("traceId")).isNull();
    }
}
