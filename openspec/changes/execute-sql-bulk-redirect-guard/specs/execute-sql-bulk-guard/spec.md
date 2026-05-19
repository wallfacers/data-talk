## ADDED Requirements

### Requirement: CallerKind 强制由入口路径注入

系统 SHALL 通过 `ActionContext.metadata().callerKind()` 区分 SQL 执行的发起方为 `USER` 还是 `AI`。`callerKind` 字段 MUST 由后端入口路径强制写入，**不得**从 action input 或客户端请求体中读取。

#### Scenario: query_editor 用户点击运行触发后端 controller

- **GIVEN** 用户在 query_editor 编辑器中写入 SQL 并点击"运行"
- **WHEN** 前端发起 `POST /sql/execute` 请求
- **THEN** `SqlExecuteController` SHALL 在构造 ActionContext 时强制设置 `callerKind = CallerKind.USER`
- **AND** 请求体中的 `source` 字段 SHALL 被忽略（即使值为 `"ai"` 也强制 USER）

#### Scenario: AI 通过 MCP 调用 datatalk_execute_sql

- **GIVEN** AI 在 chat 会话中调用 MCP 工具 `datatalk_execute_sql`
- **WHEN** `McpActionBridge` 接收 OpenCode 的 action.invoke 请求并构造 ActionContext
- **THEN** ActionContext SHALL 设置 `callerKind = CallerKind.AI`
- **AND** input 中即使包含 `"source": "user"` 字段也 SHALL 不影响 callerKind

#### Scenario: ActionDispatcher 兜底默认值

- **GIVEN** 内部 dispatcher 未显式指定 callerKind
- **WHEN** `ActionExecutionMetadata.empty()` 被构造
- **THEN** `callerKind` SHALL 默认为 `CallerKind.AI`（保守默认，避免漏拦）

### Requirement: AI 路径下批量写入 SQL 必须被 BulkSqlGuard 拒绝

当 `callerKind = CallerKind.AI` **且 SQL 含至少一条写入语句**（INSERT/UPDATE/DELETE/DDL）且命中任一阈值时，系统 SHALL 拒绝执行并返回结构化错误。

判定流程：
1. `SqlRiskAnalyzer` 解析 statements
2. 若所有 statements 均为只读类（SELECT/EXPLAIN/SHOW/DESCRIBE/纯 SELECT WITH/CTE）→ guard 放行（纯 SELECT 无 import_data 替代，强行拒绝会给 AI 制造死路）
3. 否则按下列三道闸判断，**任一**满足即 reject：
   - **A. size_threshold**: `sql.getBytes(UTF_8).length > 4096`
   - **B. insert_count_threshold**: `INSERT` 语句数 > 20
   - **C. originated_from_file**: `input.metadata.sourceFileId` 非空

#### Scenario: AI 提交 5KB SQL（size 闸）

- **GIVEN** AI 调用 `datatalk_execute_sql`，`sql` 长度为 5120 字节，仅 1 个 INSERT
- **WHEN** `ExecuteSqlAction.execute()` 进入 BulkSqlGuard 检查
- **THEN** guard SHALL 返回 verdict `shouldReject = true`，`reason = "size_threshold"`
- **AND** 响应 SHALL 包含 `status: "rejected"` + `error.code: "use_import_data"`
- **AND** SHALL NOT 执行任何 SQL（不写库、不创建 artifact）

#### Scenario: AI 提交 30 条 INSERT，总字节 < 4096（count 闸）

- **GIVEN** AI 提交 30 条独立 INSERT 语句，每条约 100 字节，总计约 3KB
- **WHEN** guard 检查
- **THEN** guard SHALL 返回 `shouldReject = true`，`reason = "insert_count_threshold"`
- **AND** 响应 SHALL 包含 `error.message` 指明实际计数与上限

#### Scenario: AI 提交 SQL 但带 sourceFileId（origin 闸）

- **GIVEN** AI 调用 `datatalk_execute_sql`，input 含 `"sourceFileId": "f-123"`，sql 长度仅 1KB 且仅 5 个 INSERT
- **WHEN** guard 检查
- **THEN** guard SHALL 返回 `shouldReject = true`，`reason = "originated_from_file"`
- **AND** 响应 `nextAction.params.source` SHALL 为 `{"type": "file", "fileId": "f-123"}`

#### Scenario: AI 提交 10KB 纯 SELECT 多 CTE 查询必须放行

- **GIVEN** AI 提交 10240 字节的 SELECT 查询（多个 WITH 子句 + 多表 JOIN），不含 INSERT/UPDATE/DELETE/DDL
- **WHEN** guard 检查
- **THEN** guard SHALL 返回 `shouldReject = false`（前提闸"含写入语句"未满足）
- **AND** SQL SHALL 正常执行
- **AND** 响应 SHALL NOT 含 `error.code = "use_import_data"`

#### Scenario: AI 提交 5KB SELECT + 1 条 INSERT 混合

- **GIVEN** AI 提交 5120 字节 SQL，包含 1 条 5KB 大的 SELECT INTO + 1 条 100 字节 INSERT
- **WHEN** guard 检查
- **THEN** 由于包含 INSERT 语句，size 闸 SHALL 触发（5120 > 4096）
- **AND** guard SHALL 返回 `shouldReject = true`，`reason = "size_threshold"`

### Requirement: USER 路径下 BulkSqlGuard 必须无条件放行

当 `callerKind = CallerKind.USER` 时，BulkSqlGuard SHALL 跳过所有阈值检查，无条件放行。

#### Scenario: 用户在 query_editor 执行 100KB 大 SQL

- **GIVEN** 用户在 query_editor 粘贴 100KB SQL（含 500 条 INSERT）并点击运行
- **WHEN** `SqlExecuteController` 路由到 `ExecuteSqlAction`，callerKind = USER
- **THEN** guard SHALL 直接返回 verdict `shouldReject = false`
- **AND** SQL SHALL 正常执行
- **AND** 响应 SHALL NOT 包含 `error.code = "use_import_data"`

#### Scenario: 用户路径下 sourceFileId 存在也放行

- **GIVEN** callerKind = USER 且 input.metadata.sourceFileId 非空（边缘场景）
- **WHEN** guard 检查
- **THEN** guard SHALL 放行（USER 优先级高于 origin 闸）

### Requirement: 拒绝响应必须携带 nextAction 自愈提示

`BulkSqlGuard` 拒绝执行时，响应 SHALL 包含 `nextAction` 字段指向 `datatalk_import_data`，并尽可能填充已知参数。

#### Scenario: nextAction 结构完整

- **GIVEN** guard 触发任一拒绝条件
- **WHEN** 构造响应
- **THEN** 响应 SHALL 满足结构：
  ```json
  {
    "status": "rejected",
    "error": {
      "code": "use_import_data",
      "message": "<人类可读说明>",
      "reason": "size_threshold" | "insert_count_threshold" | "originated_from_file"
    },
    "nextAction": {
      "action": "datatalk_import_data",
      "params": {
        "source": {"type": "file", "fileId": "<from sourceFileId 或 null>"},
        "target": {"connectionId": "<from ctx>", "tableName": "<from INSERT INTO 解析 或 null>"}
      }
    }
  }
  ```

#### Scenario: 从 SQL 解析 INSERT 目标表

- **GIVEN** SQL 含 `INSERT INTO td_orders VALUES ...` 多条
- **WHEN** guard 构造 nextAction
- **THEN** `nextAction.params.target.tableName` SHALL 为 `"td_orders"`

#### Scenario: 多表 INSERT 无法确定单表

- **GIVEN** SQL 含针对 `t1` / `t2` 两个不同表的 INSERT
- **WHEN** guard 构造 nextAction
- **THEN** `nextAction.params.target.tableName` SHALL 为 `null`
- **AND** `error.message` SHALL 提示"多表 INSERT，请按文件拆分后分别调用 import_data"

### Requirement: confirmation 流程不重复触发 BulkSqlGuard

`ExecuteSqlAction` 处理 confirmation 流程时 SHALL NOT 重复运行 BulkSqlGuard。

#### Scenario: 持有 confirmationId 的二次调用直接执行

- **GIVEN** 上一次调用返回了 `requires_confirmation` + confirmationId
- **WHEN** AI 用该 confirmationId 再次调用 `datatalk_execute_sql`
- **THEN** `ExecuteSqlAction.execute()` SHALL 直接进入 `executeConfirmation` 分支
- **AND** SHALL NOT 再次运行 BulkSqlGuard（避免对已批准的 SQL 二次拒绝）

### Requirement: action.execute_sql.description 必须包含 guard 规则明示

i18n 资源 `action.execute_sql.description`（英文与中文）SHALL 包含 BulkSqlGuard 的触发条件、拒绝行为、用户路径放行说明。

#### Scenario: 英文 description 包含关键短语

- **GIVEN** `messages.properties` 中 `action.execute_sql.description` 文本
- **THEN** 文本 SHALL 包含字面短语 `"MUST USE datatalk_import_data"`
- **AND** SHALL 包含字面短语 `"use_import_data"`
- **AND** SHALL 包含字面数字 `"4096"` 与 `"20 INSERT"`
- **AND** SHALL 包含字面短语 `"query editor"` 与"用户路径不受约束"含义陈述

#### Scenario: 中文 description 与英文对齐

- **GIVEN** `messages_zh_CN.properties` 中 `action.execute_sql.description` 文本
- **THEN** 文本 SHALL 包含等价于英文 description 的三道闸说明与用户路径放行说明
- **AND** 长度 SHALL 不超过 600 字符

### Requirement: ExecuteSqlDescriptionContractTest 必须防漂移

新增 `ExecuteSqlDescriptionContractTest` SHALL 加载 i18n 资源并断言关键短语存在。

#### Scenario: 测试断言英文 description 关键短语

- **GIVEN** 测试加载 `messages.properties`
- **WHEN** 断言 `action.execute_sql.description` 文本
- **THEN** 缺失任一 D6 / D7 / D8 提到的关键短语 SHALL 触发测试失败
