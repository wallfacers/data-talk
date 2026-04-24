# Chat Tool Call Overflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复聊天区工具调用卡片在长参数场景下内容溢出卡片边界的问题。

**Architecture:** 沿用现有 `BasicTool` 组件作为统一工具调用触发器，不新增新的 renderer 或样式 token。通过调整 trigger 区的行内布局，让标题/副标题与参数按现有语义分层显示，并为机器生成的长参数提供安全换行能力。

**Tech Stack:** React 19, TypeScript, Tailwind CSS, Vitest, Testing Library.

---

## Design Inputs

- Source: [`client/DESIGN.md`](../../client/DESIGN.md)
- Applied constraints:
  - 聊天消息工具卡片继续使用既有 `message.toolSurface` 语义，不引入新的视觉层级或颜色 token。
  - 聊天区属于 `comfortable` 密度，修复仅调整触发行的换行和对齐，不改变卡片边界、交互 affordance 和正文节奏。
  - 文本继续沿用现有 UI/mono 字体体系；长机器参数可以换行，但不应破坏标题和副标题的可读性。
  - 双主题语义保持不变，本次不新增主题分支样式。

## Spec Mapping

- Source: 用户提供的缺陷截图（工具调用框内长参数溢出）。
- Covers:
  - 工具调用触发区必须在长 `sessionId` / `callId` / `nonce` 等参数下保持卡片内收，不产生水平溢出。
  - 不改变工具卡片展开/收起逻辑，仅修复触发行排版。

## File Structure

- Modify: `client/src/features/chat/components/tools/basic-tool.tsx`
- Modify: `client/src/features/chat/components/tools/__tests__/basic-tool.test.tsx`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-24-chat-tool-call-overflow-plan.md`

## Task 1: 为长参数溢出补失败用例

**Files:**
- Modify: `client/src/features/chat/components/tools/__tests__/basic-tool.test.tsx`

- [x] Step 1: 新增长参数场景测试，约束 trigger 内容区允许换行，参数节点具备行内断开能力。
- [x] Step 2: 运行 `cd client && npx vitest run src/features/chat/components/tools/__tests__/basic-tool.test.tsx`，确认红灯来自当前 `BasicTool` 单行布局与参数缺少断行约束。

## Task 2: 最小修复 BasicTool 触发行布局

**Files:**
- Modify: `client/src/features/chat/components/tools/basic-tool.tsx`

- [x] Step 1: 将标题/副标题行与参数行分开，保留原有交互与状态控制。
- [x] Step 2: 为长参数增加容器内换行能力，确保不会撑出卡片宽度。
- [x] Step 3: 保持 pending / arrow / action button 行为不变。

## Task 3: 集中验证与文档收尾

**Files:**
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-24-chat-tool-call-overflow-plan.md`

- [x] Step 1: 运行 `cd client && npx vitest run src/features/chat/components/tools/__tests__/basic-tool.test.tsx`。
- [x] Step 2: 运行 `cd client && npx tsc --noEmit`。
- [x] Step 3: 将本计划 checklist 更新为实际执行结果。
- [x] Step 4: 将本计划从 `docs/exec-plans/index.md` 的 Active 移到 Completed。

## Execution Notes

- [x] Task 1 Step 1: 已在 `client/src/features/chat/components/tools/__tests__/basic-tool.test.tsx` 新增长参数工具调用场景，锁定 trigger 顶对齐、参数容器允许换行、参数文本可断开三个约束。
- [x] Task 1 Step 2: 初次运行 `cd client && npx vitest run src/features/chat/components/tools/__tests__/basic-tool.test.tsx` 红灯，失败点为 `tool-trigger` 仍使用 `items-center`，验证当前布局确实不满足长参数场景。
- [x] Task 2 Step 1-3: 已在 `client/src/features/chat/components/tools/basic-tool.tsx` 将 trigger 内容拆为标题行和参数行，外层改为顶对齐，参数行使用 `flex-wrap`，参数文本增加 `break-all`，未改 pending / arrow / action button 逻辑。
- [x] Task 3 Step 1: `cd client && npx vitest run src/features/chat/components/tools/__tests__/basic-tool.test.tsx` 通过（1 file, 4 tests）。
- [x] Task 3 Step 2: `cd client && npx tsc --noEmit` 通过。
