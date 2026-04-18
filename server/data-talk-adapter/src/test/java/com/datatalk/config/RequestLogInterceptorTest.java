package com.datatalk.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLogInterceptorTest {

    private final RequestLogInterceptor interceptor = new RequestLogInterceptor(1000);

    @AfterEach
    void tearDown() {
        MDC.clear();
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

        assertThat(MDC.get("traceId")).isNull(); // MDC cleared
    }

    @Test
    void afterCompletion_logsSlowWarning_forSlowRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/sessions/1/channel");
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new Object());
        // 人为修改 startTime 模拟慢请求
        request.setAttribute("startTime", System.nanoTime() - 2_000_000_000L);
        response.setStatus(200);

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void afterCompletion_logsError_forErrorResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sessions/999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new Object());
        response.setStatus(404);

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void afterCompletion_clearsMdc_evenWhenException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sessions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new Object());

        interceptor.afterCompletion(request, response, new Object(), new RuntimeException("test"));

        assertThat(MDC.get("traceId")).isNull();
    }
}
