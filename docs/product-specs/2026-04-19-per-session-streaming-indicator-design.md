# Per-Session Streaming Indicator — Design

**Date:** 2026-04-19
**Author:** wallfacers
**Status:** Approved

## 1. 背景与目标

AI 消息渲染迁移完工后对多会话并发的评估发现：**数据层、SSE 流本身、渲染层都已按 sessionId 分片隔离**——Session A 生成中时切到 Session B，A 的 `streamingPost` 是一条 POST-SSE 长连接，没绑 `AbortSignal`，sink 闭包的 sessionId 也写对了 store 的 A 分片；切到 B 能独立滚动 / 发新消息；切回 A，新 parts 已经到位。

唯一的真实缺陷：**`useChannel().isStreaming` 是 hook 内 `useState`，而 `PromptComposer` 全局只渲染一份**。"Session A 是否还在跑"这个状态跟着组件实例走，切到 B 后被覆盖；切回 A 时看不到 "还在跑"（spinner / 停止按钮 / 发送禁用），视觉上会误以为任务已结束。

**目标**：让 composer 的 "正在跑" 指示**按 sessionId** 表达。回到 A 时，若 A 的 AI 流仍在后台运行，composer 继续显示 spinner + 停止按钮。

**非目标**：
- 不改 SSE 订阅池（方案 B：切走 > 30s 的事件保底）— 登记为 tech debt
- 不给 `streamingPost` 加 `AbortSignal`（当前"切走不断连"正是想要的行为）
- 不引入全局 ChannelManager（方案 C，YAGNI）

## 2. 决策参数

| # | 决策点 | 值 |
|---|---|---|
| Q1 | `isStreaming` 承载位置 | `chat-parts-store` 加 `streamingBySession: Set<string>`（和现有 sessionId 分片自然同构） |
| Q2 | `useChannel().isStreaming` 语义 | 当前 `activeSessionId` 是否在跑；`activeSessionId=null` 时恒为 `false` |
| Q3 | 写入时机 | `sendMessage` / `retryPendingUser` 的 `try...finally`：start 时 `setStreaming(sid, true)`，finally `setStreaming(sid, false)` |
| Q4 | 卸载清理 | 不在 hook unmount 时清，流本身是"进程级"的（POST-SSE 跨组件生命周期存在）；正常由 finally 收尾。崩溃路径（未触达 finally）靠下次 send 覆盖 |

## 3. 架构与数据流

```
Session A                                     Session B
  │                                                │
  ├─ sendMessage()                                 │
  │     setStreaming(A, true)                      │
  │     POST /sessions/A/channel  ── SSE ─┐        │
  │                                       │        │
[用户切到 B：activeSessionId=B]            │        │
  │                                       │        │
  │  useChannel().isStreaming             │        │
  │    = streamingBySession.has(B) = false ← composer 显示发送按钮
  │                                       │        ├─ sendMessage()
  │                                       │        │     setStreaming(B, true)
  │                                       │        │     POST /sessions/B/channel ── SSE ─┐
  │                                       │        │                                      │
[用户切回 A：activeSessionId=A]            │        │                                      │
  │                                       │        │                                      │
  │  useChannel().isStreaming             │        │                                      │
  │    = streamingBySession.has(A) = true ← composer 显示 spinner + 停止按钮
  │                                       │        │                                      │
  │  ← message.part.delta events ─────────┘        │                                      │
  │     sink 写 store.partsBySession[A]            │                                      │
  │                                                │                                      │
  │  [A 流完成] setStreaming(A, false)             │   [B 流完成] setStreaming(B, false)──┘
```

**不变量**：`streamingBySession` 严格反映"POST-SSE 流是否还在读取"。任何时刻 `UI.composer.isStreaming === streamingBySession.has(activeSessionId) || (activeSessionId === null ? false : ...)`。

## 4. 组件拆分

### 4.1 `client/src/stores/chat-parts-store.ts`

新增状态 + 2 个方法：

```typescript
streamingBySession: Set<string>
setStreaming: (sessionId: string, on: boolean) => void
isStreaming: (sessionId: string | null) => boolean
```

实现（草图）：
```typescript
streamingBySession: new Set<string>(),

setStreaming: (sessionId, on) => set((s) => {
  const next = new Set(s.streamingBySession)
  if (on) next.add(sessionId); else next.delete(sessionId)
  return { streamingBySession: next }
}),

isStreaming: (sessionId) => {
  if (!sessionId) return false
  return get().streamingBySession.has(sessionId)
},
```

`clearSession(sessionId)` 顺带删 `streamingBySession` 项（避免残留）。

### 4.2 `client/src/services/channel/use-channel.ts`

删掉本地 `useState(isStreaming)`，改成 store select：

```typescript
const sessionId = useSessionStore((s) => s.activeSessionId)
const isStreaming = useChatPartsStore((s) =>
  sessionId ? s.streamingBySession.has(sessionId) : false
)
```

`sendMessage` / `retryPendingUser` 把 `setIsStreaming(true/false)` 改为 `setStreaming(sid, true/false)`：

```typescript
const sendMessage = useCallback(async (parts) => {
  if (!client || !sessionId) return
  const pendingId = useChatPartsStore.getState().upsertPendingUser(sessionId, pendingText)
  const setStreaming = useChatPartsStore.getState().setStreaming

  setStreaming(sessionId, true)
  enterSplit(sessionId)
  const sink = buildEventSink(sessionId, client, queryClient, connectionId, pendingId)
  try {
    await client.sendMessage(parts, sink)
  } catch (err) {
    // ... 已有错误处理
  } finally {
    setStreaming(sessionId, false)
  }
}, [client, sessionId, enterSplit, queryClient, connectionId])
```

关键点：`sessionId` 已经在 `useCallback` 闭包里，切走切回 A 时 finally 用的仍是 A 的 sid（和 sink 写入一致）。

### 4.3 测试

#### `client/src/stores/chat-parts-store.test.ts`（新增 4 case）

1. `setStreaming_turnsOn`: `setStreaming('A', true)` → `streamingBySession.has('A') === true`
2. `setStreaming_turnsOff`: 打开后再关 → `has('A') === false`
3. `setStreaming_independentAcrossSessions`: A/B 同时打开 → 两个都 true，关 A 不影响 B
4. `clearSession_removesStreamingEntry`: streamingBySession 有 A 时 `clearSession('A')` → has('A') === false

#### `client/src/services/channel/use-channel.test.ts`（新增 2 case）

1. `isStreaming_reflectsActiveSession`: mock `activeSessionId` 为 A，store `setStreaming('A', true)` → `useChannel().isStreaming === true`；切 `activeSessionId` 为 B → `isStreaming === false`；`setStreaming('B', true)` → `isStreaming === true`
2. `isStreaming_nullActiveSession_alwaysFalse`: `activeSessionId=null` 时无论 store 状态如何，返回 false

## 5. 错误处理与边界

| 场景 | 行为 |
|---|---|
| sendMessage 抛异常（网络/RPC 失败） | `finally` 跑 `setStreaming(sid, false)`；`markPendingUserFailed` 照常 |
| 同一 session 二次 send（不该发生，但兜底） | 第二次 `setStreaming(sid, true)` 幂等；两个流的 finally 都会 setFalse，最后状态正确（以最后完成者为准） |
| 组件卸载（切 session 不卸载，PromptComposer 是常驻 portal） | store 状态不受影响；后端流继续，finally 照常跑 |
| 整个页面刷新 | store 重置（非 persisted），streamingBySession 归空。后端流也因 fetch 断开被服务端感知 |
| `activeSessionId` 切到不存在的 session | `isStreaming` 读 `streamingBySession.has(invalid) === false`，UI 正常 |

## 6. 验收

### 自动（CI）

- `chat-parts-store.test.ts` 新增 4 case 通过
- `use-channel.test.ts` 新增 2 case 通过
- `npx tsc --noEmit` 零 error
- `npm run lint` 零新增 warning

### 手动（产品验证）

- **M1**：Session A 发消息后立即切到 B → B 的 composer 显示发送按钮（可用）；切回 A → composer 显示 spinner + 停止按钮（直到 A 流结束）
- **M2**：A 生成中切 B，B 也发消息，两个都在跑 → 分别看到各自正确的 composer 状态
- **M3**：A 生成完成时若正位于 B，B 的 composer 状态不受影响；回到 A → 显示发送按钮
- **M4**：A 生成中点停止按钮 → `abort()` 发 RPC，finally 跑 setStreaming(A, false)；composer 恢复发送按钮

## 7. 技术债

`docs/exec-plans/tech-debt-tracker.md` 登记一条 P2：

> **TD-MULTI-SESSION-SSE-POOL** (P2)：后台 session 的 GET 订阅（`useSessionSubscribe`）当前仅跟随 `activeSessionId`；切到其他 session 超过 30s（后端 bus eviction grace）+ ring buffer 溢出（500 条 / 5min TTL）时，外部主动推送事件（ontology.updated / session.meta.updated）或长时断网重连可能丢。真实触发率低，暂不改造；若未来出现用户反馈"回到老 session 看不到更新工件"，按订阅池方案重做 `useSessionSubscribe`（pool of subscribed sessionIds + 生命周期管理）。

## 8. 文档范围

- 本 spec：`docs/product-specs/2026-04-19-per-session-streaming-indicator-design.md`
- 登记：`docs/product-specs/index.md` §8
- 执行计划：`docs/exec-plans/2026-04-19-per-session-streaming-indicator-plan.md`（由 writing-plans 产出）
- 索引：`docs/exec-plans/index.md` Active → 完工移 Completed
- 技术债：`docs/exec-plans/tech-debt-tracker.md` 加 TD-MULTI-SESSION-SSE-POOL
- 不涉及：`CLAUDE.md` / `ARCHITECTURE.md` / 后端 / schema / migrations

## 9. 不变/不碰

- 后端 `SessionBusRegistry` / `SessionBus` / `EventRepository` 逻辑不变
- `ChannelClient` 逻辑不变（`streamingPost` 仍不绑 `AbortSignal`，这是功能的一部分）
- `useSessionSubscribe` 不变（GET 订阅仍随 active 切换）
- `buildEventSink` 不变（sink 按闭包 sessionId 写 store，保持数据分片）
- Zustand persist / SSR 行为：store 非 persisted，刷新即重置——符合预期
