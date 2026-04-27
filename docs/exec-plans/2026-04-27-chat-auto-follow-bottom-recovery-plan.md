# Chat Auto-Follow Bottom Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复聊天区 auto-follow 恢复条件，让用户主动上滚后暂停跟随，但只要再次严格触底，无论通过手动滚动还是“回到底部”按钮，后续流式内容都恢复自动跟随。

**Architecture:** 保持 `SplitView` 结构、按钮显隐和视觉契约不变，只在 `useAutoScroll` 内补齐“严格触底恢复 follow”的状态机。继续保留 `FOLLOW_THRESHOLD_PX = 150` 作为 UI 近底判定，同时新增严格触底判定，仅用于恢复 auto-follow。

**Tech Stack:** React 19, TypeScript, Vitest, Testing Library.

---

## Design Inputs

- [client/DESIGN.md](../../client/DESIGN.md)
  - 保持现有聊天阅读面、按钮样式、层级和动效不变
  - 不新增视觉状态，只修正已有交互语义
- [2026-04-27-chat-auto-follow-bottom-recovery-design.md](../product-specs/2026-04-27-chat-auto-follow-bottom-recovery-design.md)
  - §1/§4.1：用户主动上滚后暂停跟随，严格触底后恢复
  - §4.2：`isAtBottom` 与严格触底恢复语义分离
  - §4.4：补足“手动到底恢复”“near-bottom 不恢复”的回归测试

## File Structure

- Modify: `client/src/hooks/use-auto-scroll.ts`
- Modify: `client/src/hooks/use-auto-scroll.test.tsx`
- Modify: `docs/product-specs/2026-04-27-chat-auto-follow-bottom-recovery-design.md`
- Modify: `docs/exec-plans/2026-04-27-chat-auto-follow-bottom-recovery-plan.md`
- Modify: `docs/exec-plans/index.md`

## Task 1: 先用测试锁定恢复条件

**Files:**
- Modify: `client/src/hooks/use-auto-scroll.test.tsx`

- [x] Step 1: 调整现有“手动滚回底部后仍不恢复”的用例，改成新预期：严格触底后，下一次内容增长会恢复 auto-follow。
- [x] Step 2: 新增一条回归测试，覆盖“只进入 near-bottom 区域但未严格触底时，不恢复 auto-follow”。
- [x] Step 3: 运行 `cd client && npx vitest run src/hooks/use-auto-scroll.test.tsx`
Expected: 至少 1 条与恢复条件相关的断言失败，证明当前实现仍不符合新需求。

## Task 2: 修正 hook 的严格触底恢复状态机

**Files:**
- Modify: `client/src/hooks/use-auto-scroll.ts`

- [x] Step 1: 在 `useAutoScroll` 中新增严格触底判定，和现有 `FOLLOW_THRESHOLD_PX` 展示判定分离。
- [x] Step 2: 保持用户上滚会关闭 `followEnabled`，但在 `scroll` 处理中只要严格触底就重新开启 `followEnabled`。
- [x] Step 3: 保持 `scrollToBottom()` 的恢复能力不变，让按钮路径和手动滚动路径统一收敛到同一 follow 语义。
- [x] Step 4: 运行 `cd client && npx vitest run src/hooks/use-auto-scroll.test.tsx`
Expected: `use-auto-scroll` 全部回归测试通过。

## Task 3: 整体验证与文档收尾

**Files:**
- Modify: `docs/product-specs/2026-04-27-chat-auto-follow-bottom-recovery-design.md`
- Modify: `docs/exec-plans/2026-04-27-chat-auto-follow-bottom-recovery-plan.md`
- Modify: `docs/exec-plans/index.md`

- [x] Step 1: 运行 `cd client && npx tsc --noEmit`
Expected: 退出码 0，无类型错误。
- [x] Step 2: 更新 spec 状态为已实现，并在计划文件中勾完全部 checkbox，补充执行说明。
- [x] Step 3: 将 `docs/exec-plans/index.md` 中该计划从 Active 移到 Completed。

## Execution Notes

- [x] Task 1 Step 1: 将原“手动滚回底部后仍不恢复”的断言翻转为新预期，要求严格触底后下一次内容增长重新触发 `scrollToBottom`。
- [x] Task 1 Step 2: 新增“只 near-bottom 不 strict-bottom 时不恢复”的测试，用 `scrollTop=930 / max=940` 锁定 10px 差值场景。
- [x] Task 1 Step 3: `cd client && npx vitest run src/hooks/use-auto-scroll.test.tsx` 首次失败，报错为 `expected "scrollTo" to be called 1 times, but got 0 times`，证明当前实现未在手动触底后恢复 follow。

- [x] Task 2 Step 1: 在 `useAutoScroll` 中新增 `STRICT_BOTTOM_THRESHOLD_PX = 2` 和 `getDistanceFromBottom()`，把恢复判定从 near-bottom 展示逻辑中拆出来。
- [x] Task 2 Step 2: `handleScroll()` 现改为“严格触底即恢复 `followEnabled`，否则仅在上滚且未触底时关闭”，保留用户上滚暂停语义。
- [x] Task 2 Step 3: `scrollToBottom()` 的恢复逻辑保持不变，因此按钮路径和手动滚动路径现在统一收敛到同一 follow 语义。
- [x] Task 2 Step 4: `cd client && npx vitest run src/hooks/use-auto-scroll.test.tsx` 通过，16 tests passed。

- [x] Task 3 Step 1: `cd client && npx vitest run src/hooks/use-auto-scroll.test.tsx src/features/session/split-view.test.tsx` → 2 files, 26 tests passed；`cd client && npx tsc --noEmit` → 退出码 0。
- [x] Task 3 Step 2: spec 状态已更新为 `Shipped (2026-04-27)`，本计划 checkbox 已全部回填。
- [x] Task 3 Step 3: `docs/exec-plans/index.md` 已将该计划移入 Completed。
