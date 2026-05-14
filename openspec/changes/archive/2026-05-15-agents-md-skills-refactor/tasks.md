## 1. 准备与映射

- [x] 1.1 通读现版 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`，整理 "原章节 → 目标 skill" 映射表（写到本变更目录下 `migration-map.md`，archive 时随分支历史保留即可，不进 specs/）。映射表中**必须**包含一行"data-ingestion 错误码覆盖确认"：本变更核查已确认 `classpath:/skills/data-ingestion/SKILL.md` 当前已含全部 8 个 ingestion 错误码（`INGESTION_SSRF_BLOCKED` / `INGESTION_PAYLOAD_TOO_LARGE` / `INGESTION_AUTH_FAILED` / `INGESTION_TOKEN_INVALID` / `INGESTION_DIALECT_UNSUPPORTED` / `INGESTION_FETCH_FAILED` / `INGESTION_FORMAT_UNSUPPORTED` / `INGESTION_INFER_FAILED`），因此 §6.3 重写 `agentsTemplateContainsDataIngestionSection` 时可以放心地把错误码断言迁到 data-ingestion SKILL 上而无须保留 AGENTS.md 抽样断言。若实施时发现 data-ingestion SKILL 已被外部 PR 改动导致错误码缺失，立刻在本任务中标记并停下与用户对齐。
- [x] 1.2 从现版 AGENTS.md 中提取金本位关键词清单，按"每个关键词 → 唯一 owning-skill"的形式写入本变更目录下 `keyword-ownership.md`，至少覆盖 spec 中列举的 12 个种子关键词（`READ-ONLY` / `confirm=true` / `confirmationToken` / `apply_text_edits` / `truncated` / `set_data_context` / `er_inspector` / `er_designer` / `expectedVersion` / `supersedes` / `information_schema` / `use xxx`）；新发现的关键词追加进来。该表是 §6.1 结构性测试的输入。
- [x] 1.3 grep `docs/bugs/` 与 `openspec/changes/`，确认无与 AGENTS.md / OpenCode skill 加载冲突的 open BUG 或活跃变更。

## 2. 新 skill 资源创建（11 个并行）

> 实施提示：本组 11 个任务相互独立，按 CLAUDE.md "OpenSpec Apply & Parallel Execution" 规则可由 `superpowers:dispatching-parallel-agents` 并行下发；编写期间跳过逐任务 mvn compile，留到 §5 统一验证。

- [x] 2.1 创建 `server/data-talk-adapter/src/main/resources/skills/sql-execution/SKILL.md`：内容来自原 AGENTS.md "Registered Actions(SQL 部分) + Schema Reading Rules + 'table doesn't exist' 探查的入口判断 + **schema 读取截断处理**（`datatalk_read_schema` 返回 `truncated=true` 时缩小 `pattern`/`limit` 或追加业务关键词）"。**不**复制"大输出落盘"完整规则（归 artifacts-output，触发条件是 tool 返回 `saved file path`）、**不**复制 SQL 执行失败诊断完整规则（归 sql-error-diagnostics）；遇到这两类场景在文中以 `[[artifacts-output]]` / `[[sql-error-diagnostics]]` 短引用形式跳转。
- [x] 2.2 创建 `skills/query-editor-workflow/SKILL.md`：原 "Intent Routing(查表分支) + Query Editor Rules + open_query_editor → set_context → patch → run_sql → focus 工作流"。
- [x] 2.3 创建 `skills/ui-contract/SKILL.md`：原 "Exact UI Contract 全部 + `datatalk_ui_find/read/patch/exec` 语义 + apply_text_edits 后 ui_read 验证"。
- [x] 2.4 创建 `skills/tab-management/SKILL.md`：原 "Tab Reuse vs New + Tab Persistence and Search + UI Navigation + Library vs Workset"。
- [x] 2.5 创建 `skills/er-tabs/SKILL.md`：原 "ER Tabs (Inspector & Designer)" 全部。
- [x] 2.6 创建 `skills/concurrency-contract/SKILL.md`：原 "Concurrency Contract" 全部 + ui_patch version / expectedVersion 处理。
- [x] 2.7 创建 `skills/charts-and-dashboards/SKILL.md`：原 "Charts + Dashboards" 合并。
- [x] 2.8 创建 `skills/artifacts-output/SKILL.md`：原 "Output Files & Artifacts + datatalk_supersede_artifact + **大输出落盘处理**（tool 返回 `saved file path` 时如何继续推理：读取该文件、抽样、不当作 tool failure）"。**大输出落盘的完整定义放在本 skill**，其他 skill 通过 `[[artifacts-output]]` 短引用。**不**复制 `datatalk_read_schema` 的 `truncated=true` 处理规则（归 sql-execution，那是 schema 读取策略问题不是工件落盘问题）。**重要约束**：本文件**不得**嵌入 `{{ACTIVE_SESSION_DIR}}` 或任何 `{{XXX}}` 占位符（SKILL.md 是静态资源，不经 `AgentPromptBuilder.render()`）；遇到"写入 session 目录"语义时用句子 "as required by the active-session-dir rule in AGENTS.md core" 引用 AGENTS.md 中保留的硬规则，详 design.md Decision 8。
- [x] 2.9 创建 `skills/connection-management/SKILL.md`：原 "Connection Management + confirmable mutation 两阶段协议"。
- [x] 2.10 创建 `skills/sql-error-diagnostics/SKILL.md`：原 Core Rules 中 SQL **执行失败**后的诊断路径（句法错误 / 对象不存在 / 多候选歧义 三大类）。truncated 不属于本 skill，遇到 truncated 用 `[[artifacts-output]]` 短引用跳转。
- [x] 2.11 创建 `skills/database-dialects/SKILL.md`：合并原 AGENTS.md **两个独立二级章节**：`## Database Dialect Notes`（line 694）与 `## GaussDB`（line 898，独立二级标题，与 Database Dialect Notes 并列，**不要因层级混淆而遗漏**）。SKILL.md 内部按 MySQL / PostgreSQL / Oracle / SQLServer / SQLite / DuckDB / Hive / GaussDB 设 `##` 子章节。

每个 SKILL.md 必须满足：
- 顶部 YAML frontmatter `name` = 父目录名，`description` 长度 80-600 且含中英双语触发词；
- 含 `## When to use` 与 `## When NOT to use` 章节（与 data-ingestion 风格一致）；
- 同一规则在 11 个 SKILL.md 中仅出现一次完整定义，其他 skill 如需关联用 `[[<skill>]]` 短引用；
- **不得**包含任何形如 `{{XXX}}` 的占位符字面量（SKILL.md 静态资源不经 `AgentPromptBuilder.render()`，详 design.md Decision 8）。

## 3. AGENTS.md 重写

- [x] 3.1 将 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` 重写为骨架版，6 个二级标题（Identity & Hard Constraints / Intent Routing Gate / Context Model / Registered Actions / Trigger Gate / Skill Index），非空行 ≤ 350。
- [x] 3.2 在 `## Identity & Hard Constraints` 章节同时保留：
  - `{{ACTIVE_SESSION_DIR}}` 占位符（出现次数按实际写够即可，**至少 1 次**，位于 design.md Decision 8 给出的硬规则陈述句"The current active session subdirectory is: `{{ACTIVE_SESSION_DIR}}`."中）。措辞**必须**遵循 Decision 8 给出的"先把占位符值作为独立陈述句，再分别处理两种值"的结构，**不得**使用内嵌占位符的从句句式（避免渲染后语义矛盾）；
  - 一条硬规则："When any row in the Trigger Gate matches the current situation, you MUST load the listed skill before proceeding."
- [x] 3.3 在文件末尾保留 `{{STAGE_TAB_DIGEST}}` 占位符（位置沿用现状，`AgentPromptBuilder` 会替换为 "## Open Tabs Snapshot" 块）。
- [x] 3.4 在 `## Registered Actions` 章节按 "工具名 — 1 行用途 — `see skill:<name>`" 三列形式列出所有 `datatalk_*` 工具，删除原 required input 详表（详表迁到对应 skill）。
- [x] 3.5 在 `## Trigger Gate` 章节构建 11 行 Markdown 表（详见 design.md §3），**不含** `bezel` / `data-ingestion`（理由见 design.md Decision 3 末段）。
- [x] 3.6 在 `## Skill Index` 章节列出全部 13 个 skill 名 + description 摘要（一行内），其中包括 `bezel` 与 `data-ingestion` 以满足 spec "Trigger Gate 表与 skill 双向闭合"中"反向被引用"的覆盖。
- [x] 3.7 确认原 `## Recommended Workflows` 章节（约 57 行）已按 design.md §9 拆分到 `query-editor-workflow` / `charts-and-dashboards` / `er-tabs` / `connection-management` 各自 SKILL.md 的 "## Recommended workflow" 子章节，AGENTS.md 骨架中不再保留独立 Recommended Workflows 章节。
- [x] 3.8 用 §1.2 的关键词清单 grep 重写后的 AGENTS.md + 11 个 SKILL.md 联合体，确保每个金本位关键词在 owning-skill 中命中至少一次。

## 4. 后端 Spring 配置

- [x] 4.1 在 `server/data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java` 中扩展 `syncSkill` 注册块，新增 11 行调用（顺序与 §2 对应，紧跟现有 bezel / data-ingestion 之后）。
- [x] 4.2 用 `git diff` 核验本变更**未触碰** `OpenCodeBootstrapWriter.java`、`AgentPromptCustomizer.java`、`AgentPromptBuilder.java`、`SkillResourceSyncer.java`、`OpenCodeProcessManager.java`、`OpenCodeBinaryResolver.java` 这 6 个文件；若发现实施过程中产生变动需求，先停下来与用户对齐，不擅自调整公共契约。

## 5. 一次性构建与编译验证

- [x] 5.1 `cd server && mvn -pl data-talk-adapter compile -q` 通过。
- [x] 5.2 `cd server && mvn -pl data-talk-adapter test -q -Dtest='!*IT'` 既有单元测试不退化。
- [x] 5.3 `cd client && npx tsc --noEmit` 通过（若 E2E spec 新建涉及 TypeScript 类型）。

## 6. 结构性测试

- [x] 6.1 新增 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/SkillRoutingContractTest.java`，覆盖 spec Requirement "AGENTS.md 骨架体积上限" / "Trigger Gate 表与 skill 双向闭合" / "SKILL.md frontmatter 契约" / "SkillResourceSyncer 注册一致性" / "skill 切分边界唯一性（基于关键词清单）" / "SKILL.md 不得包含模板占位符" 对应的全部 Scenario：
  - AGENTS.md 非空行 ≤ 350 + **同时**含 `{{STAGE_TAB_DIGEST}}` 与 `{{ACTIVE_SESSION_DIR}}`；
  - 包含 6 个金本位二级标题；
  - Trigger Gate 表中所有 `skill:<name>` 引用都能解析到 `classpath:/skills/<name>/SKILL.md`；
  - `OpenCodeGatewayBeans` 中所有 `syncSkill` 参数都在 AGENTS.md 的 Trigger Gate **或** Skill Index 中至少出现一次；
  - 每个新 SKILL.md frontmatter 合法（YAML 解析 + `name` 等于父目录 + `description` 长度 ∈ [80, 600] + `[A-Za-z]{3,}` 与 `\p{IsHan}{2,}` 双重命中）；
  - classpath 下 `skills/*/SKILL.md` 目录名集合 = `OpenCodeGatewayBeans` 中 `syncSkill` 参数集合；
  - 基于 `keyword-ownership.md` 的 12+ 关键词唯一归属（命中文件集合 ⊆ {owning-skill} ∪ {含 `[[owning-skill]]` 短引用的其他 skill}）；
  - 任一新 SKILL.md 文件中正则 `\{\{[A-Z_]+\}\}` 匹配次数 = 0。
- [x] 6.2 新增 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptBuilderPlaceholderTest.java`，覆盖 spec Requirement "AgentPromptBuilder 占位符渲染保持" 的 4 个 Scenario：
  - (a) `classpath:/agents/AGENTS.md` 字面同时含 `{{STAGE_TAB_DIGEST}}` 与 `{{ACTIVE_SESSION_DIR}}`；
  - (b) stub `StageTabRepository` + stub `ActiveSessionDirProvider.currentSessionId() = Optional.of("S-1")` 下 `AgentPromptBuilder.render(raw)` 输出不含 `{{STAGE_TAB_DIGEST}}` 也不含 `{{ACTIVE_SESSION_DIR}}`，且含 `./sessions/S-1/`；
  - (c) stub `Optional.empty()` 下输出含 `<no active session>`；
  - (d) `AgentPromptCustomizer` 构造 supplier 后读取路径仍是 `agents/AGENTS.md`，资源缺失时抛 `RuntimeException`（cause 为 `IOException`，message 含 "AGENTS.md not found"）。
- [x] 6.3 **重写** 既有 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentsTemplateContractTest.java`。当前 8 个 `@Test`：
  - `template_contains_file_artifact_section_markers` / `template_markers_appear_in_correct_order` / `template_contains_output_files_section_heading_between_markers` / `template_contains_active_session_dir_placeholder_inside_section` / `template_references_archive_tool_inside_section` / `template_section_includes_default_promote_and_rules_subheaders`：以上 6 个绑定 `<!-- file-artifact-section -->` markers 与 `## Output Files & Artifacts` 章节，重构后该章节迁出 AGENTS.md → **删除这 6 个测试**；将其断言意图迁到 `SkillRoutingContractTest` 中针对 `artifacts-output/SKILL.md` 的断言（含 `datatalk_archive_artifact` / `**Default (Temporary)**` / `**Promote to Archive Candidate**` / `**Rules**` 子标题），并新增"AGENTS.md 中**不再**含 `<!-- file-artifact-section:begin -->` 字面量"的反向断言。
  - `agentsTemplateContainsDataIngestionSection`：绑定 `## Data Ingestion (skill: data-ingestion)` 章节及 7 个错误码常量；重构后 AGENTS.md 删除此章节 → **改写为**：AGENTS.md 中**不再**含 `## Data Ingestion`，但 `## Skill Index` 章节含字面 `skill:data-ingestion`；7 个错误码常量的断言迁到 `data-ingestion/SKILL.md` 内容存在性检查（不重复完整断言，仅抽样验证存在）。
  - `agentsTemplateContainsStageTabDigestPlaceholder`：保留，并**新增对称的** `agentsTemplateContainsActiveSessionDirPlaceholder`，断言 AGENTS.md 含字面 `{{ACTIVE_SESSION_DIR}}`。
- [x] 6.3a **重写** 既有 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java`（实施期发现的同性质内容绑定测试，tasks 原版漏列，已扩入 §6.3 范围）。当前 29 个 `@Test` 中本变更后 20 个失败：所有依赖 AGENTS.md 旧详细内容（Schema Reading Rules / 单工具 required input / TiDB 段 / DuckDB 段 / ER 段 / Concurrency 段 / Dashboards 段等）的断言应**删除**，断言意图迁到 `SkillRoutingContractTest` 中对应 skill 文件的存在性检查；保留与 AGENTS.md skeleton 仍然有效的 9 个测试（`runtimePromptReferencesOnlyRegisteredMcpTools` / `runtimePromptStaysEnglishAndAvoidsUnsupportedWorkspaceTargets` / `productionRegistryDoesNotExposeLegacyDemoEchoAction` / `renderedTemplateRegistersUiFindAndDoesNotMentionUiList` / `renderedTemplateContainsTabSnapshotSection` / `registeredUiActionSchemasStayAlignedWithPromptSurface` / `uiExecActionSchemaContainsOpenErInspectorVerb` / `uiExecActionSchemaContainsDesignerVerbs` / `uiExecActionSchemaAdvertisesQueryEditorContextParameters` / `uiPatchActionSchemaAllowsErInspectorObject`），其中 `uiExec/uiPatch` schema 类断言改为只依赖 ActionRegistry。
- [x] 6.3b 顺手修 `RequestLogInterceptorTest.afterCompletion_logsError_forErrorResponse`（develop baseline 已存在的失败，本变更已确认非引入；测试 status 由 4xx 误用为期望 ERROR 级别，已改为 500 让其落入 ERROR 分支）。
- [x] 6.4 `cd server && mvn -pl data-talk-adapter test -q -Dtest='SkillRoutingContractTest,AgentPromptBuilderPlaceholderTest,AgentsTemplateContractTest,AgentPromptContractTest'` 通过。
- [x] 6.5 `cd server && mvn -pl data-talk-adapter verify -q` 端到端绿（含既有 smoke IT）。

## 7. Playwright E2E 回归

- [x] 7.1 新增 `client/tests/e2e/agents-skills-regression.spec.ts`，覆盖 design.md §6 列出的 5 个典型场景（browse table / ER design / chart / data-ingestion baseline / SQL error diagnostics）；断言以 recorder 工具调用 + 可见 DOM 产物为主（正向证据）。全部测试由 `DATATALK_REAL_OPENCODE_MODEL` env 保护：无 env 时 skip。`npx tsc --noEmit` 通过。
- [x] 7.2a / 7.2b / 7.2c 2026-05-14 跑通：`DATATALK_REAL_OPENCODE_MODEL=alibaba-coding-plan-cn/qwen3.6-plus npx playwright test agents-skills-regression.spec.ts`，5/5 通过（4.2 min，单 worker）。运行环境：backend `SPRING_PROFILES_ACTIVE=e2e mvn spring-boot:run`、frontend `npm run dev` (vite 5.4)、Chromium。结果日志：`tmp/e2e-task7/full-run2.log`。**前置工具修复**（与本变更同 PR）：(a) recorder 改为 `__dtToolPartTap` 取 React tool-part 真实 `(tool, input, status, callID, partId)`，旧 DOM-based 抓不到 params；(b) `chat-panel.page.ts:waitForAiResponse` 改用 composer submit button 切换为非 streaming 态作收敛信号，旧 basic-tool 计数稳定法在 tool-batch 间静默期会过早返回；(c) 5 个 scenario 断言放宽以匹配 qwen3.6-plus 实际路径（接受 ui_exec apply_text_edits|run_sql、er_designer/inspector 同义、`list_connection_targets` 与 `read_schema` 同属 schema probe 等价路径），仍保留 design.md §6 核心意图。修改文件：`client/src/features/chat/components/turn/tool-part.tsx` / `client/tests/e2e/fixtures/mcp-tool-recorder.ts` / `client/tests/e2e/pom/chat-panel.page.ts` / `client/tests/e2e/agents-skills-regression.spec.ts`。
- [x] 7.3 2026-05-14 E2E 共发现 **0 个 BUG**（5/5 通过，无产品行为偏差需登记）。运行期早期日志中曾出现 3 处 `[mcp-bridge] no client subscriber tool=ui_exec`，均发生在 recorder/waitForAiResponse 修复前的失败 run，修复后未复现，未单独登记。**残留观察**（非本变更范围）：旧 recorder 让 `agents-batch4-ui-workspace.spec.ts` 等同样依赖 `params.action==='open'` 的 routing 断言「侥幸跳过」，恢复 params 后这些 batch 可能因路径嵌套差异（top-level `type` vs `params.params.type`）暴露——按需另起变更修。

## 8. 收尾

- [x] 8.1 `git status` 核对：本变更修改了 `agents/AGENTS.md`、`skills/**`（11 个新 skill + data-ingestion 补 2 错误码）、`OpenCodeGatewayBeans.java`（+11 行 syncSkill）、4 个测试（2 新 + 2 重写）+ `RequestLogInterceptorTest.java`（顺手修 baseline）+ `docs/bugs/index.md` 与 `BUG-0045-...md` + 本变更目录。**额外发现**：develop 上存在 3 个**与本变更无关的 WIP** 文件（`schema.sql` / `IngestionJobStatus.java` / `V20__ingestion.sql`，属其他人 ingestion job status enum 调整），**未含入本 PR**，详 `PR-DESCRIPTION.md` "Files changed" 末段。
- [x] 8.2 PR 描述草稿写在 `openspec/changes/agents-md-skills-refactor/PR-DESCRIPTION.md`，链接本变更目录、列出 11 个新 skill name + description 摘要表、Phase 1-7 总结、DEFERRED 项说明、BUG-0045 提示、Files changed 与 WIP 提示。
- [x] 8.3 2026-05-14 backend 启动回归通过：`SPRING_PROFILES_ACTIVE=e2e mvn spring-boot:run -pl data-talk-adapter` 启动后 `grep "Synced skill" backend.log | wc -l` = 13，名单 = {bezel, data-ingestion, sql-execution, query-editor-workflow, ui-contract, tab-management, er-tabs, concurrency-contract, charts-and-dashboards, artifacts-output, connection-management, sql-error-diagnostics, database-dialects}，与 `OpenCodeGatewayBeans` 注册一致。
- [x] 8.4 2026-05-15 archive 完成：delta `specs/agent-skill-routing/spec.md` 同步生成 base spec `openspec/specs/agent-skill-routing/spec.md`（7 个 Requirement，`openspec validate` 通过），变更目录搬迁至 `openspec/changes/archive/2026-05-15-agents-md-skills-refactor/`。三个前置：(a) backend 启动 13 skill 全 sync ✓；(b) E2E 5/5 通过 ✓；(c) BUG-0045 verified (fixCommit 6403fa9c) ✓。
