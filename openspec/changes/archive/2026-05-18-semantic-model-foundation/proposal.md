## Why

DataTalk 当前的 agent 体系（13 个 skill + AGENTS.md 骨架）解决了 **"如何用工具"** 的问题，但完全没有触及 **"业务语义"** 的问题：

- 用户问"上周销售额"，AI 每次都要重新走 schema 探索 → 猜列名 → 拼 SQL，结果可能拼错（amount vs paid_amount？）、口径不一致（要不要扣退款？）、时间窗口含混（自然周 vs 滚动 7d？）。
- 同一个问题被问 100 次，AI 跑 100 次完整推理，成本极高且响应慢。
- 业务术语（"已完成"="COMPLETED"、"GMV"="销售额-退款"）每次都靠 LLM 猜，没有沉淀。
- 用户经验（"我们这家公司客单价口径是按 paid_orders 算"）会话结束就丢，下一次重新教。

Hermes Agent 的「四层记忆 + Semantic Model」架构提供了一条路径：把业务语义固化为可演进的本地化 YAML、把高频查询沉淀为 Verified Queries、把用户偏好沉淀为本地 profile。本次变更聚焦 **L3 程序记忆（Semantic Model 本体）+ L1 热记忆（AGENTS.md 注入摘要）** 两层——这是杠杆最大、最能落到 DataTalk 现有架构上的两层。

## What Changes

### 新增能力（按用户优先级）

- **Semantic Model 本体（per-connection YAML）**：每个 connection 一个 `~/.data-talk/semantic/<connectionId>/` 目录，按业务域拆分 model 文件（参考 dbt MetricFlow 子集 DSL：entities / dimensions / measures / metrics / literal_mappings / verified_query_refs）。
- **Verified Queries 高频缓存**：高频问题沉淀为 `verified_queries.jsonl`，四层路由（精确字面 → 规范化 → LLM 候选选择 → 全量生成）。Layer 0/1 不调 LLM，Layer 2 LLM 看 Top-K 候选自主决策，Layer 3 兜底全量。
- **Patch 机制（仅追加）**：AI 只允许做 `ADD_VERIFIED_QUERY` / `INC_HIT_COUNT` / `ADD_LITERAL_MAPPING` 三种增量写入；结构性变更（改 measure 定义、加 entity）必须走 `pending/` + 用户审。
- **AI 自我编写 skill 能力**：把 `anthropics/skills` 的 `skill-creator` 本地化后装进 `resources/skills/`，配套新 Action `datatalk_skill_create` 让 OpenCode AI 把 yaml 内容写入 `pending/`。
- **L1 热记忆注入**：`AgentPromptBuilder` 新增 `{{SEMANTIC_MODEL_DIGEST}}` 占位符，每次会话按当前 connection 注入 ≤2000 字符的摘要（Top 度量 + 字面值 + Top-K verified queries + 用户偏好）。
- **Stage tab：semantic_model_editor**：新 tab type，参照 `er_designer` 风格，承担 pending 审核、yaml 编辑、verified query 标记。
- **builtin 模板**：附 `ecommerce` + `saas` 两个示例 model 模板，新建 connection 时给"从模板创建"选项；同时支持 AI 推断初稿（首次连接到陌生 schema 时）。

### 修改能力

- **ConnectionDeletionService**：删除 connection 时级联清理 `~/.data-talk/semantic/<connectionId>/`（force=true 时直接移到 `_trash`；force=false 时若存在 verified queries 返回 `BlockedByResources`）。
- **AGENTS.md 骨架**：新增 `{{SEMANTIC_MODEL_DIGEST}}` 占位符与 Semantic Model 相关 Trigger Gate 行，但保持 350 行体积上限。
- **OpenCodeGatewayBeans**：注册 `skill-creator` skill（与现有 13 个并列）。
- **HousekeepingScheduler**：新增 `.patches.jsonl` compaction 任务（行数 > 200 时合并回 yaml），与 `_trash` 清理任务复用同一调度器。

## Capabilities

### New Capabilities

- `semantic-model`：业务语义层 YAML 契约、加载/合并/补丁机制、per-connection 存储与级联删除、与 `ConnectionDeletionService` 的二阶段删除集成、模板与 AI 推断、四层 Verified Query 路由、`AgentPromptBuilder` 占位符注入、Stage `semantic_model_editor` tab、AI 自我编写 skill 的 Action 集合（`datatalk_semantic_lookup`、`datatalk_verified_query_find`、`datatalk_verified_query_record`、`datatalk_semantic_propose_change`、`datatalk_literal_mapping_add`、`datatalk_skill_create`）。

### Modified Capabilities

- `agent-skill-routing`：新增 `{{SEMANTIC_MODEL_DIGEST}}` 占位符（与现有两个占位符并列），新增 `skill-creator` 到 SKILL Index 与 `OpenCodeGatewayBeans` 注册集合，Trigger Gate 新增"用户问业务指标 / 自然语言含业务术语"等行指向 `skill:semantic-model-usage`（注：路由 skill 本身由本变更新增 SKILL.md，但 Semantic Model **数据**不走 SkillResourceSyncer，只通过占位符注入）。

## Impact

### 代码新增

- `server/data-talk-domain/src/main/java/com/datatalk/domain/semantic/` — SemanticModel / Entity / Dimension / Measure / Metric / LiteralMapping / VerifiedQuery 等 record 与 sealed interface
- `server/data-talk-application/src/main/java/com/datatalk/application/semantic/` — SemanticModelRepository / SemanticModelLoader / VerifiedQueryRouter / SemanticModelDigester / PatchApplier
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/semantic/` — FsSemanticModelRepository（基于 `~/.data-talk/semantic/`）+ JdbcVerifiedQueryStats
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/semantic/` — 6 个 ActionHandler
- `server/data-talk-adapter/src/main/resources/skills/skill-creator/SKILL.md` — 本地化版（替换 Write 工具调用为 `datatalk_skill_create`）
- `server/data-talk-adapter/src/main/resources/skills/semantic-model-usage/SKILL.md` — 给 OpenCode AI 看的 Semantic Model 使用契约
- `server/data-talk-adapter/src/main/resources/semantic-models/builtin/_templates/` — `ecommerce.model.template.yaml` + `saas.model.template.yaml` + README
- `client/src/features/semantic-model/` — `semantic_model_editor` tab 实现（参照 `er-designer` 结构）
- `client/src/features/stage/registry/tab-type-registry.ts` — 注册新 tab type

### 代码修改

- `AGENTS.md`：新增 `{{SEMANTIC_MODEL_DIGEST}}` 占位符、SKILL Index 新增 `skill-creator` / `semantic-model-usage`、Trigger Gate 新增 1-2 行
- `AgentPromptBuilder.render(String)`：新增第三个占位符替换分支，依赖 `SemanticModelDigester` bean
- `ConnectionDeletionService.delete(String, boolean)`：force=true 时调用 `semanticModelRepository.deleteAllByConnection(id)`；force=false 时 `BlockedByResources` 增加 `verifiedQueries` 字段
- `OpenCodeGatewayBeans`：新增 `skillSyncer.syncSkill("skill-creator", cwd)` 与 `syncSkill("semantic-model-usage", cwd)`
- `HousekeepingScheduler`：新增 `compactPatches()` 周期任务
- `DtEvent`：新增 `SemanticPendingCreated` / `VerifiedQueryRecorded` 两个事件类型（带 exhaustive switch 同步）

### 不影响

- 现有 13 个 skill 内容与契约（`sql-execution`、`query-editor-workflow` 等保持不变）
- `SkillResourceSyncer` 实现本身不动，只是注册集合从 13 → 15
- `AgentPromptBuilder` 的两个旧占位符渲染语义、加载路径、异常签名
- 与 `script-runner` change 完全无耦合，可并行实施

### 不在本变更范围（留给后续 change）

- L2 冷记忆（`datatalk_session_search` + 摘要小模型）
- L4 用户画像深度建模（本变更只做 `_user_profile.json` 静态字段）
- 跨设备同步、云端备份 Semantic Model
- 多 connection 共享 model（`shared/` 子目录预留路径但不实现引用解析）
- 向量检索辅助 VQ 路由（Layer 2 仅靠 LLM 候选选择）
