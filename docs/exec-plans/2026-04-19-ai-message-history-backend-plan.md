# AI 消息历史后端改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 让 `GET /api/sessions/{id}/messages` 透传 OpenCode 的消息 API，删除本地 `messages` 表；`OpenCodeEventTranslator` 的 Part payload 改为透传 OpenCode 原生 JSON；`ActionDescriptor` 扩展 `riskLevel` / `category` 字段并在现有 7 个 Action 上注解回填。修复「切换会话后 AI 消息丢失」bug 并为前端风险分级视觉提供数据源。

**Architecture:**
1. 消息持久化职责完全下沉到 OpenCode（已有 `GET /session/:id/message`）。DataTalk 后端不再本地存 `messages`，`HistoryService` 通过 `OpenCodeGateway` 代理透传。
2. `DtEvent.MessagePart*` 事件的 `part` 字段类型由 sealed interface `Part` 改为 `JsonNode`，避免在 DataTalk 层做反序列化/再序列化损失 OpenCode 原生字段（尤其 `sessionID`/`messageID`/`step-start` 等）。
3. `ActionDescriptor` 新增两个可空字段 `riskLevel` / `category`，由 `@DataTalkAction` 注解驱动；`part.state.metadata.riskLevel` 通道通过 Task 4 的 JSON 透传自然打通，本期后端回传恒为空（留给 TD-020）。
4. `ChannelService.sendMessage` 删除本地 USER 消息生成 + `DtEvent.MessageCreated/MessagePartCreated` 广播；USER 消息的前端呈现依赖 OpenCode 回推事件（前端以乐观 UI 填补空窗）。

**Tech Stack:** Spring Boot 3.5、Java 21（虚拟线程）、JdbcTemplate、Flyway SQLite、JUnit 5、AssertJ、WireMock 3.x、Spring WebClient

**Pre-requisites before task 1:**
- 确认当前分支 clean（`git status`）
- 后端基线编译通过（`cd server && mvn compile -q`）

---

## Phase 1 — 消息透传（修 bug 最小集）

完成 Phase 1 + 前端阶段 0（Spec §7.6）后，「AI 消息切换丢失」「流式无流式感」「USER 消息 ID 不一致」三个原 bug 全部解决。

### Task 1: OpenCodeHttpClient 新增 listMessages 方法

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeHttpClient.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/OpenCodeHttpClientTest.java`

**Context:** 现有 `OpenCodeHttpClient` 只有 `sendMessage` / `createSession` / `deleteSession` 等写操作，需要新增对 OpenCode `GET /session/{id}/message` 接口的封装。返回 JSON 数组，透传给上层。

- [x] **Step 1: 在 `OpenCodeHttpClientTest` 添加 listMessages 的 WireMock 测试**

在 `OpenCodeHttpClientTest` 里找到 `createSession` 测试附近，加一个新 `@Test`：

```java
@Test
void listMessagesReturnsOpenCodePayload() {
    stubFor(get(urlPathEqualTo("/session/ses_abc/message"))
        .withQueryParam("limit", equalTo("100"))
        .willReturn(okJson("""
            [
              {
                "info": { "id": "msg_1", "role": "user", "sessionID": "ses_abc",
                          "time": { "created": 1776000000000 } },
                "parts": [
                  { "type": "text", "text": "hi", "id": "prt_1",
                    "sessionID": "ses_abc", "messageID": "msg_1" }
                ]
              }
            ]
            """)));

    JsonNode node = client.listMessages("ses_abc", 100);

    assertThat(node.isArray()).isTrue();
    assertThat(node).hasSize(1);
    assertThat(node.get(0).path("info").path("id").asText()).isEqualTo("msg_1");
    assertThat(node.get(0).path("parts").get(0).path("text").asText()).isEqualTo("hi");
}
```

- [x] **Step 2: 运行测试确认失败**

```bash
cd server && mvn -pl data-talk-infrastructure test -Dtest=OpenCodeHttpClientTest#listMessagesReturnsOpenCodePayload -q
```

Expected: FAIL with 编译错 `cannot find symbol: method listMessages`.

- [x] **Step 3: 在 `OpenCodeHttpClient` 加实现**

在 `listProviders()` 上方（第 93 行左右）加：

```java
public JsonNode listMessages(String openCodeSessionId, Integer limit) {
    String body = wc.get()
        .uri(uriBuilder -> uriBuilder
            .path("/session/{id}/message")
            .queryParamIfPresent("limit", java.util.Optional.ofNullable(limit))
            .build(openCodeSessionId))
        .retrieve()
        .bodyToMono(String.class)
        .block();
    try {
        return om.readTree(body);
    } catch (Exception e) {
        throw new IllegalStateException("cannot parse OpenCode /session/"
            + openCodeSessionId + "/message response", e);
    }
}
```

- [x] **Step 4: 运行测试确认通过**

```bash
cd server && mvn -pl data-talk-infrastructure test -Dtest=OpenCodeHttpClientTest#listMessagesReturnsOpenCodePayload -q
```

Expected: PASS.

- [x] **Step 5: 补一个空数组的边界测试**

```java
@Test
void listMessagesReturnsEmptyArrayWhenSessionHasNoMessages() {
    stubFor(get(urlPathEqualTo("/session/ses_empty/message"))
        .willReturn(okJson("[]")));

    JsonNode node = client.listMessages("ses_empty", null);

    assertThat(node.isArray()).isTrue();
    assertThat(node).hasSize(0);
}
```

Run: `mvn -pl data-talk-infrastructure test -Dtest=OpenCodeHttpClientTest -q`
Expected: 全部 PASS.

- [x] **Step 6: Commit**

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeHttpClient.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/OpenCodeHttpClientTest.java
git commit -m "feat(opencode): add listMessages HTTP client method for message history passthrough"
```

---

### Task 2: OpenCodeGateway 暴露 listMessages 接口

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeGateway.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeGatewayTest.java`

**Context:** `OpenCodeGateway` 是 application 层对外统一的门面，真实 HTTP 细节由 adapter 层注入。新增一个 `MessageLister` 函数接口让单测可 stub。

- [x] **Step 1: 在 `OpenCodeGatewayTest` 加 listMessages 测试**

参考现有测试写法（`forwardUserMessage` 测试附近）：

```java
@Test
void listMessagesDelegatesToLister() {
    java.util.concurrent.atomic.AtomicReference<String> capturedOcSid = new java.util.concurrent.atomic.AtomicReference<>();
    com.fasterxml.jackson.databind.node.ArrayNode fixture =
        new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode();
    fixture.add(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("test", 1));

    OpenCodeGateway gateway = new OpenCodeGateway(
        new ActionRegistry(mock(ApplicationContext.class)) {
            @Override public java.util.Collection<ActionDescriptor> all() { return java.util.List.of(); }
        },
        (name, desc, params, cb) -> {},
        (sessionId, body) -> {},
        () -> "ocsid-1",
        sid -> {},
        (ocSid, limit) -> { capturedOcSid.set(ocSid); return fixture; },
        "http://localhost:8080");

    JsonNode result = gateway.listMessages("ses_abc", 50);

    assertThat(capturedOcSid.get()).isEqualTo("ses_abc");
    assertThat(result.isArray()).isTrue();
    assertThat(result).hasSize(1);
}
```

注意：需要 import `JsonNode`、`mock`、`ApplicationContext`；如果现有测试没 Mockito，用 inline 子类代替。

- [x] **Step 2: 运行测试确认失败**

```bash
cd server && mvn -pl data-talk-application test -Dtest=OpenCodeGatewayTest#listMessagesDelegatesToLister -q
```

Expected: FAIL（编译错或 `listMessages` 不存在）.

- [x] **Step 3: 修改 `OpenCodeGateway`**

在 `OpenCodeGateway.java` 加新接口 + 构造字段：

```java
public interface MessageLister {
    com.fasterxml.jackson.databind.JsonNode list(String openCodeSessionId, Integer limit);
}
```

在字段区加 `private final MessageLister lister;`，构造函数最后加参数：

```java
public OpenCodeGateway(ActionRegistry registry, ToolPusher pusher,
                       MessageSender sender, Supplier<String> sessionCreator,
                       SessionDeleter deleter, MessageLister lister,
                       String callbackBase) {
    this.registry = registry;
    this.pusher = pusher;
    this.sender = sender;
    this.sessionCreator = sessionCreator;
    this.deleter = deleter;
    this.lister = lister;
    this.callbackBase = callbackBase;
}
```

新增方法（在 `deleteOpenCodeSession` 之后）：

```java
/**
 * 透传 OpenCode {@code GET /session/:id/message}。
 * 调用方（HistoryService）应在 ocSid 为空 / blank 时短路返回空数组，不要走到这里。
 */
public com.fasterxml.jackson.databind.JsonNode listMessages(String openCodeSessionId, Integer limit) {
    return lister.list(openCodeSessionId, limit);
}
```

- [x] **Step 4: 更新 `OpenCodeGatewayBeans` 注入 MessageLister**

打开 `server/data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java`，在构造 `OpenCodeGateway` bean 的地方，把 lambda `(ocSid, limit) -> httpClient.listMessages(ocSid, limit)` 作为新参数传进去。具体替换看文件里现有的 `new OpenCodeGateway(...)` 调用，按 Step 3 定义的新构造顺序加 `(ocSid, limit) -> httpClient.listMessages(ocSid, limit)` 位于 `deleter` 和 `callbackBase` 之间。

- [x] **Step 5: 修复所有调用 `new OpenCodeGateway(...)` 的测试**

```bash
cd server && grep -rn "new OpenCodeGateway(" --include="*.java"
```

每个地方都加一个 `(ocSid, limit) -> { throw new UnsupportedOperationException("lister stub"); }` 作为 MessageLister 占位（或 return empty array 视测试需要），保证编译通过。至少包括：
- `OpenCodeGatewayTest` 原有测试
- 如 `EndToEndSmokeIT` 等 IT 里可能用到

- [x] **Step 6: 运行全量测试确认**

```bash
cd server && mvn compile -q
cd server && mvn -pl data-talk-application test -Dtest=OpenCodeGatewayTest -q
```

Expected: PASS.

- [x] **Step 7: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeGateway.java \
        server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeGatewayTest.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java
# 如 Step 5 改了 IT，一并加入
git commit -m "feat(opencode): expose listMessages on OpenCodeGateway"
```

---

### Task 3: HistoryService 改走 OpenCode 透传

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/channel/HistoryService.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/HistoryController.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/channel/HistoryServiceTest.java`

**Context:** 现在 `HistoryService.getMessages(sessionId)` 读本地 `messages` 表返回 `List<Message>`。改为：查 `SessionRepository.findById(dtSid)` → 取 `openCodeSid` → 空返回空数组 → 非空调 `gateway.listMessages(ocSid, null)` → 返回 `JsonNode`。同时 `HistoryController` 的 `/messages` 响应从 `{"messages": [...]}` 改为直接数组（同步文档第 1 条约定）。

- [x] **Step 1: 创建 `HistoryServiceTest`（或在已有测试里加）**

Create `server/data-talk-application/src/test/java/com/datatalk/application/channel/HistoryServiceTest.java`:

```java
package com.datatalk.application.channel;

import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HistoryServiceTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void getMessagesReturnsEmptyArrayWhenSessionHasNoOpenCodeBinding() {
        SessionRepository sessions = mock(SessionRepository.class);
        ArtifactRepository artifacts = mock(ArtifactRepository.class);
        OpenCodeGateway gateway = mock(OpenCodeGateway.class);
        when(sessions.findById("dt-1")).thenReturn(Optional.of(
            new SessionRecord("dt-1", null, "t", false, null, 0L, 0L, false)));

        HistoryService svc = new HistoryService(sessions, artifacts, gateway, om);

        JsonNode result = svc.getMessages("dt-1");

        assertThat(result.isArray()).isTrue();
        assertThat(result).hasSize(0);
        verify(gateway, never()).listMessages(anyString(), any());
    }

    @Test
    void getMessagesForwardsToGatewayWhenOpenCodeSidExists() {
        SessionRepository sessions = mock(SessionRepository.class);
        ArtifactRepository artifacts = mock(ArtifactRepository.class);
        OpenCodeGateway gateway = mock(OpenCodeGateway.class);
        when(sessions.findById("dt-1")).thenReturn(Optional.of(
            new SessionRecord("dt-1", null, "t", true, "ses_oc", 0L, 0L, false)));
        ArrayNode fixture = om.createArrayNode();
        fixture.add(om.createObjectNode().set("info",
            om.createObjectNode().put("id", "msg_1").put("role", "user")));
        when(gateway.listMessages("ses_oc", null)).thenReturn(fixture);

        HistoryService svc = new HistoryService(sessions, artifacts, gateway, om);

        JsonNode result = svc.getMessages("dt-1");

        assertThat(result).isSameAs(fixture);
        verify(gateway).listMessages(eq("ses_oc"), eq((Integer) null));
    }

    @Test
    void getMessagesReturnsEmptyArrayWhenSessionNotFound() {
        SessionRepository sessions = mock(SessionRepository.class);
        ArtifactRepository artifacts = mock(ArtifactRepository.class);
        OpenCodeGateway gateway = mock(OpenCodeGateway.class);
        when(sessions.findById("unknown")).thenReturn(Optional.empty());

        HistoryService svc = new HistoryService(sessions, artifacts, gateway, om);

        JsonNode result = svc.getMessages("unknown");

        assertThat(result.isArray()).isTrue();
        assertThat(result).hasSize(0);
    }
}
```

- [x] **Step 2: 运行测试确认失败**

```bash
cd server && mvn -pl data-talk-application test -Dtest=HistoryServiceTest -q
```

Expected: FAIL with 编译错（`HistoryService` 构造签名不匹配 / `getMessages` 返回类型错）.

- [x] **Step 3: 重写 `HistoryService`**

替换 `HistoryService.java` 的全部内容：

```java
package com.datatalk.application.channel;

import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.domain.util.Strings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HistoryService {

    private final SessionRepository sessions;
    private final ArtifactRepository artifacts;
    private final OpenCodeGateway gateway;
    private final ObjectMapper om;

    public HistoryService(SessionRepository sessions, ArtifactRepository artifacts,
                          OpenCodeGateway gateway, ObjectMapper om) {
        this.sessions = sessions;
        this.artifacts = artifacts;
        this.gateway = gateway;
        this.om = om;
    }

    /**
     * Returns an array of OpenCode messages in native shape
     * ({@code [{info, parts}]}). Empty array when the session has no OpenCode
     * binding (never sent a message) or the session doesn't exist.
     * Upstream errors from OpenCode bubble up as RuntimeException so the
     * controller can translate to 502/503.
     */
    public JsonNode getMessages(String sessionId) {
        return sessions.findById(sessionId)
            .map(s -> s.openCodeSid())
            .filter(Strings::isNotBlank)
            .map(ocSid -> gateway.listMessages(ocSid, null))
            .orElseGet(om::createArrayNode);
    }

    public List<ArtifactRecord> getArtifacts(String sessionId) {
        return artifacts.findBySession(sessionId);
    }
}
```

- [x] **Step 4: 更新 `HistoryController`**

替换 `HistoryController.java` 的 `messages` 方法：

```java
@GetMapping("/messages")
public JsonNode messages(@PathVariable String sessionId) {
    return svc.getMessages(sessionId);
}
```

同时调整 import：删 `Message`、`Map`、`List`（除非 artifacts 方法还在用），加 `import com.fasterxml.jackson.databind.JsonNode;`。

- [x] **Step 5: 运行测试确认通过**

```bash
cd server && mvn -pl data-talk-application test -Dtest=HistoryServiceTest -q
```

Expected: 3 个用例全部 PASS.

- [x] **Step 6: 全量编译 + IT 冒烟**

```bash
cd server && mvn compile -q
# 如果 HistoryControllerIT / 其他 IT 引用了旧的 {"messages": [...]} 响应体，这里会编译过但 IT 失败；
# 后续 Task 14 会统一修
```

Expected: compile PASS.

- [x] **Step 7: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/channel/HistoryService.java \
        server/data-talk-application/src/test/java/com/datatalk/application/channel/HistoryServiceTest.java \
        server/data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/HistoryController.java
git commit -m "feat(history): proxy GET /api/sessions/{id}/messages to OpenCode (passthrough array)"
```

---

### Task 4: OpenCodeEventTranslator Part payload 改为透传 JSON

**Files:**
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventTranslator.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OcEvent.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventLoop.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventTranslatorTest.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventLoopParseTest.java`

**Context:** 这是 Phase 1 最复杂的任务。目前 `DtEvent.MessagePartCreated/Updated` 的 `part` 字段是 `Part`（sealed interface），`OpenCodeEventLoop` 解析 SSE 时通过 Jackson 反序列化到 `Part`。改造后 payload 保留为 `JsonNode`，保留 OpenCode 原生字段（`sessionID`/`messageID`/`step-start` 等）。

影响：
- `DtEvent.MessagePartCreated/Updated` record 的字段类型变更
- `OcEvent.MessagePartUpdated` 也相应变为 `JsonNode`
- `OpenCodeEventLoop` 解析时不再 `readValue(..., Part.class)`，直接 `path("part")` 保留 JsonNode
- 所有对 `DtEvent.MessagePart*.part()` 调用者（如测试）需要改为 `JsonNode` API
- `MessagePartDelta / MessagePartRemoved` 不涉及 Part 对象，不受影响

- [x] **Step 1: 调整 `DtEvent` 的 MessagePartCreated/Updated 字段类型**

打开 `DtEvent.java`，找到这两条 record：

```java
record MessagePartCreated(Part part) implements DtEvent {}
record MessagePartUpdated(Part part) implements DtEvent {}
```

改为：

```java
record MessagePartCreated(com.fasterxml.jackson.databind.JsonNode part) implements DtEvent {}
record MessagePartUpdated(com.fasterxml.jackson.databind.JsonNode part) implements DtEvent {}
```

删除文件里对 `Part` 的 import（如果已不被其他 event 使用）。

- [x] **Step 2: 调整 `OcEvent.MessagePartUpdated` 字段类型**

打开 `OcEvent.java`，类似改动：把 `MessagePartUpdated(Part part)` 改为 `MessagePartUpdated(com.fasterxml.jackson.databind.JsonNode part)`。

- [x] **Step 3: 在 `OpenCodeEventTranslatorTest` 添加透传断言**

在现有测试里找到 MessagePartUpdated 相关用例，替换或补充：

```java
@Test
void messagePartUpdatedPayloadIsPassedThroughAsRawJson() throws Exception {
    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
    com.fasterxml.jackson.databind.JsonNode rawPart = om.readTree("""
        { "type": "step-start",
          "id": "prt_1",
          "sessionID": "ses_abc",
          "messageID": "msg_1" }
        """);
    OpenCodeEventTranslator t = new OpenCodeEventTranslator(
        (ocSid, title) -> false); // stub SessionTitleSyncer

    var events = t.translate("dt-session-1", new OcEvent.MessagePartUpdated("prt_1", rawPart));

    assertThat(events).hasSize(1);
    DtEvent.MessagePartCreated created = (DtEvent.MessagePartCreated) events.get(0);
    assertThat(created.part().path("type").asText()).isEqualTo("step-start");
    assertThat(created.part().path("sessionID").asText()).isEqualTo("ses_abc");
    assertThat(created.part().path("messageID").asText()).isEqualTo("msg_1");
}

@Test
void messagePartPreservesRiskLevelMetadataForPartLevelChannel() throws Exception {
    // Plan-1 只验证透传通道，本期后端不回传 riskLevel；future TD-020 在此通道回填
    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
    com.fasterxml.jackson.databind.JsonNode rawPart = om.readTree("""
        { "type": "tool",
          "id": "prt_t",
          "sessionID": "ses_abc",
          "messageID": "msg_1",
          "state": { "status": "completed", "metadata": { "riskLevel": "L3" } } }
        """);
    OpenCodeEventTranslator t = new OpenCodeEventTranslator((ocSid, title) -> false);

    var events = t.translate("dt-session-1", new OcEvent.MessagePartUpdated("prt_t", rawPart));

    DtEvent.MessagePartCreated created = (DtEvent.MessagePartCreated) events.get(0);
    assertThat(created.part().path("state").path("metadata").path("riskLevel").asText())
        .isEqualTo("L3");
}
```

（第 2 个用例为 Phase 3 部分，提前打包在这里避免重复测试基础设施。）

- [x] **Step 4: 运行测试确认失败**

```bash
cd server && mvn -pl data-talk-application test -Dtest=OpenCodeEventTranslatorTest -q
```

Expected: FAIL with 编译错（`OcEvent.MessagePartUpdated` 构造参数类型不匹配 / `DtEvent.MessagePartCreated.part()` 返回类型错）.

- [x] **Step 5: 更新 `OpenCodeEventTranslator` 透传**

打开 `OpenCodeEventTranslator.java` 第 70-77 行：

```java
case OcEvent.MessagePartUpdated p -> {
    Set<String> parts = seenParts.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet());
    String partId = p.part().id();
    if (parts.add(partId)) {
        yield List.of(new DtEvent.MessagePartCreated(p.part()));
    }
    yield List.of(new DtEvent.MessagePartUpdated(p.part()));
}
```

改为从 JsonNode 取 id：

```java
case OcEvent.MessagePartUpdated p -> {
    Set<String> parts = seenParts.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet());
    String partId = p.part().path("id").asText();
    if (parts.add(partId)) {
        yield List.of(new DtEvent.MessagePartCreated(p.part()));
    }
    yield List.of(new DtEvent.MessagePartUpdated(p.part()));
}
```

- [x] **Step 6: 更新 `OpenCodeEventLoop` 的解析**

打开 `OpenCodeEventLoop.java`，找到 `message.part.updated` 事件解析的地方（grep `MessagePartUpdated`）。原本可能是：

```java
Part part = om.treeToValue(props.path("part"), Part.class);
yield new OcEvent.MessagePartUpdated(part);
```

改为：

```java
com.fasterxml.jackson.databind.JsonNode part = props.path("part");
if (part.isMissingNode() || part.isNull()) {
    yield new OcEvent.Unknown();
}
yield new OcEvent.MessagePartUpdated(part);
```

- [x] **Step 7: 更新 OpenCodeEventLoopParseTest**

打开 `OpenCodeEventLoopParseTest.java`，找 `messageID` 相关的断言（grep `msg_da05448a30013tKskYObsKxSSw`）。原本类似：

```java
Part p = ((OcEvent.MessagePartUpdated) evt).part();
assertThat(p.messageID()).isEqualTo("msg_da05448a30013tKskYObsKxSSw");
```

改为：

```java
JsonNode p = ((OcEvent.MessagePartUpdated) evt).part();
assertThat(p.path("messageID").asText()).isEqualTo("msg_da05448a30013tKskYObsKxSSw");
```

补充 import `com.fasterxml.jackson.databind.JsonNode`.

- [x] **Step 8: 编译 + 跑 Translator 和 EventLoop 测试**

```bash
cd server && mvn -pl data-talk-application test -Dtest=OpenCodeEventTranslatorTest,OpenCodeEventLoopParseTest -q
```

Expected: PASS.

- [x] **Step 9: 全量编译**

```bash
cd server && mvn compile -q
```

Expected: PASS（可能会有其他文件引用 `Part` 的地方报错，如 `ChannelService` 里 `MessagePartCreated(Part p)` 发事件的路径 — 这些在 Task 5 会被整体删除，此处暂时修到能编译过即可：改为构造 JsonNode。但由于 Task 5 会删那段代码，可先注释掉这里的 publish 调用或改为 `new DtEvent.MessagePartCreated(om.valueToTree(p))` 保持编译通过）。

- [x] **Step 10: Commit**

```bash
git add server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java \
        server/data-talk-application/src/main/java/com/datatalk/application/opencode/OcEvent.java \
        server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventTranslator.java \
        server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventLoop.java \
        server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventTranslatorTest.java \
        server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventLoopParseTest.java
# 加上 Step 9 里为保编译通过而顺手改的 ChannelService 等文件
git commit -m "refactor(events): pass OpenCode part payload through as JsonNode (preserve native field names)"
```

---

### Task 5: ChannelService.sendMessage 清理本地 USER 消息路径

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/channel/ChannelService.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/channel/ChannelServiceTest.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/channel/ChannelServiceModelParamTest.java`

**Context:** 根据同步文档第 3 条约定，`ChannelService.sendMessage` 不再本地生成 user message id / 不再 `messages.save` / 不再发 `DtEvent.MessageCreated` / `DtEvent.MessagePartCreated`。只保留 forward 给 OpenCode + `SessionStatus("busy")` + `markHasEverSent`。USER 消息的前端呈现依赖 OpenCode 回推事件。

- [x] **Step 1: 修改 `ChannelServiceTest` 里 sendMessage 的断言**

打开 `ChannelServiceTest.java`，找到现有 sendMessage 测试（如 `sendMessagePersistsUserMessageAndEmitsEvents`）。把断言改为：

```java
@Test
void sendMessageForwardsToOpenCodeAndEmitsBusyStatusOnly() {
    // arrange ... (保留原 arrange，只删 ids/messageRepository 相关 stub)
    channelService.sendMessage("s-1", List.of(new TextPart("p-1", "s-1", "m-ignored", "hi", Map.of())));

    // 不再 save 到 messages 表
    verify(messageRepository, never()).save(any());
    // 不再发 MessageCreated/MessagePartCreated
    ArgumentCaptor<DtEvent> captor = ArgumentCaptor.forClass(DtEvent.class);
    verify(sessionBus, atLeastOnce()).publish(captor.capture());
    assertThat(captor.getAllValues())
        .noneMatch(e -> e instanceof DtEvent.MessageCreated)
        .noneMatch(e -> e instanceof DtEvent.MessagePartCreated)
        .anyMatch(e -> e instanceof DtEvent.SessionStatus s && "busy".equals(s.status()));
    // 仍 forward 到 OpenCode
    verify(gateway).forwardUserMessage(anyString(), anyMap());
}
```

如果有测试依赖 `ids.next()` 被调用一次生成 messageId，改为 `verify(ids, never()).next();`（因为本地不再生成）。

- [x] **Step 2: 运行测试确认失败**

```bash
cd server && mvn -pl data-talk-application test -Dtest=ChannelServiceTest -q
```

Expected: FAIL（现有实现仍在发 MessageCreated）。

- [x] **Step 3: 重写 `ChannelService.sendMessage`**

找到 `sendMessage` 方法（第 71 行左右），替换其中第 74-84 行（从 `long now = clock.millis();` 到 `bus.publish(new DtEvent.SessionStatus("busy", ...));` 之间），保留 `SessionStatus("busy")` 和 OpenCode forward，删除本地消息持久化：

```java
public void sendMessage(String sessionId, List<Part> parts) {
    SessionRecord session = sessions.findById(sessionId)
        .orElseThrow(() -> new IllegalArgumentException("unknown session: " + sessionId));
    long now = clock.millis();
    sessions.markHasEverSent(sessionId, now);

    SessionBus bus = buses.getOrCreate(sessionId);
    bus.publish(new DtEvent.SessionStatus("busy", Map.of()));

    // Forward to OpenCode — prefer the persisted opencode_sid...
    // （以下保留原有 OpenCode forward 逻辑，即第 86-110 行）
    String ocSid = session.openCodeSid();
    if (Strings.isBlank(ocSid)) {
        ocSid = sessionMap.openCodeFor(sessionId);
    }
    if (Strings.isBlank(ocSid)) {
        ocSid = gateway.createOpenCodeSession();
        sessions.updateOpenCodeSid(sessionId, ocSid, now);
    }
    sessionMap.bind(sessionId, ocSid);

    Map<String, Object> body = new LinkedHashMap<>();
    List<Map<String, Object>> wireParts = parts.stream()
        .map(this::partForWire)
        .filter(Objects::nonNull)
        .toList();
    body.put("parts", wireParts);
    String model = userPrefs.getCurrentModel();
    if (Strings.isNotBlank(model)) {
        body.put("model", normalizeModel(model));
    }
    gateway.forwardUserMessage(ocSid, body);
}
```

注意：
- 删除返回值（从 `String` 改为 `void`）—— 原 `return messageId;` 去掉
- 删除 `String messageId = ids.next();` / `List<Part> stamped = parts.stream()...` / `Message m = new Message(...)` / `messages.save(m)` / `bus.publish(new DtEvent.MessageCreated(m))` / `for (Part p : stamped) bus.publish(new DtEvent.MessagePartCreated(p))`
- 注意 `wireParts` 直接用 `parts`，不再 stamp 本地 messageId

- [x] **Step 4: 修改 `ChannelService` 构造函数移除 `MessageRepository` 依赖**

```java
public ChannelService(SessionRepository sessions,
                      SessionBusRegistry buses, PendingCallRegistry pending,
                      IdGenerator ids, Clock clock,
                      OpenCodeGateway gateway, OpenCodeSessionMap sessionMap,
                      ObjectMapper om, AiUserPrefsRepository userPrefs) {
    this.sessions = sessions;
    this.buses = buses;
    this.pending = pending;
    this.ids = ids;
    this.clock = clock;
    this.gateway = gateway;
    this.sessionMap = sessionMap;
    this.om = om;
    this.userPrefs = userPrefs;
}
```

删除 `private final MessageRepository messages;` 字段。Note：`ids` 字段保留，后续可能仍有其它地方用（如果没有其它用途，保留也不冲突）。

- [x] **Step 5: 修改调用 `sendMessage` 的 caller 签名**

```bash
cd server && grep -rn "channelService.sendMessage\|\.sendMessage(sessionId" --include="*.java"
```

任何 `String messageId = channel.sendMessage(...)` 的地方，改为 `channel.sendMessage(...)` 并删除对 messageId 的使用。Controller 层返回结构也要改（如果有 `{"messageId": ...}` 响应，改为 `{"status": "ok"}` 或空 200）。

- [x] **Step 6: 运行测试确认通过**

```bash
cd server && mvn -pl data-talk-application test -Dtest=ChannelServiceTest,ChannelServiceModelParamTest -q
```

Expected: PASS.

- [x] **Step 7: 全量编译**

```bash
cd server && mvn compile -q
```

Expected: PASS（MessageRepository 在 Task 6 才删除，此处还在，编译能过）.

- [x] **Step 8: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/channel/ChannelService.java \
        server/data-talk-application/src/test/java/com/datatalk/application/channel/ChannelServiceTest.java \
        server/data-talk-application/src/test/java/com/datatalk/application/channel/ChannelServiceModelParamTest.java
# 加上 Step 5 改的 caller 文件
git commit -m "refactor(channel): remove local user message generation; rely on OpenCode echo for MessageCreated/PartCreated"
```

---

### Task 6: 删除 MessageRepository + messages 表 Flyway drop

**Files:**
- Delete: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/MessageRepository.java`
- Delete: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/MessageRepositoryIT.java`
- Create: `server/data-talk-infrastructure/src/main/resources/db/migration/V8__drop_messages.sql`
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/part/Message.java`（保留还是删除：保留，因为仍可能被测试 fixture 或未来历史归档使用）

**Context:** 本期完全下沉到 OpenCode，messages 表退休。使用 `DROP TABLE IF EXISTS` 保证空库也能过。

- [x] **Step 1: 确认 `MessageRepository` 无其他调用方**

```bash
cd server && grep -rn "MessageRepository\|messages\.save\|messages\.findBySession" --include="*.java" | grep -v "^.*test\|^.*Test\|V[0-9]*__"
```

Expected: 仅剩 `MessageRepository.java` 自身（ChannelService / HistoryService 在前面 Task 已移除引用）。如果有其它生产代码还引用，先在 Task 5 处理完再来这里。

- [x] **Step 2: 创建 Flyway migration**

Create `server/data-talk-infrastructure/src/main/resources/db/migration/V8__drop_messages.sql`:

```sql
-- Messages are now authoritative in OpenCode; DataTalk only stores events.
-- See docs/exec-plans/2026-04-19-ai-message-history-backend-plan.md.
DROP TABLE IF EXISTS messages;
```

- [x] **Step 3: 删除 MessageRepository + IT**

```bash
rm server/data-talk-application/src/main/java/com/datatalk/application/persistence/MessageRepository.java
rm server/data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/MessageRepositoryIT.java
```

- [x] **Step 4: 确认 `Message` domain 对象是否仍在用**

```bash
cd server && grep -rn "import com.datatalk.domain.part.Message\b\|new Message(" --include="*.java"
```

如果只有 `MessageRepository` 和几个测试 fixture 引用了 `Message`，可以保留类定义不删（domain 对象本身无害）。如果有活跃调用者（如 `DtEvent.MessageCreated` / `MessageUpdated` 仍带 `Message` 字段），也保留。

- [x] **Step 5: 全量编译 + 运行测试**

```bash
cd server && mvn compile -q
```

Expected: PASS.

```bash
cd server && mvn test -pl data-talk-application,data-talk-infrastructure -q
```

Expected: 全部 PASS.

- [x] **Step 6: 启动后端验证 Flyway migration**

```bash
cd server && mvn spring-boot:run -pl data-talk-adapter -q &
sleep 15
# 观察日志：Flyway 应用 V8__drop_messages.sql 成功；
# 或手动 sqlite3 ./server/data/datatalk.db ".tables" 确认没有 messages 表
pkill -f spring-boot:run
```

Expected: Flyway log 包含 `V8__drop_messages`，启动无报错.

- [x] **Step 7: Commit**

```bash
git add server/data-talk-infrastructure/src/main/resources/db/migration/V8__drop_messages.sql
git add -u  # 把删除的文件纳入
git commit -m "chore(db): drop messages table; OpenCode now authoritative for message persistence"
```

---

## Phase 2 — ActionDescriptor 风险分级扩展

### Task 7: domain 层新增 RiskLevel + Category 枚举

**Files:**
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/action/RiskLevel.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/action/Category.java`

**Context:** 按前端 spec §3.4，枚举值对齐字符串形式（前端用 'L1'/'L2'/'L3' / 'metadata'/'query'/...）。Jackson 默认 `@JsonFormat` 输出 enum name。

- [x] **Step 1: 创建 RiskLevel 枚举**

Create `RiskLevel.java`:

```java
package com.datatalk.domain.action;

/**
 * Front-end risk color coding for tool UI cards. See product spec
 * {@code 2026-04-19-ai-message-rendering-migration-design.md} §3.4, §5.3.
 */
public enum RiskLevel {
    L1,  // Safe: metadata / read-only (green)
    L2,  // Mutation requiring confirmation (yellow)
    L3   // Destructive / high-impact (red)
}
```

- [x] **Step 2: 创建 Category 枚举**

Create `Category.java`:

```java
package com.datatalk.domain.action;

/**
 * Functional category of an action. Drives front-end grouping
 * (e.g. METADATA actions are merged into a ContextToolGroup) and
 * visual variants (QUESTION has its own layout, not a risk-colored card).
 */
public enum Category {
    METADATA,  // describe_table / list_tables / show_schema — merge into ContextToolGroup
    QUERY,     // execute_sql — read-only data access
    MUTATION,  // preview_sql — DML preview + confirm
    ARTIFACT,  // artifact_created / pin / supersede
    DDL,       // reserved for future schema-changing actions
    QUESTION,  // AI → user interactive question (independent visual)
    MISC       // default / unclassified
}
```

- [x] **Step 3: 编译验证**

```bash
cd server && mvn -pl data-talk-domain compile -q
```

Expected: PASS.

- [x] **Step 4: Commit**

```bash
git add server/data-talk-domain/src/main/java/com/datatalk/domain/action/RiskLevel.java \
        server/data-talk-domain/src/main/java/com/datatalk/domain/action/Category.java
git commit -m "feat(domain): add RiskLevel and Category enums for action risk classification"
```

---

### Task 8: @DataTalkAction 注解扩展

**Files:**
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/action/DataTalkAction.java`
- Test: `server/data-talk-domain/src/test/java/com/datatalk/domain/action/DataTalkActionAnnotationTest.java`

**Context:** 注解增加 `riskLevel` / `category` 属性，默认 `MISC` / 用 `RiskLevel` 无自然 default（约束上可空），所以注解里 default 值用「sentinel 值」—— Java 注解要求 default，可用 `RiskLevel.L1` + 额外一个 `riskLevelPresent` 标志，或者更简洁：新增 `RiskLevel` 枚举一个 `UNSET` 成员作为"未设置"哨兵值。这里选后者，结构简洁。

等等 —— 改 `RiskLevel` 枚举加 `UNSET` 会污染域。更干净的方式：

用独立的 "risk level source" 设计：注解层用自定义枚举 `RiskLevel`（不带 UNSET），但注解属性默认值为 `MISC` 类似的哨兵 → 不行因为无自然默认。

**最终选择**：把 `@DataTalkAction.riskLevel()` 的返回类型用 `RiskLevel[]` 数组（数组允许长度为 0 = default `{}`），同理 `category()`。Registry 在读取时 len == 0 视为 null。Java 注解常用手法。

- [x] **Step 1: 创建注解反射测试**

Create `server/data-talk-domain/src/test/java/com/datatalk/domain/action/DataTalkActionAnnotationTest.java`:

```java
package com.datatalk.domain.action;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataTalkActionAnnotationTest {

    @DataTalkAction(
        id = "test.full",
        executor = Executor.SERVER,
        description = "full",
        riskLevel = { RiskLevel.L2 },
        category = { Category.MUTATION }
    )
    static class FullHandler {}

    @DataTalkAction(id = "test.bare", executor = Executor.SERVER, description = "bare")
    static class BareHandler {}

    @Test
    void annotationCarriesRiskLevelAndCategoryWhenPresent() {
        DataTalkAction meta = FullHandler.class.getAnnotation(DataTalkAction.class);
        assertThat(meta.riskLevel()).containsExactly(RiskLevel.L2);
        assertThat(meta.category()).containsExactly(Category.MUTATION);
    }

    @Test
    void annotationDefaultsToEmptyArraysMeaningUnset() {
        DataTalkAction meta = BareHandler.class.getAnnotation(DataTalkAction.class);
        assertThat(meta.riskLevel()).isEmpty();
        assertThat(meta.category()).isEmpty();
    }
}
```

- [x] **Step 2: 运行测试确认失败**

```bash
cd server && mvn -pl data-talk-domain test -Dtest=DataTalkActionAnnotationTest -q
```

Expected: FAIL（`riskLevel()` / `category()` 方法不存在）.

- [x] **Step 3: 扩展 DataTalkAction 注解**

替换 `DataTalkAction.java`:

```java
package com.datatalk.domain.action;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DataTalkAction {
    String id();
    Executor executor();
    String description();
    String[] produces() default {};
    boolean requiresConnection() default false;
    int timeoutMs() default 30_000;

    /**
     * Optional risk classification for front-end UI. Empty array means unset;
     * front-end falls back through {@code part.state.metadata.riskLevel} then
     * a heuristic regex (see product spec §3.5).
     * Exactly 0 or 1 element; more is undefined.
     */
    RiskLevel[] riskLevel() default {};

    /**
     * Optional functional category. Empty array means unset (treated as MISC
     * for grouping purposes). Exactly 0 or 1 element; more is undefined.
     */
    Category[] category() default {};
}
```

- [x] **Step 4: 运行测试确认通过**

```bash
cd server && mvn -pl data-talk-domain test -Dtest=DataTalkActionAnnotationTest -q
```

Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add server/data-talk-domain/src/main/java/com/datatalk/domain/action/DataTalkAction.java \
        server/data-talk-domain/src/test/java/com/datatalk/domain/action/DataTalkActionAnnotationTest.java
git commit -m "feat(action): extend @DataTalkAction with optional riskLevel / category"
```

---

### Task 9: ActionDescriptor 扩展字段

**Files:**
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/action/ActionDescriptor.java`

**Context:** record 增加 2 个可空字段（nullable）。现有 record 构造器调用点会编译失败 —— 在 Task 10 一并修。

- [x] **Step 1: 修改 ActionDescriptor record**

替换 `ActionDescriptor.java`:

```java
package com.datatalk.domain.action;

import java.util.List;
import java.util.Map;

public record ActionDescriptor(
    String id,
    Executor executor,
    String description,
    Map<String, Object> inputSchema,
    Map<String, Object> outputSchema,
    List<String> produces,
    List<OntologyEffect> sideEffects,
    boolean requiresConnection,
    int timeoutMs,
    RiskLevel riskLevel,   // nullable — see product spec §3.4
    Category category      // nullable — see product spec §3.4
) {}
```

- [x] **Step 2: 运行编译（预期失败，在 Task 10 修）**

```bash
cd server && mvn -pl data-talk-application compile -q
```

Expected: FAIL（`ActionRegistry.buildDescriptor` 构造参数不匹配）.

跳到 Task 10 修复，不单独 commit 这一步（Task 10 合并 commit）。

---

### Task 10: ActionRegistry 透传注解值 + 兼容测试

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/registry/ActionRegistry.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/registry/ActionRegistryTest.java`

- [x] **Step 1: 在 `ActionRegistryTest` 加新断言**

找到 `AlphaHandler` / `BetaHandler` 附近，加：

```java
@DataTalkAction(
    id = "test.gamma",
    executor = Executor.SERVER,
    description = "Gamma",
    riskLevel = { RiskLevel.L3 },
    category = { Category.MUTATION }
)
static class GammaHandler implements ActionHandler<Map, Map> {
    @Override public CompletionStage<Map> handle(ActionContext ctx, Map input) { return null; }
    @Override public Map<String, Object> inputSchema() { return Map.of(); }
    @Override public Map<String, Object> outputSchema() { return Map.of(); }
    @Override public List<OntologyEffect> sideEffects() { return List.of(); }
    @Override public Class<Map> inputType() { return Map.class; }
}
```

加测试方法：

```java
@Test
void registersRiskLevelAndCategoryFromAnnotation() {
    ActionRegistry registry = /* 用现有辅助方法构造，包含 GammaHandler */;
    ActionDescriptor d = registry.require("test.gamma");
    assertThat(d.riskLevel()).isEqualTo(RiskLevel.L3);
    assertThat(d.category()).isEqualTo(Category.MUTATION);
}

@Test
void missingRiskLevelOrCategoryAreNullInDescriptor() {
    ActionRegistry registry = /* 用 AlphaHandler（没有 riskLevel/category）构造 */;
    ActionDescriptor d = registry.require("test.alpha");
    assertThat(d.riskLevel()).isNull();
    assertThat(d.category()).isNull();
}
```

- [x] **Step 2: 运行测试确认失败**

```bash
cd server && mvn -pl data-talk-application test -Dtest=ActionRegistryTest -q
```

Expected: FAIL（编译错或字段断言 null）.

- [x] **Step 3: 修改 `ActionRegistry.buildDescriptor`**

第 51-63 行改为：

```java
private ActionDescriptor buildDescriptor(DataTalkAction meta, ActionHandler<?, ?> handler) {
    RiskLevel risk = meta.riskLevel().length > 0 ? meta.riskLevel()[0] : null;
    Category category = meta.category().length > 0 ? meta.category()[0] : null;
    return new ActionDescriptor(
        meta.id(),
        meta.executor(),
        meta.description(),
        handler.inputSchema(),
        handler.outputSchema(),
        Arrays.asList(meta.produces()),
        List.copyOf(handler.sideEffects()),
        meta.requiresConnection(),
        meta.timeoutMs(),
        risk,
        category
    );
}
```

注意 import `RiskLevel` / `Category`.

- [x] **Step 4: 全量编译（预期 application 模块可能还有其它 `new ActionDescriptor(...)` 调用不匹配）**

```bash
cd server && mvn -pl data-talk-application compile -q
```

所有现存 `new ActionDescriptor(...)` 调用（除了 registry）都需要加两个 `null` 参数。grep 找：

```bash
cd server && grep -rn "new ActionDescriptor(" --include="*.java"
```

对找到的每个，在末尾加 `, null, null`。

- [x] **Step 5: 运行测试**

```bash
cd server && mvn -pl data-talk-application test -Dtest=ActionRegistryTest -q
```

Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add server/data-talk-domain/src/main/java/com/datatalk/domain/action/ActionDescriptor.java \
        server/data-talk-application/src/main/java/com/datatalk/application/registry/ActionRegistry.java \
        server/data-talk-application/src/test/java/com/datatalk/application/registry/ActionRegistryTest.java
# 加上 Step 4 修改的其它 new ActionDescriptor 调用文件
git commit -m "feat(action): ActionDescriptor carries riskLevel/category; registry wires from annotation"
```

---

### Task 11: 现有 7 个 Action 注解回填默认值

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/DemoEchoAction.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/PinArtifactAction.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/SupersedeArtifactAction.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/LayoutErdAction.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/RenderChartAction.java`

**Context:** 按 §7.0.2 前端 spec 决策表回填。注意：前端 spec 里 `execute_sql` / `describe_table` 等是裸名，后端实际 ID 带 `datatalk.` 前缀 — 这是 ID 命名约定问题，**不在本 plan 范围**（同步文档已说明前端会用全名分派）。只处理风险分级字段。

| Action | id | riskLevel | category | 理由 |
|---|---|---|---|---|
| ExecuteSqlAction | datatalk.execute_sql | L1 | QUERY | 已有 `SqlStatementGuard.assertSelectOnly` 保证只读 |
| ReadSchemaAction | datatalk.read_schema | L1 | METADATA | 纯元数据读取 |
| DemoEchoAction | datatalk.demo.echo | null（unset） | MISC | 示例 action |
| PinArtifactAction | datatalk.pin_artifact | L1 | ARTIFACT | 无数据风险 |
| SupersedeArtifactAction | datatalk.supersede_artifact | L1 | ARTIFACT | 仅逻辑操作 |
| LayoutErdAction | datatalk.layout_erd | L1 | MISC | 计算型，无数据侧效应 |
| RenderChartAction | datatalk.render_chart | L1 | ARTIFACT | 产出图表 artifact |

- [x] **Step 1: ExecuteSqlAction**

找到注解（第 22-29 行），加 `riskLevel = { RiskLevel.L1 }` / `category = { Category.QUERY }`：

```java
@DataTalkAction(
    id = "datatalk.execute_sql",
    executor = Executor.SERVER,
    description = "Run a SELECT query on the given connection and persist the result as a table Artifact.",
    produces = {"datatalk.artifact"},
    requiresConnection = true,
    timeoutMs = 30_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.QUERY }
)
```

加 import。

- [x] **Step 2: ReadSchemaAction**

```java
riskLevel = { RiskLevel.L1 },
category = { Category.METADATA }
```

- [x] **Step 3: DemoEchoAction**

```java
category = { Category.MISC }
// riskLevel 不填（默认 empty）
```

- [x] **Step 4: PinArtifactAction**

```java
riskLevel = { RiskLevel.L1 },
category = { Category.ARTIFACT }
```

- [x] **Step 5: SupersedeArtifactAction**

```java
riskLevel = { RiskLevel.L1 },
category = { Category.ARTIFACT }
```

- [x] **Step 6: LayoutErdAction**

```java
riskLevel = { RiskLevel.L1 },
category = { Category.MISC }
```

- [x] **Step 7: RenderChartAction**

```java
riskLevel = { RiskLevel.L1 },
category = { Category.ARTIFACT }
```

- [x] **Step 8: 编译 + 启动验证**

```bash
cd server && mvn compile -q
cd server && mvn spring-boot:run -pl data-talk-adapter -q &
sleep 15
curl -s http://localhost:8080/api/actions | jq '.actions[] | {id, riskLevel, category}'
pkill -f spring-boot:run
```

Expected: 7 个 actions 返回值对齐上表；如 `{"id":"datatalk.execute_sql","riskLevel":"L1","category":"QUERY"}`.

- [x] **Step 9: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/
git commit -m "feat(actions): annotate existing 7 actions with riskLevel and category defaults"
```

---

### Task 12: DiscoveryController 响应字段断言

**Files:**
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/discovery/DiscoveryControllerIT.java`

**Context:** `ActionDescriptor` 是 record，Jackson 自动序列化。只要字段加上，REST 自然带上。需要在 IT 加断言防止回归。

- [x] **Step 1: 加 IT 测试**

在 `DiscoveryControllerIT` 里新增：

```java
@Test
void actionsPayloadCarriesRiskLevelAndCategory() throws Exception {
    mvc.perform(get("/api/actions"))
       .andExpect(status().isOk())
       .andExpect(jsonPath("$.actions[?(@.id == 'datatalk.execute_sql')].riskLevel")
           .value(org.hamcrest.Matchers.hasItem("L1")))
       .andExpect(jsonPath("$.actions[?(@.id == 'datatalk.execute_sql')].category")
           .value(org.hamcrest.Matchers.hasItem("QUERY")))
       .andExpect(jsonPath("$.actions[?(@.id == 'datatalk.read_schema')].category")
           .value(org.hamcrest.Matchers.hasItem("METADATA")));
}
```

- [x] **Step 2: 运行测试**

```bash
cd server && mvn -pl data-talk-adapter test -Dtest=DiscoveryControllerIT -q
```

Expected: PASS.

- [x] **Step 3: Commit**

```bash
git add server/data-talk-adapter/src/test/java/com/datatalk/adapter/discovery/DiscoveryControllerIT.java
git commit -m "test(discovery): assert riskLevel/category present in /api/actions response"
```

---

## Phase 3 — part-level riskLevel 通道预留

### Task 13: part-level riskLevel 通道的单测已在 Task 4 Step 3 完成

**Context:** Task 4 Step 3 的第 2 个测试用例 `messagePartPreservesRiskLevelMetadataForPartLevelChannel` 已经验证了 `part.state.metadata.riskLevel` 从 OpenCode JSON 原样透传到 `DtEvent.MessagePart*`。本期后端不填该字段（TD-020 异步落地 AST）。

此 Task 本质上是**回顾 + 确认**已经覆盖。

- [x] **Step 1: 确认 Task 4 Step 3 的测试存在并通过**

```bash
cd server && mvn -pl data-talk-application test -Dtest=OpenCodeEventTranslatorTest#messagePartPreservesRiskLevelMetadataForPartLevelChannel -q
```

Expected: PASS. 若不存在，回到 Task 4 补上再运行。

- [x] **Step 2: 无额外 commit**

无代码改动。

---

## Phase 4 — 联调与文档

### Task 14: 全量 mvn clean verify + 前后端手动联调

**Context:** 这是 plan 的关键验收点。

- [x] **Step 1: 清理并全量测试**

```bash
cd server && mvn clean verify
```

Expected: 所有模块 BUILD SUCCESS、所有单元测试 + IT 通过。如有失败，回到上游 Task 修复（最常见：IT 断言老的 `{"messages": [...]}` 响应结构、`new ActionDescriptor(...)` 构造参数个数、DtEvent 字段类型变动）。

- [x] **Step 2: 启动后端**

```bash
cd server && mvn spring-boot:run -pl data-talk-adapter
```

- [x] **Step 3: 启动前端（假设前端阶段 0-3 已完成）**

```bash
cd client && npm run tauri dev
```

- [x] **Step 4: 手动验收（同步文档 §验收场景 1-6）**

1. **切走再切回不丢 AI 消息**：session A 问 AI "你好" → 切到 B → 切回 A → 完整显示 user + assistant 消息
2. **刷新页面不丢**：同上场景，用浏览器刷新代替切会话
3. **流式实时感**：问 "写一个 500 字春天作文" → 观察文字逐步增长（不卡顿后一股脑）
4. **乐观 UI**：点发送 → 立即显示 placeholder → 收到 OpenCode 回推后 ID 无缝切换
5. **空会话**：新建 session 不发消息直接打开 → 页面不报错，空白对话
6. **OpenCode 离线**：`pkill -f opencode` → 打开会话 → 前端显示错误提示

如任一场景失败，记录日志回溯代码。

- [x] **Step 5: Commit 验收日志**

无代码提交；若发现小 bug 补 commit 单独记录。

---

### Task 15: 更新项目文档

**Files:**
- Modify: `docs/generated/db-schema.md`
- Modify: `ARCHITECTURE.md`
- Modify: `docs/DESIGN.md`（若提及 messages 表）

- [x] **Step 1: 更新 db-schema.md**

删除 `messages` 表的章节；在顶部版本说明加：
```
V8 (2026-04-19): dropped `messages` table — OpenCode is now authoritative for
message persistence; DataTalk only stores `events` for SSE resume.
```

- [x] **Step 2: 更新 ARCHITECTURE.md**

在"数据持久化"章节把 `messages` 相关段落改写为"AI 消息由 OpenCode 持久化；DataTalk 通过 `GET /session/:id/message` 透传"。添加一行说明 `ActionDescriptor` 扩展了 `riskLevel` / `category`。

- [x] **Step 3: grep 确认无残留 messages 表引用**

```bash
grep -rn "messages 表\|messages\.save\|MessageRepository" docs/
```

Expected: 只有本 plan 文件自身和 db-schema 的历史章节。

- [x] **Step 4: Commit**

```bash
git add docs/generated/db-schema.md ARCHITECTURE.md docs/DESIGN.md
git commit -m "docs: reflect messages-table drop and ActionDescriptor risk extension"
```

---

### Task 16: 登记 plan 完成，移到已完成区

**Files:**
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-19-history-opencode-passthrough-sync.md`
- Modify: `docs/exec-plans/2026-04-19-ai-message-history-backend-plan.md`（本文件勾完所有 checkbox）

- [x] **Step 1: 勾选 plan 内所有 `- [x]` → `- [x]`**

```bash
sed -i 's/- \[ \]/- [x]/g' docs/exec-plans/2026-04-19-ai-message-history-backend-plan.md
```

手动复核关键 checkbox，必要时保留未完成（如手动验收场景某条失败）。

- [x] **Step 2: index.md 把本 plan 移到「已完成」区**

在"已完成计划"表顶部新增：

```markdown
| [AI Message History Backend](./2026-04-19-ai-message-history-backend-plan.md) | 2026-04-XX | messages 表下沉到 OpenCode；HistoryService 透传 GET /session/:id/message；OpenCodeEventTranslator Part 透传；ActionDescriptor 扩展 riskLevel/category；7 个 Action 注解回填 |
```

同步把「设计完成，待 `/plan`」的同步文档条目状态改为「后端完工，待前端联调」。

- [x] **Step 3: 同步文档标记后端部分完成**

在 `2026-04-19-history-opencode-passthrough-sync.md` 顶部元数据加：
```markdown
**后端完工**：2026-04-XX（plan: 2026-04-19-ai-message-history-backend-plan.md）
```

- [x] **Step 4: Final commit**

```bash
git add docs/exec-plans/2026-04-19-ai-message-history-backend-plan.md \
        docs/exec-plans/index.md \
        docs/exec-plans/2026-04-19-history-opencode-passthrough-sync.md
git commit -m "docs: mark AI message history backend plan complete"
```

---

## 计划执行后的遗留工作

- **新 Action 实现**（preview_sql / describe_table / list_tables / show_schema / artifact_created / question）：独立 brainstorm + plan，不在本 plan 范围
- **TD-020 SQL AST 真实判级**：已登记到 tech-debt-tracker.md，P2 优先级异步推进
- **前端阶段 0-6**：由前端 AI 并行推进，本后端 plan 完工后进入联调
