# Reasoning Placeholder Flicker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复聊天区“思考中…”在 reasoning 首包阶段因两个占位入口快速切换而产生的抖动。

**Architecture:** 保持现有 `SessionTurn` 首包占位与 `ReasoningPart` 展开状态机，不新增全局状态。修复聚焦在“何时由 `SessionTurn` 兜底渲染占位，何时交给 `ReasoningPart` 自己渲染”，保证同一时刻只存在一个 thinking indicator。

**Tech Stack:** React 19, Zustand, lucide-react, Vitest, Testing Library, TypeScript.

---

## Design Inputs

- Source: [`client/DESIGN.md`](../../client/DESIGN.md)
- Applied constraints:
  - 聊天消息区属于 `comfortable` 密度，修复必须保持现有消息行高、节奏和占位空间稳定，优先消除布局抖动。
  - assistant surface 继续沿用既有中性色与语义 token，不引入新的正式视觉颜色；红绿蓝仅作为调试验证手段。
  - 交互和 motion 维持现状，只修正状态切换条件，不新增动画或视觉层级。

## Spec Mapping

- Source: [`docs/product-specs/2026-04-24-reasoning-auto-expand-setting-design.md`](../product-specs/2026-04-24-reasoning-auto-expand-setting-design.md)
- Covers:
  - reasoning 在思考期间与首包占位应表现为同一套交互语义，不应在不同占位实现之间闪烁切换。
  - 非目标：不改 reasoning 面板样式，不改 auto-expand 设定语义。

## File Structure

- Modify: `client/src/features/chat/components/turn/assistant-stream.tsx`
- Modify: `client/src/features/chat/components/turn/session-turn.tsx`
- Modify: `client/src/features/chat/components/turn/reasoning-part.tsx`
- Modify: `client/src/features/chat/components/turn/__tests__/session-turn.test.tsx`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-24-reasoning-placeholder-flicker-plan.md`

## Task 1: 补失败测试锁定重复占位

**Files:**
- Modify: `client/src/features/chat/components/turn/__tests__/session-turn.test.tsx`

- [x] Step 1: 新增测试，覆盖空的 streaming reasoning part 不应抢占 `SessionTurn` 的兜底“思考中…”占位。
- [x] Step 2: 运行 `cd client && npx vitest run src/features/chat/components/turn/__tests__/session-turn.test.tsx`，确认红灯来自当前 empty reasoning shell 被渲染出来。

## Task 2: 最小修复占位切换边界

**Files:**
- Modify: `client/src/features/chat/components/turn/assistant-stream.tsx`
- Modify: `client/src/features/chat/components/turn/session-turn.tsx`
- Modify: `client/src/features/chat/components/turn/reasoning-part.tsx`

- [x] Step 1: 调整可见性边界，empty reasoning shell 不再被视为可见 part，也不再渲染自己的 placeholder；只有 reasoning 真正收到文本后才接管界面。
- [x] Step 2: 保持首包无 part、普通 text/tool 流式、error/interrupted 分支行为不变。

## Task 3: 集中验证与文档收尾

**Files:**
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-24-reasoning-placeholder-flicker-plan.md`

- [x] Step 1: 运行 `cd client && npx vitest run src/features/chat/components/turn/__tests__/session-turn.test.tsx src/features/chat/components/turn/__tests__/reasoning-part.test.tsx`。
- [x] Step 2: 运行 `cd client && npx tsc --noEmit`。
- [x] Step 3: 将本计划 checklist 更新为实际执行结果。
- [x] Step 4: 将本计划从 `docs/exec-plans/index.md` 的 Active 移到 Completed。

## Execution Notes

- [x] Task 1 Step 1: 在 `client/src/features/chat/components/turn/__tests__/session-turn.test.tsx` 新增 `keeps the outer thinking placeholder until a streaming reasoning part has text` 回归测试。
- [x] Task 1 Step 2: 初次运行 `cd client && npx vitest run src/features/chat/components/turn/__tests__/session-turn.test.tsx` 红灯，失败点为 DOM 中已出现 `[data-component="reasoning-part"]`，说明 empty reasoning shell 过早接管了占位。
- [x] Task 2 Step 1: `ReasoningPart` 现在对空文本直接返回 `null`，`AssistantStream` 过滤空 reasoning part，`SessionTurn` 仅把带文本的 reasoning 视为可见内容。
- [x] Task 2 Step 2: 首包无 part 时继续显示 `SessionTurn` 占位；普通 text/tool、error/interrupted 逻辑未改。
- [x] Task 3 Step 1: `cd client && npx vitest run src/features/chat/components/turn/__tests__/session-turn.test.tsx src/features/chat/components/turn/__tests__/reasoning-part.test.tsx` 通过（2 files, 21 tests）。
- [x] Task 3 Step 2: `cd client && npx tsc --noEmit` 通过。
