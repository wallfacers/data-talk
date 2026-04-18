# 请求耗时统计和全链路日志跟踪设计

**日期**: 2026-04-18
**状态**: Draft

## 1. 目标

为 DataTalk 后端实现 HTTP 请求级别的耗时统计和全链路日志跟踪，使每个请求都有唯一的 traceId，所有相关日志可追溯，慢请求自动标记告警。

## 2. 需求

- 每个 HTTP 请求生成唯一 traceId
- 记录请求方法、路径、客户端 IP、响应状态码、耗时
- 同一请求的所有业务日志自动携带 traceId
- 慢请求（>1000ms）使用 WARN 级别记录，阈值可配置
- 不记录请求体和响应体内容
- 不影响现有业务代码

## 3. 架构

```
HTTP Request
     │
     ▼
┌─────────────────────────┐
│  RequestLogInterceptor  │  HandlerInterceptor (preHandle)
│  ├─ 生成 UUID traceId   │
│  ├─ traceId → MDC       │
│  └─ 记录 nanoTime       │
└─────────────────────────┘
     │
     ▼
┌─────────────────────────┐
│    Controller / Service │  所有 log.*() 自动带 traceId
└─────────────────────────┘
     │
     ▼
┌─────────────────────────┐
│  RequestLogInterceptor  │  HandlerInterceptor (afterCompletion)
│  ├─ 计算耗时            │
│  ├─ 按阈值输出日志       │
│  └─ 清理 MDC            │
└─────────────────────────┘
     │
     ▼
HTTP Response
```

## 4. 组件

### 4.1 RequestLogInterceptor

**位置**: `server/data-talk-adapter/src/main/java/com/datatalk/config/RequestLogInterceptor.java`

**职责**:
- `preHandle`: 生成 `UUID.randomUUID().toString().replace("-", "")` 作为 traceId，放入 `MDC.put("traceId", traceId)`，记录 `System.nanoTime()` 到 request attribute
- `afterCompletion`: 从 request attribute 取出 startTime，计算耗时毫秒，根据状态码和耗时输出结构化日志，`MDC.clear()` 清理

**慢请求阈值**: 从 `application.yml` 读取 `app.logging.slow-request-threshold-ms`，默认 1000ms

**日志级别策略**:
- 耗时 < 阈值: INFO
- 耗时 >= 阈值: WARN，日志尾部追加 `[SLOW]` 标记
- 状态码 >= 400: ERROR，追加异常信息（如果有）

### 4.2 WebMvcConfig

**位置**: `server/data-talk-adapter/src/main/java/com/datatalk/config/WebMvcConfig.java`

**职责**:
- 实现 `WebMvcConfigurer`
- `addInterceptors()`: 注册 `RequestLogInterceptor`，拦截 `/api/**`

**为什么是 WebMvcConfigurer 而不是 @Component**: 拦截器注册需要显式配置，避免自动扫描导致重复注册。

### 4.3 Logback 格式增强

**文件**: `server/data-talk-adapter/src/main/resources/logback-spring.xml`

**变更**: 在 CONSOLE 和 FILE appender 的 pattern 中加入 `%X{traceId}`

原 pattern:
```
%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n
```

新 pattern:
```
%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] [%X{traceId:-}] %logger{36} - %msg%n
```

`%X{traceId:-}` 表示如果 MDC 中没有 traceId（如启动日志），输出空字符串而非 "null"。

## 5. 日志输出格式

### 5.1 请求日志（Interceptor 输出）

**正常请求** (INFO):
```
2026-04-18 10:30:45.123 INFO  [main] [a1b2c3d4] c.d.config.RequestLogInterceptor - GET /api/sessions/123 | ip=127.0.0.1 | ua=DataTalk/1.0 → 200 | 45ms
```

**慢请求** (WARN):
```
2026-04-18 10:30:45.123 WARN  [main] [e5f6g7h8] c.d.config.RequestLogInterceptor - POST /api/sessions/123/channel | ip=127.0.0.1 → 200 | 2341ms [SLOW]
```

**错误请求** (ERROR):
```
2026-04-18 10:30:45.123 ERROR [main] [i9j0k1l2] c.d.config.RequestLogInterceptor - GET /api/sessions/999 → 404 | 12ms [ERROR] NoSuchElementException: Session not found
```

### 5.2 业务日志（自动携带 traceId）

```
2026-04-18 10:30:45.100 DEBUG [main] [a1b2c3d4] c.d.application.session.SessionService - Fetching session by id: 123
2026-04-18 10:30:45.120 WARN  [main] [a1b2c3d4] c.d.application.session.SessionService - Session 123 not found in cache, fallback to DB
```

## 6. 异常处理

- `afterCompletion` 中的异常不会影响原有响应（响应已发送）
- 使用 `try-finally` 确保 `MDC.clear()` 始终执行，防止 traceId 泄漏到后续请求
- 如果 `GlobalExceptionHandler` 捕获了异常，拦截器通过 `request.getAttribute("javax.servlet.error.exception")` 获取异常信息

## 7. 影响范围

| 文件 | 操作 | 说明 |
|------|------|------|
| `RequestLogInterceptor.java` | 新建 | 核心拦截器 |
| `WebMvcConfig.java` | 新建 | 注册拦截器 |
| `logback-spring.xml` | 修改 | pattern 加 `%X{traceId:-}` |
| `application.yml` | 修改 | 添加 `app.logging.slow-request-threshold-ms` 配置 |
| 现有 Controller/Service | 无改动 | 自动继承 traceId |

## 8. 配置

```yaml
# application.yml
app:
  logging:
    slow-request-threshold-ms: 1000  # 慢请求阈值，超过此值的请求记为 WARN
```

## 9. 测试

- `RequestLogInterceptorTest`: 单元测试，mock HttpServletRequest/HttpServletResponse/HandlerMethod
  - 验证正常请求输出 INFO 日志
  - 验证慢请求输出 WARN 日志
  - 验证错误请求输出 ERROR 日志
  - 验证 MDC clear 始终执行
- 集成测试：通过 MockMvc 发送请求，验证日志输出包含 traceId

## 10. 非目标

- 不记录请求体和响应体内容
- 不暴露 HTTP 监控端点（如 Actuator /metrics）
- 不集成外部监控系统（Prometheus/Grafana）
- 不追踪下游服务调用（如 OpenCode 服务内部耗时）
