# SSE Heartbeat & Async Timeout 治理设计

> **日期**：2026-04-20
> **状态**：已实现（2026-04-20）
> **相关层级**：`data-talk-application`（无改动）/ `data-talk-infrastructure`（心跳调度 + Controller 集成）/ `data-talk-adapter`（全局异常降噪 + 配置项）

## 1. 背景

`ChannelController` 两个 SSE 端点（`POST` turn 流 / `GET` 订阅流）均使用 `new ResponseBodyEmitter(SSE_STREAM_TIMEOUT_MS=10*60_000L)`。运行时观察到如下噪音日志：

```
WARN  o.s.w.s.m.s.DefaultHandlerExceptionResolver - Ignoring exception, response committed already:
  org.springframework.web.context.request.async.AsyncRequestTimeoutException
```

根因：

1. **`ResponseBodyEmitter` 的 timeout 语义是"异步请求总存活时间"，不是 idle timeout**。Tomcat 的 `AsyncContext.setTimeout(long)` 从 dispatch 返回开始计时，不会被 `emitter.send(...)` 重置。即订阅连接一旦建立，无论是否还有数据流动，10 分钟到点必被踢。
2. **通道缺少心跳帧**。idle 期间没有任何字节从服务端推向客户端，无法尽早感知中间代理 / NAT 断连，也会因长时间 idle 触发 async timeout。
3. **异常产生时 SSE 响应头已 committed**，Spring 无法再写 503，`DefaultHandlerExceptionResolver` 只能以 WARN 吞掉；但这条 WARN 具有误导性（听起来像 bug，实际是设计语义不匹配）。

## 2. 目标

- GET 订阅通道支持**长期存活**（数小时~数日级别），直到客户端主动断开或 TCP 失联
- 通过定期 SSE 注释帧（heartbeat）**主动探测**客户端存活，尽早清理死连接
- POST turn 流保留 10 分钟上限作为"单 turn 最长运行时间"业务兜底
- 消除 `AsyncRequestTimeoutException` 的误导性 WARN（降级到 DEBUG），不掩盖其他异步异常

## 3. 非目标

- 不引入客户端心跳（EventSource API 自带重连，无需额外应用层协议）
- 不改变 `DtEvent` 模型（心跳不是业务事件，不进事件总线，不占 `eventId`，不持久化）
- 不重构 `SessionBus` / `ChannelService`
- 不引入 Spring `@EnableScheduling`（避免容器级调度器污染，用局部 `ScheduledExecutorService`）

## 4. 设计方案

### 4.1 总览

```
┌─────────────────────────────────────────────────────────────────────┐
│  ChannelController (POST / GET)                                     │
│  ─────────────────────────────────────────────────────────────────  │
│  new ResponseBodyEmitter(timeout)                                   │
│     │                                                               │
│     ├── POST:  timeout = 10 * 60_000L   (turn 上限，保持不变)       │
│     └── GET:   timeout = 0L             (无限，靠心跳探活)          │
│                                                                     │
│  heartbeat.register(emitter)  →  ScheduledFuture                    │
│     │                                                               │
│     └── 每 N 秒执行: emitter.send(":\n\n", TEXT_PLAIN)              │
│                                                                     │
│  onCompletion / onTimeout / onError:                                │
│     future.cancel(false)                                            │
│     onDisconnect.run()   (原有清理：unsubscribe + bus eviction)     │
└─────────────────────────────────────────────────────────────────────┘
                        ▲                          ▲
                        │                          │
┌───────────────────────┴──────────┐   ┌───────────┴─────────────────┐
│ SseHeartbeatScheduler @Component │   │ AsyncTimeoutHandler         │
│  (infrastructure/channel)        │   │ @ControllerAdvice           │
│  ─────────────────────────────── │   │  (adapter/config)           │
│  ScheduledExecutorService(2)     │   │  ─────────────────────────  │
│  daemon=true                     │   │  捕获                       │
│  DisposableBean → shutdownNow()  │   │  AsyncRequestTimeoutException│
│                                  │   │  → log.debug() 并返回空响应 │
└──────────────────────────────────┘   └─────────────────────────────┘
```

### 4.2 新增组件：`SseHeartbeatScheduler`

**位置**：`data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/SseHeartbeatScheduler.java`

**职责**：为 SSE emitter 提供"每 N 毫秒发送一次 SSE 注释帧"的调度能力。纯传输层保活，与业务事件解耦。

**API**：

```java
@Component
public class SseHeartbeatScheduler implements DisposableBean {

    /**
     * 注册一个心跳任务。调用方在 emitter 的 onCompletion/onTimeout/onError
     * 回调中必须 cancel 返回的 future，以释放定时任务与避免对已关闭 emitter 的写操作。
     *
     * @param emitter 目标 emitter
     * @param intervalMs 心跳间隔（毫秒）
     * @return 可取消的定时任务句柄
     */
    ScheduledFuture<?> register(ResponseBodyEmitter emitter, long intervalMs);

    @Override
    void destroy();  // shutdownNow()
}
```

**关键实现细节**：

- `ScheduledExecutorService` 容量 **2 线程**（心跳是极轻量 IO，2 线程足以服务数百并发 emitter；过多反而浪费）
- 自定义 `ThreadFactory` 生成 **daemon 线程**（命名 `sse-heartbeat-<n>`），避免 JVM 退出阻塞
- 心跳帧内容：`":\n\n"`（SSE 规范：以 `:` 开头的行为注释，客户端 `EventSource` 自动忽略）
- 心跳发送异常处理：`emitter.send(...)` 抛 `IOException` / `IllegalStateException` 时，**catch 住、调度自身 `cancel(false)`、不向上抛**。由 Spring 的 emitter 回调（`onError`）负责清理订阅，避免 scheduler 线程直接操作 `SessionBus`（保持单一职责）
- `destroy()` 在 bean 销毁时 `shutdownNow()`，与现有 `server.shutdown=graceful` + `spring.lifecycle.timeout-per-shutdown-phase=3s` 协同

### 4.3 `ChannelController` 改造

**文件**：`data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/ChannelController.java`

**改动清单**：

1. 构造函数注入 `SseHeartbeatScheduler`
2. 新增 `@Value("${app.sse.heartbeat-interval-ms:30000}")` 注入心跳间隔（毫秒）
3. 常量拆分：
   - `POST_STREAM_TIMEOUT_MS = 10 * 60_000L`（单 turn 上限，不变）
   - `GET_STREAM_TIMEOUT_MS = 0L`（Tomcat 语义：0 = 无超时）
4. `subscribe(...)` 方法中 `new ResponseBodyEmitter(GET_STREAM_TIMEOUT_MS)` 后立即 `scheduler.register(emitter, heartbeatIntervalMs)`，拿到 `future`
5. `stream(...)` 方法同样注册心跳（POST 也要，因为 turn 中模型可能长时间思考不出字）
6. 改写 `onCompletion` / `onTimeout` / `onError` 三个回调：首先 `future.cancel(false)`，再调用原有 `onDisconnect.run()`

**伪代码示例（`subscribe` 部分）**：

```java
ResponseBodyEmitter emitter = new ResponseBodyEmitter(GET_STREAM_TIMEOUT_MS);
ScheduledFuture<?> hb = heartbeat.register(emitter, heartbeatIntervalMs);

Runnable onDisconnect = () -> { bus.unsubscribe(clientId); buses.onUnsubscribe(sessionId); };
Runnable cleanup = () -> { hb.cancel(false); onDisconnect.run(); };

emitter.onCompletion(cleanup);
emitter.onTimeout(cleanup);
emitter.onError(ex -> cleanup.run());
```

### 4.4 新增组件：`AsyncTimeoutHandler`

**位置**：`data-talk-adapter/src/main/java/com/datatalk/config/AsyncTimeoutHandler.java`

**职责**：全局拦截 `AsyncRequestTimeoutException`，降噪到 DEBUG。SSE 端点 GET 无限 + 心跳后基本不再触发；POST 10 分钟 turn 上限仍可能触发；此外未来新增的其他 SSE 端点也天然受益。

```java
@ControllerAdvice
public class AsyncTimeoutHandler {

    private static final Logger log = LoggerFactory.getLogger(AsyncTimeoutHandler.class);

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void onAsyncTimeout(AsyncRequestTimeoutException ex, HttpServletRequest req) {
        log.debug("Async request timed out for {} {}", req.getMethod(), req.getRequestURI());
        // 返回类型 void + 无 @ResponseStatus：因为响应已 committed，
        // 让 Spring 直接结束即可；不返回体也不设 status。
    }
}
```

**关键点**：只处理这一个异常类，其他异步异常（含 `IOException` 等）不受影响。

### 4.5 配置项

**文件**：`data-talk-adapter/src/main/resources/application.yml`

```yaml
app:
  logging:
    slow-request-threshold-ms: 1000
  sse:                          # 新增
    heartbeat-interval-ms: 30000
```

**测试配置覆盖**：`data-talk-adapter/src/test/resources/application.yml` 或 `@TestPropertySource` 中指定 `app.sse.heartbeat-interval-ms=200`，使集成测试可在秒级完成。

## 5. 测试策略

### 5.1 单元测试

**文件**：`data-talk-infrastructure/src/test/java/com/datatalk/infra/channel/SseHeartbeatSchedulerTest.java`

用例：

| # | 场景 | 断言 |
|---|------|------|
| 1 | `register(emitter, 50ms)`，等 200ms | `emitter.send` 至少被调用 3 次，内容为 `":\n\n"` 字节 |
| 2 | `register` 后立即 `future.cancel(false)`，等 200ms | `emitter.send` 调用次数 ≤ 1 |
| 3 | emitter.send 抛 `IOException` | future 被取消（`isCancelled()` 为 true），不再继续调度；异常不向上传播 |
| 4 | `destroy()` 后调用 `register` | 抛 `RejectedExecutionException`（`ScheduledExecutorService` 在 `shutdownNow()` 后的标准行为，`register` 透传不额外包装） |

使用 stub `ResponseBodyEmitter` 或 `Mockito` 验证调用次数与参数。

### 5.2 集成测试

**文件**：`data-talk-adapter/src/test/java/com/datatalk/adapter/channel/ChannelControllerIT.java`（新增用例）

```java
@Test
@TestPropertySource(properties = "app.sse.heartbeat-interval-ms=200")
void emits_heartbeat_while_idle() {
    // 1. 建立 GET /api/sessions/{id}/channel 订阅
    // 2. 不触发任何业务事件
    // 3. 500ms 内从响应流中读到至少 2 个 ":\n\n" 帧
    // 4. 无业务 event: 帧
}
```

读取时注意：SSE 注释帧不会被标准 `WebTestClient` 事件反序列化器当作 `ServerSentEvent` 吐出，需要直接读原始字节（例如 `exchange().returnResult(byte[].class).getResponseBodyContent().block()` 后扫描注释模式）。

### 5.3 回归验证

- 现有 `ChannelControllerIT` / `ChannelControllerTurnDoneTest` 用例应全部通过（心跳不破坏任何已有语义）
- `mvn clean verify` 应全绿

## 6. 实施顺序

1. 实现 `SseHeartbeatScheduler` + 单元测试（完全独立）
2. 实现 `AsyncTimeoutHandler` + 最小测试（完全独立）
3. 改造 `ChannelController`（注入 scheduler、拆分 timeout 常量、注册/取消心跳）
4. 新增 `application.yml` 配置项
5. 新增集成测试 `emits_heartbeat_while_idle`
6. 全量 `mvn clean verify`

1、2、4 两两独立，可并行；3 依赖 1；5 依赖 3。

## 7. 风险与对策

| 风险 | 对策 |
|------|------|
| 心跳线程池太小导致延迟累积 | 2 线程服务 send `":\n\n"` 这种 μs 级 IO 绰绰有余；如观察到延迟可调大为 4 |
| 慢消费者（TCP backpressure）阻塞心跳线程 | `emitter.send` 在网络阻塞时会同步阻塞，极端情况下两个慢消费者可占满 2 个线程。对策：慢消费者最终会触发 `onError` → `future.cancel` 自解除；若成常态问题，可增大线程池或在 scheduler 内包 `Future.get(timeout)` 中断 |
| emitter 已关闭时 send 抛 `IllegalStateException` | scheduler 同样 catch + cancel，与 `IOException` 走同一分支 |
| `ResponseBodyEmitter(0L)` 行为在不同 servlet 容器有差异 | 本项目固定 Tomcat（Spring Boot 默认），`AsyncContext.setTimeout(0)` 是 Servlet 3.1 定义的"no timeout"语义，可靠 |
| 前端 `EventSource` 兼容性 | SSE 规范第 9.2.4 节：以 `:` 开头的行必须被解析器忽略。所有主流浏览器（Chromium、Firefox、Safari/WebKit）都符合；Tauri v2 webview 使用系统 webview（macOS/WKWebView、Linux/WebKitGTK、Windows/WebView2），全部 OK |
| `AsyncTimeoutHandler` 意外吞掉真实问题 | 只处理 `AsyncRequestTimeoutException` 这一个类；其他异步异常（如 `IOException`）不受影响，仍走原处理链 |
| POST turn 被心跳"续命"超过 10 分钟 | 不会。Tomcat timeout 是总存活时间，心跳 send 不重置；10 分钟到点仍会触发 onTimeout，走原路径 |

## 8. 影响面评估

- **API 兼容性**：零改动。请求 / 响应签名 / SSE 事件名全部不变。客户端无需任何修改。
- **数据模型**：零改动。
- **其他模块**：零改动。`SessionBus` / `ChannelService` / `OpenCodeGateway` 不感知心跳存在。
- **运维**：日志静默了 `AsyncRequestTimeoutException`；如果团队依赖这条日志做监控（不太可能），需要调整告警规则。

## 9. 参考

- MDN SSE 规范：`EventSource` API 第 9.2.4 节"Comment lines"
- Servlet 3.1 `AsyncContext.setTimeout(long)`：`0 or less` → no timeout
- Spring `ResponseBodyEmitterReturnValueHandler#handleReturnValue`：透传 `emitter.getTimeout()` 到 `AsyncWebRequest.setTimeout(...)`
- 现有相关文件：
  - `data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/ChannelController.java`
  - `data-talk-application/src/main/java/com/datatalk/application/session/SseEmitterSubscriber.java`
  - `data-talk-application/src/main/java/com/datatalk/application/session/SessionBus.java`
