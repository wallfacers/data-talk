## Why

undo_log 表已经为每条 DML 操作记录了完整数据（session、connection、表名、原始 SQL、inverse SQL、before_state、状态、过期时间），但这些数据目前只能通过 SQL 执行结果面板逐条内联查看。用户无法按 connection 维度纵览所有操作历史、无法批量管理 undo 操作、也无法实时感知当前连接上的数据变更活动。需要一个独立的 operation log 面板来释放 undo_log 数据的审计和管控价值。

## What Changes

- **新增 REST API**: `GET /api/connections/{id}/op-logs` 分页列表查询，支持按 status、operation、table、时间范围、SQL 全文搜索过滤；`POST /api/connections/{id}/op-logs/batch-undo` 批量回滚
- **新增 SSE 广播通道**: `GET /api/connections/{id}/op-log/stream`，当 undo_log 有新记录创建/状态变更时实时推送轻量通知
- **新增后端组件**: `ConnectionOpLogBus`（per-connection 事件分发）、`UndoLogRepository` 新增分页查询方法、新的 REST controller
- **新增前端 Stage Tab 类型**: `operation_log`，workspace scope，persistent，展示按 connection 聚合的操作日志
- **新增前端组件**: OperationLogTab（基于 @tanstack/react-table）、OpLogDetailRow（展开行展示 SQL/before_state）、OpLogFilterBar、批量 undo 操作栏
- **新增 Tab 打开入口**: sidebar connection 右键菜单、SQL 执行结果面板链接、工具栏新建

## Capabilities

### New Capabilities

- `connection-op-log-query`: 按 connection 维度分页查询 undo_log 列表，支持多维度过滤（status/operation/table/dateRange/sqlText），返回 JOIN session title 的完整记录
- `connection-op-log-sse`: per-connection SSE 广播通道，实时推送 undo_log 创建/状态变更事件，前端订阅后触发 TanStack Query 刷新
- `connection-op-log-batch-undo`: 批量选择多条 active undo_log 记录并执行 undo，支持汇总确认 UX
- `connection-op-log-tab`: 独立 stage tab 组件，展示操作日志表格（排序/过滤/分页/行展开）、自适应 before_state 展示（JSON 树/mini 表格/diff）、实时更新

### Modified Capabilities

- `undo-log`: 新增列表查询和批量 undo 相关的 requirement，不影响已有的单条 undo 流程

## Impact

- **Backend — UndoLogRepository**: 新增 `findByConnectionId(connectionId, page, size, filters)` 分页查询方法和 `batchUndo(ids)` 方法
- **Backend — New Controller**: `ConnectionOpLogController`，处理 `/api/connections/{id}/op-logs` 和 SSE stream
- **Backend — New Component**: `ConnectionOpLogBus` + `ConnectionOpLogBusRegistry`，per-connection SSE 广播
- **Backend — UndoLogCapture**: 写入 undo_log 后发布 Spring ApplicationEvent，触发 SSE 广播
- **Frontend — TabTypeRegistry**: 注册 `operation_log` tab type
- **Frontend — New Components**: OperationLogTab、OpLogTable、OpLogDetailRow、OpLogFilterBar、BatchUndoBar
- **Frontend — New API Client**: `connection-op-log.ts`（list、batch-undo、SSE subscription）
- **Frontend — New Store**: `op-log-store.ts`（可选，或直接用 TanStack Query server state）
- **Frontend — Sidebar**: Connection 右键菜单增加 "View Operation Log" 入口
- **Frontend — SqlDmlSummaryPanel**: 增加 "View all operations" 链接
- **Frontend — Toolbar**: 新建 tab 选项增加 operation log
