# Session Canvas UX Fixes Implementation Plan

**Status:** 代码就绪（2026-04-18）—— `tsc --noEmit` 零错误、`vitest run` 失败数 3 files/6 tests 与 baseline 一致（均为 pre-existing ChatHeader/useSidebar 与 providers-page 文案测试问题，不涉及本改动）。待用户 E2E Smoke + commit 授权。

**Goal:** 修 4 个前端 UX bug：(1) 发消息后 composer 消失；(2) composer 不在聊天区底部、无法与 stage 面板底部对齐；(3) 刷新页面丢失选中会话；(4) 用户消息气泡被错误渲染成灰色 pill（assistant 样式）。

**Architecture:**
- `SplitView` 的 chat 列保留两分支布局：有消息 → ChatHeader + 滚动消息区 + 底部 composer；空态 → ChatHeader + 居中（欢迎文案 + composer 叠放，HERO 风格）。
- `useComposerSlot` 扩展依赖 `hasMessages`，在两分支间切换时重新 `getElementById` 定位 portal target，避免挂到已卸载的 DOM 节点。
- `useSessionStore` 加 zustand `persist` middleware，仅持久化 `activeSessionId`（`Map` 字段仍在内存里，partialize 跳过）。
- `MessageStream` 不再对缺失的 `meta` 回落到 `assistant`，而是跳过该 message——让后端的 `message.created` 到达前不渲染，避免用户消息被画成灰色气泡。
- `useChannel.sendMessage` 增加 try/catch + `toast.error`，避免 SSE/POST 失败时静默。

**Tech Stack:** React 19、Zustand（含 persist middleware）、sonner、TanStack Query。

---

## 背景

- 发送"你好"后截图显示：composer 消失、用户消息以 `bg-muted` 灰色 pill 居中显示、刷新后回到新会话。
- 根因：
  - **Q1-a**：`prompt-composer.tsx` 的 `useComposerSlot` 通过 `getElementById('composer-slot')` 拿到 DOM ref，`useLayoutEffect` 只在 `activeSessionId` 变化时重查；`split-view.tsx` 的 hasMessages 切换会卸载旧分支的 slot 节点挂新分支的同 id 节点，portal state 仍指向已卸载节点。
  - **Q1-b**：后端模型调用可能失败（超出前端职责），但 `sendMessage` 没有 catch，错误被吞。
  - **Q2**：空态分支用 `justify-center` 把 composer 挤到中央。
  - **Q3**：`useSessionStore` 无 persist、路由 `/` 不携带 sessionId，刷新后 `activeSessionId=null`。
  - **Q4**：`message-stream.tsx:29` 的 `g.meta?.role ?? 'assistant'` 在 `message.created` 到达前给所有新消息套上 assistant 样式。

## 范围

**改动文件：**

- `client/src/features/session/split-view.tsx` — 合并 chat 列两分支，composer-slot 固定在底部
- `client/src/stores/session-store.ts` — 加 `persist({ partialize: s => ({ activeSessionId }) })`
- `client/src/features/chat/components/message-stream.tsx` — meta 缺失时跳过渲染（不再 fallback assistant）
- `client/src/services/channel/use-channel.ts` — `sendMessage` 加 try/catch + `toast.error`

**不改：**

- 后端 channel 协议、`createTextPart` 签名（后端 `ChannelService.java:67` 自己生成 messageId，前端乐观 upsert 会造成消息重复，故不做）
- `useComposerSlot` 实现（DOM 结构改后 slot 不再被卸载，portal 天然稳定）

---

## Task 1：修 Q1-a（composer 不再消失）+ Q2（位置）

- [x] `split-view.tsx` 保留两分支：有消息 → 底部 composer，与 stage 面板底部同水平；空态 → 欢迎文案与 composer 垂直居中（HERO 风格，满足用户"初始无消息时居中对齐"诉求）
- [x] `prompt-composer.tsx:useComposerSlot` 依赖 `hasMessages`，hasMessages 翻转时重新 `getElementById('composer-slot')`，让 portal target 跟随新挂载的 slot DOM 节点

## Task 2：修 Q3（activeSessionId 持久化）

- [x] `session-store.ts` 用 `persist` middleware 包裹，`name: 'data-talk.session'`，`storage: createJSONStorage(() => localStorage)`，`partialize` 只保留 `activeSessionId`
- [x] Map/Set 字段不入 partialize（JSON 无法序列化 Map，且是派生状态）

## Task 3：修 Q4（用户消息气泡样式）

- [x] `message-stream.tsx` 渲染循环里：`if (!g.meta) return null`，不再 fallback 到 assistant
- [x] 附短注释说明 why（避免用户消息被画成灰色气泡）

## Task 4：Q1-b 软化（发送失败时给反馈）

- [x] `use-channel.ts` 在 `sendMessage` 的 try/finally 中间加 catch，用 `toast.error` 显示错误
- [x] 从 `sonner` 导入 `toast`

---

## 验证

- [x] `npx tsc --noEmit` 零错误
- [x] `npx vitest run` 失败数与改动前一致（6 failed / 68 passed，均为 pre-existing）
- [ ] E2E Smoke（等用户确认）：
  1. `cd client && npm run dev`
  2. 无会话状态：composer 应在 chat 列底部（不是居中）；打开 stage 后 composer 与右栏底部同水平
  3. 输入"你好"发送：composer 保持可见、按钮变 spinner；用户消息以蓝底右对齐气泡出现（meta 到达后，不再是灰色 pill）；若后端返回错误会看到 sonner toast
  4. 刷新页面：仍保持在同一会话，而不是跳回新会话

## 风险与回滚

- **风险 1**：`persist` 中间件读取 localStorage 时若存了已删除的 sessionId，会尝试加载历史并返回 404。`use-session-history.ts` 的 `catch` 已 soft-fail 到 `console.warn`，所以最坏情况是一条 warn，不会崩溃。
- **风险 2**：Zustand persist 首次挂载有一个 hydrate tick；如果在那之前 `useSessionMode` 读到 `null`，会短暂渲染"无会话"态——下个 tick 就会纠正。视觉上可能看到一帧闪烁，先观察 smoke，再决定是否加 `onHydrationDone` 同步等待。
- **回滚**：单 commit，`git revert` 即可。
