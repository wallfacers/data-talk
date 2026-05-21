# Reasoning Placeholder Chevron Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 AI 首包占位“思考中…”的箭头方向，使其与“思考中自动展开”设置保持一致。

**Architecture:** 沿用现有 `SessionTurn` 首包占位渲染，不新增状态源；直接消费 `ui-settings-store.autoExpandReasoning` 决定占位态 Chevron 朝向。真实 `ReasoningPart` 的展开状态机保持不变，只补齐占位态与既有设计的一致性。

**Tech Stack:** React 19, Zustand, lucide-react, Vitest, Testing Library, TypeScript.

---

## Design Inputs

- Source: [`client/DESIGN.md`](../../client/DESIGN.md)
- Applied constraints:
  - 聊天消息区属于 `comfortable` 密度，修复应保持现有消息行高、按钮样式和布局节奏不变。
  - assistant message 继续使用既有语义 surface 与中性色反馈，不引入新视觉 token。
  - 交互反馈沿用当前轻量 motion，仅修正状态驱动，不新增动画或视觉层级。

## Spec Mapping

- Source: [`docs/product-specs/2026-04-24-reasoning-auto-expand-setting-design.md`](../product-specs/2026-04-24-reasoning-auto-expand-setting-design.md)
- Covers:
  - 4.3 Reasoning 面板状态机：流式开始时的自动展开/收起行为需要由 `autoExpandReasoning` 决定。
  - 1.2 非目标 2：不改变 reasoning 面板的视觉样式，只修正状态一致性。

## File Structure

- Modify: `client/src/features/chat/components/turn/session-turn.tsx`
- Modify: `client/src/features/chat/components/turn/__tests__/session-turn.test.tsx`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-24-reasoning-placeholder-chevron-sync-plan.md`

## Task 1: 为占位态箭头补失败用例

**Files:**
- Modify: `client/src/features/chat/components/turn/__tests__/session-turn.test.tsx`

- [x] Step 1: 新增测试，覆盖 `autoExpandReasoning = false` 时占位“思考中…”箭头默认不旋转。
- [x] Step 2: 新增测试，覆盖 `autoExpandReasoning = true` 时占位“思考中…”箭头默认旋转为展开态。
- [x] Step 3: 运行 `cd client && npx vitest run src/features/chat/components/turn/__tests__/session-turn.test.tsx`，确认红灯来自当前硬编码 `rotate-90`。

## Task 2: 最小修复占位态箭头状态

**Files:**
- Modify: `client/src/features/chat/components/turn/session-turn.tsx`

- [x] Step 1: 在 `SessionTurn` 中消费 `useUISettingsStore().autoExpandReasoning`。
- [x] Step 2: 仅调整首包占位“思考中…” Chevron 的 `className`，使其跟随设置旋转。
- [x] Step 3: 保持 `showThinking` 判定、占位空间和真实 `ReasoningPart` 行为不变。

## Task 3: 集中验证与文档收尾

**Files:**
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-24-reasoning-placeholder-chevron-sync-plan.md`

- [x] Step 1: 运行 `cd client && npx vitest run src/features/chat/components/turn/__tests__/session-turn.test.tsx src/features/chat/components/turn/__tests__/reasoning-part.test.tsx`。
- [x] Step 2: 运行 `cd client && npx tsc --noEmit`。
- [x] Step 3: 将本计划 checklist 更新为实际执行结果。
- [x] Step 4: 将本计划从 `docs/exec-plans/index.md` 的 Active 移到 Completed。

## Execution Notes

- [x] Task 1 Step 1-2: 已在 `client/src/features/chat/components/turn/__tests__/session-turn.test.tsx` 新增占位态 Chevron 开/关两组断言。
- [x] Task 1 Step 3: 初次运行 `cd client && npx vitest run src/features/chat/components/turn/__tests__/session-turn.test.tsx` 红灯，失败点为 `autoExpandReasoning = false` 场景下 Chevron 仍然带 `rotate-90`。
- [x] Task 2 Step 1-3: 已在 `client/src/features/chat/components/turn/session-turn.tsx` 消费 `autoExpandReasoning`，仅修正首包占位“思考中…” Chevron 旋转状态，未改 `showThinking` 判定与真实 `ReasoningPart` 状态机。
- [x] Task 3 Step 1: `cd client && npx vitest run src/features/chat/components/turn/__tests__/session-turn.test.tsx src/features/chat/components/turn/__tests__/reasoning-part.test.tsx` 通过（2 files, 20 tests）。
- [x] Task 3 Step 2: `cd client && npx tsc --noEmit` 通过。
