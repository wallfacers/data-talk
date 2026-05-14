# AGENTS.md → 11 + 2 Skill 迁移映射表

> 实施期工件，archive 时随分支历史保留，不进 `openspec/specs/`。
> 用于 Phase 2 并行 SKILL.md 生成的输入；锁定每个原章节的归属。

## 1. data-ingestion 错误码覆盖核查

| 检查项 | 结果 |
|---|---|
| `classpath:/skills/data-ingestion/SKILL.md` 当前已含的错误码 | 8 个：`INGESTION_SSRF_BLOCKED` / `INGESTION_PAYLOAD_TOO_LARGE` / `INGESTION_AUTH_FAILED` / `INGESTION_TOKEN_INVALID` / `INGESTION_DIALECT_UNSUPPORTED` / `INGESTION_FETCH_FAILED` / `INGESTION_FORMAT_UNSUPPORTED` / `INGESTION_INFER_FAILED` |
| 当前 AGENTS.md `## Data Ingestion` 章节（L967-977）含的错误码 | **10 个**：上述 8 个 + `INGESTION_NAME_REQUIRED` + `INGESTION_ALREADY_TERMINAL` |
| 差异 | AGENTS.md 多 2 个（BUG-0040 修复时 inline 加入），SKILL.md 缺。**Day-1 决策**：本变更顺手把这 2 个补到 `data-ingestion/SKILL.md` 的 "Error handling" 表，使其成为唯一权威。AGENTS.md 删 `## Data Ingestion` 章节正文，仅在 `Skill Index` 留一行引用。 |

> tasks.md §6.3 重写 `agentsTemplateContainsDataIngestionSection` 时，断言迁到 `data-ingestion/SKILL.md`，使用补齐后的 10 个错误码作为抽样断言依据。

## 2. 原 AGENTS.md 19 个二级章节 → 目标归属

| # | 原 `## ` 章节 | 行号区间 | 目标 | 备注 |
|---|---|---|---|---|
| 1 | Core Rules | L5-27 | AGENTS.md `## Identity & Hard Constraints` 保留 5-7 条金本位 | 失败诊断 / UI 验证 / 切换上下文 / "table doesn't exist" 探查路径搬到对应 skill |
| 2 | Intent Routing Gate | L29-39 | AGENTS.md `## Intent Routing Gate` 保留全文 | 路由根节点，不能拆 |
| 3 | Context Model | L41-53 | AGENTS.md `## Context Model` 保留全文 | 同上 |
| 4 | Registered Actions（含 ### Session Data Context / Connection Management / Schema/Query/Artifacts / Query Diagnostics / Mutation Actions / Diagnostics Workflow Rules / Diagnostics Capability Matrix / EXPLAIN Warnings / INDEX_HINTS Impact Tier / UI Actions） | L55-254 | AGENTS.md 仅留工具名目录 + `see skill:<name>`；详表全部下沉到对应 skill | 主要去向：sql-execution / connection-management / sql-error-diagnostics / artifacts-output / ui-contract |
| 5 | Exact UI Contract | L256-328 | `skills/ui-contract/SKILL.md` 全部 | — |
| 6 | ER Tabs (Inspector & Designer) | L330-415 | `skills/er-tabs/SKILL.md` 全部 | 包含 ### When to open which / Hard rules / Recipe shortcuts |
| 7 | Concurrency Contract | L417-477 | `skills/concurrency-contract/SKILL.md` 全部 | 含 ### 子章节 Required guard fields / Conflict response shape / What you MUST do / Multi-edit batches / What you MUST NOT do / Multi-session etiquette |
| 8 | Library vs Workset | L479-488 | `skills/tab-management/SKILL.md` | — |
| 9 | Tab Reuse vs New Tab | L490-519 | `skills/tab-management/SKILL.md` | 含 ### New task / Continuation / Continuation signals / Ambiguous cases |
| 10 | UI Navigation Rules | L521-530 | `skills/tab-management/SKILL.md` | — |
| 11 | Query Editor Rules | L532-539 | `skills/query-editor-workflow/SKILL.md` | — |
| 12 | Charts | L541-545 | `skills/charts-and-dashboards/SKILL.md` | — |
| 13 | Dashboards | L547-622 | `skills/charts-and-dashboards/SKILL.md` | 含 ### When to use / Creating / Incremental updates / P1 widget types / Path addressing / Error handling / Relationship to single chart |
| 14 | **Recommended Workflows**（横跨场景） | L624-679 | 拆 4 块到对应 skill 的 `## Recommended workflow` 子章节（见下） | AGENTS.md 骨架中不再保留独立 Recommended Workflows 章节 |
| 15 | Tab Persistence and Search | L681-692 | `skills/tab-management/SKILL.md` | — |
| 16 | Database Dialect Notes | L694-896 | `skills/database-dialects/SKILL.md` 内部按 `##` 子章节 MariaDB / TiDB / Oracle / SQLServer / DuckDB / ClickHouse / Apache Doris / OceanBase / StarRocks / Trino / Presto / Dameng / Hive 排列 | — |
| 17 | **GaussDB**（独立二级章节，与 Database Dialect Notes 并列） | L898-910 | `skills/database-dialects/SKILL.md` 内追加 GaussDB 子章节 | **必须**显式合并，不能因为它不在 "Database Dialect Notes" 之下而被遗漏 |
| 18 | Output Files & Artifacts | L912-940（含 `<!-- file-artifact-section:begin/end -->` markers） | 详细规则 → `skills/artifacts-output/SKILL.md`；`{{ACTIVE_SESSION_DIR}}` 占位符 + 1 行硬规则 → 留 AGENTS.md `## Identity & Hard Constraints`（详 design.md Decision 8） | SKILL.md **不得**嵌入 `{{ACTIVE_SESSION_DIR}}` 字面量 |
| 19 | Data Ingestion (skill: data-ingestion) | L942-977 | **整体移除**，仅在 AGENTS.md `## Skill Index` 留一行 `skill:data-ingestion — ...` | 完整内容（含 10 个错误码）保持在 `skills/data-ingestion/SKILL.md`（本变更顺手补齐 2 个缺失） |

## 3. Recommended Workflows 拆分细则（原 L624-679 共 9 个子小节）

| 原小节 | 行号 | 目标 SKILL.md |
|---|---|---|
| Inspect the Current SQL Editor | L626-632 | `query-editor-workflow/SKILL.md` `## Recommended workflow` |
| Browse Table Rows or Simple Counts in Query Editor | L634-640 | `query-editor-workflow/SKILL.md` `## Recommended workflow` |
| Answer an Analytical Data Question | L642-647 | `sql-execution/SKILL.md` `## Recommended workflow`（与 sql-execution 主题更贴近，归属调整自 design §9 原 charts-and-dashboards） |
| Switch Connection, Database, or Schema | L649-654 | `connection-management/SKILL.md` `## Recommended workflow` |
| Open or Reuse a SQL Workspace | L656-658 | `query-editor-workflow/SKILL.md` `## Recommended workflow` |
| Edit SQL in a Query Editor | L660-666 | `query-editor-workflow/SKILL.md` `## Recommended workflow` |
| Locate Text Inside an Existing Tab | L668-672 | `tab-management/SKILL.md` `## Recommended workflow` |
| No Active Connection | L674-679 | `connection-management/SKILL.md` `## Recommended workflow` |

> "ER 设计 / Inspector 推荐工作流" 在原 AGENTS.md `## ER Tabs` 的 `### Recipe shortcuts` 中已有 6 段示例（L368-407），全部归 `er-tabs/SKILL.md` 自身的 "## Recommended workflow / Recipe shortcuts" 子章节。

## 4. Skill 边界冲突点 → 唯一归属决议（与 design.md Decision 1 一致）

| 主题 | 完整定义归属 | 其他 skill 引用形式 |
|---|---|---|
| 大输出落盘（tool 返回 `saved file path`） | `artifacts-output`（唯一） | `[[artifacts-output]]` 短引用 |
| schema 读取截断 `datatalk_read_schema` `truncated=true` | `sql-execution`（唯一） | — |
| SQL 执行错误诊断（句法 / 对象不存在 / 多候选） | `sql-error-diagnostics`（唯一） | `[[sql-error-diagnostics]]` 短引用 |
| Schema 读取规则 / cross-DB 探查 / pattern+limit | `sql-execution`（唯一） | — |
| `confirm=true` / `confirmationToken` 两阶段协议 | `connection-management`（唯一） | — |
| `ui_patch` version 冲突 / `expectedVersion` 处理 | `concurrency-contract`（唯一） | — |
| `apply_text_edits` 后 `ui_read` 再读验证 | `ui-contract`（唯一） | — |

## 5. 工具名 → owning skill 速查（Phase 3 §3.4 写 `## Registered Actions` 表的依据）

| 工具 | owning skill |
|---|---|
| `datatalk_get_data_context` / `datatalk_set_data_context` / `datatalk_resolve_use_target` / `datatalk_list_connection_targets` / `datatalk_list_connections` / `datatalk_select_connection` | `connection-management` |
| `datatalk_create_connection` / `datatalk_test_connection` / `datatalk_update_connection_confirmable` | `connection-management` |
| `datatalk_read_schema` / `datatalk_execute_sql` | `sql-execution` |
| `datatalk_render_chart` | `charts-and-dashboards` |
| `datatalk_supersede_artifact` / `datatalk_archive_artifact` / `datatalk_pin_artifact` | `artifacts-output` |
| `datatalk_explain_query` / `datatalk_index_hints` | `sql-error-diagnostics`（性能诊断隶属错误诊断的延伸面） |
| `datatalk_lock_info` / `datatalk_pool_status` / `datatalk_table_space` | `sql-error-diagnostics` |
| `datatalk_terminate_session` / `datatalk_optimize_table` | `connection-management`（确认型 mutation；归 connection-management 因含 confirm 协议主路径） |
| `datatalk_ui_find` / `datatalk_ui_read` / `datatalk_ui_patch` / `datatalk_ui_exec` | `ui-contract`（语义契约）；编辑器细分流程在 `query-editor-workflow`；ER 细分流程在 `er-tabs`；Tab 管理细分在 `tab-management` |
| `datatalk_http_request` / `datatalk_infer_ingestion_schema` / `datatalk_create_ingestion_table` / `datatalk_ingest_payload` / `datatalk_get_ingestion_job` / `datatalk_list_ingestion_jobs` | `data-ingestion`（既有 skill 不动） |

## 6. AGENTS.md 骨架硬规则候选（≤7 条金本位，详 design.md §2）

1. READ-ONLY: `datatalk_execute_sql` 只接受 SELECT / WITH，绝不 DDL/DML。
2. 不猜 `connectionId` / tab id / database / schema / 编辑器。
3. 工件写入路径受 `{{ACTIVE_SESSION_DIR}}` 约束（含 sentinel `<no active session>` 处理；详 Decision 8 给定句式）。
4. 任何工具调用参数必须用原生 JSON 类型（对象 / 数组），不传 JSON 编码字符串。
5. 工具调用首次即传齐 required input，不用半参数探测错误。
6. `use xxx` 视为上下文切换请求，不当作 SQL 执行。
7. 当任一 Trigger Gate 行匹配当前情况，**MUST** 加载该行的 skill 才能继续。

## 7. Phase 1.3 grep 检查结果

| 检查 | 结果 |
|---|---|
| `docs/bugs/` 与 AGENTS.md / OpenCode skill 加载冲突的 **open** BUG | 无（BUG-0036 与 BUG-0040 均 status=fixed） |
| `openspec/changes/` 活跃变更 | 仅 `agents-md-skills-refactor` 本身 |
| 结论 | 无阻塞；可继续 Phase 2 |
