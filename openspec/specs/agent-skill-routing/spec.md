# agent-skill-routing Specification

## Purpose

定义 OpenCode agent 提示的"骨架 + skill 库"路由契约：精简版 `agents/AGENTS.md` 作为骨架（含 Trigger Gate 表与 Skill Index）按主题路由到独立的 `skills/<name>/SKILL.md` 文件，由 `SkillResourceSyncer` 在启动时同步到 OpenCode CWD。本 spec 约束骨架体积、章节、占位符渲染（`AgentPromptBuilder`）、skill frontmatter、关键词归属与双向闭合，确保 AGENTS.md 与 11 个新 skill 之间无重复定义、无悬挂引用。

## Requirements

### Requirement: AGENTS.md 骨架体积上限

精简后的 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` SHALL 在打包到 classpath 时，去掉空行与 `<!-- -->` 注释后非空行数不超过 350 行；模板渲染占位符 `{{STAGE_TAB_DIGEST}}` 与 `{{ACTIVE_SESSION_DIR}}` 必须**同时**保留（详 Requirement "AgentPromptBuilder 占位符渲染保持"）。

#### Scenario: 骨架体积合规

- **GIVEN** AGENTS.md 已经写入精简版
- **WHEN** 构造期工具读取 `classpath:/agents/AGENTS.md` 并去除空行 / HTML 注释
- **THEN** 非空行数 ≤ 350
- **AND** 文本中同时存在子串 `{{STAGE_TAB_DIGEST}}` 与 `{{ACTIVE_SESSION_DIR}}`

#### Scenario: 骨架包含金本位章节

- **WHEN** 解析精简后的 AGENTS.md 顶级二级标题（`## `）
- **THEN** 标题集合 MUST 包含以下章节，且顺序不强约束：
  - `Identity & Hard Constraints`
  - `Intent Routing Gate`
  - `Context Model`
  - `Registered Actions`
  - `Trigger Gate`
  - `Skill Index`

### Requirement: Trigger Gate 表与 skill 双向闭合

AGENTS.md 中的 `## Trigger Gate` 章节 SHALL 以 Markdown 表格出现，每一条目通过形如 `skill:<name>` 的引用唯一指向一个 SKILL.md；该 SKILL.md MUST 存在于 classpath `skills/<name>/SKILL.md`；反向，任何被 `OpenCodeGatewayBeans` 注册的 skill 都 MUST 至少在 AGENTS.md 的 `Trigger Gate` 或 `Skill Index` 中被引用一次。

#### Scenario: 表中 skill 引用全部命中资源

- **WHEN** 提取 AGENTS.md 中所有匹配 `skill:[a-z0-9-]+` 的引用名
- **THEN** 每个引用名都对应一个可读取的 `classpath:/skills/<name>/SKILL.md`

#### Scenario: 注册 skill 反向被引用

- **WHEN** 收集 `OpenCodeGatewayBeans` 中所有 `skillSyncer.syncSkill("<name>", ...)` 的实参
- **THEN** 每个 `<name>` 都 MUST 出现在 AGENTS.md 的 `Trigger Gate` 表或 `Skill Index` 章节中至少一次

### Requirement: SKILL.md frontmatter 契约

新增的每个 SKILL.md（`sql-execution`、`query-editor-workflow`、`ui-contract`、`tab-management`、`er-tabs`、`concurrency-contract`、`charts-and-dashboards`、`artifacts-output`、`connection-management`、`sql-error-diagnostics`、`database-dialects`）SHALL 在文件首部包含一个 YAML frontmatter 块，块内 MUST 提供 `name` 与 `description` 两个键。`name` MUST 与目录名一致。`description` MUST 同时包含中文与英文触发词（至少各 1 个），且总字符长度在 80 至 600 之间。

#### Scenario: frontmatter 字段完备

- **GIVEN** 任一新增 SKILL.md
- **WHEN** 解析 YAML frontmatter
- **THEN** `name` 字段存在且等于父目录名
- **AND** `description` 字段存在且字符长度 ∈ [80, 600]

#### Scenario: 中英双语触发词

- **GIVEN** 任一新增 SKILL.md 的 `description` 字段
- **WHEN** 同时执行 `[A-Za-z]{3,}` 与基于 Unicode CJK Unified Ideographs 区段（`\p{IsHan}` 等价；在 Java 实现中使用 `Pattern.compile("\\p{IsHan}{2,}")`）的正则匹配
- **THEN** 两个匹配集合都至少有 1 个命中

### Requirement: SkillResourceSyncer 注册一致性

`com.datatalk.adapter.config.OpenCodeGatewayBeans` SHALL 在启动时为本变更新增的全部 11 个 skill 各调用一次 `skillSyncer.syncSkill(name, opencodeCwd)`；已有的 `bezel` 与 `data-ingestion` 注册 MUST 保留。`classpath:/skills/` 下的目录数与 `OpenCodeGatewayBeans` 中 `syncSkill` 调用次数 MUST 一致。

#### Scenario: 11 个新 skill 全部注册

- **WHEN** 在测试运行时反射或文本扫描 `OpenCodeGatewayBeans` 中的 `syncSkill` 调用
- **THEN** 调用参数集合 ⊇ {
    `sql-execution`, `query-editor-workflow`, `ui-contract`, `tab-management`, `er-tabs`,
    `concurrency-contract`, `charts-and-dashboards`, `artifacts-output`,
    `connection-management`, `sql-error-diagnostics`, `database-dialects`,
    `bezel`, `data-ingestion`
  }

#### Scenario: classpath 与注册数对齐

- **WHEN** 扫描 `classpath:/skills/*/SKILL.md` 的目录名集合
- **THEN** 该集合等于 `OpenCodeGatewayBeans` 中 `syncSkill` 调用的参数集合

### Requirement: skill 切分边界唯一性（基于关键词清单）

设 `K` = 实施期间从原版 AGENTS.md 中提取并固化在 `openspec/changes/agents-md-skills-refactor/keyword-ownership.md` 的金本位关键词归属表（每条形如 `keyword | owning-skill`，例 `confirmationToken | connection-management`）。AGENTS.md 中由 11 个新 skill 接管的关键规则 SHALL 满足：对 `K` 中每个 `keyword`，其在 11 个新 SKILL.md 全文中作为大小写敏感子串出现的总次数 **首次完整定义** 仅在 `owning-skill` 中；其他 skill 如需引用该规则 MUST 使用 `[[<owning-skill>]]` 形式的短引用而非复制原句。

`keyword-ownership.md` 是本变更目录下的实施期工件（archive 时随分支历史保留，不进 `openspec/specs/`），其内容由 tasks 1.2 产出，至少覆盖：`READ-ONLY`、`confirm=true`、`confirmationToken`、`apply_text_edits`、`truncated`、`set_data_context`、`er_inspector`、`er_designer`、`expectedVersion`、`supersedes`、`information_schema`、`use xxx`。

#### Scenario: 关键词唯一归属

- **GIVEN** 关键词归属表 `K` 与 11 个新 SKILL.md 文件集合
- **WHEN** 对 `K` 中每个 `keyword` 在 11 个 SKILL.md 中执行大小写敏感子串搜索
- **THEN** 命中文件集合 ⊆ {`owning-skill` 自身, 任意使用 `[[<owning-skill>]]` 短引用的其他 SKILL.md}
- **AND** 在 `owning-skill` 自身文件中至少命中 1 次（即首次完整定义存在）

#### Scenario: 非 owning-skill 文件不复制原文

- **GIVEN** 一个 `keyword`（归属 `owning-skill=X`）和另一个 `skill=Y`（`Y≠X`）
- **WHEN** `Y` 的 SKILL.md 中包含该 `keyword`
- **THEN** 该出现位置所在的句子 MUST 同行或上下相邻行包含 `[[X]]` 形式的短引用

### Requirement: AgentPromptBuilder 占位符渲染保持

`com.datatalk.application.stage.AgentPromptBuilder.render(String)` SHALL 在精简版 AGENTS.md 上继续替换且**仅替换**以下两个占位符：
- `{{STAGE_TAB_DIGEST}}` → `## Open Tabs Snapshot` 内容块（截断阈值 `MAX_RENDERED_CHARS = 1_500`）。
- `{{ACTIVE_SESSION_DIR}}` → 当 `ActiveSessionDirProvider.currentSessionId()` 返回 `Optional.of(sid)` 时 `./sessions/<sid>/`；返回 `Optional.empty()` 时 `<no active session>`。

精简版 AGENTS.md MUST 同时包含这两个占位符的字面文本；wiring 类 `com.datatalk.adapter.agents.AgentPromptCustomizer` SHALL 继续从 classpath `agents/AGENTS.md` 加载文本，并把 `AgentPromptBuilder.render()` 输出注入 `OpenCodeBootstrapWriter`。本变更 MUST NOT 修改 `AgentPromptBuilder` / `AgentPromptCustomizer` 任何公共方法签名或异常合约。

#### Scenario: 两个占位符在精简版 AGENTS.md 中都存在

- **WHEN** 读取 `classpath:/agents/AGENTS.md` 全文
- **THEN** 字面字符串 `{{STAGE_TAB_DIGEST}}` 出现 ≥ 1 次
- **AND** 字面字符串 `{{ACTIVE_SESSION_DIR}}` 出现 ≥ 1 次

#### Scenario: 渲染后无残留占位符

- **GIVEN** stub `StageTabRepository`（任意状态，返回 ≤ MAX_TABS 条 tab）+ stub `ActiveSessionDirProvider.currentSessionId() = Optional.of("S-1")`
- **WHEN** `AgentPromptBuilder.render(rawAgentsMd)` 被调用
- **THEN** 输出字符串中**不再**包含子串 `{{STAGE_TAB_DIGEST}}` 或 `{{ACTIVE_SESSION_DIR}}`
- **AND** 输出包含子串 `./sessions/S-1/`

#### Scenario: 无活动 session 时渲染 sentinel

- **GIVEN** stub `ActiveSessionDirProvider.currentSessionId() = Optional.empty()`
- **WHEN** `AgentPromptBuilder.render(rawAgentsMd)` 被调用
- **THEN** 输出包含子串 `<no active session>`

#### Scenario: 加载路径与异常签名不变

- **WHEN** `AgentPromptCustomizer` 构造的 supplier 被调用
- **THEN** 实际读取的 classpath 资源路径仍是 `agents/AGENTS.md`
- **AND** 资源缺失时抛出 `RuntimeException`（cause 是 `IOException`），message 含 "AGENTS.md not found"

### Requirement: SKILL.md 不得包含模板占位符

任何新增的 `classpath:/skills/<name>/SKILL.md` SHALL NOT 在文本中包含形如 `{{[A-Z_]+}}` 的占位符字面量；占位符仅允许出现在 AGENTS.md 中（因为 `AgentPromptBuilder.render()` 不处理 skill 文件）。

#### Scenario: skill 文件无占位符

- **WHEN** 对每个新增 SKILL.md 执行正则 `\{\{[A-Z_]+\}\}` 搜索
- **THEN** 匹配次数 = 0
