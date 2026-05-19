## ADDED Requirements

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

## MODIFIED Requirements

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
