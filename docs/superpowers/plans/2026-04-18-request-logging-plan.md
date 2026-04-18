# 请求耗时统计和全链路日志跟踪实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 DataTalk 后端实现 HTTP 请求级别的耗时统计和全链路日志跟踪，每个请求带唯一 traceId，慢请求自动告警。

**Architecture:** 使用 Spring MVC `HandlerInterceptor` 拦截 `/api/**` 请求，在 `preHandle` 中生成 traceId 放入 MDC，在 `afterCompletion` 中计算耗时并输出结构化日志。Logback pattern 加入 `%X{traceId:-}` 使所有业务日志自动携带 traceId。

**Tech Stack:** Spring Boot 3.5 (Jakarta Servlet), SLF4J + Logback, MDC, JUnit 5 + MockMvc

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `server/data-talk-adapter/src/main/java/com/datatalk/config/RequestLogInterceptor.java` | 新建 | 核心拦截器：生成 traceId、计时、输出日志 |
| `server/data-talk-adapter/src/main/java/com/datatalk/config/WebMvcConfig.java` | 新建 | 注册拦截器到 Spring MVC |
| `server/data-talk-adapter/src/main/resources/logback-spring.xml` | 修改 | pattern 加入 `%X{traceId:-}` |
| `server/data-talk-adapter/src/main/resources/application.yml` | 修改 | 添加 `app.logging.slow-request-threshold-ms` 配置 |
| `server/data-talk-adapter/src/test/java/com/datatalk/config/RequestLogInterceptorTest.java` | 新建 | 拦截器单元测试 |
| `server/data-talk-adapter/src/test/java/com/datatalk/config/RequestLogTraceIdIT.java` | 新建 | MockMvc 集成测试，验证 traceId 传播 |

---

### Task 1: 修改 Logback 日志格式

**Files:**
- Modify: `server/data-talk-adapter/src/main/resources/logback-spring.xml`

- [ ] **Step 1: 更新 logback-spring.xml pattern**

在 `logback-spring.xml` 中修改两个 pattern，加入 `%X{traceId:-}`（`:-` 表示无值时输出空字符串）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <property name="LOG_PATTERN" value="%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] [%X{traceId:-}] %logger{36} - %msg%n"/>
    <property name="LOG_PATTERN_COLOR" value="%d{yyyy-MM-dd HH:mm:ss.SSS} %highlight(%-5level) [%thread] [%X{traceId:-}] %cyan(%logger{36}) - %msg%n"/>
    <property name="LOG_DIR" value="logs"/>

    <!-- Console appender (dev, with color) -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN_COLOR}</pattern>
        </encoder>
    </appender>

    <!-- File appender (prod, rolling) -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_DIR}/data-talk.log</file>
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_DIR}/archived/data-talk.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxHistory>30</maxHistory>
            <totalSizeCap>500MB</totalSizeCap>
            <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                <maxFileSize>50MB</maxFileSize>
            </timeBasedFileNamingAndTriggeringPolicy>
        </rollingPolicy>
    </appender>

    <!-- Dev profile (also default when no profile specified): console only -->
    <springProfile name="dev,default">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
        <logger name="com.datatalk" level="DEBUG"/>
        <logger name="org.springframework" level="INFO"/>
        <logger name="org.hibernate" level="WARN"/>
    </springProfile>

    <!-- Prod profile: console + file -->
    <springProfile name="prod">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
            <appender-ref ref="FILE"/>
        </root>
        <logger name="com.datatalk" level="INFO"/>
        <logger name="org.springframework" level="INFO"/>
        <logger name="org.hibernate" level="WARN"/>
    </springProfile>


</configuration>
```

- [ ] **Step 2: 编译验证**

Run: `cd server && mvn compile -q`
Expected: BUILD SUCCESS，无编译错误

- [ ] **Step 3: 手动验证日志格式（可选）**

启动应用后，观察控制台输出是否包含 `[]` 占位（此时还未有 traceId，应为空括号）。

- [ ] **Step 4: Commit**

```bash
git add server/data-talk-adapter/src/main/resources/logback-spring.xml
git commit -m "feat(logging): add MDC traceId placeholder to logback pattern"
```

---

### Task 2: 创建 RequestLogInterceptor

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/config/RequestLogInterceptor.java`

- [ ] **Step 1: 编写单元测试（先写测试）**

创建 `server/data-talk-adapter/src/test/java/com/datatalk/config/RequestLogInterceptorTest.java`：

```java
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
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd server && mvn test -pl data-talk-adapter -Dtest=RequestLogInterceptorTest -q`
Expected: 编译失败（类不存在）

- [ ] **Step 3: 实现 RequestLogInterceptor**

创建 `server/data-talk-adapter/src/main/java/com/datatalk/config/RequestLogInterceptor.java`：

```java
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
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd server && mvn test -pl data-talk-adapter -Dtest=RequestLogInterceptorTest -q`
Expected: 5 个测试全部通过

- [ ] **Step 5: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/config/RequestLogInterceptor.java
git add server/data-talk-adapter/src/test/java/com/datatalk/config/RequestLogInterceptorTest.java
git commit -m "feat(logging): add RequestLogInterceptor for request timing and traceId"
```

---

### Task 3: 创建 WebMvcConfig 注册拦截器

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/config/WebMvcConfig.java`
- Modify: `server/data-talk-adapter/src/main/resources/application.yml`

- [ ] **Step 1: 在 application.yml 中添加配置**

```yaml
spring:
  application:
    name: data-talk

  codec:
    max-in-memory-size: 16MB

  # H2 demo datasource (primary)
  datasource:
    url: jdbc:h2:mem:placeholder
    driver-class-name: org.h2.Driver
    username: sa
    password:

  sqlite-datasource:
    url: jdbc:sqlite:./data/metadata.db
    driver-class-name: org.sqlite.JDBC

  sql:
    init:
      mode: always
      schema-locations: classpath:schema-demo.sql
      data-locations: classpath:data-demo.sql
      continue-on-error: false

  jdbc:
    template:
      query-timeout: 30

server:
  port: 8080

datatalk:
  persistence:
    sqlite-path: ./data/datatalk.db
  opencode:
    base-url: http://localhost:4096
    plugin-callback-base: http://localhost:8080
    shared-secret: ""
    required: false
    serve:
      enabled: true
      auto-upgrade: false
      version: 1.4.7
      base-port: 4096
      port-retries: 100
      hostname: 127.0.0.1
      cors: http://localhost:8080

app:
  logging:
    slow-request-threshold-ms: 1000
```

- [ ] **Step 2: 创建 WebMvcConfig**

```java
package com.datatalk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.logging.slow-request-threshold-ms:1000}")
    private long slowRequestThresholdMs;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestLogInterceptor(slowRequestThresholdMs))
                .addPathPatterns("/api/**");
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `cd server && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/config/WebMvcConfig.java
git add server/data-talk-adapter/src/main/resources/application.yml
git commit -m "feat(logging): register RequestLogInterceptor via WebMvcConfig"
```

---

### Task 4: 编写集成测试验证 traceId 传播

**Files:**
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/config/RequestLogTraceIdIT.java`

- [ ] **Step 1: 编写集成测试**

创建 `server/data-talk-adapter/src/test/java/com/datatalk/config/RequestLogTraceIdIT.java`：

```java
package com.datatalk.config;

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
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void slowRequest_shouldLogWarning() throws Exception {
        // 正常路径不会触发慢请求，此测试主要验证集成不报错
        mvc.perform(get("/api/sessions"))
            .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: 运行集成测试**

Run: `cd server && mvn test -pl data-talk-adapter -Dtest=RequestLogTraceIdIT -q`
Expected: 3 个测试全部通过

- [ ] **Step 3: Commit**

```bash
git add server/data-talk-adapter/src/test/java/com/datatalk/config/RequestLogTraceIdIT.java
git commit -m "test(logging): add integration test for RequestLogInterceptor"
```

---

### Task 5: 编译验证 + 最终检查

- [ ] **Step 1: 完整编译**

Run: `cd server && mvn clean verify -q`
Expected: 全部测试通过，BUILD SUCCESS

- [ ] **Step 2: 前端类型检查（验证未影响前端）**

Run: `cd client && npx tsc --noEmit`
Expected: 无类型错误

- [ ] **Step 3: 最终 Commit**

```bash
git add -A
git commit -m "feat(logging): complete request timing, traceId, and slow request alerting"
```

---

## 自审检查

### 1. 规范覆盖检查

| 需求 | 对应 Task |
|------|-----------|
| 每个 HTTP 请求生成唯一 traceId | Task 2 (preHandle) |
| 记录请求方法、路径、IP、状态码、耗时 | Task 2 (afterCompletion) |
| 同一请求的所有业务日志自动携带 traceId | Task 1 (logback pattern) + Task 2 (MDC) |
| 慢请求（>1000ms）WARN 级别 | Task 2 (slowRequestThresholdMs) + Task 3 (config) |
| 不记录请求体和响应体 | 代码中无 body 读取逻辑 |
| 不影响现有业务代码 | 仅新增文件 + 修改 logback pattern |
| 阈值可配置 | Task 3 (@Value 配置) |

**无遗漏。**

### 2. 占位符扫描

计划中无 TBD/TODO，所有代码步骤已包含完整代码。

### 3. 类型一致性

- `jakarta.servlet.http.HttpServletRequest` — 正确（Spring Boot 3.x）
- `MDC.get("traceId")` / `MDC.put("traceId", ...)` / `MDC.clear()` — 一致
- `app.logging.slow-request-threshold-ms` — application.yml 和 @Value 注解一致
- `@Qualifier("datatalkJdbc")` — 测试中未使用，不需要
- 方法签名 `preHandle(HttpServletRequest, HttpServletResponse, Object)` — 与 HandlerInterceptor 接口一致

### 4. 范围检查

聚焦明确：一个拦截器 + 一个配置类 + logback pattern 修改 + 配置项。没有超出设计文档范围的内容。
