# Chat Auto-Scroll Reentry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复聊天区在 AI 流式输出时的 auto-follow 误接管问题：用户只要手动向上滚离开底部，系统就停止自动滚动，直到用户再次回到底部。

**Architecture:** 保持现有 `SplitView` 结构不变，只重写 `useAutoScroll` 的跟随判定状态机。将“几何上是否靠近底部”和“语义上是否允许自动跟随”拆开管理；`SplitView` 继续在切换 session 时显式重置到底部；测试重点放在 hook 的状态转换与 session 视图集成行为。

**Tech Stack:** React 19, TypeScript, Zustand, Vitest, Testing Library.

---

## Spec Mapping

- [2026-04-23-chat-auto-scroll-reentry-design.md](../product-specs/2026-04-23-chat-auto-scroll-reentry-design.md)
  - §1/§4.1：用户主动上滚后立即暂停 auto-follow，回到底部才恢复
  - §4.3：切换 session 时重置跟随许可并自动滚底
  - §4.4：补 3 个回归测试覆盖自动跟随、暂停、恢复三个状态

## File Structure

- Create: `client/src/hooks/use-auto-scroll.test.tsx`
- Modify: `client/src/hooks/use-auto-scroll.ts`
- Modify: `client/src/features/session/split-view.tsx`
- Modify: `docs/product-specs/2026-04-23-chat-auto-scroll-reentry-design.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-23-chat-auto-scroll-reentry-plan.md`

## Task 1: 用测试锁定 auto-follow 状态机回归

**Files:**
- Create: `client/src/hooks/use-auto-scroll.test.tsx`

- [x] Step 1: 新增 hook 级回归测试，覆盖“在底部时追加内容会自动滚底”。
- [x] Step 2: 在同一测试文件补“用户上滚后追加内容不会自动滚底”与“回到底部后再次恢复自动滚底”。
- [x] Step 3: 运行 `cd client && npx vitest run src/hooks/use-auto-scroll.test.tsx`，确认在修复前先红。

## Task 2: 修正 hook 跟随判定并保持 session 切换行为

**Files:**
- Modify: `client/src/hooks/use-auto-scroll.ts`
- Modify: `client/src/features/session/split-view.tsx`

- [x] Step 1: 将 `useAutoScroll` 改成“双状态模型”，拆分“是否在底部附近”和“是否允许自动跟随”。
- [x] Step 2: 保持 `MutationObserver` 与依赖更新仅在 `followEnabled` 为真时触发 `scrollToBottom('auto')`。
- [x] Step 3: 保持 `SplitView` 切换 `sid` 时显式重置跟随许可并立即滚到底部。
- [x] Step 4: 重新运行 `cd client && npx vitest run src/hooks/use-auto-scroll.test.tsx`，确认转绿。

## Task 3: 整体验证与文档收尾

**Files:**
- Modify: `docs/exec-plans/2026-04-23-chat-auto-scroll-reentry-plan.md`
- Modify: `docs/product-specs/2026-04-23-chat-auto-scroll-reentry-design.md`
- Modify: `docs/exec-plans/index.md`

- [x] Step 1: 运行 `cd client && npx vitest run src/hooks/use-auto-scroll.test.tsx src/features/session/split-view.test.tsx`
- [x] Step 2: 运行 `cd client && npx tsc --noEmit`
- [x] Step 3: 将计划文件中的 checkbox 全部勾完，并补充实际执行说明。
- [x] Step 4: 将 `docs/exec-plans/index.md` 中该计划从 Active 移到 Completed，并将 spec 状态更新为已实现。

## Execution Notes

- [x] Task 1 Step 1: 新增 `use-auto-scroll.test.tsx`，覆盖三条核心回归：底部自动跟随、用户上滚后暂停、回到底部后恢复。
- [x] Task 1 Step 2: 用 `scrollHeight=1000/clientHeight=100/scrollTop=860` 锁定“仅离底部 40px 仍被错误拉回”的问题。
- [x] Task 1 Step 3: 初次运行 `cd client && npx vitest run src/hooks/use-auto-scroll.test.tsx` 失败，红灯为“expected scrollTo to not be called at all, but actually been called 1 times”。

- [x] Task 2 Step 1: `useAutoScroll` 现拆分 `isAtBottom` 与 `followEnabled`；用户向上滚时关闭 follow，只有重新回到底部才恢复。
- [x] Task 2 Step 2: `MutationObserver` 与依赖更新统一改为检查 `followEnabled.current`，不再用“距离底部 < 150px”直接代替用户意图。
- [x] Task 2 Step 3: `SplitView` 切 session 时改为仅调用 `scrollToBottom('auto')`，由 hook 内部统一重置 follow 状态，移除外部直接篡改 ref 状态。
- [x] Task 2 Step 4: 修复后 `cd client && npx vitest run src/hooks/use-auto-scroll.test.tsx` 通过，3 tests passed。

- [x] Task 3 Step 1: `cd client && npx vitest run src/hooks/use-auto-scroll.test.tsx src/features/session/split-view.test.tsx` → 2 files, 7 tests passed。
- [x] Task 3 Step 2: `cd client && npx tsc --noEmit` → 通过（无输出）。
- [x] Task 3 Step 3: 本计划与执行说明已完成更新。
- [x] Task 3 Step 4: `docs/exec-plans/index.md` 已完成归档，spec 已标记为已实现。
