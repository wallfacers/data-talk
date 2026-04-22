# Stage SQL Editor Format Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Stage Query Editor 增加稳定的 SQL 格式化能力，统一支持工具栏 `Format` 按钮和 `Cmd/Ctrl + Shift + F` 快捷键。

**Architecture:** 新增一个 Stage 内部 `format-sql` helper 作为唯一格式化入口，封装 `sql-formatter` 调用、方言映射和异常 fallback；`SqlWorkbenchTab` 负责根据当前上下文推导方言并回写 store；`SqlMonacoEditor` 只负责注册快捷键并透出 `onFormat` 回调。

**Tech Stack:** React 19, TypeScript, Monaco Editor, Vitest, `sql-formatter`

---

## Spec Mapping

- [2026-04-22-stage-sql-editor-format-design.md](../product-specs/2026-04-22-stage-sql-editor-format-design.md)
  - §3/§5：新增统一 helper 并收敛格式化职责边界
  - §4：按钮和快捷键统一触发全文格式化
  - §6：helper / workbench / editor 三层测试覆盖
  - §8：保持现有执行链路不变并通过类型检查

## File Structure

- Create: `client/src/features/stage/utils/format-sql.ts`
- Create: `client/src/features/stage/utils/format-sql.test.ts`
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.test.tsx`
- Modify: `client/src/features/stage/components/sql-monaco-editor.tsx`

## Task 1: 建立统一 SQL formatting helper

**Files:**
- Create: `client/src/features/stage/utils/format-sql.ts`
- Create: `client/src/features/stage/utils/format-sql.test.ts`

- [x] Step 1: 写 helper failing tests，覆盖基础格式化、空白输入直返、未知方言 fallback。
- [x] Step 2: 运行 `cd client && npx vitest run src/features/stage/utils/format-sql.test.ts`，确认先红。
- [x] Step 3: 实现 `format-sql.ts`，封装 `sql-formatter` 和方言映射。
- [x] Step 4: 重新运行 `cd client && npx vitest run src/features/stage/utils/format-sql.test.ts`，确认转绿。

## Task 2: 让 Query Editor 通过 helper 执行格式化

**Files:**
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.test.tsx`

- [x] Step 1: 先补 failing tests，验证点击 `Format` 会按连接方言调用 helper 并回写编辑器内容。
- [x] Step 2: 运行 `cd client && npx vitest run src/features/stage/components/sql-workbench-tab.test.tsx`，确认先红。
- [x] Step 3: 将 `SqlWorkbenchTab` 的格式化逻辑切到 `format-sql.ts`，按当前连接/上下文推导 dialect，并保持异常静默 fallback。
- [x] Step 4: 重新运行 `cd client && npx vitest run src/features/stage/components/sql-workbench-tab.test.tsx`，确认转绿。

## Task 3: 给 Monaco 编辑器补 `Cmd/Ctrl + Shift + F`

**Files:**
- Modify: `client/src/features/stage/components/sql-monaco-editor.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.test.tsx`

- [x] Step 1: 先补 failing tests，验证编辑器会注册格式化快捷键并触发 `onFormat`。
- [x] Step 2: 运行 `cd client && npx vitest run src/features/stage/components/sql-workbench-tab.test.tsx`，确认先红。
- [x] Step 3: 给 `SqlMonacoEditor` 增加 `onFormat` prop，并注册 `Cmd/Ctrl + Shift + F`。
- [x] Step 4: 重新运行 `cd client && npx vitest run src/features/stage/components/sql-workbench-tab.test.tsx`，确认转绿。

## Task 4: 收尾验证与文档归档

**Files:**
- Modify: `docs/exec-plans/2026-04-22-stage-sql-editor-format-plan.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/product-specs/2026-04-22-stage-sql-editor-format-design.md`

- [x] Step 1: 运行 `cd client && npx vitest run src/features/stage/utils/format-sql.test.ts src/features/stage/components/sql-workbench-tab.test.tsx`
- [x] Step 2: 运行 `cd client && npx tsc --noEmit`
- [x] Step 3: 将计划文件 checkbox 全部勾完并补实际结果说明。
- [x] Step 4: 将 `docs/exec-plans/index.md` 中该计划从 Active 移到 Completed，并把设计 spec 状态改为已落地状态。

## Execution Notes

- Task 1 红灯表现：`format-sql.test.ts` 初次运行因 `./format-sql` 不存在而失败；补 helper 后 3 tests 转绿。
- Task 2/3 红灯表现：`sql-workbench-tab.test.tsx` 初次运行失败于“未调用 helper”和“未注册第二个 Monaco command”；补 `SqlWorkbenchTab`/`SqlMonacoEditor` 最小实现后 11 tests 转绿。
- 最终验证：
  - `cd client && npx vitest run src/features/stage/utils/format-sql.test.ts src/features/stage/components/sql-workbench-tab.test.tsx` → 2 files, 14 tests passed
  - `cd client && npx tsc --noEmit` → 通过（无输出）
