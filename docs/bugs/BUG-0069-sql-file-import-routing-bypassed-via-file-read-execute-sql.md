---
id: BUG-0069
title: 20KB INSERT-only SQL 文件被 file_read + execute_sql 绕过 datatalk_import_data 路由
status: fixed
priority: P2
source: manual-report
modules: [agent-skills, file-upload-routing, data-import]
discovered: 2026-05-19
discoveredBy: human
testRunId: null
fixCommit: 2ab9039f
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

用户上传 20KB SQL 文件（100 条 `INSERT INTO td_orders`，无 DDL），意图明确为"导入数据"。
按 `file-upload-routing/SKILL.md` 契约（L33-37），SQL 文件 + Import 意图 + `statementTypes ⊆ {INSERT, DROP, CREATE}` + `targetTables.length == 1` 必须走 `datatalk_import_data`；但 AI 直接 `datatalk_file_read ×2` 拼接全文 + `datatalk_execute_sql ×2` 跑了出来，绕开了专用导入路径。

与 [[BUG-0065]]（ui_exec 绕 Pre-Action 协议）、[[BUG-0067]]（script_run 直连 DB 绕 backend write API）同一族：**专用合同工具被 AI 用"通用 + 拼装"路径绕过**。

## Reproduction Steps

1. 在 chat 中上传一个 20KB SQL 文件，内容是 `INSERT INTO td_orders VALUES (...), ...;` 100 行，无 DROP / CREATE / DELETE
2. 发送消息："把数据导入到 datatalk_ctx"
3. 观察工具调用序列

## Expected vs Actual

- **Expected**：一次 `datatalk_import_data(source={type:'file', fileId}, target={connectionId, tableName:'td_orders'})` 调用搞定。`import_data` 内部走 dialect-aware `IdentifierQuoter`（[[BUG-0066]] 已修），自动处理保留字 / 引号 / 多 batch 流式写入
- **Actual**：4 次工具调用绕路 ——
  ```
  datatalk_file_read(fileId, offset=0)      # 读前半
  datatalk_file_read(fileId, offset=4096)   # 读后半
  datatalk_execute_sql(sql=<拼接全文>)        # 失败：no active database
  datatalk_list_connection_targets
  datatalk_set_data_context(database='datatalk_ctx')
  datatalk_execute_sql(sql=<拼接全文>)        # 成功，100 rows
  ```
  虽然数据最终写入正确，但路径完全绕开 `datatalk_import_data` 的批处理 / 进度回报 / 错误分段回滚能力

## Environment

- Backend commit: develop @ 0893d6de（含 BUG-0066 / BUG-0067 修复在 working tree 中）
- Frontend commit: 同上
- Data source: MySQL 8.x
- File: 20KB SQL，100 INSERT，单表 `td_orders`

## Evidence

用户对话原文（节选）：

```
datatalk_file_read x2
datatalk_execute_sql (failed, no db context)
datatalk_list_connection_targets
datatalk_set_data_context
datatalk_execute_sql -> 100 rows inserted
```

用户原话："导入的文件大小是 20k，按理来说这样大小的文件应该走文件导入，看情况是直接走了，直接执行"

## Root Cause

主因（按概率排序）：

1. **`file-upload-routing/SKILL.md` L37 fallback 闸太松**：
   > "Fallback when `datatalk_import_data` rejects the file (dialect-incompatible parsing, backtick quoting unrecognized, `unsupported_sql_dialect`, or any non-recoverable validation error)... read `analysis.summary.preview` and call `datatalk_execute_sql` directly"

   这段写的本意是"先调 import_data，失败才回落"，但 AI 把"或任何非可恢复验证错误"理解成"如果心里觉得可能失败，可以直接回落"。文本里举的失败例子（dialect-incompatible parsing / backtick quoting unrecognized）恰好是 [[BUG-0066]] 修前的典型症状，可能让 AI 学到"SQL 文件 → execute_sql 更稳"

2. **routing skill 没在中段重激活**：SKILL 描述写"Activated when user message contains a file_upload part"。若 file_upload part 在更早回合附带、本回合只是确认导入意图，skill 可能未重激活，AI 凭"上下文记忆"选短路径

3. **L33 SQL Files 段缺 ❗ DO NOT 显式禁令**：与 [[BUG-0067]] 修复套路对比 —— data-collection skill 加了顶部 DO NOT 段（pymysql / psycopg2 等明文禁止）才有效。file-upload-routing 的 SQL Files 段全是 SHOULD / fall through 等软措辞，缺一句"DO NOT skip directly to file_read + execute_sql when import_data would apply"

## Fix

**思路 A（弱契约 + 合同测试，与 BUG-0065 / 0067 修复套路一致）：**

1. 改 `server/data-talk-adapter/src/main/resources/skills/file-upload-routing/SKILL.md`：
   - 在 `### SQL Files` 段顶部加 `**❗ DO NOT**` 子段（参照 data-collection SKILL.md DO NOT 段）：禁止"`file_read` 全文 + `execute_sql`"短路径，仅在 `import_data` 已实际返回非可恢复错误后才允许回落
   - 把 L37 fallback 闸措辞收紧：从"any non-recoverable validation error"改为"explicit `unsupported_sql_dialect` / `unsupported_statement_type` error from import_data response"，并强调"must call import_data first; cannot pre-judge based on file content"
2. 扩展 `SkillRoutingContractTest`：增加 `fileUploadRoutingSqlFilesSection_containsExplicitDoNotForFileReadShortcut` 用例，断言 SKILL.md 包含 DO NOT + "MUST call import_data first" 字面字符串
3. （可选）`AgentsTemplateContractTest` 加正向关键词："datatalk_import_data" / "fileId" 必须在 SKILL.md 中出现 N+ 次

**思路 B（硬执法，建议作为 follow-up 单独 change）**：在 `ExecuteSqlAction.handle` 增加守门：若同一 chat session 最近 30 秒内有相同 fileId 的 `file_read` 调用，且该 fileId 的 analysis 显示 "SQL + 单表 INSERT" 满足 import_data 前提，拒绝执行并返回 `error.code='import_data_required'` + `nextAction={action:'import_data', params:{fileId, connectionId, tableName}}`。

本 BUG 先走 A，B 留作 follow-up。

## Verification

**Static contract（已通过）**：`SkillRoutingContractTest.fileUploadRoutingSqlSection_hasDoNotShortcut_andTightenedFallbackGate` 断言：
- file-upload-routing SKILL.md 含 `❗ DO NOT` 子段
- 同时出现 `datatalk_file_read` / `datatalk_execute_sql` / `import_data` 三个关键词（明文禁令）
- 含 `BUG-0069` 回溯锚
- 含 "MUST call import_data first" 或等价短语
- fallback gate 引用具体 error.code（`unsupported_sql_dialect` + `unsupported_statement_type` / `unrecoverable_parse_error`），不再是含混的 "any non-recoverable error"

**Runtime（pending E2E）**：用 playwright 重跑：上传同样 20KB SQL 文件，发消息 "导入到 datatalk_ctx"，工具调用序列应满足：

- ✅ 第一个工具调用 = `datatalk_import_data`
- ✅ 总工具调用数 ≤ 2（含可能的 `set_data_context`）
- ❌ 不应出现 `file_read` 调用

## Notes

与本族 BUG 对比：

| BUG | 误用模式 | 修法 |
|---|---|---|
| BUG-0065 | ui_exec 推 SQL 绕 Pre-Action 协议 | ExecuteSqlAction 后端守门 + skill 文档 |
| BUG-0067 | script_run 装 pymysql 直连 DB | SKILL.md DO NOT 段 + DataCollectionSkillContractTest |
| **BUG-0069** | file_read + execute_sql 绕 import_data | SKILL.md DO NOT 段 + SkillRoutingContractTest（本 BUG 思路 A） |

LLM 倾向"通用+拼装"路径源于：通用工具调用链失败时进退弹性更高，专用合同工具一次错就要重整。修法上需要"专用工具 metadata 显式禁止退路 + 后端硬契约兜底"两层并行，缺一不可。
