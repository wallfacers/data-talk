## Context

DataTalk 前端使用 5 层防御保证 prompt-composer 的发送/停止按钮在 Ctrl+R 后正确显示：

```
L1 event-id dedupe      use-channel.ts:315-319    丢弃 id ≤ prev 的旧帧
L2 500ms wall-clock     use-channel.ts:372,428    L1 失效兜底（id=0 或 prev=0）
L3 cursor sync write    channel-store.ts:21-52    lastEventIdBySession 同步落盘
L4 streaming sync write chat-parts-store.ts:287   streamingBySession 同步落盘
L5 history skip-guard   use-session-history.ts:18 streaming 时禁止 history replace
```

L3 / L4 由 [BUG-0037](../../../docs/bugs/BUG-0037-ctrl-r-during-streaming-flips-stop-button-to-send.md) 引入，L1 / L2 由 [BUG-0038](../../../docs/bugs/BUG-0038-replay-idle-on-resubscribe-clears-streaming-flag.md) 引入。两次 BUG 都把焦点放在 **SSE 重放路径** 上 —— 后端 GET /subscribe 从 cursor 0 重放历史 `SessionIdle` / `SessionStatus(idle)`，前端误清 streaming。

但用户报告显示，在 BUG-0037 + BUG-0038 修复之后，该现象仍可稳定复现。本设计追踪到第三条路径：**不是重放事件，而是 POST send_message 的 finally 块在 Ctrl+R 触发的 fetch abort 之后同步清掉了 streaming**。

### 后端事实确认（读 ChannelController.java + SessionBus.java）

| 事实 | 行号 | 含义 |
|------|------|------|
| GET 默认 cursor 行为 | `ChannelController:102` | `bus.subscribe(clientId, lastEventId == null ? 0L : lastEventId, sub)` —— 无 header 则 cursor=0 重放 |
| POST 默认 cursor 行为 | `ChannelController:159` | `bus.subscribe(clientId, lastEventId == null ? cursor : lastEventId, sub)` —— BUG-0038 已修，POST 从当前 cursor 起 |
| POST turn 收尾补发 idle | `ChannelController:177` | `bus.publish(new DtEvent.SessionStatus("idle", Map.of()))` —— **仅在 `!clientGone && !sub.isBroken()` 时执行**；Ctrl+R 触发 onDisconnect → clientGone=true，**此路径下不补发 idle** |
| 事件 id 分配 | `SessionBus:110` | `long id = seq.incrementAndGet()` —— **所有经 bus 流出的事件 id ≥ 1**，不可能为 0 |
| Connected 帧 | `ChannelController:101,152` | `bus.publish(new DtEvent.Connected(...))` 走 bus，有 id |

**重要结论**：早期 explore 阶段提出的"事件 id=0 导致 L1 失效"假设**不成立**。SessionBus 给每个出 bus 的事件分配 id。L1 不会因 id=0 漏过 idle 帧。

### 前端事实确认（读 use-channel.ts + channel-client.ts）

`sendMessage` 当前实现（use-channel.ts:507-535）：

```ts
const sendMessage = useCallback(async (parts) => {
  if (!client || !sessionId) return false
  const pendingId = useChatPartsStore.getState().upsertPendingUser(sessionId, pendingText)
  useChatPartsStore.getState().setStreaming(sessionId, true)   // ✓ sync write [sessionId]
  enterSplit(sessionId)
  markSessionSent(sessionId)
  invalidateSessionLists(queryClient)
  const sink = buildEventSink(sessionId, client, queryClient, connectionId, pendingId)
  try {
    await client.sendMessage(parts, sink)
    return true
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err)
    useChatPartsStore.getState().markPendingUserFailed(sessionId, pendingId, msg)
    showErrorToast(normalizeError(err))
    return false
  } finally {
    useChatPartsStore.getState().setStreaming(sessionId, false)  // ❌ sync write []
  }
}, ...)
```

`retryPendingUser`（同文件 537-563）拥有同样的 finally 结构。

`ChannelClient.subscribe` 已发送 `Last-Event-ID` header（channel-client.ts:68），且后端正确响应（ChannelController:91-102）。L1/L2/L3 都到位。

### 真实根因

```
T0    用户按发送
      setStreaming(true)       → sync write streamingBySession=[sessionId] ✓
      POST /channel 发起        → fetch + reader.read() 循环

T+200ms  SSE 收到 message.created (id=5), part.delta (id=6,7,8,...)
      L1 更新 lastEventIdBySession[sessionId] = 8（同步落盘）

T+300ms  用户按 Ctrl+R
      浏览器开始 navigation；fetch AbortController abort
      reader.read() reject with AbortError
      consumeSseStream throws
      sendMessage's await rejects

T+301ms  catch 块运行 → markPendingUserFailed (副作用 1)
         finally 块运行 → setStreaming(false) → sync write streamingBySession=[]
         注意：sessionStorage 是同步的，写入立即落盘

T+302ms  页面 unload；JS 上下文销毁
T+~ms    页面 reload
         Zustand hydrate streamingBySession=Set([]) ← 来自 T+301ms 写入
         Composer 渲染 ArrowUpIcon（发送态）❌
         GET /subscribe 启动 with Last-Event-ID: 8
         backend 重放 id>8 的事件（part.delta 9, 10, ...）—— 但已经太晚

T+若干秒后  AI 真正完成 turn，session.idle 到达 GET sink
            buildEventSink → setStreaming(false) —— 实际上是 no-op，已经是 false
            （按钮在此之前一直显示发送态，状态被永久错位直到下一次发送）
```

**这条路径在 BUG-0037 / BUG-0038 的修复之前可能被 Zustand async flush 慢掩盖** —— `setStreaming(false)` 进的是异步队列，可能在 unload 前没来得及刷盘，于是 `streamingBySession=[sessionId]` 反而幸存。BUG-0037 的同步写修复让这条路径变成了稳定可复现的 bug。

## Goals / Non-Goals

**Goals:**
1. Ctrl+R 触发的 fetch abort **不得**导致 `streamingBySession[sessionId]` 被清。
2. 真实 turn 结束（`session.idle` / `session.error`）**必须**清 `streamingBySession`。
3. POST 请求在 SSE 流打开之前失败（网络层错误、5xx、TLS）**必须**清 `streamingBySession`，否则按钮永久卡在停止态。
4. 不破坏既有 L1–L5 防御，不回退 BUG-0037 / BUG-0038 修复。
5. 与 retryPendingUser 一致处理（同样的 finally 模式）。

**Non-Goals:**
- 不修改后端 SSE / SessionBus / ChannelController 协议。
- 不修改 GET /subscribe 的 cursor 重放语义（保持 BUG-0038 的 500ms 窗口 + cursor 同步写双保险）。
- 不引入新的状态字段或 store。
- 不修改 UI 视觉/token，纯状态机修复。
- 不解决"长寿 GET sink 缺席时谁清 streaming"的边界（见 Open Questions）。

## Decisions

### Decision 1：streaming 清理与请求生命周期解耦

把"POST 请求结束"与"turn 结束"区分开 —— 后者由 SSE 事件 (`session.idle` / `session.status=idle` / `session.error`) 决定，前者只在**请求从未变成 SSE 流**时才参与清理。

**Rationale:**
- 长寿 GET /subscribe 已经在订阅同一个 SessionBus；session.idle / error 一定会到达 GET sink。
- POST sink 和 GET sink 共用 `buildEventSink`，都能调用 `setStreaming(false)`，幂等。
- Ctrl+R 抹掉 POST sink 后，GET sink 仍在工作（页面 reload 后会重新挂起）；reload 后 GET 会通过 Last-Event-ID 续接，最终拿到 idle。

### Decision 2：识别"SSE 流是否已打开"的实现方式

候选三种：

| 方案 | 利 | 弊 |
|------|----|----|
| **A. 局部状态标志位** —— 用 `streamOpened` 闭包变量；通过 `sink` 触发首次回调时翻为 true | 实现直接，无 API 改动 | 需要把信号点放在 sink 里（用 `Connected` 帧作分界），事件顺序耦合 |
| **B. 改造 ChannelClient.sendMessage 返回值** —— 把"流已打开"做成独立 promise | API 更清晰 | 改 channel-client.ts，回归面变大 |
| **C. 区分 AbortError 与其他错误** —— catch 块按 err 类型决定是否清 streaming | 不需要新状态 | DOMException AbortError 在不同浏览器/平台名称略有差异；Tauri WebView 行为需验证 |

**选择 A**。理由：
- 仅 1 个闭包变量，最小侵入。
- 不依赖 AbortError 命名（跨平台稳定）。
- 与 sink 同生命周期，sink 已经接收 `Connected` 帧作为流开始的可靠信号 —— Connected 由后端 `ChannelController:152` 在订阅成功后立即 publish（POST 路径）/ line 101（GET 路径），是 SSE 流的"开盘信号"。

#### A 的具体形态

```ts
const sendMessage = useCallback(async (parts) => {
  // ... 前面不变
  let streamOpened = false
  const guardedSink: typeof sink = (evt) => {
    if (!streamOpened && evt.event === 'connected') streamOpened = true
    sink(evt)
  }
  try {
    await client.sendMessage(parts, guardedSink)
    return true
  } catch (err) {
    if (!streamOpened) {
      // pre-stream failure: 没有任何 SSE 事件到达过，turn 在后端从未开始
      useChatPartsStore.getState().setStreaming(sessionId, false)
    }
    // streamOpened === true: 让 SSE 事件流自己清理（POST sink 或 GET sink 都行）
    useChatPartsStore.getState().markPendingUserFailed(sessionId, pendingId, msg)
    showErrorToast(normalizeError(err))
    return false
  }
  // 注意：没有 finally —— 成功路径下 streaming 由 session.idle 事件清理
}, ...)
```

**为什么用 `connected` 作分界？**
- `Connected` 帧在 `ChannelController:152`（POST）/ `ChannelController:101`（GET）通过 `bus.publish(new DtEvent.Connected(...))` 推送，是订阅成功后第一个出 bus 的事件。
- `SseEmitterSubscriber` 构造时也接受 "connected" 字符串作为初始字段（line 97, 124），表明这是协议层定义的"开盘信号"。
- buildEventSink 当前不处理 `connected` 事件分支（它走 else 隐式忽略），加 guard 不破坏既有逻辑。

### Decision 3：成功路径不需要 finally 兜底

`await client.sendMessage(parts, sink)` 自然完成意味着：
- 后端 POST 线程把 `emitter.complete()` 调用了（`ChannelController:182`）
- 这意味着 `turnDone.countDown()` 触发 或 `clientGone == true` 或超时
- `turnDone` 由 `isTurnDoneSignal` watcher 监听（`ChannelController:196-200`：SessionIdle / SessionError / SessionStatus("idle")）
- 这些事件**已经**通过 sink 路径调用过 setStreaming(false)

所以成功路径下，setStreaming(false) 在 await 返回之前就已经发生了。**finally 是冗余的**。

边界：如果 POST 流因超时（`postStreamTimeoutMs=600000`，10 分钟）自然结束但 OpenCode 没发 idle，前端 await 也会返回。此时 streaming 仍为 true。**接受这个边界** —— 因为：
1. 长寿 GET sink 仍会收到后续 idle/error；
2. 10 分钟无响应已是异常态，UI 卡停止态可以促使用户手动 abort（abort() 内已 setStreaming(false)）。

### Decision 4：retryPendingUser 同步修改

retryPendingUser 是相同结构（同文件 537-563），同步应用 streamOpened 模式。

### Decision 5：测试策略

新增 `client/src/services/channel/use-channel.test.ts` 套件 "BUG-0046 stream-lifecycle vs request-lifecycle"：

1. **Pre-stream failure clears streaming**：mock `client.sendMessage` 在 `connected` 帧发出之前 throw → 验证 setStreaming(false) 被调用。
2. **Post-stream abort does NOT clear streaming**：mock 发出 connected 帧 → 然后 throw AbortError → 验证 setStreaming **未被调用**清 false（streamingBySession 仍含 sessionId）。
3. **Normal turn completion clears streaming via SSE**：mock 发出 connected → 然后 session.idle → 验证 streamingBySession 被 SSE 路径清。
4. **retryPendingUser 镜像三个用例**。

保留既有 BUG-0037 / 0038 用例不动；本变更测试与之正交。

### Decision 6：观察性 / 日志

不加日志。事件流可以通过 Tauri devtools 直接观察；加日志会污染生产输出。

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| **GET sink 未启动时按钮卡死**：useSessionSubscribe 在某种情况下未挂载（路由竞态、subscribedSessions 重复守卫），且 POST sink 被中断，streaming 永远不会被清 | 该路径**已存在**于当前代码，非本变更引入。在 design 的 Open Questions 中记录；后续若复现单独立 BUG |
| **依赖 connected 事件名约定**：未来后端如重命名 connected 帧，guard 将失效 | 加测试断言 `connected` 是 stream-opened 信号；后端协议变更会触发测试失败 |
| **Tauri WebView 在 unload 时的 sessionStorage 写入语义**：不同 webview 实现可能行为不一致 | 本修复是降级写入 —— 不再写入 false，而非依赖写入；与 webview 行为无关 |
| **AbortError 误归类为 pre-stream failure**：若 fetch 在 connected 之前就被 abort（极快的 Ctrl+R），会落入 catch + `!streamOpened` 分支，setStreaming(false) 被调用 | 极小概率场景；按本设计在该场景下确实应清 streaming —— 因为后端从未真正开始一个 turn（订阅都没成功），消息可能根本未送达 OpenCode。回避策略：用户感知是"按了发送马上刷新"，按钮变发送态是合理结果 |
| **长寿 GET sink 重放 idle 误清**：与 BUG-0038 同一路径，BUG-0038 已用 500ms 窗口防御 | 不变 |

## Migration Plan

- 纯前端修复，无后端协议变更，无数据库 schema 变更，无配置变更。
- 部署一次发版即可生效。
- 回滚：恢复 use-channel.ts 修改即可，无遗留状态。
- 不需要 Flyway 迁移。
- 不需要 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` 更新。

## Open Questions

1. **长寿 GET sink 启动失败时谁清 streaming？**
   `useSessionSubscribe` 用模块级 `subscribedSessions: Set<string>` 守卫去重（use-session-subscribe.ts:7）。该 set 在页面 reload 时清空（模块重加载），但 StrictMode 双调用或异常路径下可能产生悬挂状态。本变更不解决该问题，记入后续观察。
2. **POST send_message 在 Ctrl+R 时，client.sendMessage 的 promise 实际是 resolve 还是 reject？**
   假设：reject with AbortError。已用以下 test scaffolding 验证：用 `MockResponse` 的 `body.getReader().read()` 抛错触发 consumeSseStream 抛出 → sendMessage promise reject。如果实际 webview 是 resolve 路径（提前 EOF），catch 块不会触发，finally 移除导致 setStreaming(false) 不调用，仍是正确结果（GET sink 兜底）。两条路径都安全。
3. **是否需要把 `streamOpened` 模式提取为可复用工具？**
   仅 sendMessage + retryPendingUser 两处使用，提取收益小。保持就地实现。
