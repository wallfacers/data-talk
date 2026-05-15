## 1. Backend — Database & Repository

- [x] 1.1 新增 Flyway 迁移：为 undo_log 添加 `idx_undo_log_conn_status_created(connection_id, status, created_at DESC)` 复合索引
- [x] 1.2 UndoLogRepository 新增 `findByConnectionId(connectionId, page, size, filters)` 分页查询方法，支持 status/operation/table/dateRange/q 过滤，LEFT JOIN sessions 获取 sessionTitle，列表不返回 before_state
- [x] 1.3 UndoLogRepository 新增 `findByIdAndConnectionId(undoLogId, connectionId)` 详情查询方法，返回完整记录含 before_state
- [x] 1.4 UndoLogRepository 新增 `batchUndo(ids)` 方法，逐条调用 UndoExecuteService 并收集结果
- [x] 1.5 编写 UndoLogRepository 新方法的单元测试（分页、过滤、JOIN session title、空结果）

## 2. Backend — SSE Broadcast

- [x] 2.1 创建 `UndoLogCreatedEvent` Spring ApplicationEvent（包含 undoLogId、connectionId、operation、tableName、affectedRows、createdAt）
- [x] 2.2 创建 `UndoLogStatusChangedEvent` Spring ApplicationEvent（包含 undoLogId、connectionId、status、undoneAt）
- [x] 2.3 创建 `ConnectionOpLogBus` 类：per-connection 事件分发，ConcurrentHashMap<String, Consumer> 管理 subscribers，publish 方法，轻量无 buffer/persistence
- [x] 2.4 创建 `ConnectionOpLogBusRegistry` 类：ConcurrentHashMap<connectionId, ConnectionOpLogBus>，首个 subscriber 创建 bus，最后一个断开后 30s grace period 销毁
- [x] 2.5 创建 `OpLogSseHeartbeatScheduler`：每 30s 发送 SSE comment 心跳帧（复用 SseHeartbeatScheduler）
- [x] 2.6 在 UndoLogCapture.insert() 成功后发布 UndoLogCreatedEvent
- [x] 2.7 在 UndoExecuteService.markUndone() 后发布 UndoLogStatusChangedEvent
- [x] 2.8 在 UndoLogCleanupScheduler.markExpiredBatch() 后发布 UndoLogStatusChangedEvent（批量）
- [x] 2.9 编写 ConnectionOpLogBus 和 Registry 的单元测试（生命周期、grace period、多 subscriber）

## 3. Backend — REST Controller

- [x] 3.1 创建 `ConnectionOpLogController`，基础路径 `/api/connections/{connectionId}/op-logs`
- [x] 3.2 实现 `GET /` 分页列表端点，解析 query params（page/size/status/operation/table/from/to/q），调用 UndoLogRepository 分页查询，返回 `{ items, total, page, size }`
- [x] 3.3 实现 `GET /{undoLogId}` 详情端点，返回完整记录含 before_state
- [x] 3.4 实现 `POST /batch-undo` 端点，接受 `{ undoLogIds }`，调用 UndoLogRepository.batchUndo，返回逐条结果
- [x] 3.5 实现 `GET /stream` SSE 端点，建立 ConnectionOpLogBus subscriber，返回 text/event-stream，注册心跳，处理断开清理
- [x] 3.6 编写 Controller 集成测试（MockMVC）：分页、过滤、详情、batch-undo、SSE stream 建立

## 4. Frontend — API Client & Types

- [x] 4.1 创建 `client/src/services/api/connection-op-log.ts`：定义 TypeScript 类型（OpLogItem、OpLogDetail、OpLogListResponse、BatchUndoResult）和 API 函数（listOpLogs、getOpLogDetail、batchUndoOpLogs）
- [x] 4.2 创建 `client/src/services/api/connection-op-log-sse.ts`：SSE 订阅函数，使用 eventsource-parser 解析事件流，返回 subscribe/unsubscribe 控制对象
- [x] 4.3 编写 API client 的 vitest 测试（mock fetch 验证请求参数和响应解析）

## 5. Frontend — Tab Registration & Routing

- [x] 5.1 在 `tab-type-registry.ts` 注册 `operation_log` tab type（scope: workspace, persistent: true, payloadSource: stage_tab, icon: ScrollText）
- [x] 5.2 在 `stage-tab-content.tsx` 添加 `case 'operation_log'` 渲染分支，lazy load OperationLogTab
- [x] 5.3 在 `open-or-focus-stage-tool-tab.ts` 添加 `operation_log` 分支，identity key = `operation_log::connectionId`（新建 `open-op-log-tab.ts`）
- [x] 5.4 在 `stage-ui-object-registry.tsx` 注册 OperationLogAdapter（如需 AI ui_read/ui_patch 支持）— 当前不需要，跳过

## 6. Frontend — OperationLogTab Component

- [x] 6.1 创建 `OperationLogTab` 组件框架：连接 TanStack Query 获取列表数据，建立 SSE 订阅，收到事件后 invalidate query
- [x] 6.2 创建 `OpLogFilterBar` 组件：status 多选下拉、operation 多选下拉、table 文本输入、date range picker、SQL 搜索输入、Clear 按钮
- [x] 6.3 创建 `OpLogTable` 组件：基于 @tanstack/react-table，配置列（checkbox、时间、操作类型 badge、表名、affected rows、状态 badge、Undo 按钮），启用 sorting、pagination、rowSelection、expansion
- [x] 6.4 创建 `OpLogStatusBadge` 组件：根据 status 渲染对应颜色 badge（active=success、undone=muted、expired=warning、pending=info）
- [x] 6.5 创建 `OpLogOperationBadge` 组件：根据 operation 类型渲染 badge（INSERT=sky、UPDATE=amber、DELETE=red）
- [x] 6.6 创建 `OpLogPagination` 组件：服务端分页控件，显示 page/total，上一页/下一页按钮

## 7. Frontend — Detail Row & Before State

- [x] 7.1 创建 `OpLogDetailRow` 组件：展开行面板，展示 original SQL（Monaco readonly 或 pre code block）、inverse SQL、session 信息、过期时间
- [x] 7.2 创建 `BeforeStateJsonTree` 组件：递归 JSON 树形展示，支持折叠/展开
- [x] 7.3 创建 `BeforeStateGrid` 组件：mini data-grid，使用 shadcn Table primitives 展示 before_state 行数据
- [x] 7.4 创建 `BeforeStateDiff` 组件：UPDATE 操作的变更前后 diff 高亮，对比变更字段
- [x] 7.5 创建 `BeforeStateRenderer` 组件：自适应分发器，根据行数/列数和操作类型选择 JsonTree / Grid / Diff

## 8. Frontend — Batch Undo UX

- [x] 8.1 创建 `BatchUndoBar` 组件：选中行时底部浮现，显示选中数量和 "Undo Selected" 按钮
- [x] 8.2 创建 `BatchUndoConfirmDialog` 组件：AlertDialog，列出可 undo 记录的表名/操作/affected rows/inverse SQL 摘要，warning variant 确认按钮
- [x] 8.3 实现 batch undo 逻辑：确认后调用 batchUndoOpLogs API，结果返回后更新列表状态，成功条目显示 undone badge，失败条目显示错误信息

## 9. Frontend — Tab Open Entry Points

- [x] 9.1 Sidebar connection 右键菜单添加 "View Operation Log" 菜单项，调用 openOrFocusStageToolTab
- [x] 9.2 SqlDmlSummaryPanel 添加 "View all operations" 链接，从 result 中提取 connectionId 并调用 openOrFocusStageToolTab
- [x] 9.3 工具栏 "New Tab" 添加 "Operation Log" 选项，弹出 connection 选择器后创建 tab

## 10. Testing & Verification

- [x] 10.1 后端：UndoLogRepository 分页查询集成测试（使用 @JdbcTest）
- [x] 10.2 后端：ConnectionOpLogController MockMVC 测试（分页/过滤/详情/batch-undo/SSE）
- [x] 10.3 前端：OperationLogTab vitest 测试（渲染、过滤交互、分页、行选择）
- [x] 10.4 前端：batch undo flow vitest 测试（选择→确认→执行→状态更新）
- [x] 10.5 后端 `mvn compile` 零错误，前端 `tsc --noEmit` 零类型错误
