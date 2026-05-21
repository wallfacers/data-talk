---
id: BUG-0038
title: CTRL+R 后 GET /subscribe 重放历史 session.idle 立刻清空 streamingBySession（BUG-0037 残留路径）
status: fixed
priority: P1
source: manual-report
modules: [session, channel, chat]
discovered: 2026-05-13
discoveredBy: human
testRunId: null
fixCommit: eb034bb3
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

BUG-0037 已让 `streamingBySession` 在 sessionStorage 同步持久化，CTRL+R 后 hydrate 能恢复 `Set([sessionId])`，按钮初渲染应为转圈停止态。但用户仍 100% 稳定复现：streaming 期间 CTRL+R 后按钮立刻翻回"待发送"。

根因不在 store 层，而在重连路径：CTRL+R 触发的新 `GET /api/sessions/{id}/channel` 订阅，在 `Last-Event-ID` header 缺失或异步 flush 未跟上时，后端 `ChannelController:102` 从 cursor 0 重放整个 `SessionBus` 缓冲区，其中通常已包含上一轮收尾的 `DtEvent.SessionIdle`（OpenCode 原生事件）和 `DtEvent.SessionStatus("idle")`（line 177 主动补发的 legacy frame）。客户端 `buildEventSink` 无条件 `setStreaming(false)`，按钮立刻翻回发送态。

## Reproduction Steps

1. 任一 session 中按发送，AI 进入流式输出（按钮处于转圈停止状态）。
2. 在 streaming 过程中按 CTRL+R 刷新页面。
3. 页面重新渲染后立刻观察按钮形态。

## Expected vs Actual

- **Expected**：按钮保持转圈停止态，因为后端 turn 可能仍在跑或刚收尾，SSE 重订阅后只应让"真正新发生的"完成事件清空 streaming。
- **Actual**：按钮立刻翻回发送态，因为后端重放了缓冲区里旧的 session.idle / session.status=idle。

## Environment

- Backend commit: 640c03e1（修复前 HEAD）
- Frontend commit: 640c03e1
- OS / Browser: Tauri v2 webview / WebKitGTK & WebView2
- Data source: N/A（前端状态机 + 后端 SSE 重放路径）

## Evidence

- `server/.../channel/ChannelController.java:102` ——
  ```java
  bus.subscribe(clientId, lastEventId == null ? 0L : lastEventId, sub);
  ```
  GET 默认 cursor 0；POST 路径 line 159 默认 `bus.latestEventId()` 不重放历史，与 GET 不一致。
- `ChannelController.java:177` ——
  ```java
  bus.publish(new DtEvent.SessionStatus("idle", Map.of()));
  ```
  POST 收尾时主动补发的 legacy idle frame 会进入 bus 缓冲，被后续 GET 重放。
- `client/src/services/channel/use-channel.ts:353-362` ——
  `session.idle` / `session.status=idle` 分支**无条件** `setStreaming(sessionId, false)`，无重放感知。
- `client/src/stores/channel-store.ts:21-28`（修复前）—— `setLastEventId` 只更新 in-memory Map，依赖 Zustand persist 异步 flush；CTRL+R 在 microtask 之前发生时，sessionStorage 里 `lastEventIdBySession` 落后于实际进度，新订阅退化成无 `Last-Event-ID`，触发 cursor=0 重放。

## Root Cause

两层递进根因：

1. **重放窗口不识别**：`buildEventSink` 没有"过去 vs 现在"分界——后端 `Connected` 帧无法用作可靠分界（旧 connected 同样在缓冲区里被重放）。任何 idle/error 帧到达即生效，包括重放的历史 idle。
2. **`lastEventId` 异步持久化**：`useChannelStore.setLastEventId` 没像 BUG-0037 那样同步落盘，CTRL+R race 下会丢失最近的 eventId，使后端从 cursor 0 重放，扩大问题面。

## Fix

两处叠加修复（同一 commit），保持纯前端最小风险：

### A. `channel-store.ts` 同步持久化 lastEventId（仿 BUG-0037）

`setLastEventId` 在 Zustand `set()` 之后同步 read-modify-write `sessionStorage['data-talk.channel']`，schema 严格为 `{state: {lastEventIdBySession: {<sessionId>: <id>, ...}}, version: 0}`。短路 `id <= prev` 时跳过同步写。

### B. `use-channel.ts` 加 500ms 重放抑制窗口

`buildEventSink` 函数顶部记录 `subscribeAt = Date.now()`。`session.idle` / `session.status=idle` / `session.error` 三处分支在 `Date.now() - subscribeAt < 500` 时直接 `return`，不调用 `setStreaming(false)`。

```ts
export function buildEventSink(sessionId, client, queryClient, _connectionId = null, pendingUserId = null) {
  const subscribeAt = Date.now()
  return (evt) => {
    // ...
    } else if (event === 'session.idle' || (event === 'session.status' && (data as any)?.status === 'idle')) {
      if (Date.now() - subscribeAt < 500) {
        // BUG-0038：抑制 GET /subscribe 从 cursor 0 重放的上一轮 idle。
        return
      }
      useChatPartsStore.getState().markSessionTurnCompleted(sessionId)
      useChatPartsStore.getState().setStreaming(sessionId, false)
    }
    // session.error 同样判定
  }
}
```

正常 turn 收尾路径 `session.idle` 一般在 turn 开始后 ≥ 数百毫秒才发出，落在 500ms 窗口之外不受影响。

## Verification

- 新增 `src/stores/__tests__/channel-store.test.ts` 2 个用例：同步写 sessionStorage 正确 schema、no-op `id <= prev` 不重写。
- 新增 `src/services/channel/use-channel.test.ts` `replay suppression (BUG-0037 follow-up)` 套件 2 个用例：fake timers 验证 500ms 内 `session.idle` / `session.error` 被抑制，越窗后正常清除 streaming。同时把已有 `turn-done` / `session.error (TD-014)` 共 6 个用例改用 `buildSinkPastReplayWindow` 辅助函数（临时替换 `Date.now` 使 sink 的 `subscribeAt` 跨过 500ms 窗口），保持稳态语义。
- `npx vitest run src/stores src/services/channel` —— 107 测试全部通过（7 文件）。

## Notes

- 也评估过两条替代方案：
  - **后端把 GET 默认 cursor 改成 `bus.latestEventId()`（与 POST 对齐）**：彻底不重放，但 frontend 在首挂或 lastEventId 缺失时会丢失少量历史 SSE。当前 store 已经能从 `useSessionHistory` 重新拉到消息，理论可行；但行为变更面较大、影响其他 in-flight subscribe 场景，暂未采用。
  - **基于 `connected` 哨兵帧分界**：多个旧 connected 同样会被重放，无法可靠区分"新 connected"，舍弃。
- 500ms 窗口是经验值；若用户 CTRL+R 后真实新 turn 在 500ms 内完成，按钮会延迟 ≤ 一个心跳才翻回发送态，可接受。
- 修复同时关闭了 BUG-0037 的隐藏后续路径，BUG-0037 本身（store 层同步写）正确无需回退。
