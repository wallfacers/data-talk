# Chat Tool Trigger Name Only Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让聊天区工具调用卡片的 trigger 只显示工具名称，所有输入参数统一下沉到展开内容。

**Architecture:** 继续把输入摘要策略留在 `GenericTool`。`GenericTool` 不再向 `BasicTool` 传任何 trigger args；所有 `part.state.input` 的 primitive 参数都渲染到 body detail，工具名称和已有 output 渲染保持不变。

**Tech Stack:** React 19, TypeScript, Tailwind CSS, Vitest, Testing Library.

---

## Design Inputs

- Source: [`client/DESIGN.md`](../../client/DESIGN.md)
- Applied constraints:
  - 工具卡片继续使用既有 `message.toolSurface` 语义，不新增视觉 token。
  - 聊天区保持 `comfortable` 密度，本次进一步压缩 trigger 信息层级。
  - 文本沿用现有 UI/mono 字体体系，双主题语义不变。

## Spec Mapping

- Source: [`docs/product-specs/2026-04-24-chat-tool-system-arg-folding-design.md`](../product-specs/2026-04-24-chat-tool-system-arg-folding-design.md)
- Covers:
  - trigger 只显示工具名称
  - 所有参数下沉到展开内容
  - output 展示与工具卡片通用交互保持不变

## File Structure

- Modify: `client/src/features/chat/components/tools/renderers/generic-tool.tsx`
- Modify: `client/src/features/chat/components/tools/__tests__/generic-tool.test.tsx`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-24-chat-tool-trigger-name-only-plan.md`

## Task 1: 先写回归测试锁定“只剩工具名”

**Files:**
- Modify: `client/src/features/chat/components/tools/__tests__/generic-tool.test.tsx`

- [x] Step 1: 更新系统参数测试，要求 trigger 中既没有系统参数名，也没有值。
- [x] Step 2: 新增或更新普通参数测试，要求 trigger 中不再显示 `object=workspace`、`action=open`、`limit=10` 等参数。
- [x] Step 3: 保持展开后完整 `key=value` 仍可见的断言。
- [x] Step 4: 运行 `cd client && npx vitest run src/features/chat/components/tools/__tests__/generic-tool.test.tsx`，确认红灯来自当前实现仍把普通短参数留在 trigger。

## Task 2: 最小实现 trigger 仅保留工具名称

**Files:**
- Modify: `client/src/features/chat/components/tools/renderers/generic-tool.tsx`

- [x] Step 1: 调整输入摘要逻辑，让任何参数都不再生成 trigger args。
- [x] Step 2: 保持所有 primitive 参数继续进入 body detail，供展开后查看完整 `key=value`。
- [x] Step 3: 保持工具名称、subtitle、output 渲染和默认展开语义不变。

## Task 3: 集中验证与文档收尾

**Files:**
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-24-chat-tool-trigger-name-only-plan.md`

- [x] Step 1: 运行 `cd client && npx vitest run src/features/chat/components/tools/__tests__/generic-tool.test.tsx src/features/chat/components/tools/__tests__/basic-tool.test.tsx`。
- [x] Step 2: 运行 `cd client && npx tsc --noEmit`。
- [x] Step 3: 将本计划 checklist 更新为实际执行结果。
- [x] Step 4: 将本计划从 `docs/exec-plans/index.md` 的 Active 移到 Completed。

## Execution Notes

- [x] Task 1 Step 1-3: 已更新 `client/src/features/chat/components/tools/__tests__/generic-tool.test.tsx`，要求 trigger 中不显示任何参数，包括系统 bridge 参数和 `object=workspace` / `action=open` / `limit=10` 这类普通短参数；展开后完整 `key=value` 仍可见。
- [x] Task 1 Step 4: 初次运行 `cd client && npx vitest run src/features/chat/components/tools/__tests__/generic-tool.test.tsx` 红灯，失败点为 trigger 仍包含 `object=workspace`、`action=open`、`limit=10`。
- [x] Task 2 Step 1-3: 已在 `client/src/features/chat/components/tools/renderers/generic-tool.tsx` 移除 trigger 摘要逻辑；`GenericTool` 不再向 `BasicTool` 传递 subtitle 或 args，所有 primitive 输入参数统一下沉到 body detail。
- [x] Task 3 Step 1: `cd client && npx vitest run src/features/chat/components/tools/__tests__/generic-tool.test.tsx src/features/chat/components/tools/__tests__/basic-tool.test.tsx` 通过（2 files, 6 tests）。
- [x] Task 3 Step 2: `cd client && npx tsc --noEmit` 通过。
