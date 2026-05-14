## Why

`server/data-talk-adapter/src/main/resources/agents/AGENTS.md` 已经膨胀到 979 行 / 76 KB / 19 个二级标题（含核心规则、UI 契约、ER 图、并发、8 种数据库方言、图表、仪表盘、产物输出、推荐工作流、数据导入等），每次 OpenCode 会话都整段塞进 system prompt。结果：

- 上下文成本固定且巨大，多数 token 与单次任务无关；
- 长指令稀释了金本位约束（"只读 SQL"、"不猜 connectionId"、"native JSON 类型"），实际命中率下降；
- 单一文件难以维护，新增方言或场景常被遗漏。

项目 OpenCode 已经支持 `classpath:/skills/<name>/SKILL.md` 按需触发机制（`bezel`、`data-ingestion` 已经验证可用），具备把 AGENTS.md 拆成"骨架 + 按需 skill"的全部基础设施。

## What Changes

- 新增 11 个 OpenCode skill 包，覆盖 AGENTS.md 中"工具用法 / UI 契约 / 并发 / 方言 / 图表 / 仪表盘 / 产物 / 连接 / SQL 报错诊断"等场景。
- 重写 AGENTS.md 为"骨架 + Trigger Gate"格式：身份与硬约束、Intent Routing、Context Model、工具目录（每条工具旁注 `see skill:<name>`）、强制触发场景表、skill 索引。目标 ≤ 350 行。
- 扩展 `OpenCodeGatewayBeans`，在启动时注册全部 11 个新 skill 到 `SkillResourceSyncer`。
- 新增 `SkillRoutingContractTest`，结构性约束 AGENTS.md 行数上限、Trigger Gate 每条目能匹配到对应 SKILL.md、每个 SKILL.md frontmatter 合法。
- Playwright E2E 验证 3-5 个典型流（简单查表、ER 设计、仪表盘、报错重试、数据导入），回归落入 `docs/bugs/`。
- 保留 `bezel`、`data-ingestion` skill 不动；保留 `{{STAGE_TAB_DIGEST}}` 与 `{{ACTIVE_SESSION_DIR}}` 两个模板占位符（由 `AgentPromptBuilder.render()` 在 OpenCode bootstrap 时渲染）；不修改 `AgentPromptCustomizer`（wiring 配置）与 `AgentPromptBuilder`（渲染逻辑）的对外行为。

**BREAKING**：精简版 AGENTS.md 不再包含方言细节、ER 协议、并发协议、图表/仪表盘协议、产物协议的全文。任何依赖"AGENTS.md 中存在某条方言规则"的下游工具或测试断言必须改为引用对应 skill 文件。

## Capabilities

### New Capabilities

- `agent-skill-routing`：定义 AGENTS.md 骨架结构、Trigger Gate 表的强制语义、SKILL.md frontmatter 契约、`SkillResourceSyncer` 注册流程；规定 11 个具名 skill 各自的职责边界。

### Modified Capabilities

（无）当前 `openspec/specs/` 下既有能力均为业务功能能力（chat-sql-codeblock、ingestion-lifecycle 等），没有覆盖 "OpenCode agent instructions" 的能力，因此本变更整体是新增能力，不涉及修改既有 spec 的需求。

## Impact

**新增资源**：
- `server/data-talk-adapter/src/main/resources/skills/sql-execution/SKILL.md`
- `server/data-talk-adapter/src/main/resources/skills/query-editor-workflow/SKILL.md`
- `server/data-talk-adapter/src/main/resources/skills/ui-contract/SKILL.md`
- `server/data-talk-adapter/src/main/resources/skills/tab-management/SKILL.md`
- `server/data-talk-adapter/src/main/resources/skills/er-tabs/SKILL.md`
- `server/data-talk-adapter/src/main/resources/skills/concurrency-contract/SKILL.md`
- `server/data-talk-adapter/src/main/resources/skills/charts-and-dashboards/SKILL.md`
- `server/data-talk-adapter/src/main/resources/skills/artifacts-output/SKILL.md`
- `server/data-talk-adapter/src/main/resources/skills/connection-management/SKILL.md`
- `server/data-talk-adapter/src/main/resources/skills/sql-error-diagnostics/SKILL.md`
- `server/data-talk-adapter/src/main/resources/skills/database-dialects/SKILL.md`

**修改文件**：
- `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`：重写为骨架版（≤350 行）。
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java`：补 11 行 `skillSyncer.syncSkill(...)`。

**新增 / 修改测试**：
- 新增 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/SkillRoutingContractTest.java`：行数上限、Trigger Gate 完备性、frontmatter 合法性、注册一致性、关键词唯一归属。
- 新增 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptBuilderPlaceholderTest.java`：两个占位符（`{{STAGE_TAB_DIGEST}}` + `{{ACTIVE_SESSION_DIR}}`）的渲染契约保持不变。
- 重写既有 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentsTemplateContractTest.java`：旧版 8 个断言中 7 个绑定 `<!-- file-artifact-section -->` markers / `## Data Ingestion (skill: data-ingestion)` 章节，这些章节将随重构被移除/重写，相关断言需迁移到 skill 文件或改写为 `Skill Index` 引用断言。
- 新增 `client/tests/e2e/agents-skills-regression.spec.ts`（或扩展既有 E2E）：5 典型场景回归。

**协议 / 依赖 / 系统影响**：
- OpenCode HTTP 协议未变更；`SkillResourceSyncer` 行为不变（仅多注册几次 skill）；`AgentPromptCustomizer`（adapter 层 wiring）、`AgentPromptBuilder`（application 层渲染器）、`OpenCodeBootstrapWriter` 均不需要改。
- OpenCode 工作目录 `~/.data-talk/opencode/.opencode/skills/`（由 `OpenCodeBinaryResolver.OPENCODE_DIR=".data-talk/opencode"` + `SkillResourceSyncer` 内部 `.opencode` 子路径拼出）会多 11 个子目录，每次启动通过 SHA-256 哈希 marker 幂等同步，磁盘开销 < 1 MB。
- 已运行的 OpenCode 实例需重新启动才能拉到新版 AGENTS.md 与 skill 集合（与现有 bezel/data-ingestion 行为一致）。

**风险与已知 BUG**：
- 经核 `docs/bugs/` 未见与 AGENTS.md 拆分 / OpenCode skill 加载相关的 open / wontfix BUG。
- 主要风险来自 OpenCode 实际对 SKILL.md description 的匹配算法不透明：以"强制 Trigger Gate 表 + AGENTS.md 中的硬规则"双轨保证命中。E2E 阶段如发现典型场景未触发对应 skill，按 BUG Gate 入档 `docs/bugs/` 并回到 design 修补 description / Trigger Gate 表述。

**Design Inputs**：
- 本变更不涉及 `client/` UI、视觉、布局或组件改动，因此不引用 `client/DESIGN.md`（N/A，原因：纯后端资源 / 资源同步配置 / 提示词治理，无前端表现层影响）。

**Data Source Type Compatibility**：
- 本变更不新增或修改任何数据库 / 数据源类型。`database-dialects` skill 仅是把已存在的方言提示词从 AGENTS.md 搬到独立文件，未引入新方言、未修改既有方言判定逻辑。`docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` 不需要更新（N/A）。
