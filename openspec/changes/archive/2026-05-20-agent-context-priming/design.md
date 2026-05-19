## Context

DataTalk 已经把 OpenCode 整合管线打通:`OpenCodeBootstrapWriter` 写 `AGENTS.md` 到 OpenCode config dir 并经 `config.instructions` 注入 system prompt;`SkillResourceSyncer` 把 classpath `skills/*` 同步到 `.opencode/skills/`;`AgentPromptBuilder` 已支持 `{{STAGE_TAB_DIGEST}}` / `{{ACTIVE_SESSION_DIR}}` / `{{SEMANTIC_MODEL_DIGEST}}` 三个动态占位符;`DataTalkMcpService` 把 37 个 `@DataTalkAction` 注解的 ActionHandler 通过 MCP 协议暴露给 OpenCode,前缀 `datatalk_`。OpenCode 侧(`packages/opencode/src/session/prompt/anthropic.txt`)已在系统 prompt 强制 LLM 优先用 Task subagent 探索代码上下文。

但 DataTalk 业务侧的 AGENTS.md 168 行只描述"该用哪个工具"(Intent Routing Gate + Trigger Gate),**没有描述"在拿到足够上下文之前别开火"**。具体表现:LLM 经常跳过 `datatalk_read_schema` 直接拼 SQL;中文表名(如 `销售订单`)无法搜索——`read_schema` 只能按 pattern 过滤,LLM 不知道关键词→表名怎么搜;失败后没有降级路径,反复对同一不存在的表做 read_schema。

历史教训:**BUG-0040**(fixed 2026-05-13)证明 AGENTS.md 任何 `skills/<name>/SKILL.md` 字面引用都会触发 LLM 幻觉补全成 `~/.agents/skills/...` 绝对路径并卡死 Read 工具。本 change 写 AGENTS.md / skill 时 MUST 杜绝此类引用。

## Goals / Non-Goals

**Goals:**

- 在 AGENTS.md 强制 LLM 在写复杂 SQL / 操作陌生表前先 introspect(schema_search / read_schema)
- 给 LLM 提供"探索失败"降级路径:同表 3 次未命中 → escalate via `question` tool
- 把"先探索"散在 sql-execution / connection-management / query-editor-workflow 三个 skill 中的描述统一到 `skill:exploring-data` 单一入口
- 把会话级动态上下文(active connection / 最近成功 query / 最近失败 SQL)灌入 system prompt,让 LLM 不重复犯错
- 补两个 introspection action:`datatalk.schema_search`(关键词→候选表)与 `datatalk.query_history`(本 session 最近成功 SQL)
- 全部测试自动化,**不留尾巴**:契约测试(AgentsTemplateContractTest)、AgentPromptBuilder 单测、新 Action 集成测、SkillResourceSyncer 同步测、OpenCodeBootstrapWriter 注入测

**Non-Goals:**

- **不动前端 UI**(`client/DESIGN.md` N/A)。数据回流走后端,前端零改动
- **不引入新依赖**(WireMock / JUnit 5 / AssertJ 已在 BACKEND.md 清单)
- **不改 OpenCode 协议**——两个新 Action 通过现有 MCP 通道暴露
- **不改 `DtEvent` sealed interface**——新增 action 走 ActionDispatcher 现有 `action.invoke` / `action_result` 通道
- **不做"sample 数据预览"action**——`schema_search` 只返回表/列元数据,行数据探索仍走 `execute_sql LIMIT 3`(避免与 `read_schema` 职责重叠)
- **不实现高级 fuzzy 检索**(如 BM25/语义向量)——首版 LIKE/`%term%` 模糊匹配即可,Day-2 可升级
- **不持久化"探索次数计数器"**——预算靠 LLM 自我约束 + AGENTS.md 写死,不在后端记账
- **不改现有 21 个 skill 的所有内容**——只动 sql-execution / connection-management / query-editor-workflow 三个 skill 的"先探索"段落 cross-reference

## Decisions

### D1: AGENTS.md 骨架体积管控 — 不破坏 350 行上限

**选择:新增 Pre-Action Exploration Protocol 段,同步把 Registered Actions 表行内重复说明精简,净增量目标 ≤ 30 行**

现有 spec `agent-skill-routing` 已写死:精简后 AGENTS.md 非空行数 ≤ 350(`AgentsTemplateContractTest` line 73 区域守门)。当前实际 168 行,本次新增:

| 内容 | 新增行数 |
|---|---|
| `## Pre-Action Exploration Protocol` 段(含时序 / 预算 / 降级 / Good/Bad Example) | ~40 行 |
| Trigger Gate 加 1 行 | 1 行 |
| Skill Index 加 1 行(`skill:exploring-data`) | 1 行 |
| Registered Actions 表新增 2 行(schema_search / query_history) | 2 行 |
| 新增 2 个占位符的字面文本 | 2 行 |
| **小计** | **~46 行** |

预期实际行数 168 + 46 = 214,**安全在 350 内**。但 Registered Actions 现有 37 行表格仍有精简空间(把 Purpose 列从 1~2 句压到 1 句),作为可选 trim,**非本次目标**。

**Alternative**: 把"Pre-Action Exploration Protocol"完全放进 `skill:exploring-data`,AGENTS.md 只放 1 行 Trigger Gate 引用。
**Rejected**: AGENTS.md 是"骨架级硬路由",skill 是"按需展开"。把强制时序放进 skill 等于让 LLM 决定何时加载,违背 Trigger Gate 的强制性。骨架里必须有最小可执行的 protocol;skill 里放完整规则、示例、边界。两层分工。

### D2: AGENTS.md 引用 skill 的格式 — 严格 `skill:<name>`,严禁路径

**选择:全文使用 `skill:<name>` 字面标识,严禁出现 `skills/`、`.opencode/skills/`、`SKILL.md` 等路径片段**

BUG-0040(fixed)已证明任何路径串都会被 LLM 启发式补全成 `~/.agents/skills/<name>/SKILL.md` 绝对路径并触发 Read 卡死。OpenCode 1.14.x 已经在启动时自动加载 skill,**LLM 不需要也不应该主动 Read SKILL.md**。

**契约保证**:`AgentsTemplateContractTest` 必须新增反断言:
```
assertThat(agentsMd).doesNotContainPattern("skills/[a-z-]+/SKILL\\.md");
assertThat(agentsMd).doesNotContain(".opencode/skills/");
```

新 skill `skill:exploring-data` 的内容里也禁止任何路径引用——如果需要展示文件位置,在 skill 内部用相对路径但 **MUST** 标注 "AI: do not Read this file; it is auto-loaded by OpenCode"。

### D3: 探索预算与降级 — 同表 3 次未命中 → question tool

**选择:同一会话同一表名(或关键词)的探索行为,累计 3 次未拿到有效结果即 MUST 调用 `question` tool 询问用户**

预算具体定义:
- "未命中"= `schema_search` 返回空 candidate list / `read_schema` 返回 `noSuchTable` 错误码 / `execute_sql` 失败带 "table doesn't exist"
- "同表"= LLM 自行判断的同一探索目标(规则在 skill 内描述,LLM 自我约束)
- "3 次"= 经验值,Claude Code prompts.ts 用类似数量级的暗示("don't retry the identical action blindly")

**为什么不做计数器**:在后端实现"探索次数追踪"需要为每个 session 维护 `Map<key, count>`,涉及生命周期管理、清理、并发,实现复杂度远高于"LLM 自我约束"的预期收益。把规则写死在 prompt + skill,依赖 LLM 遵循指令(Claude / GPT-4 对这类硬约束遵循率 > 90%)。

**降级目标**:LLM 调 OpenCode 内置 `question` tool(已在 OpenCode `default` agent 权限里 `question: "allow"`),向用户提问"我没找到对应表,你能确认表名吗?"

**Alternative A**:服务端硬限流,3 次后强制返回 error。**Rejected**:破坏 MCP 单 tool 单 call 的 idempotency 语义,且无法定义"同一探索"的边界——LLM 的 keyword 在不同 call 里会变形。
**Alternative B**:不设预算,任 LLM 重试。**Rejected**:历史经验显示 GPT-4 / Claude 在没有明确预算时会陷入"5+ 次相同失败 SQL"循环。

### D4: schema_search 跨方言策略 — 按 `connection.kind` 分发到 metadata SQL 模板

**选择:在 `data-talk-infrastructure` 新增 `SchemaSearchRepositoryJdbc`,内部按 `kind` switch 到不同 metadata SQL 模板**

每个 kind 一份模板,统一返回 `List<TableMatch> { table, schema, database, columnsMatched, commentSnippet, score }`:

| Kind | 元数据来源 | 中文表名支持 |
|---|---|---|
| `mysql` / `mariadb` / `tidb` / `oceanbase` / `apache_doris` / `starrocks` | `information_schema.tables/columns` + `TABLE_COMMENT` | ✓ |
| `postgresql` / `gaussdb` / `kingbase` | `pg_class` / `pg_attribute` + `pg_description.description` | ✓ |
| `oracle` / `dameng` | `ALL_TABLES` / `ALL_TAB_COLUMNS` + `USER_TAB_COMMENTS` | ✓ |
| `sqlserver` | `sys.tables` / `sys.columns` + `extended_properties` | ✓ |
| `sqlite` | `sqlite_master` + 解析 `CREATE TABLE` 取注释 | ⚠️ 无原生 column comment,降级:仅表名/列名匹配 |
| `clickhouse` | `system.tables` / `system.columns` + `comment` 字段 | ✓ |
| `duckdb` | `information_schema.*`(社区扩展不全) | ⚠️ comment 部分支持,降级:仅表名/列名匹配 |
| `hive` | HMS `DESCRIBE EXTENDED <table>` 解析 | ⚠️ 性能差,首版仅返回表名匹配 |
| `trino` / `presto` | `information_schema.*` | ✓ |

**降级策略**:不支持注释列的 kind,score 计算只用表名/列名 LIKE 匹配,不抛错。响应里 `commentSnippet` 字段为空。

**匹配算法**(首版,Day-2 可升级 BM25):
```
score = 3 * (表名 ILIKE %keyword%) + 2 * (列名 ILIKE %keyword%) + 1 * (注释 ILIKE %keyword%)
```
单次返回 top 10。

**Alternative**:统一用 JDBC `DatabaseMetaData.getTables()` API。
**Rejected**:JDBC metadata API 不返回 column comment / table comment,而中文表名场景下注释是关键信号(用户写中文,DB 表是英文,只有注释里有"销售订单"这种翻译)。必须直查 information_schema 或方言特定视图。

### D5: query_history 存储 — Flyway V3 新表

**选择:在 DataTalk metadata SQLite 新增 Flyway `V3__sql_execution_history.sql`**

```sql
CREATE TABLE sql_execution_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id TEXT NOT NULL,
  connection_id TEXT NOT NULL,
  database_name TEXT,
  schema_name TEXT,
  sql_text TEXT NOT NULL,            -- 截断到 4 KB
  status TEXT NOT NULL,              -- 'success' | 'failure'
  error_code TEXT,                   -- 仅 failure 行有
  error_message TEXT,                -- 仅 failure 行有,截断到 1 KB
  executed_at INTEGER NOT NULL,      -- epoch ms
  duration_ms INTEGER,
  row_count INTEGER                  -- 仅 success 行有
);
CREATE INDEX idx_sql_history_session_executed
  ON sql_execution_history(session_id, executed_at DESC);
CREATE INDEX idx_sql_history_session_status
  ON sql_execution_history(session_id, status, executed_at DESC);
```

**写入路径**:`ExecuteSqlAction.execute()` 内部在结果落定后顺路写入(application 层调 `SqlExecutionHistoryService.record(...)`)。失败 SQL 也写,带 `error_code` / `error_message`。**前端零改动**。

**保留策略**:每个 session 仅保留最近 100 条;`AgentPromptBuilder` 渲染时只取 top 3。100 条上限通过 `INSERT` 后异步触发的 `DELETE FROM sql_execution_history WHERE session_id = ? AND id NOT IN (SELECT id FROM ... LIMIT 100)` 维护,**不阻塞** SQL 执行返回路径。

**Alternative**:写日志文件 / 写 OpenCode 自己的 session log。
**Rejected**:OpenCode session log 是 LLM 视角的对话记录,字段不一致;日志文件无法被 `AgentPromptBuilder` 高效消费。SQLite metadata 表是 DataTalk 一直在用的模式,与 `user_message_attachments`(V2 已建)同层。

### D6: AgentPromptBuilder 新占位符的实现

**选择:沿用现有 `Provider` 接口模式,新增 2 个 Provider + 2 个 placeholder 替换分支**

现有架构(`AgentPromptBuilder.java`):
```
private final StageTabRepository repo;           → {{STAGE_TAB_DIGEST}}
private final ActiveSessionDirProvider activeDir;→ {{ACTIVE_SESSION_DIR}}
private final SemanticModelDigester semanticDigester;
private final ConnectionIdProvider connectionIdProvider;
                                                 → {{SEMANTIC_MODEL_DIGEST}}
```

新增:
```
private final ActiveConnectionSummaryProvider connectionSummary;
                                                 → {{ACTIVE_CONNECTION_SUMMARY}}
private final SqlExecutionHistoryProvider executionHistory;
                                                 → {{RECENT_FAILED_QUERIES_DIGEST}}
```

两个 Provider 都是 application 层接口(纯函数式),infrastructure 层实现:
- `ActiveConnectionSummaryProvider.summary(): Optional<ConnectionSummary>` — 返回 `{ connectionId, kind, database, schema, recentSuccessfulQueries: List<String> top 3 }`
- `SqlExecutionHistoryProvider.recentFailures(sessionId, limit): List<FailedQuery>` — 返回 `[{ sqlText, errorCode, errorMessage, executedAt }]`

**渲染规则**:
- 空数据 / 无 session → 输出 `<no recent activity>` 字面文本,不留占位符
- 超过 `MAX_RENDERED_CHARS = 1500` → 截断 + "..." 后缀(与现有 STAGE_TAB_DIGEST 一致)
- SQL 文本里的 markdown 反引号 / 三连反引号 / `<!--` 转义(沿用 `escape()` 工具方法)

**保持向后兼容**:`AgentPromptBuilder` 的两个现有构造器维持公共 API 不变,新构造器是它们的超集。`AgentPromptCustomizer` wiring 类不变签名。

### D7: schema_search 输入 schema 设计

**输入**:
```json
{
  "keyword": "string (required, ≤ 200 chars)",
  "connectionId": "string (optional, 默认从 session data context 取)",
  "database": "string (optional)",
  "schema": "string (optional)",
  "limit": "integer (optional, default 10, max 30)"
}
```

**输出**:
```json
{
  "candidates": [
    {
      "table": "t_sales_order",
      "schema": "public",
      "database": "shop",
      "score": 5,
      "matchedOn": ["table_name", "column.order_date.comment"],
      "commentSnippet": "销售订单主表..."
    }
  ],
  "totalCandidates": 12,
  "truncated": false,
  "hint": "若结果为空,可尝试拼音 / 同义词 / 缩写"
}
```

**i18n description**(必须明确"何时调用"):
> 关键词搜表,把"销售订单/users/u"这类自然语言关键词解析为候选表 top-K。**在不知道表名时优先调用此 action**,而非反复 `read_schema` 猜表名。返回每张表的匹配位置(表名/列名/注释)、score、注释摘要。支持中文/英文/拼音。

### D8: query_history 输入 schema 设计

**输入**:
```json
{
  "limit": "integer (optional, default 10, max 50)",
  "status": "string (optional, 'success' | 'failure' | 'all', default 'success')",
  "connectionId": "string (optional)",
  "database": "string (optional)"
}
```

**输出**:
```json
{
  "queries": [
    {
      "sqlText": "SELECT count(*) FROM users WHERE created_at > '2026-05-01'",
      "status": "success",
      "executedAt": "2026-05-18T11:30:00Z",
      "durationMs": 45,
      "rowCount": 12345
    }
  ],
  "totalQueries": 27
}
```

**i18n description**:
> 查询本 session 最近 SQL 执行历史。**在写新 SQL 前 SHOULD 调用此 action 看用户最近的 query 模式**,以保持风格一致、避免重复探索。可按状态过滤,看成功/失败例。session 范围内,跨 connection 默认包含全部。

### D9: skill:exploring-data 的边界划分

**新 skill `skills/exploring-data/SKILL.md` 负责**:
- Pre-Action Exploration Protocol 完整描述
- 探索预算明确数值(同表 3 次未命中)
- 失败降级路径(question tool)
- Good/Bad Example(共 4 个,2 个 schema_search 用法 + 2 个 query_history 用法)
- 与 `skill:sql-execution` / `skill:connection-management` / `skill:query-editor-workflow` 的边界一句话

**现有 skill 调整**(cross-reference,不复述):
- `skill:sql-execution`:删除 "table doesn't exist" 探索段落,改为 "see skill:exploring-data for first-time table探索 workflow"
- `skill:connection-management`:删除 "确认表存在" 类描述,引用 `skill:exploring-data`
- `skill:query-editor-workflow`:维持原有"打开 editor"流程,仅在头部加 "if user is exploring an unfamiliar table, route to skill:exploring-data first"

### D10: 数据回流路径 — 后端写入,前端零改动

**选择:`ExecuteSqlAction.execute()` 内部在结果落定后写 `sql_execution_history`**

`ExecuteSqlAction` 已在 application 层(`com.datatalk.application` ...实际上在 `data-talk-adapter`),它有 SQL text、connection、result row count、duration、error 全部上下文。增加一行 `historyService.record(...)` 即可。

**前端**:不动 composer、不动 session 历史 API、不动 SSE 事件流。`{{RECENT_FAILED_QUERIES_DIGEST}}` 在下一轮 LLM 调用前由后端 `AgentPromptBuilder` 在 `OpenCodeBootstrapWriter.write()` 调用时填充——这条路径已经在每次 OpenCode session 启动前跑(`AgentPromptCustomizer` wire 的 supplier)。

**Alternative**:让前端在 `services/channel/channel-client.ts` 监听 `execute_sql` 完成事件后调 history API。
**Rejected**:增加前端复杂度,且事件流可能丢失;后端写入是单点真相。

## Coordination with simplify-sql-execution-gate

本 change 与并行进行中的 `simplify-sql-execution-gate`(以下简称 *simplify*)共同修改 `ExecuteSqlAction` / `AGENTS.md` / `agent-skill-routing` delta。两者目标互补但触碰同表/同函数,**协调按以下 6 条对齐**,以免合并时互相覆盖。本 change **不修改 simplify 的任何文件**——所有协调动作都落在本 change 自身的实现上。

### C1: Pre-Action Exploration Protocol 措辞中性化

**问题**:本 change 在 AGENTS.md 新增 "Pre-Action Exploration Protocol" 段时,若假设 `execute_sql` 只读(SELECT/WITH),会与 simplify 的"放宽 execute_sql 到任意 SQL,仅 DELETE 需确认"语义打架。

**对齐**:Protocol 措辞中 MUST NOT 出现"execute_sql 只能 SELECT/WITH"或类似只读暗示。改为:

> 写 SQL 前(无论 SELECT、INSERT 还是 DDL)若涉及陌生表 / 多表 JOIN,MUST 先 `datatalk_schema_search` 或 `datatalk_read_schema` 自验。DELETE 语句额外需要用户对话式确认(走 simplify 的 requires_confirmation 流程,本 protocol 不重述其细节)。

**Trigger Gate** 加 1 行只写"准备写涉及多表的 SQL → load skill:exploring-data",不写读/写性质区分。

### C2: sql_execution_history.status CHECK 取值不扩

**问题**:simplify 引入 `requires_confirmation` 中间状态,理论上历史表 `status` 列可加 `'pending_confirmation'` / `'cancelled'` 取值,但会增加 schema 复杂度。

**决定**:**不扩** CHECK 取值。`sql_execution_history.status` 保持 `('success', 'failure')` 两值。理由:

- `query_history` action 目的是给 LLM "看用户最近 query 模式" 的参考,未真正执行的 SQL(pending)与已取消的 SQL(cancelled)对 LLM 风格学习无价值
- 减少 schema 演进风险——若后续 simplify 又引入新中间状态,只需调整记录时机,不动表结构
- 与最小可行原则一致

**对应记录时机** 见 C3。

### C3: DELETE 二次确认流程的 history 写入时机

**问题**:simplify 的 DELETE 流程是"第一次调用 → 返回 `requires_confirmation` 不真正执行 → 用户确认 → 第二次调用 `confirmationId` 走二次执行路径"。两次调用都经过 `ExecuteSqlAction`,但只有一次真正落库。

**对齐**:

- **第一次调用**(返回 `requires_confirmation`)**MUST NOT** 写 `sql_execution_history`(SQL 尚未执行)
- **第二次调用**(`confirmationId` 非空,真正执行)**MUST** 写,与普通 `execute_sql` 一致:成功 → `status='success'`,失败 → `status='failure'`
- **超时/未确认**:Store 5 分钟 TTL 到期后,从未真正执行,**MUST NOT** 写

**实现位置**:`historyService.record(...)` 调用 MUST 放在 `ExecuteSqlAction` 内"真正调 JDBC 之后"的分支,而非"action 入口处"。

### C4: ExecuteSqlActionTest 协作约定

**问题**:simplify 已计划重写 `ExecuteSqlActionTest`(移除 L2/L3 `blocked_in_chat` 断言、新增 DELETE `requires_confirmation` 场景);本 change 也要在测试里加 "history 记录" 断言。两份测试改动若同时落地会互覆。

**对齐**:

- 本 change 的 `ExecuteSqlActionTest` 修改 **在 simplify 完成之后** 进行(由 apply 顺序保证,见 C7)
- 本 change 在测试里新增的场景:`记录 success history`、`记录 failure history`、`第一次 requires_confirmation 不记录 history`、`第二次确认成功记录 history`、`第二次确认失败记录 history` 5 个
- 5 个场景的代码 MUST 复用 simplify 引入的 `SqlPendingConfirmationStore` 测试夹具,**不**新建并行夹具

### C5: AGENTS.md 350 行预算合并预算

**问题**:simplify 净减约 5 行(删 L2/L3 `blocked_in_chat` 描述、改 `execute_sql` 描述、删 Hard Constraints 中 read-only 约束),本 change 净增约 46 行。合并后总量 168 - 5 + 46 = 209 行,仍在 350 上限内。

**对齐**:

- `AgentsTemplateContractTest` 的 350 上限断言保持不变
- 本 change 在 Phase 3 修改 AGENTS.md 之前 MUST 先确认 simplify 的 AGENTS.md 修改已经合入(若先合本 change 后合 simplify,体积仍安全;但 conflict 会更复杂)
- 双方都 MUST 在 `## Registered Actions` 表里维持"每行一个 action,以 id 字典序"的风格约定

### C6: `agent-skill-routing` spec delta 文件合并

**问题**:两个 change 都在 `openspec/changes/<name>/specs/agent-skill-routing/spec.md` 写 delta;同名 spec、不同段(simplify 改 `execute_sql` Registered Actions 行,本 change 加 Pre-Action Protocol + exploring-data skill + AgentPromptBuilder 占位符)。OpenSpec archive 阶段会把两个 delta merge 进 `openspec/specs/agent-skill-routing/spec.md`。

**对齐**:

- 两份 delta **不**触及对方的 Requirements(simplify 改 `execute_sql action 行为契约`,本 change 加全新 Requirements 段),archive 时无 textual conflict
- 本 change 的 spec 文本中 **MUST NOT** 出现 "execute_sql 只能 SELECT" 类断言(C1 的 spec 化保证)
- archive 顺序 = 实施顺序 = simplify 先,本 change 后(见 C7)

### C7: 实施(apply)与归档(archive)顺序

**推荐顺序**:`simplify-sql-execution-gate` 先 apply + archive,然后本 `agent-context-priming` apply + archive。

理由:

1. 本 change 修改 `ExecuteSqlAction` 落 history 记录,需要 simplify 的 DELETE 分支已经稳定(避免在变动中的代码上加新行)
2. AGENTS.md 上 simplify 净减、本 change 净增,顺序 = 净减→净增 让中间状态体积始终在上限内
3. spec delta 上 simplify 改既有 Requirement、本 change 加新 Requirement,顺序 = 改→加 让 archive 后的 base spec 结构更清晰

**若顺序颠倒(本 change 先)**,需要做的额外动作(本 change 不预先做):

- `ExecuteSqlAction` 落 history 的代码会在 simplify apply 时需要 rebase 到新的 DELETE 分支(应只是把同一行换到二次执行路径下)
- `ExecuteSqlActionTest` 的 5 个 history 场景需要重写以兼容 simplify 的 `SqlPendingConfirmationStore` 路径
- AGENTS.md `Pre-Action Exploration Protocol` 段措辞不变(C1 已中性化),`Hard Constraints` 中 simplify 要删的 read-only 约束行仍保留到 simplify 实施时

**MUST**:apply 启动前由用户确认顺序,不擅自决定。

## Risks / Trade-offs

- **[BUG-0040 复现]** AGENTS.md / `skill:exploring-data` 任何 `skills/<name>/SKILL.md` 路径串都会触发 LLM 幻觉补全。**Mitigation**:`AgentsTemplateContractTest` 加 `.doesNotContainPattern("skills/[a-z-]+/SKILL\\.md")` 和 `.doesNotContain(".opencode/skills/")` 反断言;新 skill 文件用 contract test 同等保护
- **[BUG-0036 复现]** `SkillResourceSyncer` 同步路径错误。**Mitigation**:新增 `SkillResourceSyncerExploringDataTest` 断言 `~/.data-talk/opencode/.opencode/skills/exploring-data/SKILL.md` 落地
- **[AGENTS.md 体积超 350 行]** 新增段会让骨架增长。**Mitigation**:目标净增 ~46 行,实际预期 214 / 350;`AgentsTemplateContractTest` 体积守门继续工作
- **[LLM 不遵循 Protocol]** 即便写在 AGENTS.md,LLM 也可能跳过 schema_search 直接拼 SQL。**Mitigation**:Trigger Gate 是硬路由(LLM 训练对 "MUST" 关键词遵循度高);加 Good/Bad Example 是经验上对 LLM 强化最有效的方式
- **[schema_search 性能]** 大型库(>10K 表)全表 LIKE 匹配可能慢。**Mitigation**:`timeoutMs=5000`;limit 默认 10 上限 30;每个方言模板里加 `WHERE table_schema = ?` 等过滤;documenta 在 description 里建议先指定 database/schema
- **[sql_execution_history 表膨胀]** 长会话累积大量历史。**Mitigation**:每 session 100 条上限,异步裁剪;**不**做跨 session 全局清理(老 session 数据用户可能想看)
- **[非 ANSI SQL 方言搜索覆盖不全]** Hive / DuckDB 等注释支持差。**Mitigation**:D4 中明确每个 kind 的降级策略;不抛错,只是 score 算法略弱
- **[多目录 skill 同步漂移]** `SkillResourceSyncer` 把 classpath 同步到 `.opencode/skills/`,但开发者本地可能手改。**Mitigation**:`syncSkill` 用 SHA-256 hash marker(已实现),开发者手改的 skill 启动时会被覆盖回 classpath 版本——这是已有行为,不本 change 引入

## Migration Plan

1. **Phase 1: Schema/Storage**(独立,无前置)
   - 创建 Flyway `V3__sql_execution_history.sql`
   - 编写 `SqlExecutionHistoryRepositoryJdbc` + 单测
   - 编写 `SchemaSearchRepositoryJdbc` + 单测(覆盖 H2 / SQLite / PG 三方言冒烟)

2. **Phase 2: Application/Action**(依赖 Phase 1)
   - 添加 `SchemaSearchAction` + `QueryHistoryAction` ActionHandler + i18n 文案
   - 修改 `ExecuteSqlAction.execute()` 落 history 调用
   - 添加 `ActiveConnectionSummaryProvider` 接口 + `Jdbc` 实现
   - 扩展 `AgentPromptBuilder` 注入 2 个新 Provider + 渲染 2 个新占位符
   - 集成测:`SchemaSearchActionTest`、`QueryHistoryActionTest`、`AgentPromptBuilderExtendedTest`

3. **Phase 3: Skill/AGENTS.md**(依赖 Phase 2 Action 已注册)
   - 新建 `server/data-talk-adapter/src/main/resources/skills/exploring-data/SKILL.md`
   - 修改 AGENTS.md:加 Pre-Action Exploration Protocol 段、加 Trigger Gate 行、加 Skill Index 行、加 Registered Actions 表 2 行、加 2 个新占位符字面
   - 审计修改 `sql-execution` / `connection-management` / `query-editor-workflow` 三个 SKILL.md
   - 扩展 `AgentsTemplateContractTest`:新段标题、新占位符、反路径断言
   - 扩展 `SkillResourceSyncerTest`:`exploring-data` 同步

4. **Phase 4: End-to-end smoke**
   - 启动 backend + opencode,前端发"分析销售趋势"(中文表名)
   - 验证 LLM 真的先调 `datatalk_schema_search('销售')`
   - 验证 LLM 在失败后调 question tool 而非循环 read_schema

**Rollback strategy**:
- Phase 1 不可回滚(Flyway 不支持 down)——但表只是新增,不影响现有功能;若严重问题,在 Phase 2 之前可以让 Action 不写历史即可
- Phase 2 单 commit 回滚不影响 Phase 1 表
- Phase 3 可全文回滚 AGENTS.md(`git revert`),`AgentsTemplateContractTest` 会自动恢复旧断言

## Open Questions

- `query_history` 是否应该跨 session 查询(用户可能想看上一次 session 写的 SQL)?**初版**:单 session 范围,Day-2 决定
- 探索预算"3 次"是否需要可配置?**初版**:硬编码 in skill,Day-2 看 LLM 行为数据决定
- `schema_search` 是否要支持拼音转换?**初版**:不做,依赖 LLM 自己分词;Day-2 看用户场景
- 中文/英文 keyword 是否要自动归一化(去空格、统一大小写)?**初版**:do `lower()` + `trim()`,其他保留
- 是否要把"探索结果摘要"也持久化(`schema_search` 调用历史)以供后续 round?**初版**:不做——MCP tool 调用本来就有 idempotent 语义,LLM 重复调代价不高;持久化反而增加状态复杂度
