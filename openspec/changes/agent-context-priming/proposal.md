## Why

DataTalk 当前的 AGENTS.md(168 行)和 21 个 skill 已经把"业务路由"做得很彻底——Intent Routing Gate 把意图分桶,Trigger Gate 硬路由到 skill。但**它只回答"该用哪个工具",没有回答"在拿到足够上下文之前别开火"**。结果是 LLM 经常在不调 `datatalk_read_schema` 的情况下直接拼 SQL,遇到中文表名/同义词时尤其严重(`read_schema` 只能按 pattern 过滤,LLM 不知道关键词→表名怎么搜),失败后又会重复同样的错。

Claude Code / OpenCode 解决这一类问题的范式是"先读取足够上下文再行动"——LLM 通过 Read/Grep/Glob 主动探索代码,主线程不写未读过的文件。本 change 把同一范式在 DataTalk 上落地:加一条 **Pre-Action Exploration Protocol** 硬规则、把会话级动态上下文(active connection / 最近成功 query / 最近失败 SQL)灌进 system prompt、补两个 introspection action(关键词搜表、查历史)、并把散在多个 skill 里的探索规则抽出成 `skill:exploring-data` 单一入口。

## What Changes

- **AGENTS.md 新增 `## Pre-Action Exploration Protocol` 段**:显式时序(`get_data_context → schema_search(if 表名未知) → read_schema → execute_sql`)、探索预算(同表 schema/sample 探索失败 3 次必须 escalate via `question` tool 询问用户)、复杂 SQL(JOIN / 聚合 / 跨表)前 MUST 自验所有涉及表
- **AGENTS.md 新增 Good/Bad Example 段**(2 组对比):"用户问销售额 → 直接拼 SQL 失败 vs 先 schema_search('销售') → read_schema → execute_sql"
- **AGENTS.md Trigger Gate 加 1 行**: "准备写涉及多表的 SQL / 第一次查询陌生表" → `skill:exploring-data`
- **AGENTS.md Skill Index 加 `skill:exploring-data`**;**严禁**任何 `skills/<name>/SKILL.md` 路径串(BUG-0040 教训)
- **新 skill: `skills/exploring-data/SKILL.md`** 汇总 Pre-Action Exploration Protocol 完整规则、预算、降级路径、与 `sql-execution` / `connection-management` / `query-editor-workflow` 的边界
- **现有 skill 审计**:`sql-execution` / `connection-management` / `query-editor-workflow` 中重复的"先探索"段落改为 cross-reference `skill:exploring-data`,避免规则漂移
- **AgentPromptBuilder 新增 2 个动态变量**:`{{ACTIVE_CONNECTION_SUMMARY}}`(当前 session connection 的 kind/db/schema/最近 3 次成功 query 摘要)和 `{{RECENT_FAILED_QUERIES_DIGEST}}`(本 session 最近 3 次失败 SQL + 错误消息)
- **新增 SQL 执行历史持久化**(Flyway V3 `sql_execution_history` 表 + repository),后端层面的最小数据回流,前端 composer **不动 UI**——仅在 services/api 层让现有 execute_sql 调用顺路写历史
- **新增 Action `datatalk.schema_search`**:关键词(中英文/拼音)→ 候选表 top-K,基于表名/列名/注释模糊匹配,`RiskLevel.L1` `Category.METADATA` `exposeToMcp=true` `timeoutMs=5000`
- **新增 Action `datatalk.query_history`**:返回本 session 最近 N 次成功 SQL,可按 `connectionId`/`database` 过滤,`RiskLevel.L1` `Category.METADATA` `exposeToMcp=true` `timeoutMs=3000`
- **i18n description**:两个新 action 的 `messages.properties` 文案需明确"何时使用",避免 LLM 不知道何时调

## Capabilities

### New Capabilities

- `database-introspection-actions`: 数据库 introspection 类 Action 的行为契约,目前覆盖关键词搜表(`schema_search`)与会话级 SQL 历史查询(`query_history`),未来其他探索 action(如 sample/relationship)在此扩展

### Modified Capabilities

- `agent-skill-routing`: AGENTS.md 新增 `## Pre-Action Exploration Protocol` 段(强制时序、探索预算、失败降级)、Trigger Gate 新增 1 行映射、Skill Index 新增 `skill:exploring-data`;`AgentPromptBuilder.render(String)` 在已有三个占位符基础上**新增**仅替换 `{{ACTIVE_CONNECTION_SUMMARY}}` 与 `{{RECENT_FAILED_QUERIES_DIGEST}}` 两个占位符;新增 `skill:exploring-data` 与 `sql-execution` / `connection-management` / `query-editor-workflow` 三个 skill 的"先探索"段落边界划分

## Impact

- **后端**:
  - `data-talk-domain`:0 改动(新 Action 走 `@DataTalkAction` 注解,无需改 core)
  - `data-talk-application`:`AgentPromptBuilder` 新增 2 个占位符渲染分支 + 对应 Provider 接口;新增 `SqlExecutionHistoryService` + repository 接口(application 层抽象);可能新增 `SchemaSearchService` 业务编排(若需要跨连接的统一搜索)
  - `data-talk-infrastructure`:新增 Flyway `V3__sql_execution_history.sql`(表 + 索引);`SqlExecutionHistoryRepositoryJdbc` 实现;`SchemaSearchRepositoryJdbc`(按 connection kind 适配中文表名/注释列名)
  - `data-talk-adapter`:新增 `SchemaSearchAction` + `QueryHistoryAction` ActionHandler;`AGENTS.md` 修改;`skills/exploring-data/SKILL.md` 新增;3 个现有 skill `SKILL.md` 审计修改;`messages.properties` 新增 i18n keys

- **前端**:**无 UI 改动**。仅在 `services/channel` 或 `services/api` 层,让 `execute_sql` 调用结束后顺路写 SQL 执行历史(若现有事件流已能 round-trip,则后端 ExecuteSqlAction 自己写入即可,**首选后端写,前端零改动**)。`client/DESIGN.md` 约束 N/A——本 change 不动控件、布局或交互。

- **数据库兼容性**:`schema_search` 需要按 `connection.kind` 适配元数据查询方式——MySQL 用 `information_schema.tables/columns`、PostgreSQL 用 `pg_catalog`、Oracle 用 `ALL_TABLES/ALL_TAB_COLUMNS`、SQLite 用 `sqlite_master`、ClickHouse 用 `system.tables`,等等。需读 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` 并在 design.md 列每个 kind 的支持表。新表 `sql_execution_history` 存在 DataTalk SQLite metadata DB,**不**写用户业务库,无方言兼容问题。

- **OpenCode 协议**:无协议变更。两个新 Action 通过现有 `DataTalkMcpService` → MCP 协议暴露,工具名 `datatalk_schema_search` / `datatalk_query_history`。

- **测试**:
  - `AgentsTemplateContractTest`(已存)扩展:断言新段标题、新占位符、**禁止任何 `skills/<name>/SKILL.md` 路径串**(对照 BUG-0040 校验)
  - `AgentPromptBuilderTest`(已存)扩展:新增 2 个占位符的"有数据/无数据/超长截断" case
  - `OpenCodeBootstrapWriterTest`(已存)扩展:`config.instructions` 注入路径未变,但 AGENTS.md 内容应包含新段
  - 新 Action 集成测:`SchemaSearchActionTest`(WireMock + H2/SQLite/PG 三方言)、`QueryHistoryActionTest`(SQLite metadata + 内存历史)
  - `SkillResourceSyncerTest`(若不存则建)断言 `exploring-data` 被同步

- **风险**(BUG gate 命中两条相关历史):
  - **BUG-0040**(fixed,2026-05-13)"AGENTS.md skill 路径引用 → LLM 幻觉绝对路径 Read 卡住":本 change 写 AGENTS.md / `skill:exploring-data` 时 **MUST** 避免任何 `skills/<name>/SKILL.md` 字面引用,只用 `skill:<name>` 标识;若需要文档片段,**inline** 进 AGENTS.md。`AgentsTemplateContractTest` 必须加 `.doesNotContain("skills/")` 路径反断言
  - **BUG-0036**(fixed)"skills 同步 cwd 错位":`SkillResourceSyncer` 已修复,但新 skill 加入后需复跑同步路径测试,确认 `exploring-data` 落到 `~/.data-talk/opencode/.opencode/skills/exploring-data/`
  - 未命中:`docs/bugs/index.md` 中无 `schema_search` / `query_history` / `AgentPromptBuilder` 占位符相关的 open BUG

- **数据源兼容性 Gate**(必读 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`):
  - `schema_search` 行为依赖 connection 元数据查询能力,涉及全部 17 个 kind,在 design.md 给出每个 kind 的 SQL 模板与降级策略;不支持的 kind(如某些只读 cluster)给明确报错
  - `query_history` 只读 DataTalk metadata,**与 connection kind 无关**

- **Design Inputs**:
  - `client/DESIGN.md`:**N/A**——本 change 不动 client UI
  - `docs/BACKEND.md`:遵守 4 层依赖、`@DataTalkAction` 注册、`mvn install -pl <module> -am -DskipTests` 跨模块编译
  - `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`:`schema_search` 适配矩阵在 design.md
  - 现有 `agent-skill-routing` spec(254 行,GIVEN/WHEN/THEN 风格):本 change delta 沿用同一风格;骨架体积上限 350 行的现有约束 **MUST** 继续满足——新增段会让 AGENTS.md 实际行数上升,需在 design.md 评估骨架是否需"瘦身"(把 Registered Actions 表精简到一句话,把详细 i/o 留给 skill)
