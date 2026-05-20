## Why

`datatalk_execute_sql` 当前接受任意大小的 SQL 并由 AI 在 tool call 里"完整拼出 SQL 字符串"。这种用法在批量 INSERT / mysqldump 等场景**产生巨额 token 消耗**（20KB SQL ≈ 5,000 token / 次输出，失败重试 + tool_history 复读把单次操作放大到 20,000+ token），同时绕过 `datatalk_import_data` 的批处理 / 流式写 / 错误分段回滚能力。

BUG-0065（ui_exec 绕 Pre-Action）/ BUG-0067（script_run 直连 DB）/ BUG-0069（file_read + execute_sql 绕 import_data）已经证明：**只靠 SKILL.md 的软提示，LLM 会反复钻空子**。我们需要在 execute_sql 这一端立硬契约 —— 但**必须区分 AI 调用 vs 用户在 query_editor 主动运行**，后者哪怕 SQL 上 MB 也不应拦截，因为它根本不进 AI token 上下文。

## What Changes

- **新增** `CallerKind` 枚举（USER/AI），由后端**入口路径强制注入** ActionContext，AI 不可在 input 中伪造来源
- **新增** `BulkSqlGuard`：当 `callerKind=AI` 且 SQL 命中任一阈值时拒绝执行，返回 `error.code=use_import_data` + `nextAction` 自愈提示
  - 阈值 A：sql 字节数 > 4096
  - 阈值 B：INSERT 语句数 > 20
  - 阈值 C：SQL 内容源自上传文件（`input.metadata.sourceFileId` 非空）
- **修改** `ExecuteSqlAction.execute()` 在 confirmation 分支之后、real execute 之前插桩 BulkSqlGuard
- **修改** `messages.properties` / `messages_zh_CN.properties` 的 `action.execute_sql.description`：写明 MUST USE import_data 的三条触发条件、被拒绝时的 nextAction 路径、execute_sql 正确用例、以及 query_editor 用户路径不受约束
- **修改** `skills/sql-execution/SKILL.md` 顶部加 `## ❗ DO NOT` 段（与 data-collection / file-upload-routing 一致的合同测试可断言模式）
- **修改** `SqlExecuteController.POST /sql/execute`：忽略请求体中的 `source` 字段，强制注入 `CallerKind.USER`（堵 AI 借此路径伪装）
- **修改** `McpActionBridge` / `ActionDispatcher`：强制注入 `CallerKind.AI`
- **BREAKING**（内部 API）：`ActionExecutionMetadata` 记录新增 `callerKind` 字段；`ExecuteSqlAction` 不再读 `input.get("source")` 用于 guard 判断（input 字段保留兼容用，仅用于历史记录）

## Capabilities

### New Capabilities

- `execute-sql-bulk-guard`: 描述 AI 发起的批量 SQL 必须被重定向到 `datatalk_import_data` 的硬契约，包括 callerKind 强制注入、三道阈值闸、拒绝响应的 nextAction 结构、用户路径放行规则

### Modified Capabilities

- `chat-sql-execute-direct`: 记录 `callerKind` 来源约定 + AI 路径下大 SQL 被拒的新行为（USER 路径行为不变）
- `agent-skill-routing`: `sql-execution` skill 增加 DO NOT 段（合同测试可断言）

## Impact

**Code**:
- `server/data-talk-domain/src/main/java/com/datatalk/domain/action/` — 新增 `CallerKind.java`，`ActionExecutionMetadata.java` 加字段
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/` — 新增 `BulkSqlGuard.java` + `BulkSqlVerdict.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java` — 插桩 + 不再信任 `input.source`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SqlExecuteController.java` — 强制 USER
- `server/data-talk-application/src/main/java/com/datatalk/application/opencode/McpActionBridge.java` — 强制 AI
- `server/data-talk-application/src/main/java/com/datatalk/application/session/ActionDispatcher.java` — 强制 AI
- `server/data-talk-adapter/src/main/resources/messages.properties` + `messages_zh_CN.properties`
- `server/data-talk-adapter/src/main/resources/skills/sql-execution/SKILL.md`

**APIs**:
- `POST /sql/execute` 请求体 `source` 字段语义变更（被忽略，但保留兼容旧客户端不报错）
- MCP `datatalk_execute_sql` 工具：新增可能返回 `status=rejected` + `error.code=use_import_data` + `nextAction`

**Dependencies**: 无新依赖。复用 `SqlRiskAnalyzer` 现有的 statement split 能力计数 INSERT。

**Risks / 已知 BUG 关联**:
- 与 BUG-0065 / BUG-0067 / BUG-0069 同一族（专用合同工具被通用拼装路径绕过），本 change 是该族修法的第 4 弹
- 需保证 `SqlRiskAnalyzer` 对方言敏感的 INSERT 计数准确（MySQL 的 `INSERT ... ON DUPLICATE KEY UPDATE` 是否计 1 条？）—— 在 design 中明确
- `extraJdbcParams`（BUG-0065 同 change 中 D 段曾讨论的 update_connection schema 扩展）继续 DEFERRED，本 change 不涉及

**N/A**:
- 不动 client/ UI —— 用户编辑器执行路径已经走 `POST /sql/execute`，无需变更，仅强制 USER 注入是后端单边修改
- 不动数据源类型 —— callerKind 与 dialect 正交。但仍按 Data Source Type Compatibility Gate 记录：N/A（不引入新方言、不改变现有方言行为，仅在 AI 路径增加跨方言通用的字节/语句计数闸）
