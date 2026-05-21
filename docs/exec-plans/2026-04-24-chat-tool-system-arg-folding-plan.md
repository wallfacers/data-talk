# Chat Tool System Arg Folding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让聊天区工具调用卡片对系统级长参数默认只显示参数名，完整值下沉到展开内容中。

**Architecture:** 保持 `BasicTool` 作为通用壳层，输入摘要策略收敛到 `GenericTool`。`GenericTool` 负责识别系统级长参数，把 trigger 展示与 body detail 分离；`BasicTool` 不承载 bridge-specific 判断。

**Tech Stack:** React 19, TypeScript, Tailwind CSS, Vitest, Testing Library.

---

## Design Inputs

- Source: [`client/DESIGN.md`](../../client/DESIGN.md)
- Applied constraints:
  - 工具卡片继续使用既有 `message.toolSurface` 语义，不新增视觉 token。
  - 聊天区保持 `comfortable` 密度，本次只调整信息层级和折叠呈现。
  - 文本沿用现有 UI/mono 字体体系，双主题语义不变。

## Spec Mapping

- Source: [`docs/product-specs/2026-04-24-chat-tool-system-arg-folding-design.md`](../product-specs/2026-04-24-chat-tool-system-arg-folding-design.md)
- Covers:
  - trigger 中系统级长参数只显示名称
  - 完整 `key=value` 下沉到展开内容
  - 普通短参数不被误伤

## File Structure

- Modify: `client/src/features/chat/components/tools/renderers/generic-tool.tsx`
- Modify: `client/src/features/chat/components/tools/__tests__/generic-tool.test.tsx`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-24-chat-tool-system-arg-folding-plan.md`

## Task 1: 先写回归测试锁定折叠行为

**Files:**
- Create: `client/src/features/chat/components/tools/__tests__/generic-tool.test.tsx`

- [x] Step 1: 新增测试，覆盖系统级长参数在 trigger 中只显示名称。
- [x] Step 2: 新增测试，覆盖展开后可以看到完整 `key=value`。
- [x] Step 3: 新增测试，覆盖普通短参数仍然保持 `key=value`。
- [x] Step 4: 运行 `cd client && npx vitest run src/features/chat/components/tools/__tests__/generic-tool.test.tsx`，确认红灯来自当前 `GenericTool` 仍然直接展示完整参数值。

## Task 2: 最小实现系统参数名折叠

**Files:**
- Modify: `client/src/features/chat/components/tools/renderers/generic-tool.tsx`

- [x] Step 1: 把输入摘要逻辑拆成“trigger 展示”和“body detail”两部分。
- [x] Step 2: 对系统级长参数仅在 trigger 中展示去前导下划线后的参数名。
- [x] Step 3: 在工具卡片 body 中补充被折叠参数的完整 `key=value` 明细。
- [x] Step 4: 保持普通短参数、已有 output 渲染和默认展开语义不变。

## Task 3: 集中验证与文档收尾

**Files:**
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-24-chat-tool-system-arg-folding-plan.md`

- [x] Step 1: 运行 `cd client && npx vitest run src/features/chat/components/tools/__tests__/generic-tool.test.tsx src/features/chat/components/tools/__tests__/basic-tool.test.tsx`。
- [x] Step 2: 运行 `cd client && npx tsc --noEmit`。
- [x] Step 3: 将本计划 checklist 更新为实际执行结果。
- [x] Step 4: 将本计划从 `docs/exec-plans/index.md` 的 Active 移到 Completed。

## Execution Notes

- [x] Task 1 Step 1-3: 已新建 `client/src/features/chat/components/tools/__tests__/generic-tool.test.tsx`，覆盖系统级长参数 trigger 仅显示名称、展开后可见完整 `key=value`、普通短参数保持 `key=value` 三个行为。
- [x] Task 1 Step 4: 初次运行 `cd client && npx vitest run src/features/chat/components/tools/__tests__/generic-tool.test.tsx` 红灯，失败点为 trigger 仍直接包含 `ses_*` / `call_*` / nonce 原始值，验证当前实现未折叠系统参数。
- [x] Task 2 Step 1-4: 已在 `client/src/features/chat/components/tools/renderers/generic-tool.tsx` 将输入摘要拆为 trigger args 与 detail args；系统级 bridge 参数在 trigger 中仅显示去前导下划线后的名称，完整 `key=value` 下沉到 body；普通短参数继续保留 `key=value`。
- [x] Task 3 Step 1: `cd client && npx vitest run src/features/chat/components/tools/__tests__/generic-tool.test.tsx src/features/chat/components/tools/__tests__/basic-tool.test.tsx` 通过（2 files, 6 tests）。
- [x] Task 3 Step 2: `cd client && npx tsc --noEmit` 通过。
