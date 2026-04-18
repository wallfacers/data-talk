# OpenCode 1.4.7 Event Envelope Adapter Implementation Plan

**Status:** 计划中（2026-04-18）

**Goal:** 修复 DataTalk `OpenCodeEventLoop.parseOcEvent` 与 OpenCode 1.4.7 wire format 的 envelope 不匹配问题——所有事件 payload 都嵌在 `properties.*` 下，老代码只读顶层字段导致 `sessionID`/`info`/`part` 全部拿不到，AI 响应事件进不了 SessionBus。

**Architecture:** 在 `parseOcEvent` 里先 unwrap `properties` 再读具体字段；新增 1.4.7 字段映射（`sessionID`→`sessionId`、`role:"user"`→`Role.USER`、嵌套 `status`/`error` 对象）。以从实机抓到的真实事件样本作为测试 fixture，锁住 wire format 契约。

**Tech Stack:** Jackson、JUnit 5、AssertJ；依赖不变。

---

## 背景

从运行中的 embedded OpenCode v1.4.7（`/event` SSE）抓到真实样本（9 种事件类型），envelope 统一为：

| Event type | 真实 1.4.7 形状 | 老代码期望 |
|-----------|-----------------|------------|
| `server.connected` | `{"type":"server.connected","properties":{}}` | 顶层为空 ✓ |
| `session.created` / `session.updated` / `session.deleted` / `session.idle` | `{"properties":{"sessionID":"ses_...","info":{"id":"ses_...","title":"...","time":{"created":...,"updated":...},"version":"1.4.6",...}}}` | 顶层有 `info` ✗ |
| `session.status` | `{"properties":{"sessionID":"...","status":{"type":"busy"}}}` | 顶层 `status` 是 string ✗ |
| `session.error` | `{"properties":{"sessionID":"...","error":{"name":"UnknownError","data":{"message":"..."}}}}` | 顶层 `error` 是 string ✗ |
| `message.updated` | `{"properties":{"sessionID":"...","info":{"id":"msg_...","role":"user","sessionID":"...","time":{"created":...},"agent":"build","model":{...}}}}` | 顶层 `info` 被直接 readAs Message；字段 `sessionID`、`role:"user"` 小写、无 `parts`、无 `createdAt` ✗ |
| `message.part.updated` | `{"properties":{"sessionID":"...","part":{"type":"text","text":"...","messageID":"...","sessionID":"...","id":"prt_..."},"time":...}}` | 顶层 `part` readAs Part ✗ |

**未抓到（需要有效 API key 才能触发）：**
- `message.part.delta` — 猜测格式 `{"properties":{"sessionID":"...","partID":"prt_...","field":"text","delta":"word"}}`
- `message.part.removed` — 猜测格式 `{"properties":{"sessionID":"...","partID":"..."}}`

这两个格式按猜测实现，加 TODO；上线后用户触发真实 AI 响应时如有偏差再修正。

## 范围

**改动文件：**

- `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventLoop.java` — `parseOcEvent` 统一从 `properties` 下读字段；`extractSessionId` 增加调试
- `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OcEvent.java`（如需调整 record 字段，由 Task 1 的调研决定）
- `server/data-talk-application/src/main/java/com/datatalk/application/opencode/SessionInfo.java` — 保持结构
- `server/data-talk-application/src/test/resources/opencode-events-147/` — 新增 fixture 目录，存 9 份 JSON 样本
- `server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventLoopParseTest.java` — 新增单测
- `server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventTranslatorTest.java` — 如有 mock 样本与 1.4.7 不符需更新

**不改：**

- `OpenCodeEventTranslator` 的 DtEvent 输出映射（这层已与 DataTalk wire 对齐）
- `SessionBus` / `ChannelController`
- 前端

---

## Task 1：调研现有 OcEvent 记录结构 + 确定是否需改字段

**Files:** 只读 `OcEvent.java`、`SessionInfo.java`、`OpenCodeEventTranslator.java`。

- [ ] **Step 1：读完三个文件，列出每个 OcEvent 子类字段、translator 用到的字段**

输出到本 plan 的"Task 1 笔记"小节，作为后续决策依据。

---

## Task 2：写 fixture + 测试（TDD）

**Files:**

- Create: `server/data-talk-application/src/test/resources/opencode-events-147/server-connected.json`
- Create: `server/data-talk-application/src/test/resources/opencode-events-147/session-created.json`
- Create: `server/data-talk-application/src/test/resources/opencode-events-147/session-updated.json`
- Create: `server/data-talk-application/src/test/resources/opencode-events-147/session-deleted.json`
- Create: `server/data-talk-application/src/test/resources/opencode-events-147/session-idle.json`
- Create: `server/data-talk-application/src/test/resources/opencode-events-147/session-status-busy.json`
- Create: `server/data-talk-application/src/test/resources/opencode-events-147/session-error.json`
- Create: `server/data-talk-application/src/test/resources/opencode-events-147/message-updated-user.json`
- Create: `server/data-talk-application/src/test/resources/opencode-events-147/message-part-updated-text.json`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventLoopParseTest.java`

- [ ] **Step 1：写 9 个 fixture JSON** — 直接 copy 此 plan 表格里的真实样本
- [ ] **Step 2：写单测**：每个 fixture 对应一条 assert，覆盖：
    - sessionId 能被正确抽取（或为 null for server.connected）
    - 具体 payload 字段映射正确（如 message.part.updated 的 part.id/text/messageID）
- [ ] **Step 3：跑一次确认全红**（parseOcEvent 还没改，大部分 case 会 NullPointer / 字段 null）

---

## Task 3：重写 parseOcEvent

**Files:**

- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventLoop.java`

- [ ] **Step 1**：顶部解包 `properties`：

```java
JsonNode root = json.isEmpty() ? om.createObjectNode() : om.readTree(json);
JsonNode props = root.path("properties");
```

后面所有字段读取从 `props` 开始。

- [ ] **Step 2**：各 case 调整字段路径：

```java
case "session.status" -> new OcEvent.SessionStatus(
    props.path("status").path("type").asText("idle"),   // 了 status.type
    om.convertValue(props.path("retryInfo"), Map.class));

case "session.created" / "updated" / "deleted" / "idle" / "compacted"
    -> new OcEvent.SessionXxx(parseSessionInfo(props));

case "session.error" -> new OcEvent.SessionError(
    parseSessionInfo(props),
    props.path("error").path("data").path("message").asText(
        props.path("error").path("name").asText("")));

case "message.updated" -> parseMessageUpdated(props);
case "message.part.updated" -> new OcEvent.MessagePartUpdated(
    om.treeToValue(props.path("part"), Part.class));  // Part record 字段一致，无需转
case "message.part.delta" -> new OcEvent.MessagePartDelta(
    props.path("partID").asText(),
    props.path("field").asText(),
    props.path("delta").asText());
case "message.part.removed" -> new OcEvent.MessagePartRemoved(
    props.path("partID").asText());
```

- [ ] **Step 3**：`parseSessionInfo` 现在接 `props` 而不是整个事件 json；从 `props.info` 下取 `id` / `title` / `time.updated`（没有 `version` 数字时回落到 `info.version` string-to-hash 或直接 `0L`）。
- [ ] **Step 4**：`parseMessageUpdated` 把 `properties.info` 的字段组装成 DataTalk `Message`——注意：
    - `info.sessionID` → `Message.sessionId`
    - `info.role`（小写字符串）→ `Message.Role`（要 `.toUpperCase()` 后 valueOf）
    - `info.time.created` → `Message.createdAt`
    - `info.parts` 不存在 → 用空 list（parts 由 `message.part.updated` 单独携带）
- [ ] **Step 5**：跑 Task 2 的单测，应全绿

---

## Task 4：确认 translator 在新 OcEvent 下映射仍正确

**Files:**

- Modify if needed: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventTranslator.java`

- [ ] **Step 1**：跑现有 `OpenCodeEventTranslatorTest`（如果它用了 mock OcEvent，应仍通过；如果它用了假 wire JSON 走了 parseOcEvent，需要把样本更新到 1.4.7 envelope）
- [ ] **Step 2**：如有失败，按 Task 2 fixture 的真实字段修正 translator

---

## Task 5：回归 + 重装 + 实机 smoke

- [ ] **Step 1**：全量回归：`cd server && mvn -pl data-talk-application test`
- [ ] **Step 2**：`mvn -pl data-talk-application install -am -DskipTests` 刷新 ~/.m2
- [ ] **Step 3**：重启后端 + 用配好 API key 的模型发消息，确认前端收到 AI 流
- [ ] **Step 4**：如 `message.part.delta` 实机格式与猜测不符，按真实抓包再修 parseOcEvent，补 fixture

---

## 验收

- [ ] `OpenCodeEventLoopParseTest` 全绿（9 种确认事件 + 2 种猜测事件）
- [ ] `mvn -pl data-talk-application test` 全绿
- [ ] 实机发送用户消息 → 前端能看到 assistant message 逐字 stream（即 `message.part.delta` 联通）
- [ ] 后端日志不再出现 `[opencode-event-loop] dropped ... — no DataTalk session mapped ...`（除 `server.connected` 无 sessionId 为预期）
- [ ] 计划从活跃区搬到已完成区

## 风险与回滚

- **风险 1**：message.part.delta 的真实字段名可能与猜测不符（可能 `part`.id 而非 `partID`；可能 `content` 而非 `delta`）。此时 Task 5 会失败并回到 Task 3 按真实抓包修正，成本低。
- **风险 2**：OpenCode 未来版本再次改 envelope。通过把 fixture 文件固化到仓库，后续再升级只需更新 fixture + rerun 测试即可定位差异。
- **回滚**：单 commit，`git revert` 即可恢复到当前状态；但当前状态下 AI 响应本就不通，revert 无实际收益。
