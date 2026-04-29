# Query Editor Session Context Truth Plan

**Status:** in_progress

**Goal:** 让 query editor 在 `session` 模式下把最新 session data context 作为 UI 显示与 SQL 执行的唯一真源，同时把 `resolvedContext` 降级为仅用于展示“上一次实际执行落点”的结果信息；并让模型能从 workspace 级状态读取每个 query editor tab 当前实际使用的上下文。

**Design Inputs:**
- `client/DESIGN.md`
- 约束应用：
  - 这是工具行为和模型可见状态修复，不增加新的 UI 结构。
  - 保持现有紧凑交互与信息密度，不新增说明性界面文案。
  - 保持现有 `query_editor` / `workspace` UI object 模式，只修正字段语义与汇总内容。

**Behavior Contract:**
- `session` 模式下：
  - UI 显示以最新 session data context 为准。
  - SQL 执行默认上下文也以最新 session data context 为准。
- `override` 模式下：
  - 仍以 tab override 为准。
- `resolvedContext`：
  - 只表示最近一次执行时后端实际落到的上下文。
  - 不参与下一次默认上下文推导。
- 模型可见性：
  - `query_editor.read('state')` 继续暴露实际有效上下文。
  - `workspace.read('state')` 需要补全每个 query editor tab 的 `connectionId / connectionName / database / schema / contextOverride / contextSource`，便于模型直接扫描。

**Scope:**
- `client/src/features/stage/components/sql-workbench-tab.tsx`
- `client/src/features/stage/utils/query-editor-actions.ts`
- `client/src/features/stage/adapters/QueryEditorAdapter.ts`
- `client/src/features/stage/adapters/WorkspaceAdapter.ts`
- 相关前端测试

**Non-Goals:**
- 不改后端 SQL execute API
- 不引入新的 tab 类型或新的上下文存储结构
- 不修改 SQL context panel 的视觉交互

## Tasks

- [ ] 增加失败测试，覆盖 session 模式下忽略陈旧 `resolvedContext`
- [ ] 增加失败测试，覆盖 `workspace.read('state')` 暴露每个 query editor tab 的完整有效上下文与来源
- [ ] 修正 query editor 的默认上下文解析逻辑，使 `resolvedContext` 仅作结果信息保留
- [ ] 扩展 workspace 状态摘要字段，使模型可直接读取每个 tab 的有效上下文
- [ ] 运行 `cd client && npx tsc --noEmit`
- [ ] 运行定向 vitest
- [ ] 只提交本次相关文件

## Verification

- [ ] session context 更新后，未 override 的 query editor 默认执行上下文会跟随变化
- [ ] session context 更新后，未 override 的 query editor 状态读取会反映最新 session context
- [ ] `workspace.read('state')` 可直接读到 query editor 的 `database/schema/contextSource`
- [ ] 前端类型检查通过
- [ ] 定向测试通过
