## MODIFIED Requirements

### Requirement: SQL 文件导入意图路由

`file-upload-routing` skill 的意图分类 SHALL 包含 SQL 文件（`analysis.type = "SQL"`），当满足以下全部条件时路由到 `datatalk_import_data`：
1. 用户消息含导入意图信号（"导入" / "入库" / "建表" / "import" / "load"）
2. `analysis.summary.statementTypes` 全部为 INSERT
3. `analysis.summary.targetTables.size = 1`

不满足以上条件时，SQL 文件保留原 riskLevel 路由规则（查询编辑器 / guarded flow）。

`AGENTS.md` 的 File Upload & Analysis 章节 SHALL 与 `file-upload-routing/SKILL.md` 保持同步：SQL file 决策树在 riskLevel 分级之前插入导入意图前置判断。

#### Scenario: SQL 文件意图分类导入

- **GIVEN** 用户上传 .sql 文件，`analysis.summary.statementTypes = ["INSERT"]`，`analysis.summary.targetTables = ["orders"]`
- **AND** 用户消息含"导入"
- **WHEN** `file-upload-routing` skill 决策路由
- **THEN** 路由到 `datatalk_import_data(source: { type: "file", fileId }, target: { connectionId, tableName: "orders" })`
- **AND** NOT 走 query_editor riskLevel 路径

#### Scenario: SQL 文件混合语句保留原行为

- **GIVEN** .sql 文件含 `CREATE TABLE` + `INSERT` 混合（`statementTypes = ["CREATE", "INSERT"]`）
- **WHEN** `file-upload-routing` skill 决策路由
- **THEN** 走原 riskLevel 路径（query_editor + guarded DDL flow）
- **AND** NOT 调用 `datatalk_import_data`

#### Scenario: SQL 文件多目标表保留原行为

- **GIVEN** .sql 文件 `targetTables = ["orders", "customers"]`
- **AND** `statementTypes` 全部为 INSERT
- **WHEN** `file-upload-routing` skill 决策路由
- **THEN** 走原 riskLevel 路径（query_editor + guarded DML flow）
- **AND** NOT 调用 `datatalk_import_data`

#### Scenario: SQL 文件非导入意图保留原行为

- **GIVEN** 用户上传 .sql 文件，`statementTypes = ["INSERT"]`，`targetTables = ["orders"]`
- **AND** 用户消息为"看看这个文件"（非导入意图）
- **WHEN** `file-upload-routing` skill 决策路由
- **THEN** 走原 riskLevel 路径（query_editor）
- **AND** NOT 调用 `datatalk_import_data`

#### Scenario: 纯 SELECT 的 SQL 文件不受影响

- **GIVEN** 用户上传纯 SELECT 的 .sql 文件（`statementTypes = ["SELECT"]`）
- **AND** 用户消息为"看看"
- **WHEN** `file-upload-routing` skill 决策路由
- **THEN** 走 L1 query_editor 路径
- **AND** NOT 调用 `datatalk_import_data`

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
  - `File Upload & Analysis`
  - `Trigger Gate`
  - `Skill Index`
