## Context

当前 `ExecuteSqlAction` 对 L2/L3 风险 SQL 执行 `blocked_in_chat` 拦截，强制前端展示 `BlockedInChatCard` 并路由到编辑器 AlertDialog 确认。这套两阶段确认（chat 拦截 → 编辑器确认）在用户明确要求"建表+导入"的场景下造成 UX 断裂。同时 `DataImportService` 的 `SqlStreamReader` 仅解析纯 INSERT，DDL+INSERT 混合 SQL 文件被错误路由到 execute_sql 而非 import_data。

**当前架构约束**：
- `ExecuteSqlAction` 是 AI 唯一的 SQL 执行入口（`datatalk.execute_sql`）
- `SqlExecuteService` 同时服务 AI 路径（action）和编辑器路径（REST），通过 `confirmed`/`riskAck` 参数区分
- `CalciteSqlRiskAnalyzer` 在两个路径共享，产出 L1/L2/L3 分级
- 编辑器 AlertDialog 确认是前端 `sql-workbench-tab.tsx` 的内联逻辑，与 chat 无耦合
- `file-upload-routing` skill 的路由决策在 AGENTS.md 中声明，AI agent 执行

## Goals / Non-Goals

**Goals:**
- AI 路径下，非 DELETE 的 SQL 全部直接执行，无需任何确认
- DELETE 操作通过对话式确认（action 返回 `requires_confirmation`，AI 展示并请求用户确认，二次调用执行）
- DDL+INSERT 混合 SQL 文件可走 `datatalk_import_data` 导入流程
- 编辑器手动执行的 L2/L3 确认流程保持不变

**Non-Goals:**
- 不合并 `execute_sql` 和 `import_data` 为单一 action（数据流向、参数模型、处理逻辑本质不同）
- 不修改 `CalciteSqlRiskAnalyzer` 的分级逻辑（编辑器路径仍需）
- 不增加前端 UI 组件（对话式确认完全在 chat 内闭环）
- 不处理 prompt injection 防御（超出本 change 范围）

## Decisions

### D1: AI 路径风险闸门策略 — 从分级拦截改为仅 DELETE 需确认

**选择**: 移除 `ExecuteSqlAction` 对 L2/L3 的 `blocked_in_chat` 逻辑。AI 调用时仅对 DELETE 语句返回 `requires_confirmation`，其他全部直接执行。

**替代方案**:
- (A) 保留 L2/L3 但改为对话式确认（分级保留，只是 UI 变化）→ 不选：分级模型本身就是过度设计，用户信任 AI 执行他们要求的操作
- (B) 完全不拦截，包括 DELETE → 不选：DELETE 不可逆风险过高，需兜底
- (C) 保持现状，只优化路由 → 不选：根因是信任模型过于保守

**原理**: DataTalk 是开发者工具，用户坐在屏幕前看着 AI 操作。AI 在 chat 中已展示将要执行的 SQL。对于用户明确要求的建表/导入/更新，额外确认是噪音。仅 DELETE 因数据不可逆需要一次快速确认。

### D2: 对话式确认机制 — confirmationId + 二次调用

**选择**: 引入 `SqlPendingConfirmationStore`（内存 ConcurrentHashMap，5 分钟 TTL）。

流程：
```
第一次调用:
  execute_sql(sql="DELETE FROM orders WHERE status='expired'")
  → SqlExecuteService 检测 DELETE → 返回 RequiresConfirmation { confirmationId, affectedRows 估算, sqlPreview }
  → ExecuteSqlAction 返回 { status: "requires_confirmation", confirmationId, message, affectedRows }

AI 向用户展示确认提示，用户在 chat 中回复"确认"/"好的"

第二次调用:
  execute_sql(confirmationId="xxx", confirmed=true)
  → SqlPendingConfirmationStore 取出挂起的 SQL + context
  → 直接执行（跳过风险分析）
  → 返回正常执行结果
```

**替代方案**:
- (A) 前端 AlertDialog 内嵌在 chat 消息中 → 不选：chat 消息是只读流，不适合嵌入交互控件
- (B) 利用 action.invoke 反向调用 → 不选：协议复杂度高，需前端新增 action handler
- (C) 每次重新传 SQL + confirmed 参数 → 不选：SQL 可能很长，浪费 token

**原理**: confirmationId 是轻量级方案，服务端挂起 SQL 上下文，AI 只传一个短 ID 即可完成二次确认。

### D3: SqlStreamReader 增加 DDL 提取

**选择**: `SqlStreamReader` 解析阶段识别 `DROP TABLE IF EXISTS <name>` 和 `CREATE TABLE <name> (...)` 语句，分离为 `ddlPrefix`（String）和 `insertStatements`（流式数据）。`DataImportService` 先执行 `ddlPrefix` 再走原有 INSERT 批处理。

**约束**:
- DDL 仅支持 `DROP TABLE IF EXISTS` 和 `CREATE TABLE`，其他 DDL（ALTER, CREATE INDEX）走编辑器路径
- DDL+INSERT 必须目标同一张表（`targetTables.size = 1`）
- `DROP TABLE` 必须带 `IF EXISTS`（安全兜底）

**替代方案**:
- (A) 用 Calcite 解析完整 SQL AST → 不选：SqlStreamReader 当前用正则+状态机，引入 Calcite 过重
- (B) AI 拆分 DDL/DML 后分别调用 → 不选：增加 AI 负担，且 import_data 的流式优势丢失

### D4: file-upload-routing 放宽条件

**选择**: 将路由条件从"statementTypes 全部为 INSERT"放宽为"statementTypes 仅包含 INSERT、DROP、CREATE，且 targetTables.size = 1，且 DROP/CREATE 目标表与 INSERT 目标表一致"。

**原理**: 用户上传的 SQL dump 文件通常包含 DROP TABLE + CREATE TABLE + INSERT INTO，这是最常见的导入格式。当前路由错误地将其推到 execute_sql L3 拦截路径。

### D5: 前端 BlockedInChatCard 移除

**选择**: 从 `execute-sql.tsx` 移除 `BlockedInChatCard` 组件和相关逻辑。AI 路径不再产生 `blocked_in_chat` 状态。

**原理**: 该组件的唯一用途是展示 L2/L3 拦截并提供"Open in Workbench"按钮。移除后 execute-sql.tsx 简化为纯结果渲染。

## Risks / Trade-offs

| Risk | 影响 | Mitigation |
|------|------|-----------|
| AI 幻觉导致错误 UPDATE/DROP 执行 | 数据损坏 | AI 在 chat 中展示 SQL，用户可实时看到并中断。undo-log 覆盖 DML 操作 |
| Prompt injection 诱导 AI 执行恶意 SQL | 安全 | 超出本 change 范围。未来可加 prompt injection 检测层 |
| confirmationId 内存泄漏 | OOM | 5 分钟 TTL + ConcurrentHashMap + 定时清理 |
| DDL 解析正则不够健壮 | 导入失败 | 仅支持 DROP TABLE IF EXISTS + CREATE TABLE，复杂 DDL 走编辑器路径 |
| 编辑器路径受影响 | 回归 | `SqlExecuteService` 通过 `source` 参数区分 AI/编辑器路径，确认逻辑仅影响 AI 路径 |

## Migration Plan

1. 后端先部署（新增 `SqlPendingConfirmationStore`，修改 `ExecuteSqlAction` / `SqlExecuteService`）
2. 前端部署（移除 `BlockedInChatCard`，清理相关 import）
3. 更新 AGENTS.md + file-upload-routing SKILL.md（路由规则重写）
4. 无数据库 schema 变更，无 Flyway migration
5. 回滚：恢复 `BlockedInChatCard` + `ExecuteSqlAction` L2/L3 阻断逻辑即可

## Open Questions

- DELETE 对话确认中，是否需要估算受影响行数（`SELECT COUNT(*)` 预查询）？这会增加一次 DB 往返但提供更好的上下文
- `SqlPendingConfirmationStore` 是否需要持久化（服务重启后恢复）？当前设计为内存态（重启即丢失，用户需重新发起）
