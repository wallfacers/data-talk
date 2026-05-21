## Context

undo_log 表（V25 迁移）已存储每条 DML 操作的完整数据：connection_id、session_id、table_name、operation、original_sql、inverse_sql、before_state、status、expires_at。后端已有 UndoLogCapture（捕获）、UndoExecuteService（单条 undo）、UndoLogCleanupScheduler（过期清理）。前端已有 SqlDmlSummaryPanel 内联 undo 按钮。

缺失的部分：
1. **列表查询**：UndoLogRepository 只有 `findById` 和 `findExpiredActive`，无分页/过滤查询
2. **实时通知**：undo_log 写入后无 SSE 广播，前端无法感知新操作
3. **批量管理**：只有单条 `POST /api/sql/undo`，无批量 undo
4. **全局视图**：无独立面板组件按 connection 聚合展示操作历史

当前 SSE 基础设施是 per-session 的（SessionBus + SessionBusRegistry），需要新增 per-connection 层。

## Design Inputs

适用的 client/DESIGN.md 约束：
- **Stage tab 全局性**：stage state 是 session-independent，tab 列表/workset/open/maximized 是单值。operation_log tab 切换 session 不变
- **Stage 表面色**：tab chrome 用 `bg.soft`，工作内容用 `bg.canvas`
- **Table 规则**：稳定 header hierarchy、轻量 hover、明确 selected state、mono 处理数值/技术内容
- **Status 语义**：success(绿色)/warning(amber)/danger(red)/info(sky)，不可仅靠颜色传达状态
- **Typography**：UI 文字用 Source Sans 3，技术内容用 JetBrains Mono
- **Motion**：motion 确认状态变更，不做装饰。fast=120ms, normal=180ms

## Goals / Non-Goals

**Goals:**
- 按 connection 维度分页查询 undo_log，支持 status/operation/table/dateRange/sqlText 过滤
- 实时 SSE 推送 undo_log 创建和状态变更事件
- 批量选择多条 active 记录执行 undo
- 独立 stage tab 展示操作日志表格（排序/过滤/分页/行展开）
- 自适应 before_state 展示：JSON 树 / mini 表格 / UPDATE diff
- 三个入口：sidebar 右键菜单、SQL 结果面板链接、工具栏新建

**Non-Goals:**
- 不修改现有 `POST /api/sql/undo` 单条 undo 流程
- 不做跨 connection 的全局聚合视图
- 不做 undo_log 的导出功能（CSV/JSON）
- 不做操作日志的权限控制（多用户场景）
- 不引入新的外部依赖（JSON viewer 等用纯 React 实现）
- 不做 undo_log 记录的手动删除

## Decisions

### D1: API 路径风格 — RESTful 嵌套 resource

`GET /api/connections/{id}/op-logs` 而非 `GET /api/op-logs?connectionId=`。

**理由**：与现有 `GET /api/connections/{id}/targets` 风格一致。操作日志是 connection 的子资源，嵌套路径语义清晰。

**备选**：扁平路径 `GET /api/op-logs?connectionId=` — 更灵活但不如嵌套路径 RESTful。

### D2: SSE 广播 — 新建 ConnectionOpLogBus 层

新建 `ConnectionOpLogBus`（per-connection 事件分发）和 `ConnectionOpLogBusRegistry`（生命周期管理），复用 SessionBus 的 pub-sub 模式但独立运行。

**理由**：
- 操作日志事件与 chat 事件是不同关注点，不应耦合到 SessionBus
- ConnectionOpLogBus 不需要 buffer/persistence/replay，只需实时分发，实现更轻量
- 生命周期管理与 SessionBusRegistry 一致：首个 subscriber 创建，最后一个断开后 grace period 销毁

**备选**：
- A) 复用 SessionBus 添加 connection 级事件 — 会污染 chat 事件通道，且 undo_log 可能涉及多个 session
- B) TanStack Query 轮询 — 实现简单但非实时，面板打开时持续浪费请求

### D3: SSE 事件触发 — Spring ApplicationEvent 解耦

UndoLogCapture 写入 undo_log 后发布 Spring `ApplicationEvent`（`UndoLogCreatedEvent`），ConnectionOpLogBusRegistry 监听该事件并路由到对应 connection 的 bus。

**理由**：UndoLogCapture 在 application 层，ConnectionOpLogBus 也在 application 层，Spring ApplicationEvent 是同层解耦的标准方式，不违反依赖方向。

**备选**：直接在 UndoLogCapture 中注入 ConnectionOpLogBusRegistry — 增加直接依赖，降低可测试性。

### D4: 前端状态管理 — TanStack Query 为主

列表数据用 TanStack Query（server state），SSE 收到通知后 invalidate query 触发 refetch。不新建独立 Zustand store。

**理由**：列表数据是 server state，TanStack Query 天然处理 loading/error/pagination/refetch。现有项目已重度使用 TanStack Query。

**备选**：新建 `op-log-store.ts` Zustand store — 增加 state 同步复杂度，收益不大。

### D5: 表格组件 — @tanstack/react-table

项目已安装 `@tanstack/react-table ^8.21.3`（TableArtifact 在用），直接使用其 sorting/filtering/pagination/row-selection/row-expansion 插件。

**理由**：零新依赖，功能完整，与 shadcn/ui Table primitives 配合良好。

### D6: before_state 展示策略 — 自适应渲染

根据数据特征自动选择展示方式：
- 行数 ≤ 5 且列数 ≤ 8：JSON 树形展开（纯 React 递归组件）
- 行数 > 5 或列数 > 8：mini data-grid 表格（shadcn Table）
- UPDATE 操作：变更前后 diff 高亮（对比 before_state 中变化的字段）

**理由**：不同数据规模和信息密度需要不同展示方式才能"好看"。JSON 树适合少量复杂数据，表格适合多行列数据，diff 适合变更对比。

**备选**：统一用一种展示方式 — 无法同时满足审计（需要全量数据）和快速浏览（需要概要）两种需求。

### D7: batch-undo UX — 汇总确认

批量 undo 时弹出一个汇总确认对话框，列出所有选中记录的 inverse SQL 和 affected_rows，一次性确认执行。

**理由**：逐条确认在批量场景下 UX 太差（10 条 = 10 次弹窗）。汇总确认让用户一次看到全局风险。执行时逐条调用 `UndoExecuteService.undo()`，任何一条失败则回滚已执行的后续条目，返回部分成功结果。

**备选**：逐条确认 — UX 差但风险更低。考虑到 undo 操作本身已有 3 天过期保护，汇总确认是合理平衡。

### D8: Tab 去重策略

同一个 connectionId 只保留一个 operation_log tab，使用 `buildStageTabIdentity()` 机制，key = `operation_log::connectionId`。

**理由**：与现有 `openOrFocusStageToolTab` 去重逻辑一致，避免同一个 connection 打开多个日志 tab。

## Risks / Trade-offs

**[undo_log 表数据量增长]** → undo_log 表无定期物理删除（只有 status 变更），长期使用后分页查询可能变慢。
**缓解**：现有的 UndoLogCleanupScheduler 已在删除 7 天前的 expired 记录。`idx_undo_log_session` 索引已覆盖 session_id + status，需要新增 `connection_id + status + created_at` 复合索引优化按 connection 查询。

**[SSE 连接数]** → 每个 connection 的 operation log tab 打开都会建立一个 SSE 长连接。
**缓解**：ConnectionOpLogBusRegistry 的 grace period 机制（最后 subscriber 断开后 30s 销毁）自动回收不活跃连接。桌面端场景下 connection 数量有限（通常 < 10）。

**[batch-undo 部分失败]** → 批量 undo 中部分记录可能已过期或已被 undo，导致部分成功。
**缓解**：API 返回逐条结果，前端展示成功/失败状态，用户可以清楚看到哪些成功哪些失败。

**[before_state JSON 解析]** → before_state 是 JSON 字符串，大操作可能包含 100 行数据，展开时 JSON.parse 可能有延迟。
**缓解**：分页加载时不在列表中返回 before_state（列表只返回概要字段），展开详情行时再通过单独 API 或在列表响应中附带，按需渲染。

## Migration Plan

1. **新增 Flyway 迁移**：为 undo_log 表添加 `idx_undo_log_connection_status_created` 复合索引（connection_id, status, created_at DESC），优化分页查询性能
2. **后端开发顺序**：UndoLogRepository 新方法 → ConnectionOpLogBus → UndoLogCreatedEvent → ConnectionOpLogController → 集成到 UndoLogCapture
3. **前端开发顺序**：API client → TabTypeRegistry 注册 → OperationLogTab 组件 → SSE 订阅 → 三个入口集成
4. **回滚**：所有新增代码不影响现有 undo 流程。回滚只需删除新代码和 Flyway 迁移
