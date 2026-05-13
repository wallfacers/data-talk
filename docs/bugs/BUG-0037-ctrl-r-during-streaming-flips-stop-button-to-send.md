---
id: BUG-0037
title: AI streaming 期间 CTRL+R 让转圈停止按钮误回"待发送"态
status: fixed
priority: P1
source: manual-report
modules: [session, chat]
discovered: 2026-05-13
discoveredBy: human
testRunId: null
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

AI 正在流式输出时，用户按 CTRL+R 触发 webview reload，刷新完成后 composer 上的"转圈停止"按钮被错误地切回"待发送"按钮。`streamingBySession` 在 `useChatPartsStore` 中由 Zustand persist 中间件异步 flush 到 sessionStorage，reload 在 flush 完成之前发生时，sessionStorage 里没有最新的 streaming 标志，hydration 后 `isStreaming === false`。

## Reproduction Steps

1. 打开任一 session，输入提问后按发送。
2. 在 AI 流式输出过程中（按钮处于转圈停止状态），按 CTRL+R 刷新页面。
3. 等页面重新渲染。

## Expected vs Actual

- **Expected**：刷新后按钮仍是"转圈停止"，因为后端 turn 还在跑、前端 SSE 重连后会继续接收事件。
- **Actual**：按钮变成"待发送"，用户被误导以为可以再发；实际后端流仍在跑。

## Environment

- Backend commit: fb5c3576
- Frontend commit: fb5c3576
- OS / Browser: Tauri v2 webview / WebKitGTK & WebView2
- Data source: N/A（仅前端状态机问题）

## Evidence

- 现象：`client/src/features/session/prompt-composer.tsx:403` `isStreaming ? <停止按钮> : <发送按钮>`，reload 后 `isStreaming=false`。
- 同源前例：`25c0b547` 修了 `setComposerDraft` 的同种 race condition（commit message: "Zustand persist middleware ... flush ... asynchronously"），但 `setStreaming` 没做同等保护。

## Root Cause

`useChatPartsStore` 通过 `persist` 中间件把 `streamingBySession`（partialized 为 `string[]`）持久化到 sessionStorage。Zustand persist 的 storage flush 不保证在 `set()` 返回时已写入；当 React 在批处理上下文中触发 `setStreaming(sessionId, true)` 时，sessionStorage 的写入可能被排到下一个 microtask/tick。

CTRL+R 触发 webview reload 时若 flush 还未发生：

1. sessionStorage 里 `data-talk.chat-parts.state.streamingBySession` 仍是旧值（不含当前 sessionId）。
2. Reload 后 Zustand hydrate 出来的 Set 不含 sessionId。
3. `prompt-composer.tsx` 读 `isStreaming = streamingBySession.has(activeSessionId) → false`，渲染发送按钮。
4. 此时 `useSessionSubscribe` 重订阅 SSE 拿到后续事件，但在收到下一个 `session.idle` / part 之前，按钮已经显错。

## Fix

将 `setStreaming` 改成在 Zustand `set()` 之后立即**同步**写 sessionStorage，模式与 `setComposerDraft`（`session-store.ts`）保持一致；schema 严格按 Zustand persist 的 `{ state: {...}, version: 0 }` 包装：

```ts
setStreaming: (sessionId, on) => {
  let nextSet: Set<string> | null = null
  set((s) => {
    // ...更新 streamingBySession，赋值给 nextSet
  })
  if (nextSet && typeof window !== 'undefined') {
    try {
      const raw = sessionStorage.getItem('data-talk.chat-parts')
      const parsed = raw ? JSON.parse(raw) : null
      const wrapped = parsed && 'state' in parsed ? parsed : { state: {}, version: 0 }
      wrapped.state.streamingBySession = Array.from(nextSet)
      sessionStorage.setItem('data-talk.chat-parts', JSON.stringify(wrapped))
    } catch { /* 静默降级 */ }
  }
}
```

这样无论 persist 中间件 flush 时机如何，下一次 hydrate 都能读到正确的 streamingBySession。

## Verification

- 新增单元测试 `setStreaming writes streamingBySession synchronously on every toggle (BUG-0037)`：toggle 后立即读 sessionStorage 验证 schema (`{state: {streamingBySession}, version}`) 与值。
- 全套 27 个 chat-parts-store 单测通过 (`npx vitest run src/stores/__tests__/chat-parts-store.test.ts`)。
- 手工验证：streaming 中按 CTRL+R 后按钮仍为停止状态。

## Notes

- 同种 race 在历史上已经修过一次（`25c0b547` for composer draft），这一次扩展到 streaming 标志。后续若发现其他持久化字段也走 `persist` 中间件且影响 reload 后的关键 UI，应统一加同步写入保护。
- `setComposerDraft` 的同步写入直接写到 root（`data.composerDrafts`），但 Zustand hydrate 时读的是 `data.state.composerDrafts` —— 严格来讲 `setComposerDraft` 当前同步写入对 hydrate 没生效（独立隐患，建议另起 ticket 校正）。本 fix 明确使用 `{ state, version }` schema，避免重蹈覆辙。
- 此 fix 不保证保留 sessionStorage 中无关字段：Zustand persist 自身的 partialize 输出仅含 `streamingBySession`，其 microtask flush 会覆盖整个 state；本 fix 的同步写只确保 `streamingBySession` 在 reload 前**至少有一次正确落盘**。
