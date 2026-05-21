# SSE Heartbeat & Async Timeout 治理 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 为 SSE GET 订阅通道加 30s 心跳（SSE 注释帧 `":\n\n"`）+ 无限 async timeout；POST turn 流保留 10 分钟上限；`AsyncRequestTimeoutException` 降级 DEBUG。

**Architecture:** 新增局部 `ScheduledExecutorService(2 threads, daemon)` 形成 `SseHeartbeatScheduler` bean，`ChannelController` 两个 emitter 创建点注册心跳并在 `onCompletion/onTimeout/onError` 中 `cancel`。GET timeout 改 `0L`（Tomcat: 0 = 无超时），POST 保持 `10 * 60_000L`。`@ControllerAdvice` 全局吞掉 `AsyncRequestTimeoutException` 降噪为 DEBUG。心跳不进 `SessionBus`、不占 `eventId`、不持久化。

**Tech Stack:** Spring Boot 3.5 / Java 21 / JUnit 5 / AssertJ / Mockito / Awaitility / Spring `WebClient`（测试）

**设计参照:** [docs/product-specs/2026-04-20-sse-heartbeat-design.md](../product-specs/2026-04-20-sse-heartbeat-design.md)

**依赖关系:**
- T1 / T2 / T3 互相独立，可并行实施
- T4 依赖 T1、T3
- T5 依赖 T4
- T6 最终验证依赖以上全部

---

## Task 1: `SseHeartbeatScheduler` + 单元测试

**Files:**
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/SseHeartbeatScheduler.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/channel/SseHeartbeatSchedulerTest.java`

- [x] **Step 1.1: 写失败的单元测试**

创建文件 `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/channel/SseHeartbeatSchedulerTest.java`：

```java
package com.datatalk.infra.channel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;

import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SseHeartbeatSchedulerTest {

    private SseHeartbeatScheduler scheduler;

    @BeforeEach
    void setUp() { scheduler = new SseHeartbeatScheduler(); }

    @AfterEach
    void tearDown() { scheduler.destroy(); }

    @Test
    void emitsHeartbeatAtInterval() {
        ResponseBodyEmitter emitter = mock(ResponseBodyEmitter.class);

        scheduler.register(emitter, 50L);

        await().atMost(ofMillis(400)).untilAsserted(() ->
            verify(emitter, atLeast(3)).send(any(), eq(MediaType.APPLICATION_OCTET_STREAM))
        );
    }

    @Test
    void cancelStopsFurtherEmissions() throws Exception {
        ResponseBodyEmitter emitter = mock(ResponseBodyEmitter.class);

        ScheduledFuture<?> future = scheduler.register(emitter, 50L);
        future.cancel(false);
        Thread.sleep(200);

        // cancel 后最多允许 1 次已入队的 tick 完成后再停
        verify(emitter, atMost(1)).send(any(), eq(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Test
    void sendExceptionCancelsFuture() throws Exception {
        ResponseBodyEmitter emitter = mock(ResponseBodyEmitter.class);
        doThrow(new IOException("broken pipe"))
            .when(emitter).send(any(), eq(MediaType.APPLICATION_OCTET_STREAM));

        ScheduledFuture<?> future = scheduler.register(emitter, 20L);

        await().atMost(ofMillis(300)).until(future::isCancelled);
    }

    @Test
    void registerAfterDestroyThrows() {
        scheduler.destroy();
        ResponseBodyEmitter emitter = mock(ResponseBodyEmitter.class);

        assertThatThrownBy(() -> scheduler.register(emitter, 50L))
            .isInstanceOf(RejectedExecutionException.class);
    }
}
```

- [x] **Step 1.2: 跑测试确认失败**

Run:
```
mvn -pl data-talk-infrastructure test -Dtest=SseHeartbeatSchedulerTest
```

Expected: 编译失败，`cannot find symbol: class SseHeartbeatScheduler`。

- [x] **Step 1.3: 实现 `SseHeartbeatScheduler`**

创建文件 `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/SseHeartbeatScheduler.java`：

```java
package com.datatalk.infra.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 为 SSE {@link ResponseBodyEmitter} 提供定时心跳，发送 SSE 注释帧 ":\n\n"。
 * 用途：避免 idle 期连接被 servlet async timeout 杀掉，并主动探活客户端。
 * 心跳仅是传输层保活机制，不经 SessionBus、不占 eventId、不持久化。
 */
@Component
public class SseHeartbeatScheduler implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SseHeartbeatScheduler.class);
    private static final byte[] HEARTBEAT_BYTES = ":\n\n".getBytes(StandardCharsets.UTF_8);

    private final ScheduledExecutorService exec;

    public SseHeartbeatScheduler() {
        AtomicInteger seq = new AtomicInteger();
        this.exec = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "sse-heartbeat-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 为 emitter 注册周期心跳任务。调用方必须在 emitter 的
     * onCompletion / onTimeout / onError 回调中 cancel 返回的 future，
     * 否则 scheduler 会继续尝试对已关闭 emitter 写入直到 send 抛异常。
     */
    public ScheduledFuture<?> register(ResponseBodyEmitter emitter, long intervalMs) {
        AtomicReference<ScheduledFuture<?>> ref = new AtomicReference<>();
        ScheduledFuture<?> future = exec.scheduleAtFixedRate(() -> {
            try {
                emitter.send(HEARTBEAT_BYTES, MediaType.APPLICATION_OCTET_STREAM);
            } catch (Exception e) {
                log.debug("Heartbeat send failed, cancelling task", e);
                ScheduledFuture<?> self = ref.get();
                if (self != null) self.cancel(false);
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        ref.set(future);
        return future;
    }

    @Override
    public void destroy() {
        exec.shutdownNow();
    }
}
```

- [x] **Step 1.4: 跑测试确认通过**

Run:
```
mvn -pl data-talk-infrastructure test -Dtest=SseHeartbeatSchedulerTest
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`.

- [x] **Step 1.5: 编译校验**

Run: `cd server && mvn compile -q`
Expected: 零错误输出。

- [x] **Step 1.6: Commit**

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/SseHeartbeatScheduler.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/channel/SseHeartbeatSchedulerTest.java
git commit -m "feat(channel): add SseHeartbeatScheduler for idle-resilient SSE streams"
```

---

## Task 2: `AsyncTimeoutHandler` + 单元测试

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/config/AsyncTimeoutHandler.java`
- Test: `server/data-talk-adapter/src/test/java/com/datatalk/config/AsyncTimeoutHandlerTest.java`

- [x] **Step 2.1: 写失败的单元测试**

创建文件 `server/data-talk-adapter/src/test/java/com/datatalk/config/AsyncTimeoutHandlerTest.java`：

```java
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
```

- [x] **Step 2.2: 跑测试确认失败**

Run:
```
mvn -pl data-talk-adapter test -Dtest=AsyncTimeoutHandlerTest
```

Expected: 编译失败，`cannot find symbol: class AsyncTimeoutHandler`。

- [x] **Step 2.3: 实现 `AsyncTimeoutHandler`**

创建文件 `server/data-talk-adapter/src/main/java/com/datatalk/config/AsyncTimeoutHandler.java`：

```java
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
```

- [x] **Step 2.4: 跑测试确认通过**

Run:
```
mvn -pl data-talk-adapter test -Dtest=AsyncTimeoutHandlerTest
```

Expected: `Tests run: 1, Failures: 0, Errors: 0`.

- [x] **Step 2.5: 编译校验**

Run: `cd server && mvn compile -q`
Expected: 零错误。

- [x] **Step 2.6: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/config/AsyncTimeoutHandler.java \
        server/data-talk-adapter/src/test/java/com/datatalk/config/AsyncTimeoutHandlerTest.java
git commit -m "feat(server): silence AsyncRequestTimeoutException via ControllerAdvice"
```

---

## Task 3: 心跳间隔配置项

**Files:**
- Modify: `server/data-talk-adapter/src/main/resources/application.yml`
- Modify: `server/data-talk-adapter/src/test/resources/application.yml`

- [x] **Step 3.1: 主 yml 加 `app.sse.heartbeat-interval-ms`**

Edit `server/data-talk-adapter/src/main/resources/application.yml`，在末尾的 `app:` 块下新增 `sse` 子节：

old:
```yaml
app:
  logging:
    slow-request-threshold-ms: 1000
```

new:
```yaml
app:
  logging:
    slow-request-threshold-ms: 1000
  sse:
    heartbeat-interval-ms: 30000
```

- [x] **Step 3.2: 测试 yml 覆盖为短间隔**

Edit `server/data-talk-adapter/src/test/resources/application.yml`，在末尾追加 `app` 块：

old（文件末尾）：
```yaml
datatalk:
  persistence:
    sqlite-path: ":memory:"
  master-key-hex: "0000000000000000000000000000000000000000000000000000000000000000"
  opencode:
    base-url: http://localhost:4096
    callback-base: http://localhost:8080
    serve:
      enabled: false
```

new：
```yaml
datatalk:
  persistence:
    sqlite-path: ":memory:"
  master-key-hex: "0000000000000000000000000000000000000000000000000000000000000000"
  opencode:
    base-url: http://localhost:4096
    callback-base: http://localhost:8080
    serve:
      enabled: false

app:
  sse:
    heartbeat-interval-ms: 200
```

- [x] **Step 3.3: 编译校验（无代码改动，跳过）**

本步骤为 YAML 改动，无代码变化。跳过编译校验。

- [x] **Step 3.4: Commit**

```bash
git add server/data-talk-adapter/src/main/resources/application.yml \
        server/data-talk-adapter/src/test/resources/application.yml
git commit -m "chore(config): add app.sse.heartbeat-interval-ms (prod=30s, test=200ms)"
```

---

## Task 4: `ChannelController` 接入心跳 + GET 无限 timeout

**Depends on:** Task 1, Task 3

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/ChannelController.java`

- [x] **Step 4.1: 替换 timeout 常量**

Edit 第 39-42 行：

old:
```java
    /** SSE streams need long idle timeout for slow model responses. Spring's default 30s triggers AsyncRequestTimeoutException. */
    private static final long SSE_STREAM_TIMEOUT_MS = 10L * 60_000L; // 10 minutes
    /** Upper bound on how long the POST thread blocks waiting for OpenCode to finish a turn. Matches the SSE idle timeout so the Spring-side timeout catches runaway sessions. */
    private static final long TURN_WAIT_TIMEOUT_MS = SSE_STREAM_TIMEOUT_MS;
```

new:
```java
    /** POST turn 流最长存活时间——单 turn 最长运行时间。 */
    private static final long POST_STREAM_TIMEOUT_MS = 10L * 60_000L;
    /** GET 订阅流 timeout——0 = Tomcat 不超时；存活由心跳探活 + 客户端断连决定。 */
    private static final long GET_STREAM_TIMEOUT_MS = 0L;
    /** POST 线程等待 OpenCode turn 完成的上限，与 POST emitter timeout 对齐。 */
    private static final long TURN_WAIT_TIMEOUT_MS = POST_STREAM_TIMEOUT_MS;
```

- [x] **Step 4.2: 引入新 import + 新字段 + 改造构造函数**

Edit `ChannelController.java`：

old（第 17-19 行附近）：
```java
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
```

new（在 `import org.springframework.web.bind.annotation.PostMapping;` 前加一行）：
```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
```

Edit `java.util.concurrent.atomic.AtomicBoolean;` 同一 import 区，加：
```java
import java.util.concurrent.ScheduledFuture;
```

old（第 46-57 行，构造函数 + 字段）：
```java
    private final JsonRpcCodec codec;
    private final ChannelService svc;
    private final SessionBusRegistry buses;
    private final ObjectMapper om;

    public ChannelController(JsonRpcCodec codec, ChannelService svc,
                             SessionBusRegistry buses, ObjectMapper om) {
        this.codec = codec;
        this.svc = svc;
        this.buses = buses;
        this.om = om;
    }
```

new:
```java
    private final JsonRpcCodec codec;
    private final ChannelService svc;
    private final SessionBusRegistry buses;
    private final ObjectMapper om;
    private final SseHeartbeatScheduler heartbeat;
    private final long heartbeatIntervalMs;

    public ChannelController(JsonRpcCodec codec, ChannelService svc,
                             SessionBusRegistry buses, ObjectMapper om,
                             SseHeartbeatScheduler heartbeat,
                             @Value("${app.sse.heartbeat-interval-ms:30000}") long heartbeatIntervalMs) {
        this.codec = codec;
        this.svc = svc;
        this.buses = buses;
        this.om = om;
        this.heartbeat = heartbeat;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }
```

- [x] **Step 4.3: 改造 `subscribe()` — GET 无限 timeout + 注册心跳 + 清理路径 cancel**

old（第 79-100 行）：
```java
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseBodyEmitter subscribe(
        @PathVariable String sessionId,
        @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId
    ) {
        SessionBus bus = buses.getOrCreate(sessionId);
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(SSE_STREAM_TIMEOUT_MS);
        SseEmitterSubscriber sub = new SseEmitterSubscriber(
            new EmitterOutputStream(emitter), om, "connected");
        String clientId = "read-" + System.nanoTime();

        // Publish connected and subscribe
        bus.publish(new DtEvent.Connected(sessionId, 1));
        bus.subscribe(clientId, lastEventId == null ? 0L : lastEventId, sub);

        // Unsubscribe on disconnect; trigger eviction when last subscriber leaves
        Runnable onDisconnect = () -> { bus.unsubscribe(clientId); buses.onUnsubscribe(sessionId); };
        emitter.onCompletion(onDisconnect);
        emitter.onTimeout(onDisconnect);

        return emitter;
    }
```

new:
```java
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseBodyEmitter subscribe(
        @PathVariable String sessionId,
        @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId
    ) {
        SessionBus bus = buses.getOrCreate(sessionId);
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(GET_STREAM_TIMEOUT_MS);
        ScheduledFuture<?> hb = heartbeat.register(emitter, heartbeatIntervalMs);
        SseEmitterSubscriber sub = new SseEmitterSubscriber(
            new EmitterOutputStream(emitter), om, "connected");
        String clientId = "read-" + System.nanoTime();

        // Publish connected and subscribe
        bus.publish(new DtEvent.Connected(sessionId, 1));
        bus.subscribe(clientId, lastEventId == null ? 0L : lastEventId, sub);

        // 断连清理：取消心跳 + 摘订阅 + 触发 bus 驱逐（onError 路径新补）
        Runnable onDisconnect = () -> {
            hb.cancel(false);
            bus.unsubscribe(clientId);
            buses.onUnsubscribe(sessionId);
        };
        emitter.onCompletion(onDisconnect);
        emitter.onTimeout(onDisconnect);
        emitter.onError(ex -> onDisconnect.run());

        return emitter;
    }
```

- [x] **Step 4.4: 改造 `stream()` — 注册心跳 + 清理路径 cancel**

**注意**：仅修改 `stream()` 方法**前半部分**（emitter 创建 + `onDisconnect` 定义）。后面的 `Thread t = new Thread(...)` 执行 `svc.sendMessage` / 等待 `turnDone` / `emitter.complete()` 的整块逻辑**保持原样不动**，本 step 不触碰。

old（第 102-126 行）：
```java
    private ResponseBodyEmitter stream(String sessionId,
                                       RpcRequest.SendMessage m,
                                       Long lastEventId) {
        SessionBus bus = buses.getOrCreate(sessionId);
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(SSE_STREAM_TIMEOUT_MS);
        SseEmitterSubscriber sub = new SseEmitterSubscriber(
            new EmitterOutputStream(emitter), om, "connected");
        String clientId = "post-" + System.nanoTime();
        String watcherId = "post-watch-" + System.nanoTime();

        // Latch that fires once OpenCode signals the turn is done (session.idle /
        // session.error / session.status:idle) or the client goes away.
        CountDownLatch turnDone = new CountDownLatch(1);
        AtomicBoolean clientGone = new AtomicBoolean(false);

        Runnable onDisconnect = () -> {
            clientGone.set(true);
            turnDone.countDown();
            bus.unsubscribe(clientId);
            bus.unsubscribe(watcherId);
            buses.onUnsubscribe(sessionId);
        };
        emitter.onCompletion(onDisconnect);
        emitter.onTimeout(onDisconnect);
        emitter.onError(ex -> onDisconnect.run());
```

new:
```java
    private ResponseBodyEmitter stream(String sessionId,
                                       RpcRequest.SendMessage m,
                                       Long lastEventId) {
        SessionBus bus = buses.getOrCreate(sessionId);
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(POST_STREAM_TIMEOUT_MS);
        ScheduledFuture<?> hb = heartbeat.register(emitter, heartbeatIntervalMs);
        SseEmitterSubscriber sub = new SseEmitterSubscriber(
            new EmitterOutputStream(emitter), om, "connected");
        String clientId = "post-" + System.nanoTime();
        String watcherId = "post-watch-" + System.nanoTime();

        // Latch that fires once OpenCode signals the turn is done (session.idle /
        // session.error / session.status:idle) or the client goes away.
        CountDownLatch turnDone = new CountDownLatch(1);
        AtomicBoolean clientGone = new AtomicBoolean(false);

        Runnable onDisconnect = () -> {
            hb.cancel(false);
            clientGone.set(true);
            turnDone.countDown();
            bus.unsubscribe(clientId);
            bus.unsubscribe(watcherId);
            buses.onUnsubscribe(sessionId);
        };
        emitter.onCompletion(onDisconnect);
        emitter.onTimeout(onDisconnect);
        emitter.onError(ex -> onDisconnect.run());
```

- [x] **Step 4.5: 编译校验**

Run: `cd server && mvn compile -q`
Expected: 零错误。

- [x] **Step 4.6: 跑模块内现有测试确认未破坏原语义**

Run:
```
mvn -pl data-talk-infrastructure test
```

Expected: 全部已有测试通过（包含 `ChannelControllerTurnDoneTest` 等），没有新的失败。

- [x] **Step 4.7: Commit**

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/ChannelController.java
git commit -m "feat(channel): wire heartbeat + make GET subscription timeout infinite"
```

---

## Task 5: 集成测试 `emitsHeartbeatFramesWhileIdle`

**Depends on:** Task 4

**Files:**
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/channel/ChannelControllerIT.java`

- [x] **Step 5.1: 追加集成测试用例**

Edit `ChannelControllerIT.java`，在文件顶部 import 区补充：

old：
```java
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
```

new：
```java
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
```

然后在类末尾（最后一个 `}` 之前）新增测试方法：

```java
    @Test
    void emitsHeartbeatFramesWhileIdle() {
        StringBuilder raw = new StringBuilder();

        client.get()
            .uri("/api/sessions/s-1/channel")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchangeToFlux(resp -> resp.bodyToFlux(DataBuffer.class))
            .take(Duration.ofMillis(800))
            .doOnNext(db -> {
                byte[] bytes = new byte[db.readableByteCount()];
                db.read(bytes);
                DataBufferUtils.release(db);
                raw.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            })
            .blockLast(Duration.ofSeconds(2));

        // test yml 配置 heartbeat-interval-ms=200，800ms 窗口应收到 ≥2 次
        // 心跳帧字面量为 ":\n\n" —— 行首冒号后立即换行，区分于 "event: x\n" 类业务帧
        String s = raw.toString();
        int count = 0;
        int idx = 0;
        while ((idx = s.indexOf(":\n\n", idx)) != -1) {
            count++;
            idx += 3;
        }
        assertThat(count).isGreaterThanOrEqualTo(2);
    }
```

- [x] **Step 5.2: 跑新测试确认通过**

Run:
```
mvn -pl data-talk-adapter -am test -Dtest=ChannelControllerIT#emitsHeartbeatFramesWhileIdle
```

Expected: `Tests run: 1, Failures: 0, Errors: 0`.

说明：`-am` 确保 `data-talk-infrastructure` 先编译到最新字节码（带心跳接入），否则 IT 会用 m2 里的旧 jar。

- [x] **Step 5.3: 跑 IT 全量确认未破坏现有 3 个用例**

Run:
```
mvn -pl data-talk-adapter -am test -Dtest=ChannelControllerIT
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`（原 3 个 + 新增 1 个）。

- [x] **Step 5.4: Commit**

```bash
git add server/data-talk-adapter/src/test/java/com/datatalk/adapter/channel/ChannelControllerIT.java
git commit -m "test(channel): assert heartbeat frames emitted during idle GET subscription"
```

---

## Task 6: 全量验证

**Depends on:** Task 1-5

- [x] **Step 6.1: 全量 `mvn clean verify`**

Run:
```
cd server && mvn clean verify
```

Expected: `BUILD SUCCESS`，所有模块测试全绿。

- [x] **Step 6.2: 手动冒烟验证（可选，供人工联调）**

1. 启动后端：`cd server && mvn spring-boot:run -pl data-talk-adapter`
2. 启动前端 Tauri dev：`cd client && npm run tauri dev`
3. 打开一个 session 但不发消息，让它空闲 35 秒
4. 观察后端日志：应 **不再出现** `AsyncRequestTimeoutException ... response committed already` 这条 WARN
5. 用浏览器 DevTools Network 看 `GET /api/sessions/:id/channel`，`EventStream` 标签页每 30s 会有一条空行注释（不可见）；响应不会被服务端主动关闭

- [x] **Step 6.3: 更新 plan 索引到「已完成」**

Edit `docs/exec-plans/index.md`，将本 plan 从活跃移到已完成（保留时间倒序），在 `## 已完成计划` 表格第一行插入：

```
| [SSE Heartbeat & Async Timeout 治理](./2026-04-20-sse-heartbeat-plan.md) | 2026-04-20 | SSE GET 订阅改无限 timeout + 30s 心跳注释帧（`":\n\n"`）主动探活；`AsyncRequestTimeoutException` 降级 DEBUG；`SseHeartbeatScheduler` 新 bean + `AsyncTimeoutHandler` `@ControllerAdvice` |
```

并从 `## 活跃计划` 表格中删除对应条目。

- [x] **Step 6.4: 同步更新 spec 状态**

Edit `docs/product-specs/2026-04-20-sse-heartbeat-design.md` 第 3 行：
- 把 `> **状态**：待评审` 改为 `> **状态**：已实现（2026-04-20）`

- [x] **Step 6.5: Commit 文档归档**

```bash
git add docs/exec-plans/index.md docs/product-specs/2026-04-20-sse-heartbeat-design.md
git commit -m "docs: mark SSE heartbeat plan complete and archive"
```

---

## 风险与回退

- **回退策略**：所有改动分 5 个 commit，任一 commit 可 `git revert` 独立回滚
- **监控建议**：部署后观察一周生产日志：
  - `AsyncRequestTimeoutException` WARN 频次应归零
  - `Heartbeat send failed` DEBUG 可能偶尔出现（客户端正常断开），属正常
  - 若 `sse-heartbeat-*` 线程 CPU 占用超过 1%，说明 emitter 数量远超设计，考虑增大线程池

## 验证清单

- [x] `mvn clean verify` 在 server/ 根目录通过
- [x] 新增 4 个 scheduler 单测、1 个 handler 单测、1 个心跳 IT 全绿
- [x] 现有 `ChannelControllerIT` 3 个用例、`ChannelControllerTurnDoneTest` 继续通过
- [x] Spec / Plan 索引同步更新
