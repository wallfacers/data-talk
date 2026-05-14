## Context

OpenCode 服务端在 `SessionStatus` 命名空间（`opencode/packages/opencode/src/session/status.ts`）维护一个 in-memory `Map<sessionID, {type: "idle" | "busy" | "retry"}>`，对外暴露：

- `GET /session/status` → `Record<sessionID, SessionStatus.Info>`（idle 不入 map，被 `set()` 主动 delete）。

DataTalk 后端目前在 `OpenCodeEventLoop.parseOcEvent` 已经 parse 出 `OcEvent.SessionStatus` 事件，但 `extractSessionId(SessionStatus)` 返回 null（line 410），等于把状态事件丢弃。换句话说：服务端权威状态从未被 DataTalk 后端转发或持久化，前端只能依赖自己持久化 `streamingBySession` 来跨刷新维持按钮态，而那套机制就是 [[BUG-0046]] 的根源。

历史 4 层防御链：
- L1 event-id 语义去重（`buildEventSink` use-channel.ts:315-319）
- L2 500ms wall-clock 重放抑制（[[BUG-0038]]）
- L3 `useChannelStore.setLastEventId` 同步写 sessionStorage（[[BUG-0038]]）
- L4 `useChatPartsStore.setStreaming` 同步写 sessionStorage（[[BUG-0037]]）
- L5 `useSessionHistory.shouldSkipReplace` streaming 时阻塞 history replace

L4 是 [[BUG-0046]] 的污染点：`sendMessage` 的 `finally { setStreaming(false) }` 在 fetch abort（CTRL+R）时同步执行，把 false 同步写入 sessionStorage，rehydrate 时按钮变发送态。

## Goals / Non-Goals

**Goals**：

1. composer 按钮形态以服务端 OpenCode `SessionStatus` 为单一权威源，刷新场景下与后端 turn 真实状态一致。
2. 完全移除 L4（streaming 持久化），让 L5 `shouldSkipReplace` 在 mount 阶段总能正确判断（默认空集 → 不阻塞 history replace）。
3. 保留 L1/L2/L3 作为事件流防御层，不动 [[BUG-0038]] 的 500ms 窗口。
4. 失败兜底：OpenCode 不可达 / session 未映射时 fail-open 返回 idle，不引入新的卡死风险。

**Non-Goals**：

1. 不重写 ChannelController 的 SSE 协议（不在 subscribe 流首帧注入 status —— 协议复杂度收益不对等）。
2. 不修 `OpenCodeEventLoop.extractSessionId(SessionStatus)` 的 null 路由（独立 follow-up）。
3. 不替 OpenCode `retry` 状态做特化 UX；按钮态仍是二态（idle / streaming-stop），retry 在前端等价于 streaming-stop。
4. 不引入 React Query / TanStack Query 的 polling；mount 时一次性读取，后续仍靠 SSE 事件流维护实时性。

## Decisions

### D1 — 权威源：HTTP `GET /api/sessions/{id}/status` vs SSE 首帧注入

**选择**：HTTP one-shot reconcile。

**理由**：
- HTTP 调用与 fetch history 平行，时序清晰：`mount → fetchHistory + fetchStatus（并发）→ reconcile streamingBySession → subscribe SSE`。
- SSE 首帧注入需要修改 `ChannelController.subscribe` 协议（额外的"现状快照"帧类型），影响面大、回归风险高。
- HTTP 调用失败时（OpenCode 短暂不可达）按钮维持当前态（默认 false → 发送），与 BUG-0046 现状相比"刷新后看到一次发送→很快回到停止"是可接受的回归路径。

**替代方案**：在 ChannelController.subscribe 启动时立即 `bus.publish(new DtEvent.SessionStatus("busy"|"idle"))` —— 协议侵入大，且需要重新审视 [[BUG-0038]] 500ms 窗口与"权威首帧"的交互。留作 follow-up。

### D2 — 透传 OpenCode `/session/status` 而非维护后端 snapshot

**选择**：后端 controller 每次请求都现拉 OpenCode，不在 DataTalk 内 cache。

**理由**：
- OpenCode 状态是 in-memory 单实例真值，后端额外缓存会引入"双写不一致"（重启 / 重连 / OpenCode 自身 set/delete 时序）。
- 调用频率低：每次 session mount 1 次，后续靠 SSE 维持。OpenCode HTTP 调用 <50ms。
- DataTalk 后端职责只是 ID 映射 + ERROR fallback。

**替代方案**：在 `OpenCodeEventLoop` 维护 `Map<dtSessionId, status>`，通过订阅事件维护。优点是无外部依赖、性能更优；缺点是要先修 `extractSessionId(SessionStatus)` 路由 bug 才能拿到 sessionID，复杂度增加。

### D3 — fail-open 策略

**选择**：OpenCode 不可达 / `OpenCodeSessionMap.openCodeFor()` 返回 null / `/session/status` 返回 404 时，DataTalk 接口返回 `{type: "idle"}`。

**理由**：
- "刷新后看到发送态"是用户已有体验，回退到 BUG-0046 现状属于幂等回归而非新坏退化。
- 若选 fail-closed（返回 503 让前端不动 streaming 状态），刷新场景下 streaming 默认空集 → 按钮显示发送 → 与 fail-open 等价；多出错误码处理负担。

### D4 — 前端清理 L4：删除 `streamingBySession` 持久化

**选择**：彻底从 `chat-parts-store.ts` 的 `partialize` / `merge` 中移除 `streamingBySession`；删除 `setStreaming` 中的同步 sessionStorage 写代码块（line 298-312）。

**理由**：
- L4 是 [[BUG-0046]] 的污染点。
- 删除后 [[BUG-0037]] 的"刷新后按钮翻回发送态"的解决路径从"L4 持久化"换为"D1 服务端 reconcile"，行为等价。
- 删除后 `shouldSkipReplace` 在 mount 阶段总能拿到空 streamingBySession，[[BUG-0044]]（历史加载守卫）路径不受影响。

**风险**：[[BUG-0037]] 的回归 —— 如果 status fetch 在 SSE 第一个事件之前没完成，按钮会短暂 flicker。Mitigation 见 R1。

### D5 — fetchStatus 时机：在 `useSessionSubscribe` 内还是独立 hook

**选择**：在 `useSessionSubscribe` 内，subscribe 之前 await。

**理由**：
- subscribe 是 streaming 真值的"新数据源"，先 reconcile 旧状态再接事件流，逻辑闭环。
- await 的总延迟 ≤ history 加载延迟，体感无差异。
- 独立 hook 会拆出第三个 mount 触发点，与 `useSessionHistory` / `useSessionSubscribe` 的并发关系增加复杂度。

**实现细节**：
```ts
useEffect(() => {
  if (!client || !sessionId) return
  if (subscribedSessions.has(sessionId)) return
  subscribedSessions.add(sessionId)
  let cancelled = false
  void (async () => {
    try {
      const status = await fetchSessionStatus(sessionId)
      if (cancelled) return
      if (status.type === 'busy' || status.type === 'retry') {
        useChatPartsStore.getState().setStreaming(sessionId, true)
      }
    } catch { /* fail-open: 按钮当前态保持 */ }
    if (cancelled) return
    const sink = buildEventSink(...)
    const unsub = client.subscribe(resumeFrom, sink)
    // ...
  })()
  return () => { cancelled = true; ... }
}, [...])
```

## Risks / Trade-offs

**R1**：status fetch 与 SSE 重放窗口竞速 —— 如果服务端 idle 重放帧先于 status 响应到达 sink，L2 500ms 抑制窗口仍生效（subscribeAt = Date.now()），其在窗口内会 return 不动 streaming。窗口外则会 setStreaming(false) —— 但此时 status fetch 应已完成并写入 true，setStreaming(false) 是合法清空（真的 idle 了）。**结论：无新增 race**。

**R2**：fetchStatus 慢（OpenCode 阻塞）时按钮 flicker —— mount 时按钮先显示发送（默认 false），fetchStatus 返回 busy 后变成停止。可接受，最差等同于 BUG-0046 现状。Mitigation：把 fetchStatus 并发到 subscribe 之前（已设计），延迟 < 100ms。

**R3**：OpenCode 进程崩溃 / 重启导致状态丢失（OpenCode 自己重启后 SessionStatus.list() 为空，所有 session 都返回 idle）—— DataTalk 接口透传，前端按钮翻发送。但此时真的没在 streaming 了，所以行为正确。

**R4**：`OpenCodeSessionMap` 未 bind（首次 session 在 client 发第一条消息之前刷新）—— 此时 dataTalkSessionId 没有对应 OpenCode session。controller fail-open 返回 idle，正确。

**R5**：网络分区导致 status fetch hang —— `WebClient.block()` 默认无超时。Mitigation：在 controller 加 2s timeout（Reactor `timeout(Duration.ofSeconds(2))`），timeout 视为 idle。

## Migration Plan

1. **后端先行**：先实现 `OpenCodeHttpClient.getSessionStatuses()` + controller + 测试，部署后接口可用但前端未消费，无行为变化。
2. **前端跟进**：实现 `fetchSessionStatus` + `useSessionSubscribe` 接入 + 移除 `streamingBySession` 持久化 + 测试。
3. **同 PR 提交**：因为前端移除持久化依赖了后端接口的存在；分阶段会留下"已删除持久化但未接服务端"的窗口期。

**回滚策略**：单 commit 内全部变更，回滚 = `git revert <commit>`。回滚后回到 BUG-0046 现状（已有 5 层防御原样运行）。

## Open Questions

- 暂无。OpenCode `/session/status` 协议稳定（v1.4.7 已锁），DataTalk 后端 `OpenCodeSessionMap` 已是稳态。
