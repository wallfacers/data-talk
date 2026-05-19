---
id: BUG-0070
title: AI 通过 datatalk_execute_sql 直传大批量 SQL，绕过 datatalk_import_data 引爆 token 与失败重试
status: fixed
priority: P1
source: manual-report
modules: [agent-skills, sql-execution, data-import, token-economy]
discovered: 2026-05-19
discoveredBy: human
testRunId: null
fixCommit: 61923d3b
fixPlanRef: openspec/changes/execute-sql-bulk-redirect-guard
duplicateOf: null
regression: false
---

## Summary

`datatalk_execute_sql` 接受任意 `sql` 字符串落库。AI 在 chat 中处理大批量数据（20KB SQL 文件、上百条 INSERT、用户上传的迁移脚本等）时倾向把整段 SQL inline 进 `tool_call.input` 走 `execute_sql`，绕过为该场景准备的 `datatalk_import_data` 工具，导致：

1. **token 失控**：单次 20KB SQL ≈ 5,000 输出 token；失败重试 + tool_history 复读放大到 20,000+ token（实测 BUG-0069）
2. **失败弹性差**：execute_sql 一次失败即全段回滚，没有 import_data 的分段提交 / 流式进度 / 错误段隔离能力
3. **dialect 隐患**：execute_sql 走 raw JDBC，不走 import_data 已经修过的 dialect-aware 标识符引用（[[BUG-0066]] 同族风险）
4. **来源不可信**：`input.source: "ai" | "user"` 由调用方填写，AI 可伪造 `source=user` 通过任何基于该字段的弱合同

与 [[BUG-0065]]（ui_exec 绕 Pre-Action 协议）、[[BUG-0067]]（script_run 直连 DB 绕 backend write API）、[[BUG-0069]]（file_read + execute_sql 绕 import_data）同一族：**专用合同工具被通用拼装路径绕过**。本 BUG 是该族系列的终结性硬契约修法。

## Reproduction Steps

**触发场景 A — 文件导入（与 BUG-0069 同源）**：

1. 在 chat 上传 20KB SQL 文件（100 条 INSERT，单表 td_orders）
2. 发送："导入到 datatalk_ctx"
3. 观察工具调用序列 — 即便 BUG-0069 修复后，仍可能再触发 execute_sql 大块拼接

**触发场景 B — AI 自拼大批量**：

1. 让 AI 生成 50 条测试数据并写入新表
2. AI 自拼 `INSERT INTO test_data VALUES ...` × 50 通过 execute_sql 一次性写入

**触发场景 C — source 字段伪造**：

1. AI 调用 `datatalk_execute_sql` 时手动在 input 加 `"source": "user"` 试图绕过基于 source 字段的弱契约

## Expected vs Actual

| 场景 | Expected | Actual（修前） |
|---|---|---|
| A | 第一次 tool_call = `datatalk_import_data`，1-2 次调用完成 | 4+ 次工具调用，含 2 次 execute_sql 大块拼接 |
| B | 走 import_data 走 batch 接口，单次 input ≤ 500 token | 单次 execute_sql input ≈ 4000 token |
| C | source 字段被 server 忽略，强制 callerKind=AI 触发 guard | 弱契约依赖 source 字段，AI 伪造即放行 |
| 用户路径 | query_editor 内任意大小 SQL 不受限制 | 同上（行为正确，但与 AI 路径无机制区分） |

## Environment

- Backend commit: develop @ 2bf80ccc（含 BUG-0066 / BUG-0067 / BUG-0069 修复）
- Frontend commit: 同上
- Data source: MySQL 8.x（多方言场景皆有效）

## Evidence

BUG-0069 复现日志（同一族）：

```
datatalk_file_read(fileId, offset=0)
datatalk_file_read(fileId, offset=4096)
datatalk_execute_sql(sql=<20KB 全文拼接>)  # token 爆点
```

用户原话："大批量不能走 execute_sql 因为 token 消耗太大，这种情况必须走导入方式"

## Root Cause

主因（按严重度排序）：

1. **`datatalk_execute_sql` description 无任何关于尺寸 / 写入语义的约束**：当前 i18n description 是
   > "Run a SELECT query on the given connection and persist the result as a table artifact"
   既不真实（实际接受所有 SQL，不只是 SELECT），也不告诉 AI 何时该改走 import_data
2. **`sql-execution` SKILL.md 无 ❗ DO NOT 段**：与 BUG-0067 / BUG-0069 修复套路对比 —— 后两者通过 SKILL.md 顶部 DO NOT 段才有效收敛 AI 路径选择
3. **后端无硬契约**：缺少 `BulkSqlGuard` 拦截，纯靠 description / SKILL.md 软提示，AI 系统性绕过
4. **来源信号不可信**：`input.source` 字段由 AI 自由填写，无法作为 USER/AI 区分的可信信号；需要从 `ActionContext.metadata` 由入口路径强制注入

## Fix

**四层组合修法（详见 `openspec/changes/execute-sql-bulk-redirect-guard`）**：

1. **可信来源信号**：domain 新增 `CallerKind {USER, AI}` enum + `ActionExecutionMetadata.callerKind` 字段。`SqlExecuteController` 入口硬编码 `USER`，`McpActionBridge` 入口硬编码 `AI`，`ActionDispatcher` 兜底 `AI`（保守默认）。`input.source` 字段不再用于安全决策。
2. **后端硬契约 BulkSqlGuard**：application 层新增 guard。当 `callerKind = AI` **且** SQL 含至少一条写入语句（INSERT/UPDATE/DELETE/DDL）**且**命中任一阈值（>4096 字节 / >20 INSERT / 含 sourceFileId）时拒绝执行，返回结构化 `{status:"rejected", error:{code:"use_import_data", reason, message}, nextAction:{action:"datatalk_import_data", params:{...}}}`。`callerKind = USER` 时无条件放行。
3. **description 重写**：`messages.properties` + `messages_zh_CN.properties` 的 `action.execute_sql.description` 写入 guard 触发条件 + 用户路径放行明示。`ExecuteSqlDescriptionContractTest` 断言关键短语防漂移。
4. **SKILL.md DO NOT 段**：`sql-execution/SKILL.md` 顶部加 `❗ DO NOT` 段，与 BUG-0067 / BUG-0069 修复套路一致。`SkillRoutingContractTest.sqlExecutionSkill_hasDoNotSectionForBulkSql` 防漂移。

**纯只读 SQL 豁免**：所有阈值均隐含前提"SQL 含写入语句"。纯 SELECT/EXPLAIN/SHOW/DESCRIBE 无 import_data 替代方案，必须放行，否则给 AI 制造死路。

## Verification

**Static contract**：
- `BulkSqlGuardTest`：USER 路径 100KB 放行；AI 路径三道闸覆盖；纯 SELECT 10KB 多 CTE 放行；INSERT 计数闸；origin 闸；parseTargetTable 单表 / 多表 / 大小写
- `ExecuteSqlActionBulkGuardTest`：mock guard 返回 reject 时响应结构含 status=rejected + error.code + nextAction；mock pass 时正常执行；confirmation 流程不触发 guard
- `ExecuteSqlDescriptionContractTest`：断言两份 i18n description 含关键短语
- `SkillRoutingContractTest.sqlExecutionSkill_hasDoNotSectionForBulkSql`：断言 SKILL.md 含 ❗ DO NOT + 关键短语

**Runtime（pending E2E）**：
- 重跑 BUG-0069 场景：上传 20KB SQL 文件 → AI 第一个工具调用必须 = `datatalk_import_data`
- 用户编辑器 100KB SQL 点击运行：必须正常执行
- 用户编辑器 10KB 纯 SELECT 复杂 CTE 查询：必须正常执行

## Notes

与本族 BUG 对比：

| BUG | 误用模式 | 修法 |
|---|---|---|
| BUG-0065 | ui_exec 推 SQL 绕 Pre-Action 协议 | ExecuteSqlAction 后端守门 + skill 文档 |
| BUG-0067 | script_run 装 pymysql 直连 DB | SKILL.md DO NOT 段 + DataCollectionSkillContractTest |
| BUG-0069 | file_read + execute_sql 绕 import_data | SKILL.md DO NOT 段 + SkillRoutingContractTest |
| **BUG-0070** | execute_sql 直传大批量 SQL（token + 失败弹性双重灾难） | **四层组合**：可信 callerKind + 后端硬契约 BulkSqlGuard + description 重写 + SKILL.md DO NOT |

为什么本 BUG 比同族严重：

- BUG-0065/0067/0069 修法以"软契约 + 合同测试"为主，依赖 AI 读 SKILL.md 收敛行为
- BUG-0070 升级为"后端硬拒绝"——即使 AI 不读 SKILL.md / 不读 description 也拦得住，且响应里直接给 AI 下一步该调用什么（nextAction），实现"0 推理自愈"
- 来源信号从 `input.source`（AI 可伪造）升级为 `ActionContext.metadata.callerKind`（后端入口路径强制注入，AI 不可触达），彻底关闭"假装是用户"绕过路径
