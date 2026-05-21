---
id: BUG-0054
title: AI 发送失败后输入框被回填，且草稿被重新写回 localStorage，CTRL+R 仍能复活已发送内容
status: fixed
priority: P1
source: manual-report
modules: [session, chat]
discovered: 2026-05-17
discoveredBy: human
testRunId: null
fixCommit: pending
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

用户输入提示并回车发送，过段时间（异步流失败后）AI 输入框又显示出刚才已经发送的内容；即使不主动刷新，只要随后 CTRL+R 也会复活该文本。表面像 BUG-0039（同步持久化 schema 错位）的回潮，实际根因不同。

## Reproduction Steps

1. 进入已有 active session，确保 `hasActiveModel = true`。
2. 在 composer 输入文本，例如 "hello"。
3. 回车发送。`useChannel.sendMessage` 内部 `upsertPendingUser` 已经把 "hello" 落到会话历史，SSE 开始流。
4. 让 SSE 在流式过程中失败（OpenCode timeout / 后端断流 / 网络抖动 / abort 等任意路径），`client.sendMessage` 抛错 → `useChannel.sendMessage` catch 后 `return false`。
5. 立即观察 composer 输入框；或不操作，直接 CTRL+R 刷新页面。

## Expected vs Actual

- **Expected**：输入框保持为空。失败的 user 气泡已经画在消息流里（红框 + 重试 + 删除），恢复路径走"重试"按钮。
- **Actual**：
  - 立即：输入框被回填 "hello"。失败气泡 + 输入框文本两份并存，用户以为没发出去会再次回车，可能造成重复发送。
  - CTRL+R 后：依然出现 "hello"。因为回填路径同时把 "hello" 写回了 `localStorage['dt.draft.<sessionId>']`，下一次 `useState` 初始化从该 key hydrate 出来。

## Environment

- Backend commit: f99f78cd
- Frontend commit: f99f78cd
- OS / Browser: Tauri v2 webview / WebKitGTK & WebView2
- Data source: N/A（纯前端状态机）

## Evidence

`client/src/features/session/prompt-composer.tsx:350-355`（修复前）：

```ts
const ok = await sendMessage(parts)
if (ok) {
  clearDone()
} else {
  updateText(trimmed)   // ← 这一行同时干了两件事
}
```

`updateText(trimmed)` 内部：

```ts
const updateText = (value: string) => {
  setText(value)                       // 回填 React state（立即症状）
  setComposerDraft(draftKey, value)    // 写回 localStorage（CTRL+R 复活症状）
}
```

`setComposerDraft` 是同步写 `dt.draft.<key>` localStorage 条目（BUG-0039 fix 落地后已不再有 race）。

`useChannel.sendMessage`（`use-channel.ts:514-532`）在调 `client.sendMessage` 之前已经 `upsertPendingUser`，失败后 `markPendingUserFailed`。`user-bubble.tsx:38-53`/`147-152` 把失败气泡画成红框 + 重试 / 删除。

## Root Cause

`prompt-composer.tsx:354` 的 `updateText(trimmed)` 把流式发送失败（SSE 已开始流后才出错）等同于"完全没发出去"，回填了：

1. React state — 立即让输入框出现刚发送的内容，与失败气泡形成重复
2. `localStorage['dt.draft.<sessionId>']` — 任意时刻 CTRL+R 都能让该文本复活

但流式发送失败时 user message 已经在 `chat-parts-store` 里以失败 pending 气泡形式存在，恢复路径已经由失败气泡上的"重试"按钮承担（`retryPendingUser` 用气泡保留的文本重发）。回填到 composer 既冗余又会盖掉用户在等待期间已经在输入框里打的新内容。

预发送阶段的其它 5 处 `updateText(trimmed)`（`createSession` 失败、`!use` resolveUseTarget 失败、`!sql` create / openDirectSqlQueryEditorTab 失败等）发生在 pending 气泡创建之前，没有承载文本的 UI 元素，因此保留回填。

## Fix

`prompt-composer.tsx:350-355` 去掉 `else { updateText(trimmed) }`：

```ts
const ok = await sendMessage(parts)
if (ok) {
  clearDone()
}
// !ok: the failed pending user bubble owns retry; restoring here would also
// re-persist the draft via setComposerDraft and resurrect on next CTRL+R.
```

失败后:
- 失败气泡保留，文本不丢失，用户走"重试"按钮（直接走 `retryPendingUser` 用气泡内文本重发）
- 输入框留空，用户可以继续输入新提示，不会被盖
- `dt.draft.<sessionId>` 不会被复活，CTRL+R 仍是空

## Verification

- `client/src/features/session/__tests__/prompt-composer.test.tsx` 中原"restores the textarea immediately when an active-session AI send fails"用例翻成"does not refill the textarea or revive the draft when an active-session AI send fails"：
  - send 前断言 `localStorage['dt.draft.sess-1'] === '你好'`
  - send 后断言 textarea 为空 + `localStorage['dt.draft.sess-1'] === null` + `composerDrafts['sess-1']` 为空
- `npx vitest run src/features/session/__tests__/prompt-composer.test.tsx` —— 15/15 通过
- `npx tsc --noEmit` —— 零类型错误

## Notes

- BUG-0039 的 fix 已经把"setComposerDraft schema 错位"那条 race 消掉，本 BUG 是另一条独立路径（异步失败回填）造成的相同表象。
- 失败气泡 + 输入框双写并存是设计层面的冗余 —— 任何 `sendMessage` 已经 `upsertPendingUser` 的失败回填都应避免在两处展示用户输入。
- 如果未来需要区分"POST 4xx 即刻失败（无 pending 气泡承载）"和"SSE 中途失败（pending 气泡已建）"两种语义，可以在 `useChannel.sendMessage` 拆 return 值。当前实现里 `upsertPendingUser` 始终先于 `client.sendMessage`，两种失败路径都有失败气泡，无需在 composer 区分。
