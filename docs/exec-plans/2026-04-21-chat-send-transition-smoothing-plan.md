# Chat Send Transition Smoothing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 AI 输入框首发消息时的闪动，并补一层更顺滑的“消息上移”入场动效。

**Architecture:** 保持现有 `PromptComposer` / `SplitView` / `useChannel` 架构不变，只修正新会话首发消息的本地状态时序，避免先渲染 HERO 空态再切到消息态。同时在用户消息气泡层增加一次性轻量入场动画，让发送后的视觉反馈更顺。

**Tech Stack:** React 19 + TypeScript + Zustand + Tailwind CSS + Vitest。

---

**Execution Notes (2026-04-21):**
- 新会话首发普通 AI 消息的创建路径已改为直接进入 `SPLIT`，避免 `PromptComposer` 先挂到 HERO 空态 slot 再切到消息态。
- pending 用户气泡新增 `motion-safe` 上移动画，并暴露 `data-pending-user-motion="true"` 供测试与后续样式演进使用。
- 后续回归修正：pending user 在服务端回显真实 `message.id` 时改为直接提升本地 optimistic turn，并保留稳定 render key，避免 turn remount 造成用户气泡自抖；位移动效也改到气泡本体并增强位移幅度。
- 继续回归修正：`b8c1c5e` 中新增的 `message.created(role=user)` 提前 promote 条件过宽，会让旧 user 事件抢走最新 pending user；现已改为仅在匹配当前 pending 文本的 user text part 到达时才 promote。
- 验证已通过：`npx vitest run src/features/session/__tests__/prompt-composer.test.tsx src/features/chat/components/turn/__tests__/session-turn.test.tsx`、`npx tsc --noEmit`。

## File Structure Map

### Modify

- `client/src/features/session/__tests__/prompt-composer.test.tsx`
- `client/src/features/session/prompt-composer.tsx`
- `client/src/features/session/hooks/use-pending-connection-resume.ts`
- `client/src/features/chat/components/turn/user-bubble.tsx`
- `client/src/features/chat/components/turn/__tests__/session-turn.test.tsx`
- `docs/exec-plans/index.md`

## Task 1: Lock The Repro With Tests

**Files:**
- Modify: `client/src/features/session/__tests__/prompt-composer.test.tsx`
- Modify: `client/src/features/chat/components/turn/__tests__/session-turn.test.tsx`

- [x] **Step 1.1: Add a regression test for “new session first send enters split/message layout immediately”**
- [x] **Step 1.2: Add a render test for the pending user bubble motion hook/class**
- [x] **Step 1.3: Run the targeted vitest cases and confirm they fail before the fix**

## Task 2: Remove The Flash And Add Motion

**Files:**
- Modify: `client/src/features/session/prompt-composer.tsx`
- Modify: `client/src/features/session/hooks/use-pending-connection-resume.ts`
- Modify: `client/src/features/chat/components/turn/user-bubble.tsx`

- [x] **Step 2.1: Open newly created send-target sessions directly in split/message mode**
- [x] **Step 2.2: Keep the pending prompt resume path intact while avoiding the intermediate HERO render**
- [x] **Step 2.3: Add a motion-safe upward entry animation for pending user bubbles**

## Task 3: Verify And Housekeeping

- [x] **Step 3.1: Run the targeted vitest suite**
- [x] **Step 3.2: Run `cd client && npx tsc --noEmit`**
- [x] **Step 3.3: Mark this plan complete and move it from Active to Completed in `docs/exec-plans/index.md`**
