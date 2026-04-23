# Reasoning Auto-Expand Setting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在“设置 > 通用”新增“思考中自动展开”开关，并使 reasoning 面板在思考开始时按设置决定是否自动展开、在思考结束后统一自动收起。

**Architecture:** 沿用现有 `ui-settings-store` 作为全局前端偏好存储，在 `general-panel` 增加新的 `Switch` 设置项，并在 `reasoning-part.tsx` 中消费该设置重写自动展开状态机。实现保持为纯前端改动，不引入按会话维度的新状态，也不改变 reasoning 面板视觉结构。

**Tech Stack:** React 19, Zustand, shadcn/ui, Vitest, Testing Library, TypeScript.

---

## Design Inputs

- Source: [`client/DESIGN.md`](../../client/DESIGN.md)
- Applied constraints:
  - 设置页属于 `compact` 密度，新增项必须沿用现有设置行布局和开关组件，不重做信息架构。
  - 视觉样式继续走现有语义 token 与 shadcn 组件，不引入临时颜色和自定义视觉语言。
  - 改动保持在现有 `General` 页内最小插入，不影响主题、语言、会话管理区的层级关系。

## Spec Mapping

- Source: [`docs/product-specs/2026-04-24-reasoning-auto-expand-setting-design.md`](../product-specs/2026-04-24-reasoning-auto-expand-setting-design.md)
- Covers:
  - 目标 1-5：新增设置项、默认关闭、按配置控制思考开始时自动展开、思考完成统一收起
  - 目标 6：保留用户手动展开/收起能力

## File Structure

- Modify: `client/src/stores/ui-settings-store.ts`
- Modify: `client/src/features/settings/general/general-panel.tsx`
- Modify: `client/src/i18n/messages.ts`
- Modify: `client/src/features/chat/components/turn/reasoning-part.tsx`
- Modify: `client/src/features/settings/general/general-panel.test.tsx`
- Modify: `client/src/features/chat/components/turn/__tests__/reasoning-part.test.tsx`
- Create: `client/src/stores/ui-settings-store.test.ts`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-24-reasoning-auto-expand-setting-plan.md`

## Task 1: 设置存储补齐新偏好

**Files:**
- Modify: `client/src/stores/ui-settings-store.ts`
- Create: `client/src/stores/ui-settings-store.test.ts`

- [x] Step 1: 先写 store 测试，覆盖 `autoExpandReasoning` 默认值为 `false`，以及 setter 会同步更新 store 与 `localStorage`。
- [x] Step 2: 运行 `cd client && npx vitest run src/stores/ui-settings-store.test.ts`，确认红灯（字段/方法尚不存在）。
- [x] Step 3: 在 `ui-settings-store.ts` 增加 `autoExpandReasoning` 与 `setAutoExpandReasoning`，并接入现有 `ui-settings` 持久化。
- [x] Step 4: 复跑 `cd client && npx vitest run src/stores/ui-settings-store.test.ts`，确认转绿。

## Task 2: 设置页新增“思考中自动展开”开关

**Files:**
- Modify: `client/src/features/settings/general/general-panel.tsx`
- Modify: `client/src/features/settings/general/general-panel.test.tsx`
- Modify: `client/src/i18n/messages.ts`

- [x] Step 1: 先扩展 `general-panel.test.tsx`，覆盖新开关渲染、默认关闭、点击后调用 store setter，并断言其位于“分栏拖拽调整”上方。
- [x] Step 2: 运行 `cd client && npx vitest run src/features/settings/general/general-panel.test.tsx`，确认红灯（新文案/新开关不存在）。
- [x] Step 3: 在设置页新增中英文文案和开关 UI，绑定 `autoExpandReasoning` store。
- [x] Step 4: 复跑 `cd client && npx vitest run src/features/settings/general/general-panel.test.tsx`，确认转绿。

## Task 3: reasoning 面板按设置控制自动展开

**Files:**
- Modify: `client/src/features/chat/components/turn/reasoning-part.tsx`
- Modify: `client/src/features/chat/components/turn/__tests__/reasoning-part.test.tsx`

- [x] Step 1: 先扩展 `reasoning-part.test.tsx`，覆盖设置关闭时流式开始默认收起、设置打开时流式开始默认展开、两种模式下流式结束统一收起。
- [x] Step 2: 运行 `cd client && npx vitest run src/features/chat/components/turn/__tests__/reasoning-part.test.tsx`，确认红灯（现有逻辑固定自动展开）。
- [x] Step 3: 在 `reasoning-part.tsx` 消费新设置，重写 `open` 状态初始化与流式边界 effect，保留用户手动切换能力。
- [x] Step 4: 复跑 `cd client && npx vitest run src/features/chat/components/turn/__tests__/reasoning-part.test.tsx`，确认转绿。

## Task 4: 集中验证与文档收尾

**Files:**
- Modify: `docs/exec-plans/2026-04-24-reasoning-auto-expand-setting-plan.md`
- Modify: `docs/exec-plans/index.md`

- [x] Step 1: 运行 `cd client && npx vitest run src/stores/ui-settings-store.test.ts src/features/settings/general/general-panel.test.tsx src/features/chat/components/turn/__tests__/reasoning-part.test.tsx`。
- [x] Step 2: 运行 `cd client && npx tsc --noEmit`。
- [x] Step 3: 将本计划 checklist 与执行说明更新为实际结果。
- [x] Step 4: 完成后将本计划从 `docs/exec-plans/index.md` 的 Active 移到 Completed。
 
## Execution Notes

- [x] Task 1 Step 1: 已新增 `client/src/stores/ui-settings-store.test.ts`，覆盖默认值与持久化。
- [x] Task 1 Step 2: 初次运行 `cd client && npx vitest run src/stores/ui-settings-store.test.ts` 红灯：
  - `autoExpandReasoning` 为 `undefined`
  - `setAutoExpandReasoning is not a function`
- [x] Task 1 Step 3: 已在 `ui-settings-store.ts` 新增 `autoExpandReasoning` 与 `setAutoExpandReasoning`，并接入 `ui-settings` 持久化。
- [x] Task 1 Step 4: 复跑 `cd client && npx vitest run src/stores/ui-settings-store.test.ts` 通过（2 tests passed）。

- [x] Task 2 Step 1: 已扩展 `general-panel.test.tsx`，覆盖新开关默认关闭、切换行为与位置顺序。
- [x] Task 2 Step 2: 初次运行 `cd client && npx vitest run src/features/settings/general/general-panel.test.tsx` 红灯：找不到“思考中自动展开”文案与对应 switch。
- [x] Task 2 Step 3: 已在 `general-panel.tsx` 与 `messages.ts` 新增设置项和中英文文案，位置放在“分栏拖拽调整”上方。
- [x] Task 2 Step 4: 复跑 `cd client && npx vitest run src/features/settings/general/general-panel.test.tsx` 通过（4 tests passed）。

- [x] Task 3 Step 1: 已扩展 `reasoning-part.test.tsx`，覆盖设置开/关时的默认展开行为，以及手动展开后在完成态统一收起。
- [x] Task 3 Step 2: 初次运行 `cd client && npx vitest run src/features/chat/components/turn/__tests__/reasoning-part.test.tsx` 红灯：旧实现会在流式时固定自动展开，导致“关闭时默认收起”用例失败。
- [x] Task 3 Step 3: 已在 `reasoning-part.tsx` 消费 `autoExpandReasoning`，改为仅在流式开始边界按设置决定是否自动展开，流式结束统一收起。
- [x] Task 3 Step 4: 复跑 `cd client && npx vitest run src/features/chat/components/turn/__tests__/reasoning-part.test.tsx` 通过（8 tests passed）。

- [x] Task 4 Step 1: `cd client && npx vitest run src/stores/ui-settings-store.test.ts src/features/settings/general/general-panel.test.tsx src/features/chat/components/turn/__tests__/reasoning-part.test.tsx` 通过（3 files, 14 tests）。
- [x] Task 4 Step 2: `cd client && npx tsc --noEmit` 通过。
- [x] Task 4 Step 3: 本计划 checklist 与执行说明已更新为实际结果。
- [x] Task 4 Step 4: `docs/exec-plans/index.md` 已从 Active 移到 Completed。
