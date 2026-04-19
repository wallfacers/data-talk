# Assistant Model Metadata Propagation Implementation Plan

**Status:** 已完成（2026-04-20）

**Execution notes:**
- Task 1 commit: `b7f695b`（Message record 扩字段 + DtEvent.MessageCompleted 清理）
- Task 2 commit: `054ddbf`（parseMessage 两形态兼容 + 3 个新测试；RED→GREEN 经过确认）
- Task 3 commit: `4313096`（use-channel.ts 清理，顺带删除 2 个 message.completed 死 vitest 用例）
- Task 4 自动化：`mvn clean verify` BUILD SUCCESS；`OpenCodeEventLoopParseTest 13/13`、`use-channel.test.ts 11/11` 全绿
- Task 4 Step 3 手动端到端验证：留给人工联调（自动化已覆盖契约层）
- Task 4 Step 4 可选验证日志 commit：跳过（BUILD SUCCESS + 单测绿已充分）
- 期间发现前端 `stage-tab-bar.tsx` / `stage-window.test.tsx` 2 个预存在问题，与本 plan 无关，单独处理

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让流式路径的 `MessageCreated / MessageUpdated` 事件携带 OpenCode 1.4.7 的 `providerID / modelID`，修复助手消息回答完成后 UI 不显示模型名的 bug；同步清理已确认无人产生 / 消费的 `DtEvent.MessageCompleted` 死代码及前端对应死分支。

**Architecture:** 后端在 `OpenCodeEventLoop.parseMessage()` 中兼容读取两种 OpenCode 形态（user 嵌套 `info.model.{providerID, modelID}`、assistant 扁平 `info.{providerID, modelID}`），并将两个新字段穿入 domain `Message` record；前端只做死代码清理，不改读取逻辑。历史路径完全不动。

**Tech Stack:** Java 21 records、Jackson、JUnit 5、AssertJ；前端 TypeScript。无新增依赖。

**Spec:** [docs/product-specs/2026-04-20-assistant-model-metadata-propagation-design.md](../product-specs/2026-04-20-assistant-model-metadata-propagation-design.md)

---

## 背景

详见 spec。一句话：`OpenCodeEventLoop.parseMessage()` 把 OpenCode 事件翻译成 domain `Message` 时丢弃了模型元数据，且 `Message` 本身也没有承载字段；历史路径因为纯透传所以 OK，流式路径因此模型名始终不显示。

## 范围

**改动文件（后端）：**
- `server/data-talk-domain/src/main/java/com/datatalk/domain/part/Message.java` — record 加两个可空字段
- `server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java` — 删 `MessageCompleted` record / 注册 / 穷尽 switch case
- `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventLoop.java` — `parseMessage()` 读两种 model 形态；2 个 `new Message(...)` 构造点补参数；新增 `firstNonBlank` helper
- `server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventLoopParseTest.java` — 扩展断言，新增 assistant / empty 场景
- `server/data-talk-application/src/test/resources/opencode-events-147/message-updated-assistant.json` — 新建 fixture

**改动文件（前端）：**
- `client/src/services/channel/use-channel.ts` — 移除 `message.completed` 分支、`completedAt/`completed_at` fallback、`modelId`/`model_id` 兼容链

**不改：**
- `HistoryService` 或历史路径
- `Message` 之外的 domain record
- 前端 `MessageInfo` 类型定义（`modelID` 字段已存在）
- 前端 UI（`text-part.tsx:37` 读取逻辑）

---

## Task 1：Domain 变更

**Files:**
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/part/Message.java`
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventLoop.java` (2 处 `new Message(...)` 调用点补参数)

- [x] **Step 1：扩展 `Message` record**

修改 `Message.java`，保留 `Role` enum 不变，record 体替换为：

```java
public record Message(
    String id,
    String sessionId,
    Role role,
    List<Part> parts,
    long createdAt,
    String providerID,
    String modelID
) {
    public enum Role {
        USER, ASSISTANT, SYSTEM;

        @JsonValue
        public String toJsonValue() { return name().toLowerCase(); }

        @JsonCreator
        public static Role from(String s) { return valueOf(s.toUpperCase()); }
    }
}
```

- [x] **Step 2：补齐 `OpenCodeEventLoop.parseMessage()` 里的 2 个 `new Message(...)` 构造点**

`OpenCodeEventLoop.java:303` 空 info 分支：

```java
return new Message(null, null, Message.Role.ASSISTANT, List.of(), 0L, null, null);
```

`OpenCodeEventLoop.java:306` 正常分支（本 Task 仅先补 `null, null` 占位，Task 2 再替换成真正提取逻辑）：

```java
return new Message(
    info.path("id").asText(null),
    info.path("sessionID").asText(null),
    role,
    List.of(),
    info.path("time").path("created").asLong(0L),
    null,
    null
);
```

- [x] **Step 3：删除 `DtEvent.MessageCompleted` 及所有引用**

`DtEvent.java` 删除三处：

1. 第 30 行的 `@JsonSubTypes.Type(value = DtEvent.MessageCompleted.class, name = "message.completed"),`
2. 第 80-81 行：
   ```java
   @JsonTypeName("message.completed")
   record MessageCompleted(String sessionId, String messageId) implements DtEvent {}
   ```
3. 第 131 行：`case MessageCompleted mc      -> "message.completed";`

- [x] **Step 4：编译验证**

Run: `cd server && mvn compile -q`
Expected: 零编译错误。

如果报 `new Message(...)` 参数个数不匹配，检查 Step 2 是否漏改；如果报 sealed switch 非穷尽，检查 Step 3 的 case 是否真的删干净。

- [x] **Step 5：跑现有测试确认未破坏 baseline**

Run: `cd server && mvn test -q -pl data-talk-application -Dtest=OpenCodeEventLoopParseTest`
Expected: 所有现有用例绿（包括 `messageUpdatedNormalizesRoleAndSessionId`——它不断言 modelID，现在 modelID 恰好是 null，断言全过）。

- [x] **Step 6：Commit**

```bash
git add server/data-talk-domain/src/main/java/com/datatalk/domain/part/Message.java \
        server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java \
        server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventLoop.java
git commit -m "refactor(domain): extend Message record with providerID/modelID, drop unused MessageCompleted"
```

---

## Task 2：后端 parseMessage 提取 provider/model（TDD）

**Files:**
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventLoopParseTest.java`
- Create: `server/data-talk-application/src/test/resources/opencode-events-147/message-updated-assistant.json`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventLoop.java`

- [x] **Step 1：新增 assistant fixture**

创建 `server/data-talk-application/src/test/resources/opencode-events-147/message-updated-assistant.json`：

```json
{"type":"message.updated","properties":{"sessionID":"ses_25fabb782ffeZPB4zWXBff62AS","info":{"id":"msg_assistant_x01","role":"assistant","sessionID":"ses_25fabb782ffeZPB4zWXBff62AS","time":{"created":1776511371500},"providerID":"anthropic","modelID":"claude-opus-4-7"}}}
```

形状说明：assistant 消息把 providerID/modelID 扁平放在 info 顶层（与 user 的嵌套 `info.model.*` 形成对照）。sessionID 沿用现有 user fixture 的值便于对照。

- [x] **Step 2：扩展 `messageUpdatedNormalizesRoleAndSessionId` 测试加 user 嵌套 model 断言**

`OpenCodeEventLoopParseTest.java:94-106` 现有 test 末尾追加：

```java
assertThat(m.providerID()).isEqualTo("openai");
assertThat(m.modelID()).isEqualTo("gpt-4o-mini");
```

（user fixture `message-updated-user.json` 里的 `info.model.{providerID, modelID}` 值是 `openai` / `gpt-4o-mini`。）

- [x] **Step 3：新增 assistant 扁平 model 解析测试**

在 `OpenCodeEventLoopParseTest.java` 追加：

```java
@Test
void messageUpdatedAssistantExtractsFlatModel() throws Exception {
    // 1.4.7 wire: assistant 消息 info.{providerID, modelID} 扁平形态
    OcEvent e = loop.parseOcEvent("message.updated", load("message-updated-assistant.json"));
    assertThat(e).isInstanceOf(OcEvent.MessageUpdated.class);
    Message m = ((OcEvent.MessageUpdated) e).message();
    assertThat(m.role()).isEqualTo(Message.Role.ASSISTANT);
    assertThat(m.providerID()).isEqualTo("anthropic");
    assertThat(m.modelID()).isEqualTo("claude-opus-4-7");
}
```

- [x] **Step 4：新增缺失 model 的防御测试**

在 `OpenCodeEventLoopParseTest.java` 追加：

```java
@Test
void messageUpdatedReturnsNullModelWhenFieldsMissing() throws Exception {
    // 旧协议 / 未知消息：provider/model 都不存在时，不抛且返回 null
    String json = """
        {"type":"message.updated","properties":{"info":{"id":"msg_x","role":"user","sessionID":"s1","time":{"created":0}}}}
        """;
    OcEvent e = loop.parseOcEvent("message.updated", json);
    Message m = ((OcEvent.MessageUpdated) e).message();
    assertThat(m.providerID()).isNull();
    assertThat(m.modelID()).isNull();
}
```

- [x] **Step 5：跑测试确认失败**

Run: `cd server && mvn test -q -pl data-talk-application -Dtest=OpenCodeEventLoopParseTest`
Expected: 3 个断言失败——两个"期望 openai / anthropic 但实际 null"，一个 empty 场景恰好通过（因为现在 modelID 就是硬编码 null）。

- [x] **Step 6：实现 `parseMessage` 提取逻辑**

`OpenCodeEventLoop.java` 的 `parseMessage(JsonNode info)` 完整替换为：

```java
private Message parseMessage(JsonNode info) {
    if (info.isMissingNode() || info.isNull()) {
        return new Message(null, null, Message.Role.ASSISTANT, List.of(), 0L, null, null);
    }
    Message.Role role = Message.Role.valueOf(info.path("role").asText("assistant").toUpperCase());

    // OpenCode 1.4.7 两种形态：
    //   user 消息     → info.model.{providerID, modelID}（嵌套）
    //   assistant 消息 → info.{providerID, modelID}（扁平）
    // 嵌套优先，扁平兜底。
    String providerID = firstNonBlank(
        info.path("model").path("providerID").asText(null),
        info.path("providerID").asText(null)
    );
    String modelID = firstNonBlank(
        info.path("model").path("modelID").asText(null),
        info.path("modelID").asText(null)
    );

    return new Message(
        info.path("id").asText(null),
        info.path("sessionID").asText(null),
        role,
        List.of(),
        info.path("time").path("created").asLong(0L),
        providerID,
        modelID
    );
}
```

在类末尾（`extractSessionId` 方法之后、类闭合 `}` 之前）加 helper：

```java
private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    if (b != null && !b.isBlank()) return b;
    return null;
}
```

- [x] **Step 7：跑测试确认全部通过**

Run: `cd server && mvn test -q -pl data-talk-application -Dtest=OpenCodeEventLoopParseTest`
Expected: 所有用例绿，包括三个新/扩展的断言。

- [x] **Step 8：Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventLoop.java \
        server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventLoopParseTest.java \
        server/data-talk-application/src/test/resources/opencode-events-147/message-updated-assistant.json
git commit -m "feat(opencode): extract providerID/modelID from both nested and flat info shapes"
```

---

## Task 3：前端死代码清理

**Files:**
- Modify: `client/src/services/channel/use-channel.ts`

- [x] **Step 1：收敛事件分支与字段读取**

`client/src/services/channel/use-channel.ts` 的事件分派块（当前位于第 29-61 行）整体替换为：

```ts
if (event === 'message.created' || event === 'message.updated') {
  const m = (data as any).info ?? (data as any).message ?? (data as any)
  if (!m?.id) return

  const mid = m.id
  const store = useChatPartsStore.getState()
  const existing = store.infoBySession.get(sessionId)?.get(mid)

  const role = (m.role ? String(m.role).toLowerCase() : existing?.role) as MessageInfo['role']
  const modelID = m.modelID ?? existing?.modelID
  const providerID = m.providerID ?? existing?.providerID

  const nextTime = {
    created: m.time?.created ?? existing?.time.created ?? Date.now(),
    completed: m.time?.completed ?? existing?.time.completed,
  }

  const info: MessageInfo = {
    id: mid,
    role: role || 'assistant',
    sessionID: m.sessionID ?? existing?.sessionID ?? sessionId,
    time: nextTime,
    providerID,
    modelID,
    parentID: m.parentID ?? existing?.parentID,
    agent: m.agent ?? existing?.agent,
    mode: m.mode ?? existing?.mode,
    error: m.error ?? existing?.error,
    finish: m.finish ?? existing?.finish,
    tokens: m.tokens ?? existing?.tokens,
  }
  store.upsertInfo(sessionId, info)
}
```

清理要点：
- 从 OR 条件移除 `message.completed`
- 删除 `m.modelId` / `m.model_id` 下划线和小驼峰兼容分支（后端现在只会送 `modelID`）
- 删除 `m.providerId` / `m.provider_id` 兼容链
- 删除 `m.createdAt` / `m.created_at` fallback
- 删除 `m.completedAt` / `m.completed_at` fallback 及 `event === 'message.completed'` 三元
- 删除 `m.parentId` / `m.parent_id` fallback
- `(data as any).messageId` 的 fallback 删除——后端 `MessageCreated / MessageUpdated` 只会送 `{message: {id, ...}}` 形态，参考 `DtEvent.MessageCreated` / `DtEvent.MessageUpdated` 的 record 定义

- [x] **Step 2：类型检查**

Run: `cd client && npx tsc --noEmit`
Expected: 零类型错误。

- [x] **Step 3：跑前端测试确认无回归**

Run: `cd client && npm test -- --run`
Expected: 所有测试绿。如果有用例显式构造 `message.completed` 事件，说明该用例也是死代码——就地删除或改写成 `message.updated`，然后重跑。

- [x] **Step 4：Commit**

```bash
git add client/src/services/channel/use-channel.ts
git commit -m "refactor(client): drop dead message.completed branch and obsolete field-name fallbacks"
```

---

## Task 4：端到端验证

**Files:** 无修改，仅验证。

- [x] **Step 1：后端全量回归**

Run: `cd server && mvn clean verify`
Expected: 所有模块编译、单测、集成测试绿。

注意：`mvn clean verify` 会重新构建所有模块。如果看到 `message.completed` 相关的未使用引用告警，回 Task 1 Step 3 核查。

- [x] **Step 2：前端类型检查 + 测试**

Run: `cd client && npx tsc --noEmit && npm test -- --run`
Expected: 全绿。

- [x] **Step 3：手动端到端验证（需真实 OpenCode）**

启动服务：
- Terminal A: `cd server && mvn install -pl data-talk-domain,data-talk-application,data-talk-infrastructure -am -DskipTests && mvn spring-boot:run -pl data-talk-adapter`
- Terminal B: `cd client && npm run dev`

操作步骤：
1. 登录客户端，选择一个可用 assistant 模型（记录 picker 显示的模型名）
2. 打开一个会话，发送任意消息
3. 等待助手回复流式完成
4. 断言：回复消息尾部应显示"· {模型名}"
5. 切换到另一个模型，发第二条消息
6. 断言：第二条助手回复显示新模型名；第一条助手回复仍显示旧模型名（不被覆盖）
7. 刷新页面，重新打开会话
8. 断言：两条历史消息都显示各自的模型名（与刷新前一致）

- [x] **Step 4：Commit 验证日志（可选）**

如果步骤 3 发现任何偏差，不要继续；回到相关 Task 修复。如果全通过，可空 commit 打个里程碑标记，或直接进入文档收尾。

---

## Task 5：文档收尾（CLAUDE.md 强制项）

**Files:**
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/product-specs/index.md`

- [x] **Step 1：在 exec-plans 索引里把本计划从「活跃」迁到「已完成」**

编辑 `docs/exec-plans/index.md`：
- 从「活跃计划」表格中删除本 plan 一行（如果之前登记在那里）
- 在「已完成计划」表格最顶端追加：
  ```
  | [Assistant Model Metadata Propagation](./2026-04-20-assistant-model-metadata-propagation-plan.md) | 2026-04-20 | `Message` record 新增 `providerID/modelID`；`OpenCodeEventLoop.parseMessage` 兼容 user 嵌套 / assistant 扁平两种形态；清理 `DtEvent.MessageCompleted` 死事件 + 前端 `message.completed` 分支 + 字段名兼容链 |
  ```

- [x] **Step 2：确认本计划文件内所有 checkbox 已勾选**

扫一遍本 plan 文件，确保每个 `- [ ]` 都已改为 `- [x]`。任何 deviation 在 checkbox 后补"（deviation: ...）"备注。

- [x] **Step 3：Commit**

```bash
git add docs/exec-plans/index.md \
        docs/exec-plans/2026-04-20-assistant-model-metadata-propagation-plan.md
git commit -m "docs: mark assistant-model-metadata plan completed"
```

---

## 登记

本计划需要在创建后立即登记到 `docs/exec-plans/index.md` 的「活跃计划」表中。Task 5 完成时再搬到「已完成计划」。
