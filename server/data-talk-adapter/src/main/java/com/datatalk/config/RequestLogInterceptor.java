package com.datatalk.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * HTTP 请求日志拦截器。为每个请求生成 traceId、计算耗时、输出结构化日志。
 * 慢请求使用 WARN 级别，错误请求使用 ERROR 级别。
 */
public class RequestLogInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestLogInterceptor.class);

    private final long slowRequestThresholdMs;

    public RequestLogInterceptor(long slowRequestThresholdMs) {
        this.slowRequestThresholdMs = slowRequestThresholdMs;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put("traceId", traceId);
        request.setAttribute("startTime", System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            Long startTime = (Long) request.getAttribute("startTime");
            if (startTime == null) return;

            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            int status = response.getStatus();
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String ip = getClientIp(request);

            String ua = request.getHeader("User-Agent");
            String uaPart = (ua != null && !ua.isEmpty()) ? " | ua=" + truncate(ua, 80) : "";

            String suffix = "";
            if (status >= 400) {
                String errorMsg = ex != null ? ex.getClass().getSimpleName() + ": " + truncate(ex.getMessage(), 100) : "";
                suffix = " [ERROR]" + (errorMsg.isEmpty() ? "" : " " + errorMsg);
                log.error("{} {} | ip={}{} → {} | {}ms{}", method, uri, ip, uaPart, status, elapsedMs, suffix);
            } else if (elapsedMs >= slowRequestThresholdMs) {
                suffix = " [SLOW]";
                log.warn("{} {} | ip={}{} → {} | {}ms{}", method, uri, ip, uaPart, status, elapsedMs, suffix);
            } else {
                log.info("{} {} | ip={}{} → {} | {}ms", method, uri, ip, uaPart, status, elapsedMs);
            }
        } finally {
            MDC.clear();
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
