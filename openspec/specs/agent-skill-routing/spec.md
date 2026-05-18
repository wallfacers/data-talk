# agent-skill-routing Specification

## Purpose

定义 OpenCode AI 通过 AGENTS.md 与 SKILL.md 进行意图路由的契约：AGENTS.md 骨架体积、Trigger Gate 表与 skill 的双向闭合关系、`SkillResourceSyncer` 与 classpath 资源的一致性。该 spec 保证 AI 可基于精简骨架快速找到匹配 skill，且打包资源与运行时注册不会漂移。

## Requirements

### Requirement: AGENTS.md 骨架体积上限

精简后的 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` SHALL 在打包到 classpath 时，去掉空行与 `<!-- -->` 注释后非空行数不超过 350 行；模板渲染占位符 `{{STAGE_TAB_DIGEST}}`、`{{ACTIVE_SESSION_DIR}}` 与 `{{SEMANTIC_MODEL_DIGEST}}` 必须**同时**保留。

#### Scenario: 骨架体积合规

- **GIVEN** AGENTS.md 已经写入精简版（含 `{{SEMANTIC_MODEL_DIGEST}}` 占位符 + 1 行 Trigger Gate + 2 行 Skill Index）
- **WHEN** 构造期工具读取 `classpath:/agents/AGENTS.md` 并去除空行 / HTML 注释
- **THEN** 非空行数 ≤ 350
- **AND** 文本中同时存在子串 `{{STAGE_TAB_DIGEST}}`、`{{ACTIVE_SESSION_DIR}}`、`{{SEMANTIC_MODEL_DIGEST}}`

#### Scenario: 骨架包含金本位章节

- **WHEN** 解析精简后的 AGENTS.md 顶级二级标题（`## `）
- **THEN** 标题集合 MUST 包含以下章节，且顺序不强约束：
  - `Identity & Hard Constraints`
  - `Intent Routing Gate`
  - `Context Model`
  - `Registered Actions`
  - `Trigger Gate`
  - `Skill Index`

### Requirement: AgentPromptBuilder 占位符渲染保持

`com.datatalk.application.stage.AgentPromptBuilder.render(String)` SHALL 在精简版 AGENTS.md 上继续替换且**仅替换**以下**三个**占位符：

- `{{STAGE_TAB_DIGEST}}` → `## Open Tabs Snapshot` 内容块（截断阈值 `MAX_RENDERED_CHARS = 1_500`）。
- `{{ACTIVE_SESSION_DIR}}` → 当 `ActiveSessionDirProvider.currentSessionId()` 返回 `Optional.of(sid)` 时 `./sessions/<sid>/`；返回 `Optional.empty()` 时 `<no active session>`。
- `{{SEMANTIC_MODEL_DIGEST}}` → `SemanticModelDigester.digest(connectionId)` 返回的 ≤ 2000 字符摘要；当 active session 未绑定 connection 时 `<no semantic model — please bind a connection>`。

精简版 AGENTS.md MUST 同时包含这三个占位符的字面文本；wiring 类 `com.datatalk.adapter.agents.AgentPromptCustomizer` SHALL 继续从 classpath `agents/AGENTS.md` 加载文本，并把 `AgentPromptBuilder.render()` 输出注入 `OpenCodeBootstrapWriter`。本变更 MUST NOT 修改 `AgentPromptBuilder` / `AgentPromptCustomizer` 任何公共方法签名或异常合约。

#### Scenario: 三个占位符在精简版 AGENTS.md 中都存在

- **WHEN** 读取 `classpath:/agents/AGENTS.md` 全文
- **THEN** 字面字符串 `{{STAGE_TAB_DIGEST}}` 出现 ≥ 1 次
- **AND** 字面字符串 `{{ACTIVE_SESSION_DIR}}` 出现 ≥ 1 次
- **AND** 字面字符串 `{{SEMANTIC_MODEL_DIGEST}}` 出现 ≥ 1 次

#### Scenario: 渲染后无残留占位符

- **GIVEN** stub `StageTabRepository`（任意状态，返回 ≤ MAX_TABS 条 tab）+ stub `ActiveSessionDirProvider.currentSessionId() = Optional.of("S-1")` + stub `SemanticModelDigester.digest("c1")` 返回非空字符串
- **WHEN** `AgentPromptBuilder.render(rawAgentsMd)` 被调用
- **THEN** 输出字符串中**不再**包含子串 `{{STAGE_TAB_DIGEST}}` / `{{ACTIVE_SESSION_DIR}}` / `{{SEMANTIC_MODEL_DIGEST}}`
- **AND** 输出包含子串 `./sessions/S-1/`
- **AND** 输出包含 `SemanticModelDigester` 返回的摘要文本

#### Scenario: 无活动 session 时渲染 sentinel

- **GIVEN** stub `ActiveSessionDirProvider.currentSessionId() = Optional.empty()`
- **WHEN** `AgentPromptBuilder.render(rawAgentsMd)` 被调用
- **THEN** 输出包含子串 `<no active session>`
- **AND** 输出包含子串 `<no semantic model — please bind a connection>`

#### Scenario: 无 connection 绑定但有 session 时渲染 Semantic Model sentinel

- **GIVEN** stub `ActiveSessionDirProvider.currentSessionId() = Optional.of("S-2")` + stub session `S-2` 未绑定 connection
- **WHEN** `AgentPromptBuilder.render(rawAgentsMd)` 被调用
- **THEN** 输出包含子串 `./sessions/S-2/`
- **AND** 输出包含子串 `<no semantic model — please bind a connection>`

#### Scenario: 加载路径与异常签名不变

- **WHEN** `AgentPromptCustomizer` 构造的 supplier 被调用
- **THEN** 实际读取的 classpath 资源路径仍是 `agents/AGENTS.md`
- **AND** 资源缺失时抛出 `RuntimeException`（cause 是 `IOException`），message 含 "AGENTS.md not found"

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

`com.datatalk.adapter.config.OpenCodeGatewayBeans` SHALL 在启动时为本变更新增的全部 skill 各调用一次 `skillSyncer.syncSkill(name, opencodeCwd)`；已有的 `bezel` 注册 MUST 保留。`classpath:/skills/` 下的目录数与 `OpenCodeGatewayBeans` 中 `syncSkill` 调用次数 MUST 一致。

#### Scenario: 所有 skill 全部注册

- **WHEN** 在测试运行时反射或文本扫描 `OpenCodeGatewayBeans` 中的 `syncSkill` 调用
- **THEN** 调用参数集合 ⊇ {
    `sql-execution`, `query-editor-workflow`, `ui-contract`, `tab-management`, `er-tabs`,
    `concurrency-contract`, `charts-and-dashboards`, `artifacts-output`,
    `connection-management`, `sql-error-diagnostics`, `database-dialects`,
    `bezel`,
    `skill-creator`, `semantic-model-usage`
  }
- **AND** 集合 MUST NOT 包含 `data-ingestion`（由 `script-runner` change 移除）

#### Scenario: classpath 与注册数对齐

- **WHEN** 扫描 `classpath:/skills/*/SKILL.md` 的目录名集合
- **THEN** 该集合等于 `OpenCodeGatewayBeans` 中 `syncSkill` 调用的参数集合

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
