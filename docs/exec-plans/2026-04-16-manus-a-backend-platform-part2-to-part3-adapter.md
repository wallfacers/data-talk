# Part 2 → Part 3 适配清单

> 这份文档列出了 Part 2 domain 类型需要修改的地方，以便 Part 3（Task 15-20）能够编译通过。
> 由 2 号智能体执行修改。

---

## 1. DtEvent.java — 补充 8 个缺失的事件类型

**文件**: `server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java`

### 现状

Part 2 现有的 14 个事件类型：
```
Connected, Disconnected, MessagePartCreated, MessagePartUpdated,
MessageCreated, MessageCompleted, SessionStarted, SessionEnded,
AgentStatus, TaskComplete, ActionInvoke, ActionResponse, StreamError, PingPong
```

### 需要新增的类型（Part 3 明确使用的）

| 新增类型 | 构造函数签名 | Part 3 使用场景 |
|----------|-------------|----------------|
| `Heartbeat` | `record Heartbeat(long timestamp) implements DtEvent` | SessionBus flusher 心跳、ChannelController 保活 |
| `MessagePartDelta` | `record MessagePartDelta(String partId, String field, String delta) implements DtEvent` | SessionBus delta coalescing（核心功能） |
| `SessionStatus` | `record SessionStatus(String status, Map<String, Object> details) implements DtEvent` | ChannelService 发送 busy/idle 状态 |
| `MessageUpdated` | `record MessageUpdated(String sessionId, String messageId, String role, Instant timestamp) implements DtEvent` | typeName() switch 分支 |
| `MessagePartRemoved` | `record MessagePartRemoved(String partId, String sessionId) implements DtEvent` | typeName() switch 分支 |
| `ActionCancel` | `record ActionCancel(String callId) implements DtEvent` | typeName() switch 分支 |
| `ArtifactSnapshot` | `record ArtifactSnapshot(String artifactId, String format, byte[] data) implements DtEvent` | typeName() switch 分支 |
| `OntologyUpdated` | `record OntologyUpdated(String ontologyId, int revision) implements DtEvent` | typeName() switch 分支 |

### 需要修改的 `@JsonSubTypes`

在现有的 `@JsonSubTypes` 数组中追加：

```java
@JsonSubTypes.Type(value = DtEvent.Heartbeat.class, name = "heartbeat"),
@JsonSubTypes.Type(value = DtEvent.MessagePartDelta.class, name = "message.part.delta"),
@JsonSubTypes.Type(value = DtEvent.SessionStatus.class, name = "session.status"),
@JsonSubTypes.Type(value = DtEvent.MessageUpdated.class, name = "message.updated"),
@JsonSubTypes.Type(value = DtEvent.MessagePartRemoved.class, name = "message.part.removed"),
@JsonSubTypes.Type(value = DtEvent.ActionCancel.class, name = "action.cancel"),
@JsonSubTypes.Type(value = DtEvent.ArtifactSnapshot.class, name = "artifact.snapshot"),
@JsonSubTypes.Type(value = DtEvent.OntologyUpdated.class, name = "ontology.updated"),
```

### 需要修改的 `permits` 子句

```java
public sealed interface DtEvent
        permits DtEvent.Connected, DtEvent.Disconnected,
                DtEvent.MessagePartCreated, DtEvent.MessagePartUpdated,
                DtEvent.MessagePartDelta,     // 新增
                DtEvent.MessageCreated, DtEvent.MessageUpdated, DtEvent.MessageCompleted,  // MessageUpdated 新增
                DtEvent.SessionStarted, DtEvent.SessionEnded, DtEvent.SessionStatus,       // SessionStatus 新增
                DtEvent.AgentStatus, DtEvent.TaskComplete,
                DtEvent.ActionInvoke, DtEvent.ActionResponse, DtEvent.ActionCancel,        // ActionCancel 新增
                DtEvent.ArtifactSnapshot, DtEvent.OntologyUpdated,                          // 新增
                DtEvent.StreamError, DtEvent.PingPong, DtEvent.Heartbeat                    // Heartbeat 新增
```

---

## 2. NumberedEvent.java — ts 类型从 Instant 改为 long

**文件**: `server/data-talk-domain/src/main/java/com/datatalk/domain/event/NumberedEvent.java`

### 现状
```java
public record NumberedEvent(
    long eventId, String sessionId, DtEvent event, Instant ts
)
```

### 需要改为
```java
public record NumberedEvent(
    long eventId, String sessionId, DtEvent event, long ts
) {
    public NumberedEvent {
        if (eventId < 0) throw new IllegalArgumentException("eventId must not be negative");
    }

    public static NumberedEvent of(long eventId, String sessionId, DtEvent event) {
        return new NumberedEvent(eventId, sessionId, event, System.currentTimeMillis());
    }
}
```

**原因**: Part 3 的 SessionBus 使用 `clock.millis()` 产生时间戳，所有 `new NumberedEvent(..., now)` 的 `now` 是 `long`。`typeName()` 和 `SseEmitterSubscriber` 中也用 `long` 处理时间。

---

## 3. Part 体系 — TextPart 等 Part 类型需要扩展字段

**文件**: 所有 `server/data-talk-domain/src/main/java/com/datatalk/domain/part/*.java`

### 现状

Part 2 的 Part 类型是极简的：
```java
TextPart(String content)
ReasoningPart(String reasoning)
ToolPart(String toolName, String toolInput, ToolState toolState)
FilePart(String filename, String mimeType, String url, long sizeBytes)
```

### Part 3 期望的 TextPart 构造函数

Part 3 的 Task 15 测试中使用：
```java
new TextPart("p1", "s-1", "m-1", "a", null, null, null, Map.of())
```
对应 8 个参数：`id`, `sessionId`, `messageId`, `content`(text), `metadata`, `createdAt`, `updatedAt`, 和一个额外字段。

### Part 3 期望的 Message 构造函数

Part 3 的 Task 19 测试中使用：
```java
new Message(messageId, sessionId, Message.Role.USER, parts, now)
```
对应 5 个参数：`id`, `sessionId`, `Role`, `parts`, `createdAt`(long millis)

但 Part 2 现有的 Message 是 6 参数：
```java
Message(String id, String sessionId, String messageId, Role role, List<Part> parts, Instant createdAt)
```

### 建议修改方案

**方案 A（推荐）**: 将 Part 体系改为抽象基类或扩展接口，让每个 Part 实现包含公共字段：

```java
// Part.java — 改为 abstract class 提供公共字段
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextPart.class, name = "text"),
    // ... 其他不变
})
public abstract sealed class Part
        permits TextPart, ReasoningPart, ToolPart, FilePart,
                StepStartPart, StepFinishPart, SubtaskPart {

    public abstract String id();
    public abstract String sessionId();
    public abstract String messageId();
    public abstract Map<String, Object> metadata();
}
```

然后每个实现类加上这些字段。但这改动量较大。

**方案 B（最小改动）**: 只改 Part 3 实际用到的 `TextPart`，其余保持不动：

```java
// TextPart.java
public record TextPart(
    String id,
    String sessionId,
    String messageId,
    String content,
    String mimeType,    // nullable
    Long createdAt,     // nullable, epoch millis
    Long updatedAt,     // nullable, epoch millis
    Map<String, Object> metadata
) implements Part {
    // 为了向后兼容，可以加一个便捷构造函数
    public TextPart(String content) {
        this(null, null, null, content, null, null, null, Map.of());
    }
}
```

**同时修改 Message.java**：

```java
// Message.java — 改回 5 参数构造函数以匹配 Part 3 计划
public record Message(
    String id,
    @JsonProperty("sessionID") String sessionId,
    Role role,
    List<Part> parts,
    long createdAt          // ← 改为 long（与 NumberedEvent.ts 统一）
) {
    public enum Role { USER, ASSISTANT, SYSTEM }
}
```

**注意**: 如果选方案 B，需要确认 `@JsonProperty("messageID") messageId` 字段从 Message 中移除（Message 的 id 就代表 messageId），或者保留 6 参数但让 Part 3 适配。

---

## 4. persistence 层 — 需要创建 Repository 接口

**目录**: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/`

当前此目录只有 `SecretVault.java`，Part 3 需要以下接口：

### 4.1 EventRepository.java

```java
package com.datatalk.application.persistence;

public interface EventRepository {
    /** 追加事件到持久化存储 */
    void persist(String sessionId, long eventId, String eventType, String payloadJson, long ts);

    /** 别名：append 是 persist 的别名 */
    default void append(String sessionId, long eventId, String eventType, String payloadJson, long ts) {
        persist(sessionId, eventId, eventType, payloadJson, ts);
    }

    /** 获取某 session 的最大 eventId（用于 SessionBus 恢复） */
    long maxEventId(String sessionId);
}
```

### 4.2 SessionRecord.java

```java
package com.datatalk.application.persistence;

public record SessionRecord(
    String id,
    String userId,
    String title,
    boolean hasEverSent,
    String connectionId,
    Long createdAt,
    Long updatedAt
) {}
```

### 4.3 SessionRepository.java

```java
package com.datatalk.application.persistence;

import java.util.Optional;

public interface SessionRepository {
    Optional<SessionRecord> findById(String sessionId);
    void upsert(SessionRecord record);
    void markHasEverSent(String sessionId, long timestamp);
}
```

### 4.4 MessageRecord.java (可选，如果 Message 不直接用 domain 类型)

如果 persistence 层和 domain 层的 Message 是同一个类型（domain type 直接用于持久化），则不需要。如果需要区分，创建一个 record。

### 4.5 MessageRepository.java

```java
package com.datatalk.application.persistence;

import com.datatalk.domain.part.Message;

public interface MessageRepository {
    void save(Message message);
}
```

---

## 5. SessionBusRegistry 构造函数签名确认

Part 3 的 Task 16 期望 `SessionBusRegistry` 的构造函数接收 `EventRepository`：

```java
public SessionBusRegistry(
    EventRepository events,
    ObjectMapper om,
    Clock clock,
    int bufferSize,
    Duration bufferTtl,
    Duration flushInterval
)
```

而 `SessionBus` 的 `Persister` 接口通过 `events::append` 方法引用连接。请确保 `EventRepository` 有 `append` 或 `persist` 方法（见上 4.1）。

---

## 6. ErrorInfo.java — 确认兼容性

**文件**: `server/data-talk-domain/src/main/java/com/datatalk/domain/event/ErrorInfo.java`

Part 2 现有：
```java
public record ErrorInfo(String code, String message, boolean retriable, Map<String, Object> details)
```

Part 3 的 `RpcRequest.ActionResultParams` 引用 `ErrorInfo`，ChannelService 的 `ActionResultError` 使用 `info.message()`。

**当前 ErrorInfo 有 `message()` 方法，兼容，无需修改。**

---

## 修改优先级

| 优先级 | 修改项 | 影响范围 |
|--------|--------|----------|
| P0 | 1. DtEvent 补充 8 个类型 | Task 15 SessionBus 编译必须 |
| P0 | 2. NumberedEvent.ts 改 `long` | Task 15/17 所有 new NumberedEvent 调用 |
| P0 | 3. TextPart / Message 构造函数对齐 | Task 15 测试、Task 19 ChannelService |
| P1 | 4. persistence Repository 接口 | Task 16/19 编译必须 |
| P2 | 5. Clock bean | Task 16 的 ClockConfig |

---

## 执行建议

1. **先改 P0 三项**：DtEvent → NumberedEvent → TextPart/Message
2. **再改 P1**：创建 4 个 persistence 文件
3. **改动量**: 约 10 个文件修改 + 4 个新文件创建
