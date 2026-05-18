## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: 注册 datatalk_import_data 和 datatalk_export_data MCP tools

`ActionRegistry` SHALL 注册两个新的 MCP action：`datatalk.import_data`（MCP 端名称 `datatalk_import_data`）和 `datatalk.export_data`（MCP 端名称 `datatalk_export_data`）。

#### Scenario: MCP tools/list 包含新工具

- **WHEN** OpenCode 客户端调用 `tools/list`
- **THEN** 返回列表 SHALL 包含 `datatalk_import_data` 和 `datatalk_export_data`
- **AND** 每个工具的 `inputSchema` SHALL 符合 design.md D8 中定义的参数结构

#### Scenario: AGENTS.md Registered Actions 包含新工具

- **WHEN** 解析 AGENTS.md 的 `## Registered Actions` 章节
- **THEN** SHALL 包含 `datatalk_import_data` 和 `datatalk_export_data` 的条目及简短使用说明

#### Scenario: import_data action 路由到 ImportDataActionHandler

- **WHEN** OpenCode 调用 `tools/call`，method 为 `datatalk_import_data`
- **THEN** `McpActionBridge` 路由到 `ImportDataActionHandler` bean
- **AND** handler 输出经过 `DataTalkMcpService.toToolResult()` 包装
- **AND** 输出大小在 128KB budget 内

#### Scenario: export_data action 路由到 ExportDataActionHandler

- **WHEN** OpenCode 调用 `tools/call`，method 为 `datatalk_export_data`
- **THEN** `McpActionBridge` 路由到 `ExportDataActionHandler` bean
