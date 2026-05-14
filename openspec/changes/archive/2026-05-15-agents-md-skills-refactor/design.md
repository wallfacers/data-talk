## Context

`AGENTS.md` 当前是 OpenCode agent 唯一的系统级指令源：

- AGENTS.md 加载链：
  - `AgentPromptCustomizer`（`data-talk-adapter` 层，Spring `@Configuration`）只做 wiring——给 `OpenCodeBootstrapWriter.setInstructionsSupplier()` 注入一个 lambda：`getResourceAsStream("agents/AGENTS.md")` → `AgentPromptBuilder.render(raw)`。
  - `AgentPromptBuilder`（`data-talk-application` 层，`@Component`）持有真正的渲染逻辑，替换 **两个** 占位符：`{{STAGE_TAB_DIGEST}}`（最近活跃 stage tab 摘要）和 `{{ACTIVE_SESSION_DIR}}`（活动 session 子目录 `./sessions/<sessionId>/`，无活动 session 时填 `<no active session>`）。
  - 渲染后的文本写入 OpenCode bootstrap，作为 system prompt 注入每次会话。
  - 当前 AGENTS.md 实测 979 行 / 19 个 `^## ` 二级标题 / 76 KB；`{{ACTIVE_SESSION_DIR}}` 共 2 处出现（line 917 / 935，均在 `## Output Files & Artifacts` 章节内）；`{{STAGE_TAB_DIGEST}}` 共 1 处（line 979，文件末尾）。
- OpenCode 工作目录 = `~/.data-talk/opencode/`（由 `OpenCodeBinaryResolver.OPENCODE_DIR` 常量决定）；`SkillResourceSyncer` 在 `opencodeCwd.resolve(".opencode")` 下写入 skill，即最终落点 `~/.data-talk/opencode/.opencode/skills/<name>/`。本文档其余位置出现的 "OpenCode skills 目录" 一律指代此真实路径，**不是** 用户家目录下的 `~/.opencode/`。
- 章节范围横跨：核心规则（多条护栏）、意图路由、上下文模型、~50 个 `datatalk_*` 工具的 required input、Exact UI Contract、ER 协议、并发协议、Tab 复用 / 持久化 / 搜索、查询编辑器规则、图表、仪表盘、推荐工作流、9 种数据库方言、产物输出、Data Ingestion（已 skill 化）。

并行存在的 skill 机制：

- `classpath:/skills/<name>/SKILL.md` + 关联资源，由 `SkillResourceSyncer` 用 SHA-256 哈希同步到 `~/.data-talk/opencode/.opencode/skills/<name>/`。
- `OpenCodeGatewayBeans` 启动时调 `skillSyncer.syncSkill(name, opencodeCwd)` 注册（目前仅注册了 `bezel` 和 `data-ingestion`）。
- OpenCode 自身根据 SKILL.md frontmatter 中的 `description` 在用户输入匹配时加载 skill 内容。
- 已有 `data-ingestion` 例子验证：description 中放中英双语触发词 + "When to use / When NOT to use" 章节是有效模式。

关键约束：

- 不能改 OpenCode HTTP 协议、`SkillResourceSyncer`、`AgentPromptCustomizer`、`AgentPromptBuilder`、`OpenCodeBootstrapWriter` 的对外行为；这些在跨变更被多处依赖。
- 不能拆破 4 层依赖方向；本变更只动 `adapter` 层（资源 + Spring config + 测试）。
- 必须满足 CLAUDE.md "工作规则"：BUG Gate（E2E 偏差入档）、数据源类型兼容性 Gate（本变更 N/A，已在 proposal 说明）、前端设计 Gate（本变更 N/A）。

## Goals / Non-Goals

**Goals**：

- 把 AGENTS.md 拆成 "≤350 行骨架 + 11 个按需 skill" 的双层结构。
- 强制性触发不依赖 OpenCode 内部 description 匹配的不透明算法——在 AGENTS.md 中显式写出 Trigger Gate 表，使 agent 看到金本位条件命中时主动加载对应 skill。
- 保留所有原有规则的语义（每条规则在新结构里恰好出现一次完整定义）。
- 不破坏 `AgentPromptCustomizer` / `AgentPromptBuilder` / `OpenCodeBootstrapWriter` / `SkillResourceSyncer` 任何对外契约；尤其 `{{STAGE_TAB_DIGEST}}` 与 `{{ACTIVE_SESSION_DIR}}` 两个占位符必须在精简版 AGENTS.md 中继续出现。
- 通过结构性测试 + 5 场景 E2E 双轨验证。

**Non-Goals**：

- 不修改 OpenCode 协议、不改 SSE / JSON-RPC 行为。
- 不引入"后端关键词拦截动态拼 prompt"的双保险（brainstorming 阶段已弃）。
- 不为每个数据库方言拆独立 skill；保留单一 `database-dialects` skill 内部分 H2 章节。
- 不重构 `bezel` / `data-ingestion` skill。
- 不引入新的 i18n 框架；description 双语写在同一字段内即可。
- 不重写 docs/ 下任何历史文档，仅在 README/architecture 必要时加一段链接。

## Decisions

### 1. Skill 切分 = 11 + 2，中粒度

**选**：按 "工具调用面 / 业务对象" 切，11 个新 skill：
`sql-execution` / `query-editor-workflow` / `ui-contract` / `tab-management` / `er-tabs` / `concurrency-contract` / `charts-and-dashboards` / `artifacts-output` / `connection-management` / `sql-error-diagnostics` / `database-dialects`。已有 `bezel` + `data-ingestion` 保持不变。

**为什么不是粗粒度（5-6 个）**：粗粒度 skill 文件本身仍是几千字，触发后吞掉的 token 接近原 AGENTS.md，丧失"按需"价值。

**为什么不是细粒度（20+ 个）**：细粒度会导致 "做一个查表任务必须同时加载 sql-execution + ui-contract + tab-management + query-editor-workflow" 这种链式依赖，反而比单 skill 更胖；同时大量小 skill 的 description 互相竞争 OpenCode 的匹配选择，命中率反降。

**为什么不是按方言切 8 个 dialect-* skill**：方言注解大多是 1-2 段提示（如 "MySQL ENGINE 默认 InnoDB" / "Hive 不支持事务"），单文件容易维护；用户提问中只有一段 SQL 含方言关键词时拆 8 个 skill 触发概率反而碎片化。

**Skill 边界一表归属（避免重叠）**：

| 主题 | 完整定义归属 | 其他 skill 引用方式 |
|---|---|---|
| **大输出落盘**：tool 返回 `saved file path`（执行结果过大被存到文件，需读取继续推理） | **artifacts-output**（唯一） | `[[artifacts-output]]` 短引用 |
| **schema 截断**：`datatalk_read_schema` 返回 `truncated=true`（结果集过大，处理方式是缩小 `pattern`/`limit` 或追加业务关键词） | **sql-execution**（唯一）—— 这是 schema 读取策略的一部分，不属于"落盘工件"语义 | — |
| SQL 执行错误诊断（句法、对象不存在、多候选） | **sql-error-diagnostics**（唯一） | `[[sql-error-diagnostics]]` 短引用 |
| Schema 读取规则、cross-DB 探查、`pattern/limit` | **sql-execution**（唯一） | — |
| `confirm=true` / `confirmationToken` 两阶段协议 | **connection-management**（唯一） | — |
| `ui_patch` version 冲突 / `expectedVersion` 处理 | **concurrency-contract**（唯一） | — |
| `apply_text_edits` 后 `ui_read` 再读验证 | **ui-contract**（唯一） | — |

任何 SKILL.md 内出现 `[[<skill>]]` 的位置代表"详见对应 skill 文件"，不复制原文。

### 2. AGENTS.md 骨架结构 = 6 个二级标题，模板渲染保留

```
# DataTalk Agent Instructions
## Identity & Hard Constraints   ← 身份 + 5-7 条不可越越的金本位规则
## Intent Routing Gate           ← 决定路径（query editor UI vs server data）
## Context Model                 ← session data context vs query editor context
## Registered Actions            ← 工具名 + 1 行用途 + skill: <name>
## Trigger Gate                  ← 强制触发表（核心新增）
## Skill Index                   ← skill 名 + description 摘要 + 路径
```

精简版 AGENTS.md 的字符串 MUST 同时保留 `{{STAGE_TAB_DIGEST}}` 与 `{{ACTIVE_SESSION_DIR}}`：
- `{{STAGE_TAB_DIGEST}}` 放在文件末尾（沿用现状，`AgentPromptBuilder` 会替换为 "## Open Tabs Snapshot" 内容块）。
- `{{ACTIVE_SESSION_DIR}}` 放在 `## Identity & Hard Constraints` 章节内的"工件写入规则"句子中（详 Decision 8）。

### 3. Trigger Gate 用 Markdown 表，引用形式 `skill:<name>`

形如：

```markdown
## Trigger Gate

| When you ... | You MUST load |
|---|---|
| call `datatalk_execute_sql` / `datatalk_read_schema` | skill:sql-execution |
| receive SQL **execution failure** (syntax / unknown column / no such table / ambiguous target) | skill:sql-error-diagnostics |
| tool response includes a `saved file path` for large output | skill:artifacts-output |
| call any `datatalk_ui_*` tool | skill:ui-contract |
| user asks to browse table / preview / open SQL editor | skill:query-editor-workflow |
| decide whether to reuse existing tab or open new | skill:tab-management |
| interact with `er_inspector` / `er_designer` | skill:er-tabs |
| receive 409 / version conflict from `ui_patch` | skill:concurrency-contract |
| user asks for chart / dashboard / 报表 / 趋势 | skill:charts-and-dashboards |
| create / update / delete saved connection | skill:connection-management |
| write dialect-specific SQL (MySQL / PG / Oracle / SQLServer / SQLite / DuckDB / Hive / GaussDB) | skill:database-dialects |
```

**为什么用 Markdown 表而非 JSON / YAML**：OpenCode 解析的是纯 prompt 文本，表对人 / agent 都最易读；JSON 块在 prompt 中会被部分 agent 误解为可执行内容。

**为什么用 `skill:<name>` 而非 `[[name]]` 链接形式**：`skill:<name>` 出现在 AGENTS.md 的常规英文里 = "must load skill X"，对 LLM 是无歧义的指令；`[[]]` 在 OpenCode 没有特殊语义，反而像 wiki 链接被忽略。

**为什么 Trigger Gate 表不含 `bezel` 与 `data-ingestion` 行**：本变更不重构这两个 skill，它们的触发条件已在自身 SKILL.md description 中通过中英双语关键词（`抓取/采集/落库/ingest/scrape/sync from API` 等）定义清晰，命中率经实战验证可接受。Trigger Gate 的语义是"骨架 AGENTS.md 中显式硬规则强制加载"，仅用于本变更新增的 11 个 skill；`bezel` / `data-ingestion` 维持原有 description-based 自动匹配。`Skill Index` 章节会列出全部 13 个 skill 名以保证可见性（spec Requirement "Trigger Gate 表与 skill 双向闭合"的"反向被引用"条件由 `Skill Index` 满足，无需写入 Trigger Gate）。

### 4. SKILL.md frontmatter = name + description（双语），其他字段不强加

每个新 skill 的 SKILL.md 头：

```yaml
---
name: sql-execution
description: Use when calling `datatalk_execute_sql` or `datatalk_read_schema`. Triggers on phrases like 执行 SQL / 读 schema / 查询 / run query / read schema / select rows. Covers read-only constraint, schema reading rules, truncated handling, cross-database probing for "table doesn't exist" errors.
---
```

**约束**：description 长度 80-600，中英文触发词各 ≥1（结构性测试强制）。

**为什么不引入 `tags` / `version`**：OpenCode 当前没有用到这些字段，YAGNI；现有 bezel / data-ingestion 也只用 name + description。

### 5. `OpenCodeGatewayBeans` 注册 = 显式 11 行 syncSkill 调用

不引入"扫描 classpath 自动注册"：自动机制掩盖资源缺失，启动期出错比静默跳过更可控（与现有 `bezel` / `data-ingestion` 一致）。

```java
skillSyncer.syncSkill("bezel", opencodeCwd);
skillSyncer.syncSkill("data-ingestion", opencodeCwd);
skillSyncer.syncSkill("sql-execution", opencodeCwd);
skillSyncer.syncSkill("query-editor-workflow", opencodeCwd);
skillSyncer.syncSkill("ui-contract", opencodeCwd);
skillSyncer.syncSkill("tab-management", opencodeCwd);
skillSyncer.syncSkill("er-tabs", opencodeCwd);
skillSyncer.syncSkill("concurrency-contract", opencodeCwd);
skillSyncer.syncSkill("charts-and-dashboards", opencodeCwd);
skillSyncer.syncSkill("artifacts-output", opencodeCwd);
skillSyncer.syncSkill("connection-management", opencodeCwd);
skillSyncer.syncSkill("sql-error-diagnostics", opencodeCwd);
skillSyncer.syncSkill("database-dialects", opencodeCwd);
```

### 6. 验证 = JUnit 结构性 + Playwright 5 场景 E2E

- **JUnit**：
  - `SkillRoutingContractTest`（adapter test）：
    1. AGENTS.md 非空行 ≤ 350，**同时含 `{{STAGE_TAB_DIGEST}}` 与 `{{ACTIVE_SESSION_DIR}}`**，含 6 个金本位二级标题。
    2. AGENTS.md 中所有 `skill:<name>` 引用都能在 `classpath:/skills/<name>/SKILL.md` 找到。
    3. 每个新 SKILL.md frontmatter 合法（YAML 解析 + name 等于父目录 + description 长度 + 中英双语正则）。
    4. `OpenCodeGatewayBeans` 中 `syncSkill` 参数集合 = `classpath:/skills/*/SKILL.md` 目录名集合。
    5. 关键词唯一归属（基于 `keyword-ownership.md`，含 `{{ACTIVE_SESSION_DIR}}` 必须仍属于 AGENTS.md 骨架而非任何 skill）。
    6. 每个新 SKILL.md 中**不含**任何 `{{XXX}}` 占位符字符串（防止误迁导致 agent 看到字面文本）。
  - `AgentPromptBuilderPlaceholderTest`（adapter test）：
    1. 加载精简版 AGENTS.md（`getResourceAsStream("agents/AGENTS.md")`）→ `AgentPromptBuilder.render()` 后输出不含子串 `{{STAGE_TAB_DIGEST}}` 也不含 `{{ACTIVE_SESSION_DIR}}`。
    2. 在 stub `StageTabRepository`（空 tabs）+ stub `ActiveSessionDirProvider`（返回 `Optional.of("S-1")`）下，渲染结果包含 `./sessions/S-1/` 字符串。
    3. 在 stub `ActiveSessionDirProvider.currentSessionId() = Optional.empty()` 下，渲染结果包含 `<no active session>` 字符串。
  - 既有 `AgentsTemplateContractTest`（adapter test，旧版 8 个断言）：必须**重写**——见 tasks §6。旧版 6 个 `file-artifact-section` 断言 + 1 个 `## Data Ingestion (skill: data-ingestion)` 断言所对应的章节即将被移除/重写；新版断言改为验证：(a) `## Output Files & Artifacts` 不再出现在 AGENTS.md（已迁出）；(b) `artifacts-output/SKILL.md` 含 `datatalk_archive_artifact` / `**Rules**` 等关键字；(c) `## Data Ingestion` 已从 AGENTS.md 移除，但 AGENTS.md 的 `## Skill Index` 章节含 `skill:data-ingestion` 引用。
- **Playwright E2E**：在新 spec `client/tests/e2e/agents-skills-regression.spec.ts` 中跑（断言全部采用 **正向 UI / 工件断言**，因为 Playwright 不能直接证明"agent 没有调某个工具"）：
  1. **查表场景**："查 users 表 10 行" → 正向断言：`query_editor` 类型的 stage tab 被打开（DOM 选择器命中标题 / connectionId）；编辑器内容含 `SELECT ... FROM users`；结果面板渲染了 ≤10 行的网格。负向证据（"不在 chat 里 inline 展示 rows"）通过断言 chat 区域的助手气泡 **不包含** Markdown 表格语法（`| --- |`）来辅助验证。
  2. **ER 设计场景**："用 ER 设计器画订单和用户的关系" → 正向断言：`er_designer` 类型 tab 被打开；调用 `datatalk_ui_patch` 后 canvas 包含 ≥2 个表节点 + 1 条外键边（通过 stage tab JSON state 读取）。
  3. **图表场景**："做一张 7 日订单趋势图" → 正向断言：chart artifact 出现在 chat 流中（`data-artifact-kind="chart"` 节点）；artifact 的底层 SQL 含 `GROUP BY` 时间列。
  4. **数据导入场景**（基线，验证 data-ingestion 自动匹配未被本变更打断）："把 https://example.com/orders.csv 的数据落到我的 H2 数据库" → 正向断言：出现 ingestion confirm 卡片 + 创建表 DDL 预览。
  5. **报错诊断场景**：复用现有 `query-editor-error-handling` E2E 中"`SELECT * FROM does_not_exist`"的 fixture，新增断言：紧随错误 chat 气泡之后，assistant 发起的下一次工具调用 `name` 是 `datatalk_read_schema`（通过 `page.evaluate` 抓 SSE 事件流的 `tool.invoke.name` 字段实现，作为辅助证据；如 SSE 抓取不便，退化为"chat 中出现 read_schema 结果片段"的可见性断言）。
- E2E 偏差按 BUG Gate 入档 `docs/bugs/`。

### 7. 不修改既有 specs，只引入新 capability

`openspec/specs/` 现有 16 个 capability 全是业务能力（chat-sql-codeblock、ingestion-lifecycle 等），均不与 AGENTS.md / skill 路由相关。新增 `agent-skill-routing` capability 覆盖本设计；archive 时整体并入 `openspec/specs/agent-skill-routing/spec.md`。

### 8. `{{ACTIVE_SESSION_DIR}}` 占位符与 SKILL.md 静态性的处理

**问题**：原 AGENTS.md `## Output Files & Artifacts` 章节内包含 `{{ACTIVE_SESSION_DIR}}` 占位符 2 处（line 917 / 935），由 `AgentPromptBuilder.render()` 在 OpenCode bootstrap 时动态替换为 `./sessions/<sessionId>/`。SKILL.md 是 `SkillResourceSyncer` 同步的静态资源，**不经过 `AgentPromptBuilder.render()`**——如果把整个 `## Output Files & Artifacts` 章节连同占位符迁到 `artifacts-output/SKILL.md`，agent 将看到未替换的字面 `{{ACTIVE_SESSION_DIR}}` 字符串，破坏既有契约。

**决定**：采用 **方案 A**——`{{ACTIVE_SESSION_DIR}}` 占位符及其唯一一行硬规则**留在 AGENTS.md 骨架内**，详细工件协议迁到 skill：

- AGENTS.md `## Identity & Hard Constraints` 章节末尾保留一条硬规则。措辞必须使得在 `{{ACTIVE_SESSION_DIR}}` 渲染为实际路径或 sentinel 两种情况下均自洽——把"占位符值"作为独立陈述句，再分别处理两种值：

  ```markdown
  - Artifact write location is constrained by the active session. The current active session subdirectory is: `{{ACTIVE_SESSION_DIR}}`. If that value equals `<no active session>`, ask the user to bind a session before writing any artifact. Otherwise, write artifacts only into that subdirectory; never into the parent cwd.
  ```

  避免使用形如 "in your active session subdirectory ({{ACTIVE_SESSION_DIR}}), when no active session ..." 这类内嵌占位符的从句——一旦 `{{ACTIVE_SESSION_DIR}}` 被替换为实际路径，后半句"when no active session"会读起来与前半句矛盾。

- `artifacts-output/SKILL.md` 负责详细规则（产物分类、`datatalk_archive_artifact` 使用、Default/Promote/Rules 三档协议、`supersedes` 链接、truncated / saved file path 处理），但**不再嵌入 `{{ACTIVE_SESSION_DIR}}` 占位符**；遇到 "写到 session 目录" 时用句子 "as required by the active-session-dir rule in AGENTS.md core" 引用，**不复制占位符**。

**为什么不用方案 B（在 SKILL.md 中改为静态描述 `./sessions/<sessionId>/`）**：`<sessionId>` 本身就是运行时变量，静态描述等同于占位符语义而无渲染保障；agent 看到 "`./sessions/<sessionId>/`" 时容易把 `<sessionId>` 字面当成路径段。

**对 spec 的影响**：`AgentPromptBuilder 占位符渲染保持` Requirement（详 spec.md）要求精简版 AGENTS.md 中两个占位符均仍可被 `AgentPromptBuilder.render()` 命中并替换，渲染输出不再含字面 `{{` 序列。

### 9. 19 个原章节 → 11 + 2 skill 的归属映射（Recommended Workflows 也在内）

原 AGENTS.md 19 个二级标题（`^## `）的去向：

| 原章节 | 目标 | 备注 |
|---|---|---|
| Core Rules | AGENTS.md 骨架（精简到 5-7 条金本位） | 失败诊断 / UI 验证 / 切换上下文等具体护栏被搬到各专项 skill |
| Intent Routing Gate | AGENTS.md 骨架（保留全文） | 是路由根节点，不能拆 |
| Context Model | AGENTS.md 骨架（保留全文） | 同上 |
| Registered Actions（含其下所有 ### 子章节：Session Data Context / Connection Management / Schema, Query, and Artifacts / Query Diagnostics / Mutation Actions / Diagnostics Workflow Rules / Diagnostics Capability Matrix / EXPLAIN Warnings / INDEX_HINTS Impact Tier / UI Actions） | 拆到对应 skill（sql-execution / connection-management / sql-error-diagnostics / artifacts-output / ui-contract），AGENTS.md 骨架只留工具名目录 + `see skill:<name>` | 这部分占原文最大体量 |
| Exact UI Contract | `ui-contract/SKILL.md` | 全部 |
| ER Tabs (Inspector & Designer) + 其下 ### 子章节 | `er-tabs/SKILL.md` | 全部 |
| Concurrency Contract + 其下 ### 子章节 | `concurrency-contract/SKILL.md` | 全部 |
| Library vs Workset | `tab-management/SKILL.md` | — |
| Tab Reuse vs New Tab + 其下 ### 子章节 | `tab-management/SKILL.md` | — |
| UI Navigation Rules | `tab-management/SKILL.md` | — |
| Query Editor Rules | `query-editor-workflow/SKILL.md` | — |
| Charts | `charts-and-dashboards/SKILL.md` | — |
| Dashboards | `charts-and-dashboards/SKILL.md` | — |
| **Recommended Workflows**（57 行，横跨场景） | **按场景拆分到 `query-editor-workflow` / `charts-and-dashboards` / `er-tabs` / `connection-management` 各自 SKILL.md 的 "## Recommended workflow" 章节** | 不单独建 skill；拆分映射在 tasks 1.1 `migration-map.md` 中逐行确认 |
| Tab Persistence and Search | `tab-management/SKILL.md` | — |
| Database Dialect Notes | `database-dialects/SKILL.md`（按 MySQL/PG/Oracle/SQLServer/SQLite/DuckDB/Hive 分子章节） | — |
| **GaussDB**（独立二级章节，与 Database Dialect Notes 并列） | `database-dialects/SKILL.md` 内追加 GaussDB 子章节 | 实施时**必须**显式合并，不能因为它不在 "Database Dialect Notes" 之下而被遗漏 |
| Output Files & Artifacts | 详细规则 → `artifacts-output/SKILL.md`；`{{ACTIVE_SESSION_DIR}}` 占位符 + 1 行硬规则 → 留 AGENTS.md 骨架（详 Decision 8） | — |
| Data Ingestion (skill: data-ingestion) | **移除**章节正文，仅在 `Skill Index` 留一行 "skill:data-ingestion — see `~/.data-talk/opencode/.opencode/skills/data-ingestion/SKILL.md`" | data-ingestion skill 自身保持不动 |

## Risks / Trade-offs

- **Risk A：OpenCode 实际不读 "must load skill:X" 指令** → Mitigation：Trigger Gate 表 + AGENTS.md `Identity & Hard Constraints` 末尾加一句 "When any Trigger Gate row's condition matches, you MUST load the listed skill before proceeding." 让指令成为硬规则；E2E 5 场景验证；如仍命中失败，退一步改 SKILL.md description（不改 AGENTS.md 结构）。
- **Risk B：description 匹配过宽，每次会话都触发某 skill** → Mitigation：description 中明示 "When NOT to use"（参考 data-ingestion 的实践）；JUnit 不能验证此点，依赖 E2E 5 场景观察。
- **Risk C：skill 内规则与 AGENTS.md 骨架内容重复 / 矛盾** → Mitigation：spec 中 "skill 切分边界唯一性" 要求每条规则在 11 skill 内仅出现一次完整定义；引用其他 skill 必须用 `[[<skill>]]` 短引用。结构性测试用 N-gram / 句子级哈希比对。
- **Risk D：精简后丢失某条原 AGENTS.md 中的护栏** → Mitigation：迁移阶段做一次"原 AGENTS.md → 11 skill" 的规则映射表（写在 tasks.md 实施前置任务中），并用 grep diff 在 commit 前对比关键金本位关键词列表（`READ-ONLY` / `confirm=true` / `confirmationToken` / `apply_text_edits` 等）必须出现至少一次。
- **Risk E：`~/.data-talk/opencode/.opencode/skills/` 同步失败但启动不报错** → Mitigation：`SkillResourceSyncer` 现有失败路径是 `log.warn` 不抛异常。为本变更不改其行为，但在新增的 `SkillRoutingContractTest` 中加一个 "classpath 资源完整" 子测验证打包正确，避免上线后才发现 jar 中缺资源。

## Migration Plan

1. **Phase 1（实施期）**：在 worktree / branch 中完成 11 个 SKILL.md + 精简 AGENTS.md + OpenCodeGatewayBeans 改动 + JUnit + Playwright spec。
2. **Phase 2（验证期）**：
   - `cd server && mvn verify` 全绿（包含 SkillRoutingContractTest）。
   - `cd client && npm run test:e2e -- agents-skills-regression` 5 场景全绿。
   - 手动启动一次 OpenCode 全链路，观察 `~/.data-talk/opencode/.opencode/skills/` 目录是否含 13 个子目录。
3. **Phase 3（上线）**：合并到 develop。已运行实例需重启 server 才生效（与现有 skill 同步行为一致，无需额外通知）。
4. **回滚策略**：保留原 AGENTS.md 备份在变更分支历史 commit；如 E2E 大面积失败，单提交回退即可——不需要清理 `~/.data-talk/opencode/.opencode/skills/`，新 skill 多余但无副作用。
5. **Flyway 迁移**：N/A。本变更不动数据库。
6. **Sealed interface / exhaustive switch 影响**：N/A。本变更不动 domain 层。

## Open Questions

- 是否在 `Skill Index` 章节列出 skill 文件的相对路径（`skills/sql-execution/SKILL.md`）？目前倾向不写——OpenCode 自身按 name 加载，路径反而是噪音。如 archive 阶段评审认为需要，可补一列。
- description 长度上限 600 是否够用？现有 data-ingestion 是 350 字符左右，sql-execution 类工具面更广，可能需要 500-600；如评审要更短，把"详细 When to use"完全推到 SKILL.md 正文。
- ~~E2E 5 场景中"SQL 报错诊断"目前缺乏既有 fixture~~ → 已决：复用 `client/tests/e2e/query-editor-error-handling.spec.ts` 现有 fixture（如不存在，复用任一会触发 `SELECT * FROM does_not_exist` 类语义错误的既有 spec），仅在该 spec 中追加一次断言"下一次 tool invoke 是 `datatalk_read_schema`"，不新建前端代码与 fixture。
