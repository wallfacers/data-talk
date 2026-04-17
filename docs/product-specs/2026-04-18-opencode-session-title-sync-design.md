# OpenCode Session 事件家族对接 · Title 自动同步

**日期**：2026-04-18  
**状态**：Draft（待 plan 执行）  
**相关文件**：`server/data-talk-application/src/main/java/com/datatalk/application/opencode/`、`client/src/features/session/chat-header.tsx`

---

## 0. 背景

当前 DataTalk 的 `SessionDto.title` 完全由前端/后端本地管理：创建会话时前端传 `"新会话"`，后续由用户在 ChatHeader 中手动 `renameSession` 改名。OpenCode 本身基于首轮对话自动生成智能标题，并通过全局 `/event` SSE 流中的 `session.updated` 事件推送，但 DataTalk 目前既未订阅该事件、也没有持久化回传通路。结果：ChatHeader 只能显示本地占位字符串。

这份设计文档规划：
1. 在 DataTalk 中订阅 OpenCode 完整的 `session.*` 事件家族（8 个事件）
2. 为 `session.updated` 打通端到端同步 pipeline（OpenCode → 后端持久化 → 前端刷新）
3. 保留用户手动 rename 的优先级（手动改名后 OpenCode 不再覆盖）
4. 其余 6 个事件（`session.idle / status / error / created / deleted / compacted / diff`）本 plan 只做事件家族翻译层的定义，消费场景留作后续技术债专项

用户约束（brainstorm 轮次确认）：
- 标题冲突策略 = 首次写入 + 手动锁定（方案 B）
- 锁定机制 = 新增 `title_locked` 列（方案 A）
- Scope = 完整事件家族翻译层（方案 C）

---

## 1. 架构

事件流向跨越四层，保持 `domain ← application ← infrastructure ← adapter` 的依赖方向：

```
┌─ OpenCode ─────────────┐
│  GET /event (SSE 全局流) │
│  event: session.updated │
│  data:  {info:{id,title,version,...}}
└───────────┬────────────┘
            │
┌───────────▼─────────── infrastructure ──┐
│  OpenCodeEventLoop (已存在)              │
│    parseOcEvent  ← 扩展 session.* 分支   │
│    extractSessionId ← 扩展 session-level │
└───────────┬────────────────────────────┘
            │ OcEvent.SessionXxx
┌───────────▼────────── application ──────┐
│  OpenCodeEventTranslator                │
│    session.updated → (副作用) 调用       │
│      SessionTitleSyncer.apply(...)      │
│    → 翻译 DtEvent.SessionMetaUpdated     │
│                                          │
│  SessionTitleSyncer (新增)               │
│    if !title_locked → update title      │
│                                          │
│  SessionRepository                       │
│    + applyAutoTitle(id, title)           │
│    + lockTitle(id)  (rename 时调用)      │
└───────────┬────────────────────────────┘
            │ DtEvent via SessionBus
┌───────────▼────────── adapter ─────────┐
│  ChannelController SSE → 客户端         │
└───────────┬────────────────────────────┘
            │
┌───────────▼────────── 前端 ────────────┐
│  use-channel.ts sink 捕获               │
│   'session.meta.updated' →              │
│   queryClient.invalidateQueries(         │
│     ['sessions'])                        │
│  ChatHeader 自动刷新                    │
└─────────────────────────────────────────┘
```

### 关键约束

- `SessionTitleSyncer` 在 application 层（不依赖 infrastructure），通过 `SessionRepository` 接口访问持久化
- `DtEvent.SessionMetaUpdated` 是 domain 层新增的 sealed 分支；所有 `switch (DtEvent)` 必须穷尽更新
- OpenCode 的 session id 通过已有的 `OpenCodeSessionMap` 映射到 DataTalk session id；如果某个 `session.updated` 的 oc-session 没在映射里（别的客户端建的），直接丢弃
- `session.idle / status / error` 等事件本 plan 也会翻译成 `DtEvent`，但是否在前端消费由后续 feature 决定；本 plan 只让 `session.updated` 走到持久化 + UI 刷新

---

## 2. 组件清单

### Domain 层

| 文件 | 改动 |
|---|---|
| `data-talk-domain/.../event/DtEvent.java` | 新增 7 个 record 分支：`SessionCreated / SessionCompacted / SessionDeleted / SessionDiff / SessionError / SessionIdle / SessionMetaUpdated`（每个携带最小 payload：`sessionId`、必要时 `title / version / error / ...`），保留已有 `SessionStatus` |
| `DtEvent.java` | 新 record 使用 `@JsonTypeName`（TD-003 已采用此方案） |

> **命名说明**：OpenCode 的原始事件名是 `session.updated`，但 `DtEvent` 里已有 `MessageUpdated` 分支。为避免消费侧读到 `SessionUpdated / MessageUpdated` 时产生语义混淆（session 本身变了 vs session 里的 message 变了），这里把对应的 DtEvent 命名为 `SessionMetaUpdated`，前端 SSE type 字段对应为 `session.meta.updated`。其他 `SessionCreated / Deleted / Idle ...` 与 OpenCode 原事件一一对应，无需改名。

### Application 层

| 文件 | 改动 |
|---|---|
| `opencode/OcEvent.java` | 新增 7 个 record（对应 OpenCode `session.*`），均含 `SessionInfo info`（id、title、version） |
| `opencode/SessionInfo.java` (**新建**) | record `(String id, String title, long version)`，匹配 OpenCode payload |
| `opencode/OpenCodeEventTranslator.java` | switch 扩展 7 分支；`session.updated` 分支先调 `SessionTitleSyncer.apply(...)` 作为副作用，再返回 `SessionMetaUpdated`；其余 6 个原样翻译但前端暂不消费（遗留技术债） |
| `opencode/SessionTitleSyncer.java` (**新建**) | `apply(String ocSessionId, String newTitle)`：经 `OpenCodeSessionMap` 拿 dtSessionId；调 `SessionRepository.applyAutoTitle`（只在 `title_locked=false` 时写入） |
| `persistence/SessionRepository.java` | 新增 `applyAutoTitle(id, title)`（带 `title_locked=0` 断言的 UPDATE） |
| `session/SessionService.java` | `rename(id, title)` 内改为单条 `UPDATE sessions SET title=?, title_locked=1, updated_at=? WHERE id=?` 原子设置 title + 锁定标记 |

### Infrastructure 层

| 文件 | 改动 |
|---|---|
| `opencode/OpenCodeEventLoop.java` | `parseOcEvent` 加 7 分支；`extractSessionId` 加 session-level 分支（从 `info.id` 取） |
| `db/migration/V4__session_title_locked.sql` (**新建**) | `ALTER TABLE sessions ADD COLUMN title_locked INTEGER NOT NULL DEFAULT 0;` |

### Adapter 层

| 文件 | 改动 |
|---|---|
| `channel/ChannelController.java` | 无功能改动；只要事件经 `SessionBus.publish` 自动流出，验证穿透即可 |
| `session/SessionDto.java` | 加 `titleLocked: boolean` 字段（便于前端将来加"AI 命名"徽章） |

### 前端

| 文件 | 改动 |
|---|---|
| `client/src/services/channel/use-channel.ts` `buildEventSink` | 加 `'session.meta.updated'` 分支：`queryClient.invalidateQueries({ queryKey: ['sessions'] })` |
| `client/src/types/generated/api.ts` | 后端 DTO 变化后由 `npm run gen:api` 重新生成 |
| `client/src/features/session/chat-header.tsx` | 无需手动改动 — `useSessions()` 由 query invalidate 触发重查即可 |

### 测试文件

| 类型 | 位置 |
|---|---|
| 单元 | `OpenCodeEventTranslatorTest`（扩展）、`SessionTitleSyncerTest`（新）、`SessionRepositoryTest`（扩展）、`DtEventJsonTest`（扩展） |
| 集成 | `ChannelControllerIT`（扩展）、`OpenCodeEventLoopIT`（新增或扩展） |
| 前端 | `use-channel.test.ts`（新建）、`chat-header.test.tsx`（扩展） |

---

## 3. 数据流

### 3.1 端到端时序

```
[t0] ChatHeader 显示 "新会话"
     用户在 composer 输入 → POST /api/sessions/{dt}/channel  (send_message)
                                  │
                                  ▼
     ChannelService.sendMessage
       └─ sessionMap.openCodeFor(dt) 或 gateway.createOpenCodeSession()
       └─ gateway.forwardUserMessage(ocSid, {...})  ← OpenCode POST /session/:id/message

[t1] OpenCode 开始生成回复（SSE 推 message.* 事件，照旧渲染）

[t2] OpenCode 内部根据首轮对话生成 title，通过全局 /event 推送:
       event: session.updated
       data:  {"info":{"id":"<ocSid>","title":"查询订单今日销量","version":2}}
                                  │
                                  ▼
     OpenCodeEventLoop.openStream → parseOcEvent("session.updated", data)
       → OcEvent.SessionUpdated(SessionInfo(...))
                                  │
                                  ▼
     handleOcEvent
       1. extractSessionId(oc)  // 从 info.id 拿 ocSid
       2. sessionMap.dataTalkFor(ocSid) → dtSid；没映射则 return
       3. translator.translate(dtSid, oc)
            ├─ side effect: SessionTitleSyncer.apply(ocSid, title)
            │     └─ SessionRepository.applyAutoTitle(dtSid, title)
            │         UPDATE sessions SET title=?, updated_at=?
            │         WHERE id=? AND title_locked=0
            └─ 返回 List.of(SessionMetaUpdated(dtSid, title, titleLocked=false))
       4. bus.publish(dt)  // SessionBus → ChannelController SSE → 客户端

[t3] 前端 use-channel.ts sink 收到 'session.meta.updated'
       → queryClient.invalidateQueries({ queryKey: ['sessions'] })
       → useSessions 重新拉 GET /api/sessions
       → ChatHeader 展示新 title
```

### 3.2 Payload 规范

**OpenCode 入站**（依据 `opencode/github/index.ts:560-563` 示例 + `OpenCodeEventLoop.parseOcEvent` 现有解析模式）

```
event: session.updated
data:  { "info": { "id": "...", "title": "...", "version": 2, ... } }
```

- 按 `parseOcEvent` 现有代码约定，`data` JSON 是 properties 的内容（不带顶层 `type`，`type` 在 `event:` 行）
- `info` 是最外层键，取值用 `node.path("info")`

**出站 DtEvent（新增）**

```json
{
  "type": "session.meta.updated",
  "sessionId": "<dtSid>",
  "title": "查询订单今日销量",
  "titleLocked": false,
  "version": 2
}
```

- 只携带 UI 需要的字段；其他 OpenCode 字段（created_at 等）不透传
- `titleLocked` 透传以支持前端后续的"AI 命名"徽章

### 3.3 并发与幂等

| 场景 | 行为 |
|---|---|
| 同一 session 短时间多次 `session.updated`（OpenCode 可能每次 title 候选都推） | `applyAutoTitle` 每次都是一次 UPDATE；以最后一次为准；无需去重 |
| `title_locked=1` 时收到 `session.updated` | UPDATE 的 WHERE 命中 0 行，DB 不变；Translator 仍发 `SessionMetaUpdated` 但 `titleLocked=true`，前端 invalidate 仍触发但 title 数据未变 |
| 用户在 OpenCode 推送 title 期间调 `renameSession` | `SessionService.rename` 单条 UPDATE 原子设置 `title` + `title_locked=1`；OpenCode 推送若在其后到达，WHERE 命中 0 行，用户命名胜出 |
| OpenCode 推的 title 与当前 title 相同 | UPDATE 命中 1 行但值相同；仍发 `SessionMetaUpdated`；前端 invalidate 产生一次多余 refetch（可接受） |
| OpenCode 推 title 时 ocSid 未在 `OpenCodeSessionMap` | `sessionMap.dataTalkFor` 返回 null → 整条事件丢弃 |

### 3.4 事件排序不变式

- `OpenCodeEventLoop` 是单 worker 线程消费 SSE → 单线程发布到 `SessionBus`，每个 session 内的事件顺序 = OpenCode 推送顺序
- `SessionMetaUpdated` 和 `MessageCreated / PartUpdated` 的相对顺序由 OpenCode 决定，我们不重排
- 前端 `invalidateQueries(['sessions'])` 触发的 refetch 是 async，不阻塞 chat parts 渲染

---

## 4. 错误处理

| 失败点 | 行为 | 可观测性 |
|---|---|---|
| OpenCode SSE 连接断开 | `OpenCodeEventLoop` 已有 1s → 30s 指数退避重连（现成逻辑） | 日志 `WARN opencode-event-loop reconnecting, backoff=...` |
| 重连后首帧丢失 | OpenCode 无 resume-from-cursor；title 靠**最终**一次 `session.updated` 补齐（最终一致） | 一致性级别：最终一致 |
| `parseOcEvent` JSON 解析失败 | 降级到 `OcEvent.Unknown(name, {parseError: ...})`，不影响其他事件 | `tap` 回调可打点 |
| `SessionInfo.id` 在 `OpenCodeSessionMap` 查不到 | 静默丢弃 | 可选 DEBUG 日志 |
| `SessionRepository.applyAutoTitle` 抛 SQL 异常 | 由 translator 副作用层捕获吞掉，title 同步不应阻断其他 DtEvent 发布 | WARN 日志含 sessionId |
| `SessionBus.publish` 失败 | 现有 `isBroken()` 已容忍 | — |
| 前端 `invalidateQueries` refetch 失败 | React Query 自动重试；失败时 ChatHeader 显示旧 title（缓存） | React Query devtools 可见 |
| `renameSession` 非原子 | 改为单条 `UPDATE title + title_locked=1` 保证原子 | N/A |
| OpenCode 推 title 晚于 `session.idle` 到达 | 无影响，通路仍在，title 仍可同步 | N/A |

**显式不处理**：OpenCode 内部 title 生成策略变更（比如某版本突然不再推 `session.updated`）— 本 plan 不做适配层，靠集成测试 + 手动回归发现。

---

## 5. 测试策略

### 5.1 单元

| 类 | 用例 |
|---|---|
| `OpenCodeEventTranslatorTest` | 每个 session.* 事件 → 对应 DtEvent.Session*；`session.updated` 验证 syncer 被调一次 |
| `SessionTitleSyncerTest` | (1) `title_locked=0` → `applyAutoTitle` 被调；(2) `=1` → 不调；(3) ocSid 未映射 → 不调；(4) Repository 抛异常 → 由 translator 层兜底 |
| `SessionRepositoryTest` | `applyAutoTitle` 在 `title_locked=0` 时写入；`=1` 时不写；`rename` 原子 |
| `DtEventJsonTest` | 每个新 session.* 子类型的 Jackson round-trip |

### 5.2 集成

| 类 | 用例 |
|---|---|
| `ChannelControllerIT` | WireMock FakeOpenCode 在 `/event` 推 `session.updated`（ocSid 已映射），断言：(1) 客户端 SSE 收 `session.meta.updated` payload 正确；(2) DB 里 session.title 已更新；(3) 另一条测试 `title_locked=1` 下 title 不变 |
| `OpenCodeEventLoopIT` | WireMock `/event` 推 7 种 session.* 原始 frame → 断言 `SessionBus.publish` 各自被调一次；`Unknown` 不触发 publish |
| Flyway 迁移测试 | 现有 `MigrationTest`（若存在）覆盖 V4；否则加 startup 断言 `title_locked` 列存在且默认 0 |

### 5.3 前端

| 文件 | 用例 |
|---|---|
| `use-channel.test.ts` | mock SSE 推 `session.meta.updated` → 断言 `queryClient.invalidateQueries` 被调一次 |
| `chat-header.test.tsx` | mock `useSessions` 依次返回初始 title = `"新会话"`、新 title = `"查询订单今日销量"` → 组件文本更新 |

### 5.4 手动 E2E（非 CI）

1. 启动真实 OpenCode（`../opencode`）
2. `mvn spring-boot:run` + `npm run tauri dev`
3. 新建连接 + 会话，输入"查询今日订单销量"
4. 观察 ChatHeader：回复开始时 title 仍是 `"新会话"`，几秒后变成 OpenCode 生成的智能标题
5. 点 ChatHeader 的"重命名"改成 `"我的查询"`，再发一条消息 → title 保持 `"我的查询"`

### 5.5 不测

- OpenCode 实际 title 生成质量 / 语言 / 长度（黑盒）
- 重连中断间隙的事件补齐（设计上接受最终一致）

---

## 6. 遗留技术债

本 plan 会定义 7 个 session.* 事件的完整翻译链（OcEvent → DtEvent），但除 `session.updated` 外，其余 6 个**只定义不消费**（Translator 翻译、SessionBus 发布，但前端 sink 不处理）。Plan 执行完成时需按以下清单登记到 `docs/exec-plans/tech-debt-tracker.md`：

| 建议 ID | 优先级 | 模块 | 描述 | 拟定消费场景 |
|---|---|---|---|---|
| TD-013 | P1 | adapter / client | `DtEvent.SessionIdle` 定义但未消费；`ChannelController.java:110-121` 仍用 1000ms 恩典期关流 | 替换恩典期为"收到 SessionIdle 再关流"（Task 23） |
| TD-014 | P2 | client | `DtEvent.SessionError` 定义但未消费 | 前端 toast 展示（取代本地 try/catch 的 sendMessage 错误路径） |
| TD-015 | P2 | client | `DtEvent.SessionCreated / SessionDeleted` 定义但未消费 | 跨客户端场景：A 建/删 session，B 自动刷新侧边栏 |
| TD-016 | P2 | client | `DtEvent.SessionCompacted` 定义但未消费 | OpenCode 压缩上下文时给 UI 角标提示 |
| TD-017 | P2 | client | `DtEvent.SessionDiff` 定义但未消费 | OpenCode 此事件语义需先调研 payload；消费方案后续专题 |

**现在就登记的理由**：避免"事件定义了但没人用"的孤儿代码堆积无人追踪。

---

## 7. Out of Scope

- OpenCode session 事件家族之外的其他事件扩展（message / tool / todo / shell / tui 事件）
- Task 23（流生命周期）实现 — 本 plan 只定义 `SessionIdle` event 翻译，不改 `ChannelController` 流关闭逻辑
- OpenCode session title 生成策略（由 OpenCode 决定，我们只订阅结果）
- 多客户端协作场景（别的客户端建/删 session 时本客户端的响应）
- "AI 命名"徽章 UI（`titleLocked` 字段已透传，UI 留作后续）
