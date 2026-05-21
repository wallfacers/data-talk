## 1. Storage — Flyway V3 + Repository 接口

- [x] 1.1 创建 `server/data-talk-infrastructure/src/main/resources/db/migration/V3__sql_execution_history.sql`,按 design.md D5 schema:`id` PK / `session_id` / `connection_id` / `database_name` / `schema_name` / `sql_text` / `status CHECK IN('success','failure')` / `error_code` / `error_message` / `executed_at` / `duration_ms` / `row_count`;创建索引 `idx_sql_history_session_executed`、`idx_sql_history_session_status`
- [x] 1.2 [parallel] 在 `data-talk-application` 新增 `com.datatalk.application.history.SqlExecutionHistoryService` 接口 + record 入参 record class(`SqlExecutionRecord`);新增 `com.datatalk.application.history.SqlExecutionHistoryProvider` 接口(`recentFailures(sessionId, limit)` / `recentSuccesses(sessionId, limit)`)
- [x] 1.3 [parallel] 在 `data-talk-application` 新增 `com.datatalk.application.connection.ActiveConnectionSummaryProvider` 接口(`summary(): Optional<ConnectionSummary>`),`ConnectionSummary` record(`connectionId / kind / database / schema / recentSuccessfulQueries: List<String>`)
- [x] 1.4 [parallel] 在 `data-talk-application` 新增 `com.datatalk.application.metadata.SchemaSearchService` 接口 + 入参 record(`SchemaSearchRequest`) + 出参 record(`SchemaSearchResult` 含 `candidates / totalCandidates / truncated / hint`)
- [x] 1.5 在 `data-talk-infrastructure` 实现 `SqlExecutionHistoryRepositoryJdbc` + `SqlExecutionHistoryServiceImpl` + `SqlExecutionHistoryProviderJdbc`:JdbcTemplate + virtual thread 写入路径;异步裁剪(`@Async` 或 `Executors.newVirtualThreadPerTaskExecutor()`)
- [x] 1.6 在 `data-talk-infrastructure` 实现 `ActiveConnectionSummaryProviderJdbc`:从 `connection_registry` + `sql_execution_history` 查询当前 session 绑定 + top 3 success
- [x] 1.7 在 `data-talk-infrastructure` 实现 `SchemaSearchRepositoryJdbc`:按 `connection.kind` switch 分发 metadata SQL 模板(MySQL 系 / PG 系 / Oracle 系 / SQLServer / SQLite / ClickHouse / DuckDB / Hive / Trino+Presto),实现 `keyword` 归一化 (`lower()` + `trim()`) 与 `%`/`_` 转义,score 算法按 D4
- [x] 1.8 验证组 1:`mvn install -pl data-talk-application -am -DskipTests` + `mvn install -pl data-talk-infrastructure -am -DskipTests`;`SqlExecutionHistoryRepositoryJdbcTest` 单测(H2 + SQLite 双方言);`SchemaSearchRepositoryJdbcTest` 至少覆盖 H2/SQLite/PG 三方言冒烟

## 2. Application 抽象 — Action 注册前置

- [x] 2.1 [parallel] 在 `data-talk-adapter` 新增 `SchemaSearchAction implements ActionHandler<SchemaSearchInput, SchemaSearchOutput>`,`@DataTalkAction(id="datatalk.schema_search", executor=SERVER, requiresConnection=true, timeoutMs=5000, riskLevel={L1}, category={METADATA}, exposeToMcp=true, description="action.schema_search.description")`;委派至 `SchemaSearchService`
- [x] 2.2 [parallel] 在 `data-talk-adapter` 新增 `QueryHistoryAction implements ActionHandler<QueryHistoryInput, QueryHistoryOutput>`,`@DataTalkAction(id="datatalk.query_history", executor=SERVER, requiresConnection=false, timeoutMs=3000, riskLevel={L1}, category={METADATA}, exposeToMcp=true, description="action.query_history.description")`;委派至 `SqlExecutionHistoryService.list(...)`
- [x] 2.3 [parallel] 修改 `ExecuteSqlAction.execute()`(`data-talk-adapter`)在结果落定后(成功/失败两条路径都)调用 `historyService.record(SqlExecutionRecord.of(...))`;写入失败仅 WARN 日志,不影响 caller(用 `try/catch` 包裹 `record(...)` 调用)
- [x] 2.4 在 `data-talk-adapter/src/main/resources/messages.properties` 新增:`action.schema_search.description=...` 与 `action.query_history.description=...`,文案符合 design.md D7/D8 i18n 描述模板(明确"何时调用")
- [x] 2.5 [parallel] 在 `data-talk-adapter/src/main/resources/messages_zh_CN.properties` 同步新增两条中文描述
- [x] 2.6 验证组 2:`mvn install -pl data-talk-adapter -am -DskipTests`;reflect 检查 `ActionRegistry` 含 `datatalk.schema_search` 与 `datatalk.query_history`

## 3. AgentPromptBuilder 扩展 — 5 个占位符

- [x] 3.1 修改 `data-talk-application` 的 `AgentPromptBuilder.java`:新增 2 个字段 `private final ActiveConnectionSummaryProvider connectionSummary` 与 `private final SqlExecutionHistoryProvider executionHistory`;新增构造器(包含 5 个 Provider 全参),保留现有 5 参 + 2 参构造器(用空数据 Provider 默认实现填充)
- [x] 3.2 在 `render(String)` 内新增两个 `if (result.contains(PLACEHOLDER_ACTIVE_CONNECTION_SUMMARY)) {...}` 与 `if (result.contains(PLACEHOLDER_RECENT_FAILED_QUERIES_DIGEST)) {...}` 分支;复用 `MAX_RENDERED_CHARS = 1500` 截断 + `...` 后缀;沿用 `escape()` 处理 markdown 反引号/三连/`<!--`
- [x] 3.3 实现 sentinel 文本:`<no active connection>` 与 `<no recent failures>`;无 active session 时两者都输出 sentinel
- [x] 3.4 添加常量 `PLACEHOLDER_ACTIVE_CONNECTION_SUMMARY = "{{ACTIVE_CONNECTION_SUMMARY}}"` 与 `PLACEHOLDER_RECENT_FAILED_QUERIES_DIGEST = "{{RECENT_FAILED_QUERIES_DIGEST}}"`
- [x] 3.5 [parallel] 修改 `AgentPromptBuilderTest`:新增"有数据/无数据/超长截断/空 session"四类 case,每个 case 同时断言 5 个占位符全部不残留
- [x] 3.6 验证组 3:`mvn install -pl data-talk-application -am -DskipTests`;运行 `AgentPromptBuilderTest`

## 4. AGENTS.md 改造 + skill:exploring-data 新增

- [x] 4.1 在 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` 新增二级章节 `## Pre-Action Exploration Protocol`(参考 design.md D1 size:净增 ~40 行),内容含:(a) 4 工具时序("get_data_context → schema_search → read_schema → execute_sql"),(b) 复杂 SQL 自验硬约束(关键词 "complex SQL" 与 "MUST"),(c) 3 次失败 → `question` tool 降级(含 "3 times" 字面),(d) 至少 1 组 Good vs Bad Example
- [x] 4.2 [parallel] 在 `AGENTS.md` 的 `## Trigger Gate` 表新增 1 行:`When you ... 写涉及多表 SQL / 第一次操作陌生表 ... | skill:exploring-data`
- [x] 4.3 [parallel] 在 `AGENTS.md` 的 `## Skill Index` 列表新增 `- skill:exploring-data — Pre-Action exploration protocol: 时序 / 探索预算 / 失败降级 / 与 sql-execution / connection-management / query-editor-workflow 的边界`
- [x] 4.4 [parallel] 在 `AGENTS.md` 的 `## Registered Actions` 表新增 2 行:`datatalk_schema_search`(Purpose: 关键词→候选表 top-K,中文/英文/拼音 |skill:exploring-data) 与 `datatalk_query_history`(Purpose: 本 session 最近 SQL 历史 |skill:exploring-data)
- [x] 4.5 在 `AGENTS.md` 顶部或合适位置追加 2 个新占位符的字面字符串:`{{ACTIVE_CONNECTION_SUMMARY}}` 与 `{{RECENT_FAILED_QUERIES_DIGEST}}`(参考现有 3 个占位符所在位置)
- [x] 4.6 全文 grep `skills/[a-z-]+/SKILL\.md` 确认 0 命中;grep `.opencode/skills/` 确认 0 命中;grep `~/.agents/skills/` 确认 0 命中(BUG-0040 防御)
- [x] 4.7 新建 `server/data-talk-adapter/src/main/resources/skills/exploring-data/SKILL.md`,内容包含:(a) YAML frontmatter `name: exploring-data` 与 `description`(中英文触发词各 ≥ 1,80~600 字符),(b) Pre-Action Exploration Protocol 完整规则,(c) 探索预算具体数值 + 失败降级路径,(d) 与 `skill:sql-execution` / `skill:connection-management` / `skill:query-editor-workflow` 各一句话边界,(e) 至少 4 组 Good/Bad Example(2 个 schema_search 用法 + 2 个 query_history 用法),(f) 全文 0 个 `skills/` 路径串,(g) 0 个 `{{...}}` 占位符
- [x] 4.8 修改 `OpenCodeGatewayBeans` 在启动时调用 `skillSyncer.syncSkill("exploring-data", opencodeCwd)`(对照现有 14 个 skill 注册位置,加 1 行)
- [x] 4.9 验证组 4(纯文档/资源,无 mvn 但需契约测试):跑 `AgentsTemplateContractTest` 与 `SkillResourceSyncerTest` 验证新增内容

## 5. 现有 skill 边界审计 — cross-reference 改造

- [x] 5.1 [parallel] 修改 `skills/sql-execution/SKILL.md`:定位"first-time table 探索" / "schema 反复读" / "table doesn't exist" 段落,删除重复内容,改为单行 cross-reference "See `skill:exploring-data` for the Pre-Action Exploration Protocol applicable when exploring an unfamiliar table or writing complex multi-table SQL."
- [x] 5.2 [parallel] 修改 `skills/connection-management/SKILL.md`:定位"确认表存在" / "schema 探索"段落,删除重复内容,改为 cross-reference `skill:exploring-data`
- [x] 5.3 [parallel] 修改 `skills/query-editor-workflow/SKILL.md`:在头部"打开 editor"流程之前加 1 行 "if user is exploring an unfamiliar table or first-time query against this DB, route via `skill:exploring-data` first",其余 editor 流程保持不变
- [x] 5.4 修改后对三个文件 grep `skills/[a-z-]+/SKILL\.md` 与 `.opencode/skills/` 确认 0 命中(本任务可能没引入路径,但顺便扫一遍)
- [x] 5.5 验证组 5:跑修改后的 SKILL.md 通过 `SkillResourceSyncerTest`(若有现成测试)与 `AgentsTemplateContractTest` 的 frontmatter 与体积约束

## 6. 测试矩阵 — 单元 + 集成 + 契约

- [x] 6.1 [parallel] 扩展 `AgentsTemplateContractTest`(`data-talk-adapter` test):新增断言 - 含 `Pre-Action Exploration Protocol` 二级标题、含 5 个占位符字面、Trigger Gate 含 `skill:exploring-data` 行、Skill Index 含 `skill:exploring-data` 条目、Registered Actions 表含 `datatalk_schema_search` 与 `datatalk_query_history` 行、全文 `doesNotContainPattern("skills/[a-z0-9-]+/SKILL\\.md")`、`doesNotContain(".opencode/skills/")`、`doesNotContain("~/.agents/skills/")`、骨架体积 ≤ 350(沿用现有断言)
- [x] 6.2 [parallel] 新增 `ExploringDataSkillContractTest`(`data-talk-adapter` test):读 `classpath:/skills/exploring-data/SKILL.md`,断言 frontmatter `name=exploring-data`、description 长度 ∈ [80, 600]、含中英文触发词各 ≥ 1、正文含三个边界引用、含 ≥ 4 个 example 标记、不含 `skills/` 路径、不含 `{{...}}` 占位符
- [x] 6.3 [parallel] `SchemaSearchActionIT`(集成测,可用 H2/SQLite 内嵌实例):中文 keyword 命中表名/列名/注释、英文 keyword 同时命中表名与列名、SQLite 降级(无 comment 时 commentSnippet=""), `%`/`_` 转义、空 keyword INVALID_ARGUMENT、超长 keyword、limit 上限保护、UNSUPPORTED_DIALECT
- [x] 6.4 [parallel] `QueryHistoryActionIT`:写入 5 条 success + 3 条 failure 后,默认 status 过滤、status="failure"、status="all"、limit 上限、跨 session 隔离、空历史、connectionId 过滤
- [x] 6.5 [parallel] `SqlExecutionHistoryRepositoryJdbcTest`:V3 migration 应用成功、INSERT 后能 SELECT、超 100 条触发裁剪、`sql_text` 4 KB 截断、`error_message` 1 KB 截断
- [x] 6.6 [parallel] `AgentPromptBuilderExtendedTest`(扩展现有):新增 5 个 Scenario:有数据/无数据 sentinel/超长截断/无 session 双 sentinel/既有 2 个构造器签名兼容(反射断言)
- [x] 6.7 [parallel] 扩展 `OpenCodeBootstrapWriterTest`:断言 AGENTS.md 内容(写入 OpenCode config dir 的文件)包含 `## Pre-Action Exploration Protocol` 段标题、含 5 个占位符替换后的实际文本(stub 数据)、`config.instructions` JSON 字段含该 AGENTS.md 绝对路径
- [x] 6.8 [parallel] `SkillResourceSyncerExploringDataTest`(新增):syncSkill("exploring-data", projectRoot) 后,`<projectRoot>/.opencode/skills/exploring-data/SKILL.md` 存在 + 内容与 classpath 一致 + marker 文件存在
- [x] 6.9 验证组 6:`cd server && mvn verify` 全量过(JUnit 5 + AssertJ + WireMock)

## 7. 数据源兼容性 + 文档同步

- [x] 7.1 [parallel] 更新 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`:新增章节 `Schema Search Support Matrix`,按 design.md D4 表格列每个 kind 的支持等级(`full` / `degraded-no-comment` / `unsupported`),关键词:MySQL/MariaDB/TiDB/OceanBase/Apache Doris/StarRocks=full,PG/GaussDB/KingBase=full,Oracle/Dameng=full,SQLServer=full,SQLite=degraded-no-comment,ClickHouse=full,DuckDB=degraded-no-comment,Hive=degraded-table-only,Trino/Presto=full
- [x] 7.2 [parallel] 在 `docs/BACKEND.md`(若存在 ActionHandler 列表段落)同步新增两个 action 的 ID 与说明;不强制(若 BACKEND.md 不维护 Action 索引则跳过此项,显式注明)
- [x] 7.3 [parallel] 检查 `docs/generated/db-schema.md`:若该文件由 build pipeline 自动生成,V3 migration 应用后下次重新 generate 即可;若 manual 维护,手工加 `sql_execution_history` 表条目

## 8. End-to-end 验证 + 集成冒烟

- [x] 8.1 `cd server && mvn clean verify` 全量过,零 compile error / test failure
- [x] 8.2 启动 backend (`mvn spring-boot:run -pl data-talk-adapter`) + 前端 dev 模式(`cd client && npm run tauri dev`) — 后端 PID 722697 监听 8080,Vite dev 监听 1420
- [x] 8.3 创建一个 MySQL connection,DB 中含中文表名 `t_销售订单`(或英文表名 + 中文 column comment 任一可触发场景) — 复用现有 `本地数据库`(MySQL),`test_metrics` 库内 `t_ord_hdr`(订单头表)、`t_ord_dtl`(订单明细表)、`t_sal_emp`(销售员表)等表 + 列均带中文 comment
- [x] 8.4 在 chat 里发 "分析销售趋势",**人工观察** OpenCode SSE 事件流是否真先调用 `datatalk_schema_search('销售')` → 再 `datatalk_read_schema` → 再 `datatalk_execute_sql` — PASS,工具序列 `get_data_context → schema_search → read_schema → execute_sql → render_chart` 完整(log 17:35:59-17:36:21,session a549780c)
- [x] 8.5 触发"探索失败 → question tool"场景:在 chat 里问"查不存在表 `wuwu` 的最近数据",观察 LLM 在 3 次 read_schema 失败后是否调用 `question` tool — FAIL,LLM 走 `datatalk_ui_exec` 绕过 Protocol,记录到 [BUG-0065](../../../docs/bugs/BUG-0065-pre-action-protocol-bypassed-via-ui-exec-path.md)
- [x] 8.6 触发"recent failures digest"场景:连续执行 2 条会失败的 SQL,然后开新轮对话,观察 LLM 是否在 system prompt 拿到了失败历史摘要并避免重蹈覆辙 — PASS,4 条 failure 入库 `sql_execution_history`(session 86a7a169),下一轮 LLM 直接 read_schema 拿真实列名,未重蹈失败 SQL
- [x] 8.7 编译/运行通过且 E2E 行为符合预期后,**写一段 PR 说明草稿** 到 `tmp/agent-context-priming-pr-draft.md`,内容:背景一段(为何引入)、Why 总结(BUG-0040 教训 + Claude Code 范式)、Behavioral diff 截图位置(若有)、Migration note(无)、Rollback note(参考 design.md Migration Plan)。**不**自动提交 PR,等用户审阅 tasks 完成后再决定
