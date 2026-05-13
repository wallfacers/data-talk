---
id: BUG-0039
title: setComposerDraft 同步写入 schema 错位，CTRL+R 后已发送内容回填到输入框
status: fixed
priority: P1
source: manual-report
modules: [session, chat]
discovered: 2026-05-13
discoveredBy: human
testRunId: null
fixCommit: acc06a12
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

用户在 composer 输入文本，回车发送后立刻 CTRL+R 刷新页面，输入框里又出现刚发送的内容（应该是空的）。

`setComposerDraft` 在 Zustand `set()` 之后做同步 localStorage 写入时用错了 schema：写到 `data.composerDrafts.key`（根节点），但 Zustand persist 的 hydrate 读的是 `data.state.composerDrafts.key`（`{state, version}` 包装后的子树）。同步写完全失效，等价于没写。BUG-0037 的 Notes 第二条已经预警过这个隐患，本 BUG 是落实修复。

## Reproduction Steps

1. 在 composer 输入任意非空文本，例如 "hello"。
2. 回车发送。
3. 立刻按 CTRL+R 刷新页面（赶在 Zustand persist 中间件 microtask flush 之前）。
4. 等页面重新渲染，观察输入框内容。

## Expected vs Actual

- **Expected**：输入框为空（草稿已被发送清空）。
- **Actual**：输入框回填出 "hello"，用户以为没发出去会再次回车，可能造成重复发送。

## Environment

- Backend commit: 640c03e1
- Frontend commit: 640c03e1
- OS / Browser: Tauri v2 webview / WebKitGTK & WebView2
- Data source: N/A（纯前端状态机问题）

## Evidence

`client/src/stores/session-store.ts:109-126`（修复前）：

```ts
setComposerDraft: (key, text) => {
  set((s) => {
    if (s.composerDrafts[key] === text) return s
    return { composerDrafts: { ...s.composerDrafts, [key]: text } }
  })
  if (typeof window !== 'undefined') {
    try {
      const raw = localStorage.getItem('data-talk.session')
      const data = raw ? (JSON.parse(raw) as Record<string, unknown>) : {}
      const drafts = (data.composerDrafts as Record<string, string>) ?? {}  // ← 错路径
      drafts[key] = text
      data.composerDrafts = drafts                                            // ← 错路径
      localStorage.setItem('data-talk.session', JSON.stringify(data))
    } catch { /* 静默降级 */ }
  }
}
```

`localStorage['data-talk.session']` 在 persist 中间件下结构为 `{state: {activeSessionId, composerDrafts}, version: 0}`。同步写应该改 `data.state.composerDrafts`，但代码改了 `data.composerDrafts`，对 hydrate 无影响。

## Root Cause

发送清空时：
1. `updateText('')` → `setComposerDraft(key, '')`
2. 同步写到 `data.composerDrafts.key=""`（**错路径，hydrate 不读这里**）
3. Zustand persist 中间件安排一次 microtask flush，**预期**把 `state.composerDrafts.key=""` 写回（在正确路径）
4. 但 CTRL+R 在 microtask 之前发生，flush 没跑完
5. Hydrate 从 `state.composerDrafts.key` 读，仍是上一步的旧值 "hello"
6. 输入框恢复成 "hello"

## Fix

把同步写入改成严格 `{state, version}` schema，与 BUG-0037 `chat-parts-store.setStreaming` 的写法对齐：

```ts
setComposerDraft: (key, text) => {
  let nextDrafts: Record<string, string> | null = null
  set((s) => {
    if (s.composerDrafts[key] === text) return s
    nextDrafts = { ...s.composerDrafts, [key]: text }
    return { composerDrafts: nextDrafts }
  })
  if (nextDrafts && typeof window !== 'undefined') {
    try {
      const raw = localStorage.getItem('data-talk.session')
      const parsed = raw ? JSON.parse(raw) : null
      const wrapped =
        parsed && typeof parsed === 'object' && 'state' in parsed
          ? (parsed as { state: Record<string, unknown>; version?: number })
          : { state: {} as Record<string, unknown>, version: 0 }
      wrapped.state = wrapped.state ?? {}
      wrapped.state.composerDrafts = nextDrafts
      localStorage.setItem('data-talk.session', JSON.stringify(wrapped))
    } catch { /* 静默降级 */ }
  }
}
```

闭包变量 `nextDrafts` 捕获最新草稿，避免 `set()` 之外再调 `getState()` 时的潜在时序问题。

## Verification

- 新增 `client/src/stores/session-store.test.ts` 中 `composerDrafts persistence (BUG-NEW-2)` 套件 3 个用例：
  - 同步写后 `localStorage['data-talk.session']` parse 后 `state.composerDrafts.<key> === ''`（不是 `data.composerDrafts.<key>`）
  - 清空草稿同步写入路径正确
  - `vi.resetModules` + 重 import store 模拟 reload，断言 hydrate 后 in-memory `composerDrafts.<key> === ''`
- `npx vitest run src/stores/session-store.test.ts` —— 6/6 通过。

## Notes

- BUG-0037 的 Notes 第二条已经写过这条隐患："`setComposerDraft` 的同步写入直接写到 root（`data.composerDrafts`），但 Zustand hydrate 时读的是 `data.state.composerDrafts` —— 严格来讲 `setComposerDraft` 当前同步写入对 hydrate 没生效（独立隐患，建议另起 ticket 校正）"。本 BUG 是落实这条 follow-up。
- 同源模式（Zustand persist 异步 flush race）后续若再发现其他 store 走同样路径，应统一用 `{state, version}` schema 同步写入。
- 修复**不**保证保留 sessionStorage 中无关字段：Zustand persist 自身的 partialize 输出为 `{activeSessionId, composerDrafts}`，其 microtask flush 会覆盖整个 state；本 fix 的同步写只确保 `composerDrafts` 在 reload 前至少有一次正确落盘。
