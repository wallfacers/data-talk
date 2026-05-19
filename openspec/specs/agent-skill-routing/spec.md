# agent-skill-routing Specification

## Purpose

定义 OpenCode AI 通过 AGENTS.md 与 SKILL.md 进行意图路由的契约：AGENTS.md 骨架体积、Trigger Gate 表与 skill 的双向闭合关系、`SkillResourceSyncer` 与 classpath 资源的一致性。该 spec 保证 AI 可基于精简骨架快速找到匹配 skill，且打包资源与运行时注册不会漂移。
## Requirements
### Requirement: AGENTS.md 骨架体积上限

精简后的 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` SHALL 在打包到 classpath 时,去掉空行与 `<!-- -->` 注释后非空行数不超过 350 行;模板渲染占位符 `{{STAGE_TAB_DIGEST}}`、`{{ACTIVE_SESSION_DIR}}`、`{{SEMANTIC_MODEL_DIGEST}}`、`{{ACTIVE_CONNECTION_SUMMARY}}` 与 `{{RECENT_FAILED_QUERIES_DIGEST}}` 必须**全部同时**保留。

#### Scenario: 骨架体积合规

- **GIVEN** AGENTS.md 已经写入扩展版(含 5 个占位符 + Pre-Action Exploration Protocol 段 + Trigger Gate 新增行 + Skill Index 新增行 + Registered Actions 表新增 2 行)
- **WHEN** 构造期工具读取 `classpath:/agents/AGENTS.md` 并去除空行 / HTML 注释
- **THEN** 非空行数 ≤ 350
- **AND** 文本中同时存在子串 `{{STAGE_TAB_DIGEST}}`、`{{ACTIVE_SESSION_DIR}}`、`{{SEMANTIC_MODEL_DIGEST}}`、`{{ACTIVE_CONNECTION_SUMMARY}}`、`{{RECENT_FAILED_QUERIES_DIGEST}}`

#### Scenario: 骨架包含金本位章节

- **WHEN** 解析扩展后的 AGENTS.md 顶级二级标题(`## `)
- **THEN** 标题集合 MUST 包含以下章节,且顺序不强约束:
  - `Identity & Hard Constraints`
  - `Intent Routing Gate`
  - `Context Model`
  - `Pre-Action Exploration Protocol`
  - `Registered Actions`
  - `File Upload & Analysis`
  - `Trigger Gate`
  - `Skill Index`

### Requirement: AgentPromptBuilder 占位符渲染保持

`com.datatalk.application.stage.AgentPromptBuilder.render(String)` SHALL 在扩展版 AGENTS.md 上继续替换且**仅替换**以下**五个**占位符:

- `{{STAGE_TAB_DIGEST}}` → `## Open Tabs Snapshot` 内容块(截断阈值 `MAX_RENDERED_CHARS = 1_500`)。
- `{{ACTIVE_SESSION_DIR}}` → 当 `ActiveSessionDirProvider.currentSessionId()` 返回 `Optional.of(sid)` 时 `./sessions/<sid>/`;返回 `Optional.empty()` 时 `<no active session>`。
- `{{SEMANTIC_MODEL_DIGEST}}` → `SemanticModelDigester.digest(connectionId)` 返回的 ≤ 2000 字符摘要;当 active session 未绑定 connection 时 `<no semantic model — please bind a connection>`。
- `{{ACTIVE_CONNECTION_SUMMARY}}` → `ActiveConnectionSummaryProvider.summary()` 返回的摘要(connectionId / kind / database / schema / 最近 3 次成功 query 列表);当 `Optional.empty()` 时 `<no active connection>`。截断阈值 `MAX_RENDERED_CHARS = 1_500`。
- `{{RECENT_FAILED_QUERIES_DIGEST}}` → `SqlExecutionHistoryProvider.recentFailures(sessionId, 3)` 返回的最近 3 条失败 query 摘要(sql + errorCode + errorMessage 截断);当列表为空或无 active session 时 `<no recent failures>`。截断阈值 `MAX_RENDERED_CHARS = 1_500`。

扩展版 AGENTS.md MUST 同时包含这五个占位符的字面文本;wiring 类 `com.datatalk.adapter.agents.AgentPromptCustomizer` SHALL 继续从 classpath `agents/AGENTS.md` 加载文本,并把 `AgentPromptBuilder.render()` 输出注入 `OpenCodeBootstrapWriter`。本变更 MUST NOT 修改 `AgentPromptBuilder` 已有公共方法签名或异常合约——新增 Provider 通过新构造器引入,保留两个现有构造器使其继续工作。

#### Scenario: 五个占位符在扩展版 AGENTS.md 中都存在

- **WHEN** 读取 `classpath:/agents/AGENTS.md` 全文
- **THEN** 字面字符串 `{{STAGE_TAB_DIGEST}}` 出现 ≥ 1 次
- **AND** 字面字符串 `{{ACTIVE_SESSION_DIR}}` 出现 ≥ 1 次
- **AND** 字面字符串 `{{SEMANTIC_MODEL_DIGEST}}` 出现 ≥ 1 次
- **AND** 字面字符串 `{{ACTIVE_CONNECTION_SUMMARY}}` 出现 ≥ 1 次
- **AND** 字面字符串 `{{RECENT_FAILED_QUERIES_DIGEST}}` 出现 ≥ 1 次

#### Scenario: 渲染后无残留占位符

- **GIVEN** stub `StageTabRepository`(任意状态,返回 ≤ MAX_TABS 条 tab) + stub `ActiveSessionDirProvider.currentSessionId() = Optional.of("S-1")` + stub `SemanticModelDigester.digest("c1")` 返回非空字符串 + stub `ActiveConnectionSummaryProvider.summary()` 返回非空 + stub `SqlExecutionHistoryProvider.recentFailures("S-1", 3)` 返回非空列表
- **WHEN** `AgentPromptBuilder.render(rawAgentsMd)` 被调用
- **THEN** 输出字符串中**不再**包含以下任一子串:`{{STAGE_TAB_DIGEST}}` / `{{ACTIVE_SESSION_DIR}}` / `{{SEMANTIC_MODEL_DIGEST}}` / `{{ACTIVE_CONNECTION_SUMMARY}}` / `{{RECENT_FAILED_QUERIES_DIGEST}}`
- **AND** 输出包含子串 `./sessions/S-1/`
- **AND** 输出包含 `SemanticModelDigester` 返回的摘要文本
- **AND** 输出包含 `ActiveConnectionSummaryProvider.summary()` 返回的 connection 标识
- **AND** 输出包含 `SqlExecutionHistoryProvider.recentFailures` 返回的至少一条 sql 片段

#### Scenario: 无活动 session 时渲染 sentinel

- **GIVEN** stub `ActiveSessionDirProvider.currentSessionId() = Optional.empty()`
- **WHEN** `AgentPromptBuilder.render(rawAgentsMd)` 被调用
- **THEN** 输出包含子串 `<no active session>`
- **AND** 输出包含子串 `<no semantic model — please bind a connection>`
- **AND** 输出包含子串 `<no recent failures>`

#### Scenario: 无 connection 绑定但有 session 时渲染 Semantic Model 与 Connection Summary sentinel

- **GIVEN** stub `ActiveSessionDirProvider.currentSessionId() = Optional.of("S-2")` + stub session `S-2` 未绑定 connection
- **WHEN** `AgentPromptBuilder.render(rawAgentsMd)` 被调用
- **THEN** 输出包含子串 `./sessions/S-2/`
- **AND** 输出包含子串 `<no semantic model — please bind a connection>`
- **AND** 输出包含子串 `<no active connection>`

#### Scenario: 无最近失败 query 时渲染 sentinel

- **GIVEN** stub `SqlExecutionHistoryProvider.recentFailures("S-1", 3)` 返回空列表
- **WHEN** `AgentPromptBuilder.render(rawAgentsMd)` 被调用
- **THEN** 输出包含子串 `<no recent failures>`

#### Scenario: 占位符内容超长截断

- **GIVEN** stub `ActiveConnectionSummaryProvider.summary()` 返回总长度 > 1500 字符的摘要
- **WHEN** `AgentPromptBuilder.render(rawAgentsMd)` 被调用
- **THEN** 输出中 `{{ACTIVE_CONNECTION_SUMMARY}}` 替换片段长度 ≤ 1500 字符
- **AND** 该片段末尾含 `...` 截断标识

#### Scenario: 加载路径与异常签名不变

- **WHEN** `AgentPromptCustomizer` 构造的 supplier 被调用
- **THEN** 实际读取的 classpath 资源路径仍是 `agents/AGENTS.md`
- **AND** 资源缺失时抛出 `RuntimeException`(cause 是 `IOException`),message 含 "AGENTS.md not found"

#### Scenario: 既有构造器签名保持

- **WHEN** 静态分析 `AgentPromptBuilder` 公共构造器
- **THEN** 含 5 参构造器 `AgentPromptBuilder(StageTabRepository, SessionTitleLookup, ActiveSessionDirProvider, SemanticModelDigester, ConnectionIdProvider)`
- **AND** 含 2 参构造器 `AgentPromptBuilder(StageTabRepository, SessionTitleLookup)`
- **AND** 这两个构造器调用时不抛新增异常,且新增的两个 Provider 默认提供"空数据"行为(渲染对应 sentinel)

### Requirement: Trigger Gate 表与 skill 双向闭合

AGENTS.md 中的 `## Trigger Gate` 章节 SHALL 以 Markdown 表格出现，每一条目通过形如 `skill:<name>` 的引用唯一指向一个 SKILL.md；该 SKILL.md MUST 存在于 classpath `skills/<name>/SKILL.md`；反向，任何被 `OpenCodeGatewayBeans` 注册的 skill 都 MUST 至少在 AGENTS.md 的 `Trigger Gate` 或 `Skill Index` 中被引用一次。本变更 MUST 在 `Trigger Gate` 中新增至少一行指向 `skill:semantic-model-usage`，并在 `Skill Index` 中同时新增 `skill:semantic-model-usage` 与 `skill:skill-creator` 两个条目。

#### Scenario: 表中 skill 引用全部命中资源

- **WHEN** 提取 AGENTS.md 中所有匹配 `skill:[a-z0-9-]+` 的引用名
- **THEN** 每个引用名都对应一个可读取的 `classpath:/skills/<name>/SKILL.md`

#### Scenario: 注册 skill 反向被引用

- **WHEN** 收集 `OpenCodeGatewayBeans` 中所有 `skillSyncer.syncSkill("<name>", ...)` 的实参
- **THEN** 每个 `<name>` 都 MUST 出现在 AGENTS.md 的 `Trigger Gate` 表或 `Skill Index` 章节中至少一次

#### Scenario: 业务语义触发词路由到 semantic-model-usage

- **GIVEN** AGENTS.md 的 Trigger Gate 表
- **WHEN** 解析所有 `When you ... | You MUST load` 行
- **THEN** 至少有 1 行的 trigger 含子串 "business metric" 或 "业务指标" 或 "natural language metric"，对应的 skill 为 `skill:semantic-model-usage`

### Requirement: SkillResourceSyncer 注册一致性

`com.datatalk.adapter.config.OpenCodeGatewayBeans` SHALL 在启动时为本变更新增的全部 skill 各调用一次 `skillSyncer.syncSkill(name, opencodeCwd)`;已有的 skill 注册 MUST 保留。`classpath:/skills/` 下的目录数与 `OpenCodeGatewayBeans` 中 `syncSkill` 调用次数 MUST 一致。

#### Scenario: 所有 skill 全部注册(含 exploring-data)

- **WHEN** 在测试运行时反射或文本扫描 `OpenCodeGatewayBeans` 中的 `syncSkill` 调用
- **THEN** 调用参数集合 ⊇ {
    `sql-execution`, `query-editor-workflow`, `ui-contract`, `tab-management`, `er-tabs`,
    `concurrency-contract`, `charts-and-dashboards`, `artifacts-output`,
    `connection-management`, `sql-error-diagnostics`, `database-dialects`,
    `bezel`,
    `skill-creator`, `semantic-model-usage`,
    `exploring-data`
  }
- **AND** 集合 MUST NOT 包含 `data-ingestion`(由 `script-runner` change 移除)

#### Scenario: classpath 与注册数对齐

- **WHEN** 扫描 `classpath:/skills/*/SKILL.md` 的目录名集合
- **THEN** 该集合等于 `OpenCodeGatewayBeans` 中 `syncSkill` 调用的参数集合

#### Scenario: exploring-data 同步到运行时目录

- **GIVEN** Spring 应用启动完成,`SkillResourceSyncer.syncSkill("exploring-data", projectRoot)` 已被调用
- **WHEN** 检查文件系统 `<projectRoot>/.opencode/skills/exploring-data/SKILL.md`
- **THEN** 文件存在且内容等于 classpath 资源
- **AND** marker 文件 `<projectRoot>/.opencode/.exploring-data-skill-synced` 存在且内容是 SHA-256 hash

### Requirement: SKILL.md frontmatter 契约

每个 SKILL.md SHALL 在文件首部包含一个 YAML frontmatter 块，块内 MUST 提供 `name` 与 `description` 两个键。`name` MUST 与目录名一致。`description` MUST 同时包含中文与英文触发词（至少各 1 个），且总字符长度在 80 至 600 之间。`skill-creator/SKILL.md` 额外 MUST 含 `forked_from` 字段，格式形如 `anthropics/skills@<commit-sha>`。

#### Scenario: 新增 skill frontmatter 字段完备

- **GIVEN** `classpath:/skills/skill-creator/SKILL.md` 或 `classpath:/skills/semantic-model-usage/SKILL.md`
- **WHEN** 解析 YAML frontmatter
- **THEN** `name` 字段存在且等于父目录名
- **AND** `description` 字段存在且字符长度 ∈ [80, 600]
- **AND** `description` 同时包含 `\p{IsHan}{2,}` 与 `[A-Za-z]{3,}` 各至少 1 个匹配

#### Scenario: skill-creator 含 forked_from 字段

- **GIVEN** `classpath:/skills/skill-creator/SKILL.md`
- **WHEN** 解析 YAML frontmatter
- **THEN** `forked_from` 字段存在
- **AND** 字符串匹配正则 `^anthropics/skills@[0-9a-f]{7,40}$`

### Requirement: skill 切分边界唯一性（基于关键词清单）

`agents-md-skills-refactor` change 已固化的 skill 关键词归属表保持不变。新增 `skill-creator` 与 `semantic-model-usage` 引入的新关键词（如 `Semantic Model` / `verified query` / `propose_change` / `pending`）MUST 在 `semantic-model-usage/SKILL.md` 中首次完整定义，其他 skill 如需引用 MUST 使用 `[[semantic-model-usage]]` 形式短引用。

#### Scenario: Semantic Model 关键词归属

- **GIVEN** 关键词集合 `K' = { "Semantic Model", "verified query", "propose_change", "pending", "literal_mapping" }`
- **WHEN** 对 `K'` 中每个关键词在所有 SKILL.md 中执行大小写敏感子串搜索
- **THEN** 命中文件集合 ⊆ { `semantic-model-usage` 自身, `skill-creator`（其首要任务即调用 propose_change） }
- **AND** 在 `semantic-model-usage/SKILL.md` 中每个关键词至少命中 1 次

### Requirement: SKILL.md 不得包含模板占位符

任何 `classpath:/skills/<name>/SKILL.md` SHALL NOT 在文本中包含形如 `{{[A-Z_]+}}` 的占位符字面量；占位符仅允许出现在 AGENTS.md 中（因为 `AgentPromptBuilder.render()` 不处理 skill 文件）。`{{SEMANTIC_MODEL_DIGEST}}` 占位符 MUST 仅出现在 AGENTS.md 中。

#### Scenario: skill 文件无任何占位符

- **WHEN** 对每个 SKILL.md 执行正则 `\{\{[A-Z_]+\}\}` 搜索
- **THEN** 匹配次数 = 0

#### Scenario: SEMANTIC_MODEL_DIGEST 占位符仅在 AGENTS.md

- **WHEN** 在 `classpath:/agents/AGENTS.md` 与所有 `classpath:/skills/*/SKILL.md` 中搜索字面串 `{{SEMANTIC_MODEL_DIGEST}}`
- **THEN** 匹配只出现在 `agents/AGENTS.md` 中，匹配次数 ≥ 1
- **AND** 所有 SKILL.md 中匹配次数 = 0

### Requirement: Registered Actions 工具描述区分 session-scope 与 global-scope

AGENTS.md 中 `## Registered Actions` 工具表对每一个返回数据的工具描述 SHALL 显式区分其作用域是 **session-scope**（依赖或反映当前会话上下文）还是 **global-scope**（与会话无关、跨 session 共享的全局数据）。对于 global-scope 工具，描述中 MUST 明确出现 "does NOT reflect current session" 或语义等价的中文/英文短语；对于 session-scope 工具，描述中 MUST 明确出现 "current session" / "active session" 字样。

`datatalk_list_connections` 是 global-scope 工具，其描述 MUST 在表格中显式提示读者改用 `datatalk_get_data_context` 来获取当前 session 选中的连接。

#### Scenario: list_connections 描述含全局作用域提示

- **GIVEN** `classpath:/agents/AGENTS.md` 中 `## Registered Actions` 表格行
- **WHEN** 定位 `datatalk_list_connections` 行的 Purpose 列
- **THEN** Purpose 列文本同时满足两个条件：
  - 包含字面子串 "does NOT" 或 "不反映" 或 "不指示" 之一（不区分大小写）
  - 包含 `datatalk_get_data_context` 字面子串

#### Scenario: get_data_context 描述含会话作用域提示

- **GIVEN** `classpath:/agents/AGENTS.md` 中 `## Registered Actions` 表格行
- **WHEN** 定位 `datatalk_get_data_context` 行的 Purpose 列
- **THEN** Purpose 列文本包含字面子串 "current session" 或 "当前会话" 之一

### Requirement: Intent Routing Gate 路由会话归属类问题

AGENTS.md 的 `## Intent Routing Gate` 章节 SHALL 包含一条明确的路由规则：当用户提问当前连接、当前数据库、当前 schema、激活的数据源或任何"现在用的是哪个数据源"语义等价问题（**session-attribution question**，跨语言）时，AI MUST 先调用 `datatalk_get_data_context` 读取激活态，然后再决定是否调用 `datatalk_list_connections` / `datatalk_list_connection_targets`。

该规则 MUST 使用语义分类描述（"session-attribution question"），而非穷举关键词，以覆盖多语言变体。

#### Scenario: Intent Routing Gate 含 session-attribution 规则

- **GIVEN** `classpath:/agents/AGENTS.md` 中 `## Intent Routing Gate` 章节文本
- **WHEN** 扫描该章节
- **THEN** 文本包含字面子串 `datatalk_get_data_context`
- **AND** 文本包含字面子串 "session-attribution" 或语义等价的中文短语（如"会话归属"）
- **AND** 该段落明示"先调用"或 "first" 的顺序约束

### Requirement: 路由规则纳入新增条目不破坏骨架体积

本变更新增的 Registered Actions 描述修订与 Intent Routing Gate 路由规则条目 MUST 不使 `classpath:/agents/AGENTS.md` 去掉空行与 `<!-- -->` 注释后的非空行数突破现有 350 行硬约束。

#### Scenario: 新增条目后体积仍合规

- **GIVEN** 本变更已修改 AGENTS.md
- **WHEN** 读取 `classpath:/agents/AGENTS.md` 并去除空行与 HTML 注释
- **THEN** 非空行数 ≤ 350

### Requirement: SQL 文件导入意图路由

`file-upload-routing` skill 的意图分类 SHALL 包含 SQL 文件（`analysis.type = "SQL"`），当满足以下全部条件时路由到 `datatalk_import_data`：
1. 用户消息含导入意图信号（"导入" / "入库" / "建表" / "import" / "load"）
2. `analysis.summary.statementTypes` 仅包含 INSERT、DROP、CREATE（不含 ALTER、UPDATE、DELETE 等）
3. `analysis.summary.targetTables.size = 1`

不满足以上条件时，SQL 文件保留原 riskLevel 路由规则（查询编辑器 / guarded flow）。

`AGENTS.md` 的 `## Registered Actions` 章节 SHALL 更新 `datatalk_execute_sql` 描述：该 action 执行任意 SQL（SELECT / DML / DDL），仅 DELETE 语句需对话式确认（action 返回 `requires_confirmation`，AI 向用户展示影响摘要，用户确认后 AI 二次调用执行）。SHALL NOT 再声明"read-only"或"SELECT only"硬约束。

#### Scenario: SQL 文件意图分类导入（纯 INSERT）

- **GIVEN** 用户上传 .sql 文件，`analysis.summary.statementTypes = ["INSERT"]`，`analysis.summary.targetTables = ["orders"]`
- **AND** 用户消息含"导入"
- **WHEN** `file-upload-routing` skill 决策路由
- **THEN** 路由到 `datatalk_import_data(source: { type: "file", fileId }, target: { connectionId, tableName: "orders" })`
- **AND** NOT 走 query_editor riskLevel 路径

#### Scenario: SQL 文件意图分类导入（DDL + INSERT 混合）

- **GIVEN** 用户上传 .sql 文件，`analysis.summary.statementTypes = ["DROP", "CREATE", "INSERT"]`，`analysis.summary.targetTables = ["orders"]`
- **AND** 用户消息含"导入"或"建表"
- **WHEN** `file-upload-routing` skill 决策路由
- **THEN** 路由到 `datatalk_import_data(source: { type: "file", fileId }, target: { connectionId, tableName: "orders" })`
- **AND** NOT 走 query_editor riskLevel 路径

#### Scenario: SQL 文件含 ALTER 等不支持 DDL 保留原行为

- **GIVEN** .sql 文件含 `ALTER TABLE` + `INSERT`（`statementTypes = ["ALTER", "INSERT"]`）
- **WHEN** `file-upload-routing` skill 决策路由
- **THEN** 走原 riskLevel 路径（query_editor + guarded flow）
- **AND** NOT 调用 `datatalk_import_data`

#### Scenario: SQL 文件多目标表保留原行为

- **GIVEN** .sql 文件 `targetTables = ["orders", "customers"]`
- **AND** `statementTypes` 全部为 INSERT
- **WHEN** `file-upload-routing` skill 决策路由
- **THEN** 走原 riskLevel 路径（query_editor + guarded DML flow）
- **AND** NOT 调用 `datatalk_import_data`

#### Scenario: AI 调用 execute_sql 执行 DDL/DML 不再拦截

- **GIVEN** AI 调用 `datatalk_execute_sql(sql="CREATE TABLE orders (id INT, name VARCHAR(100)); INSERT INTO orders VALUES (1, 'test')")`
- **WHEN** 后端处理该 action
- **THEN** SHALL 直接执行并返回结果
- **AND** SHALL NOT 返回 `blocked_in_chat`

#### Scenario: AI 调用 execute_sql 执行 DELETE 触发确认

- **GIVEN** AI 调用 `datatalk_execute_sql(sql="DELETE FROM orders WHERE id = 1")`
- **WHEN** 后端处理该 action
- **THEN** SHALL 返回 `{ status: "requires_confirmation", confirmationId, message, sqlPreview, affectedObjects }`
- **AND** SHALL NOT 执行任何数据库操作

### Requirement: `datatalk_promote_report` 工具注册与 Trigger Gate

`server/data-talk-adapter/src/main/resources/agents/AGENTS.md` 的 `## Trigger Gate` 表格 SHALL 新增一行将"汇报 / 周报 / 月报 / 季报 / 复盘 / 总结 / postmortem / weekly report / monthly report / quarterly review"类用户意图路由到 `skill:ledger`；`## Skill Index` SHALL 新增 `skill:ledger` 条目，指向 `skills/ledger/SKILL.md`；`OpenCodeGatewayBeans` SHALL 注册 `PromoteReportActionHandler` 使其在 OpenCode tool 列表中暴露名为 `datatalk_promote_report` 的工具。

#### Scenario: Trigger Gate 含 ledger 行

- **WHEN** 解析 AGENTS.md `## Trigger Gate` 表格
- **THEN** MUST 存在至少 1 行的"target"或"skill"列含字符串 `skill:ledger`
- **AND** 该行的"trigger"列 MUST 含字符串"周报"、"月报"、"复盘"、"汇报"、"report"、"postmortem"之一

#### Scenario: Skill Index 含 ledger 条目

- **WHEN** 解析 AGENTS.md `## Skill Index` 章节
- **THEN** MUST 含 `skill:ledger` 字面字符串
- **AND** 该条目 MUST 引用 `skills/ledger/SKILL.md` 路径

#### Scenario: OpenCode 工具列表含 datatalk_promote_report

- **GIVEN** OpenCode gateway bean 初始化完成
- **WHEN** 检查注册的工具列表
- **THEN** MUST 含名为 `datatalk_promote_report` 的工具描述符
- **AND** 该工具的 input schema MUST 至少含 `report` (object, required) 与 `workspaceId` (string, required) 两个字段

### Requirement: ledger skill classpath 资源与运行时目录闭合

ledger skill 在 classpath `skills/ledger/**` 资源中 vendor 的内容 SHALL 与 `data-talk/.opencode/skills/ledger/` 同步后的内容一致；任何被 AGENTS.md 引用的 `skills/ledger/<filename>` 路径 MUST 在 classpath 资源中真实存在。

#### Scenario: classpath 内含 SKILL.md

- **WHEN** 列出 `classpath*:skills/ledger/**` 资源
- **THEN** MUST 含 `SKILL.md`
- **AND** MUST 含 `templates/monthly-business-review.md`
- **AND** MUST 含 `templates/incident-postmortem.md`
- **AND** MUST 含 `data-contract.md`
- **AND** MUST 含 `design-language.md`
- **AND** MUST 含 `section-patterns.md`
- **AND** MUST 含 `assets/fonts/NotoSerifSC-Regular.otf`
- **AND** MUST 含 `assets/fonts/NotoSansSC-Regular.otf`
- **AND** MUST 含 `assets/styles/ledger.css`

#### Scenario: AGENTS.md 引用的 skill 路径同步后存在

- **GIVEN** 服务端启动完成
- **WHEN** 对 AGENTS.md 中每个 `skills/ledger/<path>` 引用做文件存在性检查
- **THEN** 每个引用路径在 `data-talk/.opencode/skills/ledger/<path>` MUST 真实存在

### Requirement: AGENTS.md Pre-Action Exploration Protocol

`server/data-talk-adapter/src/main/resources/agents/AGENTS.md` SHALL 包含一段标题为 `## Pre-Action Exploration Protocol` 的二级章节,内容 MUST 满足下列硬约束:

1. 显式规定时序顺序:`get_data_context → schema_search(if 表名未知) → read_schema → execute_sql`,文本中 MUST 含子串 `Pre-Action` 与该 4 工具的字面名称
2. 显式规定**复杂 SQL**(含 JOIN / GROUP BY / 跨表 / 子查询)前 MUST 先 `read_schema` 自验所有涉及表;文本含子串 "MUST" 与 "complex SQL"(或中文等价"复杂 SQL")
3. 显式规定探索预算:同一会话同一表名 schema/sample 探索失败累计 3 次 MUST 通过 OpenCode 内置 `question` tool 询问用户,而非继续盲试。文本 MUST 含子串 "3 times" 或 "3 次" 与 "question tool"
4. 至少包含 1 组 Good Example 与 1 组 Bad Example(以 markdown code block 或 inline 列表形式),演示"先 schema_search('销售')→read_schema→execute_sql" vs "直接拼 SQL 失败"
5. 全段 MUST NOT 含任何 `skills/<name>/SKILL.md` 字面路径(对照 BUG-0040);如需引用 skill,仅可使用 `skill:<name>` 标识

#### Scenario: Pre-Action Exploration Protocol 章节存在且金句齐全

- **GIVEN** `classpath:/agents/AGENTS.md` 全文
- **WHEN** 扫描二级标题
- **THEN** 标题集合包含 `Pre-Action Exploration Protocol`
- **AND** 该章节文本同时包含字面子串 `get_data_context`、`schema_search`、`read_schema`、`execute_sql`、`question tool`

#### Scenario: 时序与预算硬约束在 protocol 段被明文化

- **GIVEN** `classpath:/agents/AGENTS.md` 中 `## Pre-Action Exploration Protocol` 章节文本
- **WHEN** 在该章节范围内执行字面子串搜索
- **THEN** 至少匹配下列任一关键词组合表达"3 次失败 → 升级":
  - "3 times" 与 "question"
  - "3 次" 与 "question"
- **AND** 至少匹配下列任一关键词表达"复杂 SQL 前自验":
  - "complex SQL" 与 "MUST"
  - "复杂 SQL" 与 "MUST"

#### Scenario: 至少一组 Good/Bad Example

- **GIVEN** `## Pre-Action Exploration Protocol` 章节文本
- **WHEN** 扫描三级或更深标题、或加粗短语
- **THEN** 至少匹配一处 `Good` / `Bad` / `✓` / `✗` 之一的对比示例标记
- **AND** 该对比段内含 `datatalk_schema_search` 字面引用

#### Scenario: protocol 段不含任何 SKILL.md 路径

- **GIVEN** `## Pre-Action Exploration Protocol` 章节文本
- **WHEN** 对该章节执行正则 `skills/[a-z0-9-]+/SKILL\.md` 搜索
- **THEN** 匹配次数 = 0
- **AND** 对该章节执行字面子串 `.opencode/skills/` 搜索,匹配次数 = 0

### Requirement: skill:exploring-data SKILL.md 存在且边界清晰

`classpath:/skills/exploring-data/SKILL.md` SHALL 存在,内容 MUST 满足:

1. YAML frontmatter 含 `name: exploring-data` 与 `description`,description 同时含中文与英文触发词(≥1 个各),长度 ∈ [80, 600] 字符
2. 正文 MUST 含 Pre-Action Exploration Protocol 的完整规则、探索预算具体数值、降级路径
3. MUST 包含与下列 3 个 skill 的边界描述(每个一句话):`sql-execution` / `connection-management` / `query-editor-workflow`
4. MUST 包含至少 4 个 Good/Bad Example(2 个 schema_search + 2 个 query_history 用法对比)
5. MUST NOT 含任何 `skills/<name>/SKILL.md` 字面路径或 `.opencode/skills/` 路径(BUG-0040 防御)
6. MUST NOT 含任何 `{{[A-Z_]+}}` 模板占位符(占位符仅允许出现在 AGENTS.md)

#### Scenario: SKILL.md 物理存在与 frontmatter 合规

- **WHEN** 读取 `classpath:/skills/exploring-data/SKILL.md`
- **THEN** 文件可读,长度 > 0
- **AND** 文件首部含 YAML frontmatter
- **AND** frontmatter `name` 字段 = `"exploring-data"`
- **AND** frontmatter `description` 字段长度 ∈ [80, 600]
- **AND** frontmatter `description` 同时匹配 `\p{IsHan}{2,}` 与 `[A-Za-z]{3,}` 各 ≥ 1 次

#### Scenario: 与三个相关 skill 的边界划分均出现

- **GIVEN** `skills/exploring-data/SKILL.md` 正文
- **WHEN** 扫描全文
- **THEN** 文本同时含字面子串 `skill:sql-execution`、`skill:connection-management`、`skill:query-editor-workflow`
- **AND** 每个引用所在的段落含明确的"边界"/"boundary"/"区别"/"vs"等关键词之一

#### Scenario: 至少 4 个示例对比

- **GIVEN** `skills/exploring-data/SKILL.md` 正文
- **WHEN** 扫描全文统计形如 `Good:` / `Bad:` / `✓` / `✗` / `Example` / `示例` 的标记
- **THEN** 标记总数 ≥ 4
- **AND** 至少 2 处与 `schema_search` 关联,至少 2 处与 `query_history` 关联

#### Scenario: 不含路径串(BUG-0040 防御)

- **GIVEN** `skills/exploring-data/SKILL.md` 全文
- **WHEN** 执行正则 `skills/[a-z0-9-]+/SKILL\.md` 搜索
- **THEN** 匹配次数 = 0
- **WHEN** 执行字面子串 `.opencode/skills/` 搜索
- **THEN** 匹配次数 = 0

### Requirement: AGENTS.md 全文禁止 skill 路径串(BUG-0040 防御)

`classpath:/agents/AGENTS.md` SHALL NOT 在任何位置(章节、表格、注释)出现下列形式的字面字符串:
- 正则匹配 `skills/[a-z0-9-]+/SKILL\.md`
- 字面子串 `.opencode/skills/`
- 字面子串 `~/.agents/skills/`

skill 引用 MUST 一律使用 `skill:<name>` 单 token 形式。该约束适用于本变更新增的所有段落,亦适用于既有段落(如有违反 MUST 在本变更内修复)。

#### Scenario: 全文无 SKILL.md 路径

- **WHEN** 对 `classpath:/agents/AGENTS.md` 执行正则 `skills/[a-z0-9-]+/SKILL\.md` 搜索
- **THEN** 匹配次数 = 0

#### Scenario: 全文无 .opencode/skills 路径

- **WHEN** 对 `classpath:/agents/AGENTS.md` 执行字面子串 `.opencode/skills/` 搜索
- **THEN** 匹配次数 = 0
- **WHEN** 对 `classpath:/agents/AGENTS.md` 执行字面子串 `~/.agents/skills/` 搜索
- **THEN** 匹配次数 = 0

### Requirement: Trigger Gate 新增 exploring-data 路由

`classpath:/agents/AGENTS.md` 的 `## Trigger Gate` 章节 SHALL 至少新增一行,该行的 `When you ...` 列包含字面子串"写涉及多表的 SQL" 或 "first-time table" 或 "join" 之一,对应的 `You MUST load` 列等于 `skill:exploring-data`。同时 `## Skill Index` 章节 MUST 新增一行以 `skill:exploring-data` 起首的条目,并附 ≤ 1 行描述。

#### Scenario: Trigger Gate 含 exploring-data 行

- **GIVEN** `classpath:/agents/AGENTS.md` 的 `## Trigger Gate` 表
- **WHEN** 扫描所有 `| When you ... | You MUST load |` 行
- **THEN** 至少 1 行的 `You MUST load` 列字面值 = `skill:exploring-data`
- **AND** 对应行的 `When you ...` 列包含上述任一关键词

#### Scenario: Skill Index 含 exploring-data 条目

- **GIVEN** `classpath:/agents/AGENTS.md` 的 `## Skill Index` 章节
- **WHEN** 扫描列表项
- **THEN** 至少 1 项以 `skill:exploring-data` 起首(允许带描述)

