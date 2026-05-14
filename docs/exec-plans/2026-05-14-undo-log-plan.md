# Undo Log — DML 操作回滚

**Goal:** DML（INSERT/UPDATE/DELETE）执行前自动捕获 before-state 快照，生成 inverse SQL，用户可在前端 DML Summary Panel 一键发起回滚。

**Architecture:** 后端 5 层（Domain → Application → Infrastructure → Adapter）+ 前端 Store + UI。无新外部依赖。

**Tech Stack:** Spring Boot 3.5, Java 21, SQLite metadata store, Apache Calcite (SQL parsing), JdbcTemplate, React 19, TypeScript, Zustand, Vitest, JUnit 5.

---

## Status

- **Created:** 2026-05-14
- **Completed:** 2026-05-14
- **State:** Completed
- **Parent:** [Phase 3 Roadmap](./2026-05-12-phase-3-roadmap-plan.md) Task 15
- **OpenSpec Change:** `openspec/changes/archive/2026-05-14-undo-log/`

---

## Completed Tasks

### 1. Domain Layer — 模型定义

- [x] 1.1 `UndoLogEntry` record: id, sessionId, connectionId, databaseName, schemaName, tableName, operation, originalSql, inverseSql, beforeState, affectedRows, undoable, status, expiresAt, createdAt, undoneAt
- [x] 1.2 `UndoCapture` record: undoable, undoLogId, beforeState, inverseSql, tableName, operation, affectedRows
- [x] 1.3 `UndoOutcome` sealed interface: Captured, NotUndoable, Skipped
- [x] 1.4 `UndoRequest` record: undoLogId, confirmed, riskAck
- [x] 1.5 `UndoResult` sealed interface: RequiresConfirmation, Undone, Expired, AlreadyUndone, NotFound

### 2. Infrastructure Layer — 持久化

- [x] 2.1 Flyway V25__undo_log.sql: undo_log 表 + idx_undo_log_session + idx_undo_log_expires
- [x] 2.2 `UndoLogRepository` (application 接口): insert, activate, findById, markUndone, markExpiredBatch, deleteOldExpired, findExpiredActive
- [x] 2.3 JDBC 实现 (infrastructure): JdbcTemplate 操作 SQLite 元数据库

### 3. Application Layer — 核心逻辑

- [x] 3.1 `InverseSqlGenerator`: INSERT→DELETE, UPDATE→反向UPDATE, DELETE→INSERT
- [x] 3.2 `UndoLogCapture`: Calcite 解析 + before-state SELECT + 主键检测 + 100 行阈值
- [x] 3.3 `UndoExecuteService`: 状态校验 (expired/alreadyUndone/notFound) + inverse SQL 执行

### 4. SqlExecuteService 集成

- [x] 4.1 DML ExecutionUnit 插入 UndoLogCapture 调用
- [x] 4.2 执行成功后 activate pending → active；rollback 删除 pending
- [x] 4.3 INSERT 通过 Statement.getGeneratedKeys() 获取自增主键
- [x] 4.4 SqlExecuteResultItem 扩展 undoLogId + undoable 字段

### 5. Adapter Layer — REST + 定时任务

- [x] 5.1 POST /api/sql/undo 端点
- [x] 5.2 UndoLogCleanupScheduler (每日清理过期记录)
- [x] 5.3 L3 确认弹窗文案更新 (undoable DML 显示"可在 3 天内回滚")

### 6. Frontend — API + Store

- [x] 6.1 `undoDml()` API 函数 (POST /api/sql/undo)
- [x] 6.2 `sql-workbench-store.ts` 扩展 undo 状态 (idle/confirming/undoing/undone/error)

### 7. Frontend — UI 组件

- [x] 7.1 DML Summary Panel Undo 按钮 (undoable=true 时渲染)
- [x] 7.2 Undo 确认弹窗 (inverse SQL 预览 + 受影响行数 + 表名)
- [x] 7.3 Undo 完成后状态更新 ("Undo completed · N rows reverted")

### 8. 测试

- [x] 8.1 InverseSqlGenerator 单元测试
- [x] 8.2 UndoLogCapture 单元测试 (主键检测/行数阈值/Calcite 解析失败降级)
- [x] 8.3 UndoExecuteService 单元测试
- [x] 8.4 SqlExecuteService 集成 UndoLogCapture (端到端 IT 待后续补充)
- [x] 8.5 POST /api/sql/undo Controller 测试
- [x] 8.6 sql-dml-summary-panel 前端测试
- [x] 8.7 undoDml API + store 状态变更测试

### 9. 验证

- [x] 9.1 后端 `mvn clean verify` BUILD SUCCESS
- [x] 9.2 前端 `npx tsc --noEmit` 零错误
- [x] 9.3 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` 已更新

---

## Deferred to Phase 2

- 全局操作日志面板 (按连接/表名/类型筛选 + before/after 行级对比)
- 批量 Undo (整个 execute 请求级别 / 按时间范围)
- DDL 回滚 (ALTER/DROP/TRUNCATE)
- MCP tool `datatalk_list_operations` / `datatalk_rollback_operation` (AI agent 驱动的回滚)
- 无主键表的全列匹配降级回滚

## Design References

- OpenSpec proposal: `openspec/changes/archive/2026-05-14-undo-log/proposal.md`
- OpenSpec design: `openspec/changes/archive/2026-05-14-undo-log/design.md`
- OpenSpec tasks: `openspec/changes/archive/2026-05-14-undo-log/tasks.md`
