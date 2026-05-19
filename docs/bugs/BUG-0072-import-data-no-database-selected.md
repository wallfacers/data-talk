---
id: BUG-0072
title: datatalk_import_data 不解析 session_data_context 的 database，server-level 连接报 "No database selected"
status: fixed
priority: P1
source: manual-report
modules: [data-import, action, session-context, mysql]
discovered: 2026-05-19
discoveredBy: human
testRunId: null
fixCommit: 9d0a8e28
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

`ImportDataActionHandler` 完全不读 `database`（input schema 不暴露该字段、也不调 `SessionDataContextService.get`），直接把 `target.connectionId` 透传给 `DataImportService.importFromFile`。当连接是 **server-level**（`ConnectionRecord.databaseName = null`，例如"本地数据库"那种纯主机/端口连接）时，`JdbcUrlBuilder` 拼出的 MySQL URL 为 `jdbc:mysql://host:port/`（无 database 段），导致 `INSERT INTO td_orders ...` 在驱动层直接报 `"No database selected"`。

用户即便先调 `datatalk_set_data_context` 把当前 database 设成 `datatalk_ctx`，import_data 路径也读不到，因为该 handler 不消费 session_data_context。

对比 `ExecuteSqlAction.resolveContext()`（已有标准模式：`input.database → sessionContext.databaseName → connection.databaseName`，并通过 `withDatabase()` 派生 `ConnectionRecord` 让 `openConnection` 拼出含 database 的 URL）—— import 链路缺这一步。

## Reproduction Steps

1. 创建 server-level MySQL 连接（host/port/user/pass，**不填默认 database**，UI 上显示为类似"本地数据库"）
2. 在 chat 里 `datatalk_set_data_context(connectionId=<conn>, database=datatalk_ctx)`
3. 上传 SQL 文件（含 `INSERT INTO td_orders ...` 若干条）
4. 发送："导入到 datatalk_ctx"
5. AI 调 `datatalk_import_data({source:{type:'file', fileId:...}, target:{connectionId:<conn>, tableName:'td_orders'}, createTable:true})`

## Expected vs Actual

| 项 | Expected | Actual |
|----|----------|--------|
| URL 派生 | `jdbc:mysql://host:port/datatalk_ctx` | `jdbc:mysql://host:port/`（无 database 段） |
| handler 行为 | 与 ExecuteSqlAction 一致，从 session_data_context 取 database | 完全不读，纯透传 connectionId |
| 用户体验 | 导入成功，N 行写入 td_orders | 报 `Failed to write data to td_orders: No database selected` |
| schema | `target` 支持显式 `database` 兜底覆盖 | schema 不暴露 `database` 字段，AI 想覆盖也无路径 |

## Environment

- Backend commit: develop @ 61923d3b（BUG-0070 修复后）
- Frontend commit: 同上
- Data source: MySQL 8.x，server-level 连接（databaseName=null）
- 触发条件：连接级 default database 为空 + 未在 import_data input 显式给 database

## Evidence

```
datatalk_set_data_context(connectionId=<conn>, database=datatalk_ctx)  # 成功
datatalk_import_data({source:{type:'file', fileId:...},
                      target:{connectionId:<conn>, tableName:'td_orders'},
                      createTable:true})
→ {"message":"Failed to write data to td_orders: No database selected"}
```

用户原话："上传的文件包含 td_orders 表的 INSERT 语句，共约 40+ 条记录。现在导入到当前连接'本地数据库'。…为什么存在这个问题呢"

## Root Cause

按严重度排序：

1. **`ImportDataActionHandler.handle()` 不消费 session_data_context**：handler 只 parse `target.connectionId` + `target.tableName`，从未注入 `SessionDataContextService` 也没读 `ctx.sessionId()`。这是和 `ExecuteSqlAction.resolveContext()` 之间最关键的结构性差异（后者注入 `SessionDataContextService` 并按 `input.database → sessionContext.databaseName → connection.databaseName` 三级回落）。
2. **`DataImportService.importFromFile` signature 缺 `database` 参数**：即便 handler 想把 database 传下去也没接口。`writeRowsToTable` 内部直接 `writeService.openConnection(connectionId)`，走 `ConnectionRecord.databaseName`。
3. **server-level 连接的 `ConnectionRecord.databaseName = null`**：`JdbcUrlBuilder` MySQL 分支（line 17-19）在 null 时回退到 `jdbc:mysql://host:port/`，驱动连上但无当前 database。第一条 INSERT 立即报 "No database selected"。
4. **input schema 不暴露 `database`**：handler 的 `inputSchema()` 的 `target` 只声明 `connectionId` + `tableName`，AI 即使想显式给 database 也没字段可填。

`importFromQuery` 路径走同样的 `writeService.openConnection(targetConnectionId)`，**同样受影响**——目前只是触发条件更窄（cross-DB copy 通常会指定 server-level 连接较少）。

## Fix

**对齐 ExecuteSqlAction.resolveContext 模式，4 处修改：**

1. **`ImportDataActionHandler.inputSchema()`**：`target` properties 增加可选 `database` 字段（覆盖 session_data_context）。
2. **`ImportDataActionHandler.handle()`**：注入 `SessionDataContextService` + `ConnectionService`，按 `input.target.database → sessionContext.databaseName → connection.databaseName` 三级解析，通过 `withDatabase()` pattern 派生 ConnectionRecord，把派生后的 `ConnectionRecord` 传给 service（或把 effective database 作为参数传下去）。
3. **`DataImportService.importFromFile` / `importFromQuery`** signature 增加 `String database` 参数；`writeRowsToTable` 内部 `writeService.openConnection(...)` 改成接收 `ConnectionRecord`（或额外接收 database 让内部 builder 用派生 record 拼 URL）。
4. **`ScriptDataWriteService.openConnection`** 新增重载 `openConnection(ConnectionRecord cr)`，让 import service 能把 `withDatabase` 派生的 record 直接传入，不再二次 lookup。

**为什么不在 ConnectionRecord 上做"懒解析"或全局 mutate**：sessions 间 database 上下文必须隔离，不能修改 `ConnectionRecord` 主记录。`withDatabase` 派生新 record 的模式是 ExecuteSqlAction 已经验证过的安全做法。

**测试**：
- `ImportDataActionHandlerTest`：unit 覆盖 input.target.database 优先级、session_data_context 回落、connection.databaseName 兜底
- `DataImportServiceTest`：现有 MySQL/H2 import 测试增加 server-level connection（databaseName=null）+ explicit database 参数的回归用例

**input.source 不可信信号**：与 BUG-0070 同族的"假装是用户"威胁不适用于本 BUG（database 不是安全决策点；这里是功能正确性问题，不是契约绕过问题），故无需 `CallerKind` 升级。

## Verification

**Static**：
- 编译 + 现有测试套件绿
- 新增 unit test 覆盖三级解析回落顺序

**Runtime（pending E2E，与原始 reproduction 一致）**：
1. server-level 连接 + `set_data_context(database=datatalk_ctx)` + import_data SQL 文件 → 成功写入 N 行
2. server-level 连接 + 不设置 data_context + import_data target.database='datatalk_ctx' → 成功（input 优先级）
3. connection 自带默认 db `prod_db` + 不设置 data_context + 不传 target.database → 沿用 `prod_db`

## Notes

**与 BUG-0042 对比**（"SQL editor session-follow 模式下不应用 connection 默认 database"）：
- BUG-0042 是 query_editor 路径不应用 connection 默认 db；session-follow mode
- BUG-0072 是 import_data 路径不应用 session_data_context；server-level connection
- 两者根因不同（前者前端 mode 解析、后者后端 handler 未消费 session context），但都暴露同一类问题：**database 三级解析在新路径上漏接**

**为什么不进入 BUG-0070 同族（execute_sql contract bypass）**：
- BUG-0070 族解决的是"专用合同工具被通用拼装路径绕过"——是安全契约问题
- BUG-0072 是"专用合同工具自身的 database 解析链路缺失"——是功能正确性问题
- 解决方法上：BUG-0070 用 `CallerKind` 升级，BUG-0072 只需对齐 `resolveContext` pattern，不需要安全升级

**潜在二次影响**：
- `importFromQuery`（cross-DB copy）path 同样受影响，本次一并修
- `ExportDataAction`（如果存在类似的"connection 透传"问题）需要 grep 确认
