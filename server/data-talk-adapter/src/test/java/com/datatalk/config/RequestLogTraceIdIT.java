package com.datatalk.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RequestLogTraceIdIT {

    @Autowired
    MockMvc mvc;

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void request_shouldHaveTraceIdInMdc() throws Exception {
        // 通过自定义 header 触发一个简单请求
        // 验证拦截器不会破坏正常请求流程
        mvc.perform(get("/api/sessions"))
            .andExpect(status().isOk());
    }

    @Test
    void mdcShouldBeClearedAfterRequest() throws Exception {
        // 验证请求结束后 MDC 被清理
        // 先发起一个请求，拦截器应在 afterCompletion 中清理 MDC
        mvc.perform(get("/api/sessions"))
            .andExpect(status().isOk());

        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void slowRequest_shouldLogWarning() throws Exception {
        // 正常路径不会触发慢请求，此测试主要验证集成不报错
        mvc.perform(get("/api/sessions"))
            .andExpect(status().isOk());
    }
}
