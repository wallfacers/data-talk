## MODIFIED Requirements

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

### Requirement: SkillResourceSyncer 注册一致性

`com.datatalk.adapter.config.OpenCodeGatewayBeans` SHALL 在启动时为本变更新增的全部 skill 各调用一次 `skillSyncer.syncSkill(name, opencodeCwd)`；已有的 `bezel` 注册 MUST 保留。`classpath:/skills/` 下的目录数与 `OpenCodeGatewayBeans` 中 `syncSkill` 调用次数 MUST 一致。

#### Scenario: 所有 skill 全部注册

- **WHEN** 在测试运行时反射或文本扫描 `OpenCodeGatewayBeans` 中的 `syncSkill` 调用
- **THEN** 调用参数集合包含 `data-collection`（替代 `data-ingestion`），且不包含 `data-ingestion`
- **AND** 调用参数集合 ⊇ { `sql-execution`, `query-editor-workflow`, `ui-contract`, `tab-management`, `er-tabs`, `concurrency-contract`, `charts-and-dashboards`, `artifacts-output`, `connection-management`, `sql-error-diagnostics`, `database-dialects`, `bezel`, `data-collection` }

#### Scenario: classpath 与注册数对齐

- **WHEN** 扫描 `classpath:/skills/*/SKILL.md` 的目录名集合
- **THEN** 该集合等于 `OpenCodeGatewayBeans` 中 `syncSkill` 调用的参数集合

## REMOVED Requirements

### Requirement: SKILL.md frontmatter 契约（ingestion 部分）

**Reason**: `data-ingestion` skill 被删除，替换为 `data-collection` skill。新 skill 的 frontmatter 遵循相同的 `name` + `description` 契约。

**Migration**: `data-ingestion/SKILL.md` → `data-collection/SKILL.md`，recipes 和 examples 完全重写。
