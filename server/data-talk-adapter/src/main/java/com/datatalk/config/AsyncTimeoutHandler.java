package com.datatalk.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

/**
 * 全局吞掉 {@link AsyncRequestTimeoutException} 降噪至 DEBUG。SSE 端点在响应头已
 * committed 后触发 async timeout 时，Spring 无法再写 503，原本的 WARN 具误导性。
 * 本处理器仅针对这一类异常，其他异步异常仍走原处理链。
 */
@ControllerAdvice
public class AsyncTimeoutHandler {

    private static final Logger log = LoggerFactory.getLogger(AsyncTimeoutHandler.class);

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void onAsyncTimeout(AsyncRequestTimeoutException ex, HttpServletRequest req) {
        log.debug("Async request timed out for {} {}", req.getMethod(), req.getRequestURI());
    }
}