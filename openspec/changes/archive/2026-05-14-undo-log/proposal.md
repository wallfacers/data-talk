## Why

用户执行 DML（INSERT/UPDATE/DELETE）后无法撤销误操作。当前 L2/L3 确认流程只能预防，不能补救。执行后唯一的补救手段是手动编写反向 SQL，门槛高且容易出错。需要一个自动化的操作日志与一键回滚机制。

## What Changes

- 新增 `undo_log` 元数据表（Flyway migration），记录每条 DML 的 before-state 快照、反向 SQL、过期时间
- 后端 `SqlExecuteService` 在 DML 执行前自动捕获受影响行快照（通过 Calcite 解析 WHERE + JDBC `getPrimaryKeys` 主键检测）
- 后端自动生成 inverse SQL（INSERT→DELETE, UPDATE→反向UPDATE, DELETE→反向INSERT）
- 新增 `/api/sql/undo` 端点或 `datatalk.execute_undo` Action，执行回滚并走现有 SQL 确认流程
- 前端 DML Summary Panel 增加 Undo 按钮 + 回滚确认弹窗
- 3 天过期 + 定时清理任务

**回滚条件约束**：仅支持有主键的单表 DML、受影响行 ≤ 100、Calcite 可解析的 SQL。不满足条件仍记录日志但不提供回滚。

## Capabilities

### New Capabilities

- `undo-log`: DML 操作的前后快照捕获、反向 SQL 生成、undo_log 表持久化、过期清理

### Modified Capabilities

- `sql-confirmation`: 回滚操作本身是 DML，需复用确认流程；L3 "cannot be undone" 文案需更新为 "Undo available within 3 days"

## Impact

**Backend**:
- `server/data-talk-infrastructure/` — Flyway migration (V25), `UndoLogRepository` JDBC 实现
- `server/data-talk-application/` — `UndoLogCapture`, `InverseSqlGenerator`, `UndoExecuteService`, `SqlExecuteService` 集成点
- `server/data-talk-adapter/` — `UndoController` 或 `ExecuteUndoAction`, 过期清理定时任务
- `server/data-talk-domain/` — 新增 `UndoLogEntry` 领域模型

**Frontend**:
- `client/src/features/stage/components/sql-dml-summary-panel.tsx` — Undo 按钮
- `client/src/services/api/sql.ts` — `undoDml()` API 调用
- `client/src/features/stage/stores/sql-workbench-store.ts` — undo 结果状态管理

**Dependencies**: 无新外部依赖。使用现有 Calcite 解析器 + JDBC DatabaseMetaData。

**Data source compatibility**: 回滚依赖 `getPrimaryKeys()` 和 WHERE 子句解析。部分数据源（ClickHouse, Hive, Trino 等分析型）可能不支持主键或 DML — 降级为不可回滚。详见 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`。

**Open BUGs overlap**: 无。当前 `docs/bugs/index.md` 无 open BUG 与 undo-log 范围重叠。

## Design Inputs

本变更涉及前端 UI（Undo 按钮 + 确认弹窗）。适用的 `client/DESIGN.md` 约束：
- 使用 shadcn/ui 组件库（AlertDialog 用于确认弹窗）
- 遵循现有 DML Summary Panel 的视觉风格
- 操作反馈使用 Toast 通知
