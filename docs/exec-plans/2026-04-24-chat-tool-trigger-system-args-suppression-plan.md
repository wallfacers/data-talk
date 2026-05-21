# Chat Tool Trigger System Args Suppression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让聊天区工具调用卡片的 trigger 只保留工具主名称和普通可读参数，不再显示系统 bridge 参数。

**Architecture:** 继续把输入摘要策略放在 `GenericTool`。系统级 bridge 参数只进入 body detail，不再生成 trigger args；普通短参数仍沿用现有 `key=value` 预览。`BasicTool` 通用壳层保持不变。

**Tech Stack:** React 19, TypeScript, Tailwind CSS, Vitest, Testing Library.

---

## Design Inputs

- Source: [`client/DESIGN.md`](../../client/DESIGN.md)
- Applied constraints:
  - 工具卡片继续使用既有 `message.toolSurface` 语义，不新增视觉 token。
  - 聊天区保持 `comfortable` 密度，本次只进一步收紧 trigger 信息层级。
  - 文本沿用现有 UI/mono 字体体系，双主题语义不变。

## Spec Mapping

- Source: [`docs/product-specs/2026-04-24-chat-tool-system-arg-folding-design.md`](../product-specs/2026-04-24-chat-tool-system-arg-folding-design.md)
- Covers:
  - trigger 不显示系统 bridge 参数
  - 完整 `key=value` 仍下沉到展开内容
  - 普通短参数继续保留 `key=value`

## File Structure

- Modify: `client/src/features/chat/components/tools/renderers/generic-tool.tsx`
- Modify: `client/src/features/chat/components/tools/__tests__/generic-tool.test.tsx`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-24-chat-tool-trigger-system-args-suppression-plan.md`

## Task 1: 先写回归测试锁定 trigger 隐藏行为

**Files:**
- Modify: `client/src/features/chat/components/tools/__tests__/generic-tool.test.tsx`

- [x] Step 1: 更新系统参数场景测试，要求 trigger 中不再显示 `dtOpenCodeSessionId` / `dtCallId` / `dtBridgeNonce`。
- [x] Step 2: 保持展开后完整 `key=value` 仍可见的断言。
- [x] Step 3: 保持普通短参数 `key=value` 仍在 trigger 中的断言。
- [x] Step 4: 运行 `cd client && npx vitest run src/features/chat/components/tools/__tests__/generic-tool.test.tsx`，确认红灯来自当前实现仍把系统参数名留在 trigger。

## Task 2: 最小实现 trigger 系统参数抑制

**Files:**
- Modify: `client/src/features/chat/components/tools/renderers/generic-tool.tsx`

- [x] Step 1: 调整输入摘要逻辑，让系统级 bridge 参数不再生成 trigger args。
- [x] Step 2: 保持这些参数继续进入 body detail，供展开后查看完整 `key=value`。
- [x] Step 3: 保持普通短参数和 output 渲染不变。

## Task 3: 集中验证与文档收尾

**Files:**
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-24-chat-tool-trigger-system-args-suppression-plan.md`

- [x] Step 1: 运行 `cd client && npx vitest run src/features/chat/components/tools/__tests__/generic-tool.test.tsx src/features/chat/components/tools/__tests__/basic-tool.test.tsx`。
- [x] Step 2: 运行 `cd client && npx tsc --noEmit`。
- [x] Step 3: 将本计划 checklist 更新为实际执行结果。
- [x] Step 4: 将本计划从 `docs/exec-plans/index.md` 的 Active 移到 Completed。

## Execution Notes

- [x] Task 1 Step 1-3: 已更新 `client/src/features/chat/components/tools/__tests__/generic-tool.test.tsx`，要求系统 bridge 参数在 trigger 中完全消失，但展开后完整 `key=value` 仍可见，普通短参数保持 `key=value`。
- [x] Task 1 Step 4: 初次运行 `cd client && npx vitest run src/features/chat/components/tools/__tests__/generic-tool.test.tsx` 红灯，失败点为 trigger 仍包含 `dtOpenCodeSessionId` / `dtCallId` / `dtBridgeNonce`。
- [x] Task 2 Step 1-3: 已在 `client/src/features/chat/components/tools/renderers/generic-tool.tsx` 调整摘要逻辑；系统级 bridge 参数不再生成 trigger args，仅保留 body detail；普通短参数与 output 渲染保持不变。
- [x] Task 3 Step 1: `cd client && npx vitest run src/features/chat/components/tools/__tests__/generic-tool.test.tsx src/features/chat/components/tools/__tests__/basic-tool.test.tsx` 通过（2 files, 6 tests）。
- [x] Task 3 Step 2: `cd client && npx tsc --noEmit` 通过。
