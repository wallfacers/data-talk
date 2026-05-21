package com.datatalk.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RequestLogTraceIdIT {

    @Autowired
    MockMvc mvc;

    private final Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(RequestLogInterceptor.class);
    private final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();

    @BeforeEach
    void setUp() {
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
    void request_shouldLogWithTraceId() throws Exception {
        listAppender.list.clear();

        mvc.perform(get("/api/sessions"))
            .andExpect(status().isOk());

        List<ILoggingEvent> events = listAppender.list;
        assertThat(events).isNotEmpty();

        ILoggingEvent logEvent = events.get(0);
        String traceId = logEvent.getMDCPropertyMap().get("traceId");
        assertThat(traceId).isNotEmpty();
        assertThat(traceId).hasSize(32); // UUID without dashes
    }

    @Test
    void mdcShouldBeClearedAfterRequest() throws Exception {
        mvc.perform(get("/api/sessions"))
            .andExpect(status().isOk());

        // MDC should be empty in the test thread after request completes
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void traceIdShouldBeUniquePerRequest() throws Exception {
        listAppender.list.clear();

        mvc.perform(get("/api/sessions"))
            .andExpect(status().isOk());
        String traceId1 = listAppender.list.get(0).getMDCPropertyMap().get("traceId");

        listAppender.list.clear();

        mvc.perform(get("/api/sessions"))
            .andExpect(status().isOk());
        String traceId2 = listAppender.list.get(0).getMDCPropertyMap().get("traceId");

        assertThat(traceId1).isNotEqualTo(traceId2);
    }
}
