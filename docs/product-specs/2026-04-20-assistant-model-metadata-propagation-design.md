# Assistant Model Metadata Propagation

**日期**：2026-04-20
**状态**：已实现（2026-04-20，见 [exec-plans/2026-04-20-assistant-model-metadata-propagation-plan.md](../exec-plans/2026-04-20-assistant-model-metadata-propagation-plan.md)）

## 1. 背景与问题

助手消息流完成后，UI（`client/src/features/chat/components/turn/text-part.tsx:37`）期望通过 `props.info.modelID` 显示模型名，但实际不展示。历史消息正常显示，仅流式消息不显示。

## 2. 根因分析

存在两条完全独立的消息数据路径：

| 路径 | 源 | 后端处理 | 结果 |
|------|----|---------|------|
| 流式 | OpenCode SSE `message.updated` | `OpenCodeEventLoop.parseMessage()` 只抽取 `id / sessionID / role / time.created`，**丢弃 `info.model` 与顶层 `modelID`** | 前端收到的 `MessageCreated / MessageUpdated` 事件里没有模型字段 |
| 历史 | `GET /api/sessions/{id}/messages` | `HistoryService.getMessages` 纯透传 OpenCode 原始 JSON | 前端直接拿到原始 `info`，assistant 消息的扁平 `modelID` 被正确渲染 |

附带发现：`DtEvent.MessageCompleted` 是 sealed 接口下的 dead branch——后端没有任何生产者（`OpenCodeEventLoop.parseOcEvent` 只处理 `message.updated`），前端最近新增的 `message.completed` 合并分支（`use-channel.ts`）永远不会触发。

## 3. 设计目标

1. 让流式路径的 `DtEvent.MessageCreated / MessageUpdated` 携带 `providerID / modelID`，与历史路径数据形状对齐。
2. 清理 `MessageCompleted` 及其相关死代码。

**非目标**：改 `HistoryService` 或历史路径的渲染逻辑；扩展 picker 兜底（当后端正确送出后不需要）。

## 4. 方案（Plan A+）

### 4.1 Domain 层

`server/data-talk-domain/src/main/java/com/datatalk/domain/part/Message.java`：

```java
public record Message(
    String id,
    String sessionId,
    Role role,
    List<Part> parts,
    long createdAt,
    String providerID,  // 新增：OpenCode provider，可空
    String modelID      // 新增：OpenCode 模型 ID，可空
) { /* Role enum 保持不变 */ }
```

**字段命名**：沿用 OpenCode 协议的 `providerID / modelID`（非 Java 习惯的 `providerId / modelId`）。Jackson 默认序列化会保留原字段名，前端读取 `m.modelID` 无需适配层。

**可空语义**：历史会话、旧协议、非消息事件场景下均允许为 `null`。

`server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java`：

- 删除 `record MessageCompleted(String sessionId, String messageId) implements DtEvent {}`
- 删除 `@JsonSubTypes.Type(value = DtEvent.MessageCompleted.class, name = "message.completed")`
- 删除 `typeName()` 方法里对应的 `case MessageCompleted mc -> "message.completed"`

### 4.2 Backend Parser

`server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventLoop.java` 的 `parseMessage(JsonNode info)`：

```java
private Message parseMessage(JsonNode info) {
    if (info.isMissingNode() || info.isNull()) {
        return new Message(null, null, Message.Role.ASSISTANT, List.of(), 0L, null, null);
    }
    Message.Role role = Message.Role.valueOf(info.path("role").asText("assistant").toUpperCase());

    // OpenCode 1.4.7 有两种形态：
    //   user 消息     → info.model.{providerID, modelID}（嵌套）
    //   assistant 消息 → info.{providerID, modelID}（扁平）
    // 嵌套路径优先，兼容扁平。
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

private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    if (b != null && !b.isBlank()) return b;
    return null;
}
```

### 4.3 Frontend 清理

`client/src/services/channel/use-channel.ts`（对应后端删除 `MessageCompleted` 和读取逻辑收敛）：

- OR 条件移除 `event === 'message.completed'`，仅保留 `message.created` 与 `message.updated`
- 删除 `m.completedAt ?? m.completed_at ?? Date.now()` 的兜底（后端永不发）
- `nextTime.completed` 简化为 `m.time?.completed ?? existing?.time.completed`；完成时间继续由 `session.idle` → `markSessionTurnCompleted` 填充
- 兼容链 `m.modelID ?? m.modelId ?? m.model_id` 收敛为 `m.modelID`——后端现在只送这一种形态，其他分支是 A+ 之前的防御性遗留，已无意义
- `providerID` 同样收敛

## 5. 测试

### 5.1 Backend

`OpenCodeEventLoopParseTest` 扩展：

1. **嵌套形态**：沿用现有 `server/data-talk-application/src/test/resources/opencode-events-147/message-updated-user.json`，断言解析出的 `Message.providerID == "openai"`、`modelID == "gpt-4o-mini"`。
2. **扁平形态**：新增 fixture `message-updated-assistant.json`（assistant 消息带扁平 `providerID / modelID`），断言扁平路径被正确提取。
3. **缺失形态**：空 info 或旧协议消息，断言 `providerID / modelID` 均为 `null`，不抛异常。

`Message` record 构造点全量适配：实现阶段一次全仓 grep `new Message(`，为所有构造点补齐两个新参数（多数在测试中，传 `null, null`）。

### 5.2 Frontend

仅在现有 vitest 用例中有显式测试 `message.completed` 事件的地方做适配删除；不扩展新测试。

### 5.3 验证

实现阶段完整跑：
- `cd server && mvn clean verify`
- `cd client && npx tsc --noEmit`
- `cd client && npm test`（如有相关前端测试）

## 6. 风险与权衡

| 风险 | 评估 | 缓解 |
|------|------|------|
| `Message` record 构造点全量变更可能漏改 | 中 | 全仓 grep + `mvn clean verify` 兜底 |
| Jackson 序列化 `providerID` / `modelID` 实际输出字段名偏离预期 | 低 | 手工验证 SSE 输出或加一个序列化测试（可选） |
| OpenCode 未来协议再变更（flat 变 nested 或反向） | 低 | 本设计已同时支持两种形态，向前兼容性好 |

## 7. 实现顺序

1. Domain：扩展 `Message` record + 删除 `DtEvent.MessageCompleted`
2. 修复全仓 `new Message(...)` 调用点
3. Backend：更新 `parseMessage()` + 新增 assistant fixture
4. Backend 测试：扩展 `OpenCodeEventLoopParseTest`
5. Frontend：清理 `use-channel.ts` 的死分支和多余兼容链
6. 端到端验证

## 8. 非包含项

- Picker 快照兜底（Plan B）——A+ 修复后不需要
- `HistoryService` 或历史路径改动
- `DtEvent.MessageCompleted` 之外其他 unused 事件清理
- 前端 `buildEventSink` 的单元测试补全（单独挂技术债）
