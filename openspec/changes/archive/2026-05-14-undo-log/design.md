## Context

DataTalk 的 SQL 执行链路已经具备良好的基础条件：`SqlExecuteService` 使用 `setAutoCommit(false)` + 显式 commit 控制事务、`CalciteSqlRiskAnalyzer` 已用 Apache Calcite 解析 SQL AST 提取表名、`SqlExecutionPlanner` 已识别 DML 批次、`JdbcErRelationDiscoveryService` 已实现 `DatabaseMetaData.getPrimaryKeys()` 主键检测。当前缺失的仅是：before-state 快照捕获、inverse SQL 生成、undo_log 持久化、前端 Undo 交互。

## Goals / Non-Goals

**Goals:**
- DML（INSERT/UPDATE/DELETE）执行前自动捕获 before-state 快照
- 自动生成 inverse SQL 用于回滚
- 用户可在 DML Summary Panel 一键发起回滚
- 回滚操作复用现有 SQL 确认流程（L2 级别）
- 3 天过期 + 定时清理
- Phase 1 仅支持有主键的单表 DML、受影响行 ≤ 100

**Non-Goals:**
- DDL 回滚（ALTER/DROP/TRUNCATE）— 留给 Phase 2
- 批量 Undo（整个 execute 请求级别）— 留给 Phase 2
- 无主键表的回滚
- 多表 JOIN UPDATE/DELETE 的回滚
- 全局操作日志审计面板 — 留给 Phase 2
- 跨数据库/跨连接的回滚

## Decisions

### D1: Before-state 捕获使用 Calcite 解析 + JDBC SELECT

**选择**: 用 Calcite 从 DML 的 AST 中提取 WHERE 子句，生成 `SELECT * FROM table WHERE <condition>` 捕获旧数据。

**替代方案**:
- B. 主键先查法：先 `SELECT pk FROM t WHERE ...` 再 `SELECT * WHERE pk IN (...)` — 两次查询，增加延迟
- C. JDBC ResultSet 拦截 — 多数 driver 不支持 before-update 回调

**理由**: Calcite 已集成在项目中用于风险分析，复用同一解析器保持一致性。WHERE 子句直接复用意味着 before-state 数据与实际受影响行完全对应（同一事务内）。解析失败的 SQL 降级为不可回滚。

### D2: 回滚粒度为单条 DML 语句

**选择**: 每个 `ExecutionUnit`（单条或 DmlBatch）独立生成 undo_log 记录，用户可单独回滚任意一条。

**理由**: 一个 execute 请求可能包含多条 DML，用户通常只想撤销其中一条误操作。单条级别的 inverse SQL 更简洁、副作用更可控。

### D3: 主键检测通过 JDBC DatabaseMetaData

**选择**: 复用 `JdbcErRelationDiscoveryService` 已验证的 `meta.getPrimaryKeys()` 方法，在 `UndoLogCapture` 中独立调用。

**理由**: 不引入新的主键发现机制，复用 JDBC 标准接口。无主键的表直接标记为不可回滚，不做全列匹配的降级。

### D4: Undo 操作通过新 REST 端点而非 ActionHandler

**选择**: 新增 `POST /api/sql/undo` 端点在 `SqlExecuteController` 中。

**替代方案**: 使用 `@DataTalkAction(id = "datatalk.execute_undo")` — 更符合现有 Action 模式，但 Undo 操作不走 OpenCode/AI 路径，纯粹是用户直接操作，REST 端点更直接。

**理由**: Undo 是用户从 UI 直接触发的操作，不需要 AI agent 参与。放在 `SqlExecuteController` 与现有 SQL 执行 API 同族，前端调用路径更清晰。回滚确认复用现有 SQL 确认流程（`confirmed` + `riskAck` 参数）。

### D5: undo_log 存储在 SQLite 元数据库

**选择**: 新增 Flyway migration (V25)，在 DataTalk 的 SQLite 元数据库中创建 `undo_log` 表。

**理由**: undo_log 是平台内部元数据，与 connections/sessions 同属管理数据。before_state JSON 存储在 TEXT 列中（SQLite 的 JSON 理论上可达 1GB，实际受 100 行阈值约束，远小于此）。不需要在用户数据库中创建任何表。

### D6: Inverse SQL 生成策略

| DML 类型 | Inverse SQL | before_state |
|---|---|---|
| INSERT | `DELETE FROM t WHERE pk IN (v1, v2, ...)` | 不需要（无旧行） |
| UPDATE | `UPDATE t SET col1=old1, col2=old2, ... WHERE pk=v` | 需要（完整旧行） |
| DELETE | `INSERT INTO t (col1, col2, ...) VALUES (v1, v2, ...)` | 需要（完整旧行） |

**INSERT 的主键获取**: 执行后通过 `Statement.getGeneratedKeys()` 获取自增 ID。若表无自增列，从 INSERT 的 VALUES 子句中解析主键值。

### D7: 过期清理使用 Spring @Scheduled

**选择**: 在 adapter 层添加 `UndoLogCleanupScheduler`，使用 `@Scheduled(fixedDelay = 86400000)` 每天清理过期记录（`status = 'active' AND expires_at < now`）。

**理由**: 3 天过期策略简单，每天跑一次足够。不需要引入 Quartz 等外部调度框架。

### D8: 前端 Undo 按钮仅在 undoable DML 结果上显示

**选择**: DML Summary Panel 仅在 `result.undoable === true` 时渲染 Undo 按钮。`SqlExecuteResultItem` 扩展 `undoLogId` 和 `undoable` 字段。

**理由**: 不可回滚的 DML 仍记录到 undo_log（审计用途），但不暴露 Undo 按钮避免用户困惑。

## Risks / Trade-offs

**[Before-state 快照增加 DML 执行延迟]** → Mitigation: 额外一次 SELECT 查询，对 ≤100 行的场景延迟可忽略（通常 <50ms）。对于已超过阈值的 DML 直接跳过快照捕获。

**[Calcite 解析方言 SQL 失败]** → Mitigation: 降级为不可回滚（undoable=false），不阻塞正常 DML 执行。解析失败不影响 DML 本身的执行。

**[同一事务内多条 DML 的快照时序]** → Mitigation: 每条 DML 独立快照，回滚使用绝对值恢复（`SET col=old_value`）而非增量，顺序无关。

**[undo_log 表增长]** → Mitigation: 3 天过期 + 定时清理 + 100 行阈值。不可回滚的记录也标记 `undoable=false`，清理时一并删除。

**[DtEvent sealed interface 变更]** → 评估后决定：Undo 操作通过 REST 端点完成，结果通过现有 `action.response` 事件推送，**不需要新增 DtEvent 类型**。避免了 exhaustive switch 的级联修改。

## Migration Plan

1. **Flyway V25__undo_log.sql** — 创建 `undo_log` 表 + 索引
2. **后端**: domain → application → infrastructure → adapter 逐层添加
3. **前端**: API 类型 → store 扩展 → UI 组件
4. **回滚**: 删除 V25 migration + 代码回退即可，无数据迁移风险

## Open Questions

- INSERT + 无自增主键 + VALUES 包含表达式（如 `NOW()`）时，如何精确获取实际插入的主键值？可能需要在执行后额外 SELECT 确认。
- 外键级联（ON DELETE CASCADE）导致的间接影响不在 undo 范围内 — 是否需要在 UI 中明确提示？
