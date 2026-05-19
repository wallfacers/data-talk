## Why

一次普通的"把 SQL 文件导入到 datatalk_ctx"任务连续耗掉 20+ 轮工具调用仍未完成，根因是 **4 处数据移动代码都把标识符引用硬编码成 ANSI 双引号** —— MySQL 默认 `sql_mode` 下 `"td_orders"` 是字符串字面量而非标识符，`import_data` / cross-DB copy / 导出 .sql 回灌 / undo log 一律踩雷。AI 在主路径失败后又因 `ui_exec` 错误信息模糊、`script_run` 误用、`update_connection` 接口空槽连续撞墙。本次一次性把这 4 个独立摩擦点闭环。

## What Changes

### A · 引入 dialect-aware 标识符引用（核心，**BREAKING** 内部 API）
- 新增 `IdentifierQuoter` 应用层组件：按 connection kind 派发引用风格 —— MySQL/MariaDB/TiDB/Doris/StarRocks/ClickHouse/OceanBase → 反引号 `` ` ``；PostgreSQL/Oracle/H2/SQLite/Kingbase/GaussDB/Dameng/DuckDB → 双引号 `"`；SQLServer → 方括号 `[]`；并按方言转义嵌入引号字符
- 将以下 4 处硬编码 `quoteIdentifier` 全部替换为 `IdentifierQuoter.quote(id, kind)`：
  - `DataImportService` (CREATE TABLE / INSERT INTO)
  - `ScriptDataWriteService` (CREATE TABLE / INSERT INTO)
  - `DataExportService` (SQL INSERT 导出)
  - `InverseSqlGenerator` (undo log SQL 文本，原实现 **完全不引用**，特殊字符列名会爆)
- 打通 `connectionId → ConnectionKind → quote style` 解析路径（注入 `ConnectionRepository` 或现成的 `ConnectionService`）
- 覆盖 19 种 first-class 连接类型；不在白名单的 kind fallback 双引号 + WARN 日志

### B · `ui_exec` 错误自愈
- `query_editor.run_sql` / `apply_text_edits` 在无活跃 query_editor tab 时返回结构化错误 `{ error: { code: "no_active_query_editor", nextAction: { object: "workspace", action: "open", params: { type: "query_editor", ... } } } }`
- AI prompt / skill 文档不需要改 —— 由 client 错误格式承担"教 AI 怎么自救"

### C · `script_run` 误用防护
- `data-collection` SKILL.md 顶部加 **DO NOT** 段：禁止脚本直连 DB（`pymysql` / `mysql.connector` / `psycopg2` / `sqlite3` 等），唯一合规出口是 `POST {DT_BACKEND_URL}/api/script-data/write`
- 新增 `DataCollectionSkillContractTest` 合同测试断言（参照 BUG-0065 的 `AgentsTemplateContractTest` 套路），防止 prompt 文档退化

### D · `update_connection` 死胡同消除（**DEFERRED**）
- 当前 `datatalk_update_connection_confirmable` schema 不暴露 `sql_mode` / `sessionVariables` / `extraJdbcParams`，AI 想用它"加 ANSI_QUOTES"必然失败
- ~~待决策~~ **决策（2026-05-19，用户确认）**：**本 change 不动 `update_connection` schema**
  - 理由：A 节修复（`IdentifierQuoter` 按方言派发反引号 / 双引号 / 方括号）已**根除 AI 想改 `sql_mode` 的动机**。AI 不再需要为了让 ANSI 双引号在 MySQL 上工作而绕过 schema —— `IdentifierQuoter` 会自动用反引号
  - `extraJdbcParams` 透传是独立的更大改动（涉及安全审查、白名单、UI 表单），需要另起 change 单独评估
  - 当前 schema 不暴露 session 变量本身就是正确的默认 —— 在未做 D 节修复前，AI 撞墙是"错误地试图绕过 A 应有的方言感知"，A 修复后这条路径自然消失
- ⇒ 本 change 不变更 `UpdateConnectionConfirmableAction` 输入 schema；如未来需要 extraJdbcParams，单独 propose `connection-extra-jdbc-params`

## Capabilities

### New Capabilities
- `dialect-identifier-quoting`: 跨 data-import / data-export / script-data-write / undo-log 的共享 IdentifierQuoter 契约。定义 quote 风格矩阵、转义规则、未识别 kind 的 fallback 策略
- `ui-exec-error-semantics`: `datatalk_ui_exec` 失败时的结构化错误契约 —— `error.code` 枚举 + 可选 `error.nextAction` 提示，让 AI 能自愈而无需 prompt 文档教学

### Modified Capabilities
- `data-import`: CREATE TABLE / INSERT INTO 标识符引用按目标 connection kind 派发（取代硬编码双引号）
- `data-export`: SQL INSERT 导出标识符引用按源 connection kind 派发（与 `DATA_SOURCE_TYPE_COMPATIBILITY.md` 已声明但未落地的契约对齐）
- `script-data-write`: 脚本通过 backend write API 写入时，后端生成的 CREATE TABLE / INSERT 标识符引用按目标 kind 派发
- `undo-log`: InverseSqlGenerator 生成的 inverse SQL 标识符按当时连接 kind 引用（含保留字 / 空格 / 引号字符列名）
- `data-collection-recipes`: 新增 "脚本必须通过 backend write API 落库" 硬约束 requirement，禁止直连 DB

## Impact

### 代码
- 新增：`server/data-talk-application/src/main/java/com/datatalk/application/dialect/IdentifierQuoter.java`（含 7+ kind 分派）
- 修改：4 个 `quoteIdentifier` 站点的调用方
- 修改：调用方需取得 `ConnectionKind`，可能涉及构造函数注入 `ConnectionRepository` 到 `InverseSqlGenerator`（当前不依赖任何 connection 信息）
- 修改：`UiExecAction` 错误返回结构（adapter 层）
- 修改：`server/data-talk-adapter/src/main/resources/skills/data-collection/SKILL.md`
- 新增：`DataCollectionSkillContractTest`

### 文档
- 更新 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` 增加 "Identifier quoting per dialect" 章节，并修正 §Streaming Export 表 984 行那条已被代码 backed out 的"按方言引用"声明
- 登记 BUG-0066（标识符方言不感知）+ BUG-0067（script_run 直连 DB 误用 / `data-collection` skill 未约束）

### API / 协议
- 内部 Java API: `DataImportService` / `ScriptDataWriteService` / `DataExportService` / `InverseSqlGenerator` 构造函数签名变化（**BREAKING** 测试装配代码）
- MCP action contract: `datatalk_ui_exec` 错误返回结构 schema 扩展（新增 `error.code` / `error.nextAction` 字段，向下兼容 —— 旧调用方忽略未识别字段即可）

### 风险
- `InverseSqlGenerator` 现在不持有任何 connection 信息，需要重设调用方传 kind 或注入 lookup。可能波及 undo log 写入路径（`UndoLogRepository`），需要 trace 一遍调用栈
- `DataExportService` 行为变更：默认导出 .sql 从"通用 ANSI"变成"源 kind 方言"。需要在 export options 显式标注 `dialect=auto|<kind>`，但默认值需要保持兼容（auto = 跟随源 connection）
- `useCursorFetch=true` / `autoCommit` 等 cursor 参数已在 `DATA_SOURCE_TYPE_COMPATIBILITY` 第 1007-1027 行记录，本次不动；仅引用层修复
- 已知开放 BUG 中无与本范围重叠条目（已 grep `docs/bugs/`，BUG-0065 是 ui_exec 协议绕过但与本次错误信息自愈是不同议题）
