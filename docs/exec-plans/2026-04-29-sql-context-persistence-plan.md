# SQL Context Persistence Plan

**Status:** completed

**Goal:** 修复 SQL 编辑器里通过“SQL 执行上下文”面板应用并固定的 database/schema 选择在 Stage Window 关闭后恢复路径中丢失的问题。

**Design Inputs:**
- `client/DESIGN.md`
- 约束应用：
  - 这是现有工具面板行为修复，不引入新的 UI 结构或视觉层级。
  - 保持紧凑、桌面型信息密度，不新增说明性文案。
  - 继续使用现有 token / 组件 / 交互，不做额外样式发散。

**Root Cause Hypothesis:**
- Query editor 的 context override 写入了 tab payload，但前端 stage 持久化只在 `sqlText` 变化时写 content payload。
- 单纯切换 connection/database/schema 并点击“应用并固定”时，`contextOverride` 不会进入持久化 payload。
- 恢复时又依赖 payload 中的 `contextOverride` 来重建 override，导致 database/schema 丢失。

**Scope:**
- `client/src/features/stage/persistence/`
- `client/src/features/stage/registry/`
- `client/src/features/stage/utils/normalize-query-editor-payload.ts`
- 相关前端测试

**Non-Goals:**
- 不改 SQL 执行 API 契约
- 不调整 SQL 上下文面板的 UI/文案
- 不处理与本 bug 无关的 query editor 全量恢复行为

## Tasks

- [x] 增加一个前端失败测试，覆盖 query editor context override 的 payload 持久化
- [x] 修正 query editor payload 快照逻辑，确保 `sqlText` 与 `contextOverride` 一起持久化
- [x] 通过同一 payload 快照把当前 SQL 与 override 一起写回，恢复路径可继续从 payload 读回 `contextOverride`
- [x] 运行 `cd client && npx tsc --noEmit`
- [x] 运行定向 vitest

## Verification

- [x] context override 改动会触发 payload 写入
- [x] 重新恢复 tab 后，database/schema/context override 仍可解析
- [x] 前端类型检查通过
- [x] 定向测试通过

## Outcome

- 新增定向测试 `stage-persistence-bootstrap.query-editor.test.ts`，先验证红灯，再验证绿灯。
- `stage-persistence-bootstrap.ts` 现在会在 query editor 的 override 变化时也安排 content payload 持久化，不再只盯 `sqlText`。
- 持久化 payload 改为写入完整 query editor 快照中的关键字段：至少包含 `initialSql`、`sqlText`、`contextOverride`，避免恢复时丢失 database/schema override。
- 验证结果：
  - `cd client && npx vitest run src/features/stage/persistence/__tests__/stage-persistence-bootstrap.query-editor.test.ts src/features/stage/persistence/__tests__/stage-persistence-bootstrap.er.test.ts src/features/stage/utils/query-editor-actions.test.ts src/features/stage/components/sql-workbench-tab.test.tsx`
  - `cd client && npx tsc --noEmit`
