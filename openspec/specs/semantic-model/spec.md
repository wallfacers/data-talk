## Requirements

### Requirement: Semantic Model 目录与文件结构

系统 SHALL 为每个 connection 在 `~/.data-talk/semantic/<connectionId>/` 维护一个独立目录，承载该 connection 的业务语义模型。目录布局 MUST 遵循以下约定：

- `_index.json` — 该 connection 下所有 model 文件清单与元信息
- `_user_profile.json` — 用户偏好（默认时间范围、默认 limit、常用过滤等）
- `<domain>.model.yaml` — 业务域 yaml（`<domain>` 等于文件名前缀，例 `orders.model.yaml`）
- `<domain>.model.yaml.patches.jsonl` — 该域的增量补丁（仅追加）
- `verified_queries.jsonl` — 跨域 Verified Query 池（单行 JSON）
- `pending/<domain>.model.yaml` — AI 提议但未审核的 yaml
- `shared/` — 预留，本变更不解析任何引用

#### Scenario: 新建 connection 后目录懒创建

- **GIVEN** 一个全新 connection 刚被创建
- **WHEN** 用户首次访问 `semantic_model_editor` tab 或后端首次需要读取该 connection 的 Semantic Model
- **THEN** 后端 MUST 在 `~/.data-talk/semantic/<connectionId>/` 创建空目录与空 `_index.json`
- **AND** 此时 `listDomains(connectionId)` 返回空列表，`listPending(connectionId)` 返回空列表

#### Scenario: 目录与文件命名约束

- **WHEN** 创建任意 `<domain>.model.yaml`
- **THEN** `<domain>` MUST 匹配正则 `^[a-z][a-z0-9_]{0,63}$`
- **AND** 同目录内 yaml 与对应 `.patches.jsonl` 文件名 MUST 一一对应（patches 文件可缺，yaml 不可缺）

### Requirement: Semantic Model yaml 契约（dbt MetricFlow 子集 + DataTalk 扩展）

每个 `<domain>.model.yaml` MUST 通过 `classpath:/semantic/model-schema.json` 校验。yaml 顶层 MUST 包含字段：`name`、`version`（int ≥ 1）、`description`、`last_modified`（ISO 8601）、`authored_by`（枚举：`ai_inferred` / `user_authored` / `hybrid`）。yaml 顶层 MAY 包含以下结构化字段：`entities`、`dimensions`、`measures`、`metrics`、`literal_mappings`、`verified_query_refs`。

#### Scenario: yaml 通过 JSON Schema 校验

- **GIVEN** 一个符合 schema 的 yaml
- **WHEN** `SemanticModelLoader.loadDomain(connectionId, name)` 被调用
- **THEN** 返回 `SemanticModel` record，所有字段填充正确
- **AND** 加载过程中无异常

#### Scenario: yaml 校验失败拒绝加载

- **GIVEN** 一个缺失 `name` 字段或 `version` 不是整数的 yaml
- **WHEN** `loadDomain(...)` 被调用
- **THEN** 抛出 `SemanticModelValidationException`，message 含具体字段路径与错误描述
- **AND** 不返回部分构造的对象

#### Scenario: entity / measure / metric 字段完备

- **GIVEN** yaml 中 `entities[0]`
- **THEN** MUST 含 `name`（matches `^[a-z][a-z0-9_]{0,63}$`）、`type`（`primary` 或 `secondary`）、`physical.{database,schema,table}`、`primary_key`（非空数组）
- **GIVEN** yaml 中 `measures[0]`
- **THEN** MUST 含 `name`、`entity`（必须引用已声明的 entity）、`agg`（枚举：`sum`/`count`/`count_distinct`/`avg`/`max`/`min`）、`expr`
- **GIVEN** yaml 中 `metrics[0]` 类型为 `ratio`
- **THEN** MUST 含 `numerator` 与 `denominator`，且二者必须引用已声明的 measure

### Requirement: Patches 仅允许增量追加，3 种 op

`<domain>.model.yaml.patches.jsonl` 中每行 MUST 是一个 JSON 对象，`op` 字段 MUST 是以下三种之一：`ADD_VERIFIED_QUERY`、`INC_HIT_COUNT`、`ADD_LITERAL_MAPPING`。任何其他 op 类型 MUST 在加载期被拒绝并抛出 `PatchValidationException`。

#### Scenario: 合法 op 加载并应用

- **GIVEN** patches.jsonl 含三行，分别是 `ADD_VERIFIED_QUERY` / `INC_HIT_COUNT` / `ADD_LITERAL_MAPPING`
- **WHEN** `SemanticModelLoader.loadDomain(...)` 被调用
- **THEN** 返回的 `SemanticModel` 中：verifiedQueryRefs 增加一项 / verified_queries.jsonl 中对应 vq 的 hitCount 增加 / literalMappings 中对应 dimension 增加映射
- **AND** 加载结果不修改原 yaml 文件内容

#### Scenario: 非法 op 拒绝加载

- **GIVEN** patches.jsonl 含一行 `{"op":"MODIFY_MEASURE",...}`
- **WHEN** `loadDomain(...)` 被调用
- **THEN** 抛出 `PatchValidationException`，message 含非法 op 名称与行号
- **AND** 该 patches.jsonl 在 trash 中归档以便人工查看

#### Scenario: 结构性变更必须走 pending

- **WHEN** AI 调用 `datatalk_semantic_propose_change(domain, yaml_text, reason)`
- **THEN** 后端校验 yaml_text 通过 JSON Schema 后，写入 `~/.data-talk/semantic/<currentConnectionId>/pending/<domain>.model.yaml`
- **AND** 发布 `SemanticPendingCreated` 事件
- **AND** 不修改 `<domain>.model.yaml` 正式文件

### Requirement: Verified Queries 四层路由

Verified Query 命中流程 MUST 按四层顺序执行：L0 精确字面 → L1 规范化 → L2 候选选择 → L3 全量生成。L0/L1 MUST 不调用 LLM；L2 MUST 通过 prompt 摘要的 Top-K 注入由 LLM 决策；L3 兜底走现有 sql-execution skill 流程。

#### Scenario: L0 精确字面命中

- **GIVEN** verified_queries.jsonl 中存在 `{"id":"vq_001","question":"上周销售额","sql":"...",...}`
- **WHEN** `VerifiedQueryRouter.findExact("上周销售额")` 被调用
- **THEN** 返回 `Optional.of(vq_001)`
- **AND** `incHit("vq_001")` 被调用，hit_count 增 1
- **AND** 整个调用过程未触发任何 LLM 调用

#### Scenario: L1 规范化命中

- **GIVEN** verified_queries.jsonl 中存在 `{"question":"上周销售额"}` 与一份同义词词表（`GMV` ↔ `销售额`）
- **WHEN** `VerifiedQueryRouter.findNormalized("上周 GMV")` 被调用
- **THEN** 规范化后字符串等于 `"上周 销售额"`（trim/同义词替换后）
- **AND** 返回 `Optional.of(vq_001)`
- **AND** 整个调用过程未触发任何 LLM 调用

#### Scenario: L2 候选选择由 LLM 完成

- **GIVEN** 用户问"本月新客单价" — L0/L1 未命中
- **WHEN** AI 通过 `datatalk_verified_query_find(question, topK=5)` 获取候选列表
- **THEN** 返回按 hit_count 降序的 Top-5 VQ
- **AND** AI 决定是否复用某条、修改某条参数、或全新写一条 — 决策由 LLM 在自身上下文中完成

#### Scenario: stale VQ 从路由中排除但保留文件

- **GIVEN** verified_queries.jsonl 中某条 vq 的 SQL 引用了已被 drop 的列
- **WHEN** `StaleChecker` 检测到该列不存在
- **THEN** 该条 vq 的 `stale` 字段被标记为 `true`
- **AND** `findExact` / `findNormalized` / `topKByHits` 在返回时 MUST 过滤 `stale=true` 项
- **AND** jsonl 文件中该行保留，便于 schema 恢复后自动重新生效

### Requirement: 6 个 Action 暴露给 OpenCode AI

后端 MUST 通过 `@DataTalkAction` 注册以下 6 个 Action，并通过 `OpenCodeGateway` 暴露给 OpenCode AI：

| Action | 输入 | 输出 | 写入位置 |
|---|---|---|---|
| `datatalk_semantic_lookup` | `query: string, kind?: 'entity'/'measure'/'metric'/'dimension'` | 匹配项列表 | 只读 |
| `datatalk_verified_query_find` | `question: string, topK?: int` | VQ 候选列表（含 hit_count 排序） | 只读（命中时副作用：incHit） |
| `datatalk_verified_query_record` | `question, sql, modelRef` | 新增 vq id | `verified_queries.jsonl` 追加 |
| `datatalk_semantic_propose_change` | `domain, yaml_text, reason` | `proposalId` | `pending/<domain>.model.yaml` |
| `datatalk_literal_mapping_add` | `domain, dimension, natural, dbValue` | 追加结果 | `<domain>.model.yaml.patches.jsonl` 追加 |
| `datatalk_skill_create` | `name, yaml_text` | `proposalId` | `pending/<name>.model.yaml`（等价于 propose_change） |

#### Scenario: Action 调用要求 active connection 绑定

- **WHEN** 任意写入类 Action（`record` / `propose_change` / `literal_mapping_add` / `skill_create`）被调用
- **GIVEN** 当前 session 未绑定 connectionId
- **THEN** Action 返回错误 `NO_ACTIVE_CONNECTION`，message 含中英双语提示
- **AND** 不创建任何文件

#### Scenario: yaml_text 必须通过 schema 校验

- **WHEN** `datatalk_semantic_propose_change` 或 `datatalk_skill_create` 收到不合法 yaml_text
- **THEN** Action 返回错误 `SCHEMA_VALIDATION_FAILED`，含具体字段路径
- **AND** 不写入 `pending/` 目录

#### Scenario: incHit 在 find 命中时自动执行

- **WHEN** `datatalk_verified_query_find` 通过 L0 或 L1 命中
- **THEN** 该 vq 的 `hit_count` 增 1，`last_hit` 更新为当前 UTC 时间
- **AND** 通过 patches.jsonl 的 `INC_HIT_COUNT` op 持久化（非原地修改 verified_queries.jsonl）

### Requirement: AgentPromptBuilder 注入 `{{SEMANTIC_MODEL_DIGEST}}` 占位符

`AgentPromptBuilder.render(rawAgentsMd)` MUST 替换且仅替换三个占位符：现有的 `{{STAGE_TAB_DIGEST}}` 与 `{{ACTIVE_SESSION_DIR}}`（语义不变），新增 `{{SEMANTIC_MODEL_DIGEST}}`。新占位符的渲染规则由 `SemanticModelDigester.digest(connectionId)` 提供，输出 MUST 保持 ≤ 2000 字符。

#### Scenario: 三占位符全部渲染

- **GIVEN** active session 绑定 connection `c-prod-shop`，该 connection 下有 2 个 domain
- **WHEN** `AgentPromptBuilder.render(rawAgentsMd)` 被调用
- **THEN** 返回字符串中**不再**包含子串 `{{STAGE_TAB_DIGEST}}` / `{{ACTIVE_SESSION_DIR}}` / `{{SEMANTIC_MODEL_DIGEST}}`
- **AND** 包含子串 `## Semantic Model Snapshot`
- **AND** 包含 connection 名称 `c-prod-shop`

#### Scenario: 无 connection 绑定渲染 sentinel

- **GIVEN** active session 存在但未绑定 connection
- **WHEN** `render(...)` 被调用
- **THEN** 输出包含子串 `<no semantic model — please bind a connection>`

#### Scenario: 字符预算超限按优先级裁剪

- **GIVEN** 一个 connection 有 50 个 measures、30 个 literal mappings、200 条 VQ
- **WHEN** `digest(connectionId)` 被调用
- **THEN** 输出长度 ≤ 2000 字符
- **AND** 优先级满足：先裁 Top-K VQ 到 K=5，再裁 measures 到 Top-N=8，再裁 literal mappings 到 Top-M=5

#### Scenario: 占位符不污染其他 skill

- **WHEN** 扫描 `classpath:/skills/*/SKILL.md`
- **THEN** 无任何 SKILL.md 文件包含子串 `{{SEMANTIC_MODEL_DIGEST}}`
- **AND** 该占位符仅出现在 `classpath:/agents/AGENTS.md` 中

### Requirement: ConnectionDeletion 联动级联清理

`ConnectionDeletionService.delete(connectionId, force)` MUST 在 `force=false` 时检测 Semantic Model 资源；在 `force=true` 时把目录移到 trash。`DeleteOutcome.BlockedByResources` MUST 新增 `verifiedQueries` 字段（int），向后兼容默认 0。

#### Scenario: force=false 时 VQ 存在阻塞

- **GIVEN** connection `c1` 下存在 5 条 verified queries
- **WHEN** `delete("c1", force=false)` 被调用
- **THEN** 返回 `DeleteOutcome.BlockedByResources` 实例
- **AND** `verifiedQueries == 5`
- **AND** `~/.data-talk/semantic/c1/` 目录保持不变

#### Scenario: force=true 时整目录移到 _trash

- **GIVEN** connection `c1` 下存在 `orders.model.yaml`、`verified_queries.jsonl`、`_user_profile.json`
- **WHEN** `delete("c1", force=true)` 被调用
- **THEN** `~/.data-talk/semantic/c1/` 不再存在
- **AND** `~/.data-talk/_trash/semantic/<ts>-c1/` 存在，且包含上述 3 个文件
- **AND** 返回 `DeleteOutcome.Ok`

#### Scenario: trash 中目录 30 天后被物理删除

- **GIVEN** `_trash/semantic/<ts>-c1/` 存在，`<ts>` 距今超过 30 天
- **WHEN** `HousekeepingScheduler.cleanupSemanticTrash()` 周期任务运行
- **THEN** 该目录被物理删除
- **AND** 不影响 `_trash/semantic/` 下时间戳小于 30 天的其他目录

### Requirement: HousekeepingScheduler patches compaction

`HousekeepingScheduler` MUST 新增 `compactPatches()` 周期任务，每日运行一次。对每个 `<domain>.model.yaml.patches.jsonl`，行数 > 200 时 MUST 把所有 op 合并到对应 yaml + verified_queries.jsonl，并清空 patches 文件。

#### Scenario: 触发 compaction

- **GIVEN** `orders.model.yaml.patches.jsonl` 含 250 行（含 200 个 `INC_HIT_COUNT` 与 50 个 `ADD_LITERAL_MAPPING`）
- **WHEN** `compactPatches()` 运行
- **THEN** `orders.model.yaml` 的 `literalMappings` 字段被合并更新，`last_modified` 时间戳更新
- **AND** `verified_queries.jsonl` 对应 vq 的 hit_count 被更新到聚合值
- **AND** `orders.model.yaml.patches.jsonl` 被截断为 0 字节
- **AND** 整个操作在临时文件 + 原子 rename 下完成，中间状态不可见

#### Scenario: 不到阈值不动

- **GIVEN** `users.model.yaml.patches.jsonl` 含 50 行
- **WHEN** `compactPatches()` 运行
- **THEN** 该文件不被修改

### Requirement: Stage `semantic_model_editor` tab

前端 MUST 注册新 tab type `semantic_model_editor`（`scope: 'workspace'`，与 connection 绑定）。该 tab MUST 提供三个视图：Pending 列表、Diff 视图、VQ 标记。

#### Scenario: tab 类型注册

- **WHEN** 扫描 `client/src/features/stage/registry/tab-type-registry.ts`
- **THEN** 包含 `semantic_model_editor` tab type 注册
- **AND** 该 type 的 `scope` 字段等于 `'workspace'`

#### Scenario: Pending 列表显示当前 connection 的待审项

- **GIVEN** connection `c1` 下 `pending/` 含 `after_sales.model.yaml`、`returns.model.yaml`
- **WHEN** 用户打开 `semantic_model_editor` tab（绑定 connection `c1`）
- **THEN** Pending 列表显示 2 项，每项可点击进入 Diff 视图

#### Scenario: Accept pending 后文件移到正式路径

- **GIVEN** Pending 中有 `after_sales.model.yaml`
- **WHEN** 用户点击 Accept
- **THEN** `pending/after_sales.model.yaml` 被移到 `~/.data-talk/semantic/<connectionId>/after_sales.model.yaml`
- **AND** 该 yaml 的 `version` 字段被 bump 到下一个整数（首次 accept 时为 1）
- **AND** 触发 `_index.json` 更新

#### Scenario: VQ 标记入口

- **GIVEN** chat 历史中存在一条 AI 生成的 SQL 回答
- **WHEN** 用户点击该回答下方的"标记为正确"按钮
- **THEN** 触发 `recordVerifiedQuery(question, sql, modelRef)` mutation
- **AND** 后端 `verified_queries.jsonl` 追加一行 vq
- **AND** UI 显示 toast "已沉淀为 verified query"

### Requirement: Builtin 模板 + AI 推断初稿双轨

系统 MUST 在 classpath `semantic-models/builtin/_templates/` 提供 `ecommerce` 与 `saas` 两个模板。`semantic_model_editor` tab 在 connection 下无任何 domain 时 MUST 显示两个入口：从模板创建 / 让 AI 推断初稿。

#### Scenario: 从模板创建

- **GIVEN** connection `c1` 下 `~/.data-talk/semantic/c1/` 为空
- **WHEN** 用户点击"从模板创建" → 选择 `ecommerce`
- **THEN** 后端复制 `classpath:/semantic-models/builtin/_templates/ecommerce.model.template.yaml` 到 `~/.data-talk/semantic/c1/ecommerce.model.yaml`
- **AND** 该 yaml 的 `authored_by` 字段为 `user_authored`
- **AND** `last_modified` 为当前时间

#### Scenario: AI 推断初稿写入 pending

- **GIVEN** connection `c1` 下 `~/.data-talk/semantic/c1/` 为空
- **WHEN** 用户点击"让 AI 推断初稿"
- **THEN** 后端通过 OpenCode 调用 AI，AI 用 `datatalk_read_schema` 扫表 + 启发推断
- **AND** AI 通过 `datatalk_semantic_propose_change` 把初稿写入 `~/.data-talk/semantic/c1/pending/<推断 domain>.model.yaml`
- **AND** 该 yaml 的 `authored_by` 字段为 `ai_inferred`
- **AND** Pending 列表中显示该项

### Requirement: 多语言 yaml 内置 label_zh / label_en

yaml 中 `dimensions[]` / `measures[]` / `metrics[]` 各项 MUST 含 `label_zh` 与 `label_en` 两个非空字符串字段。`SemanticModelDigester` 在注入 prompt 时 MUST 同时输出 zh 与 en 标签（以 `/` 分隔）。

#### Scenario: 双语标签必填

- **GIVEN** yaml 中某 measure 缺 `label_en` 字段
- **WHEN** `loadDomain(...)` 被调用
- **THEN** 抛出 `SemanticModelValidationException`，message 含字段路径与缺失字段名

#### Scenario: digest 输出双语

- **GIVEN** yaml 中 `measures.gmv = {label_zh: "销售额", label_en: "GMV"}`
- **WHEN** `SemanticModelDigester.digest(connectionId)` 被调用
- **THEN** 输出含子串 `销售额 / GMV → measures.gmv`

### Requirement: skill-creator 本地化版

系统 MUST 在 `classpath:/skills/skill-creator/SKILL.md` 维护一份本地化版 `skill-creator` skill，frontmatter 含 `name: skill-creator` 与中英双语 description（≥ 80 字符，符合 `agent-skill-routing` spec 现有契约），含 `forked_from` 字段指向上游 commit SHA。

#### Scenario: frontmatter 合规

- **GIVEN** `classpath:/skills/skill-creator/SKILL.md`
- **WHEN** 解析 YAML frontmatter
- **THEN** `name` 字段存在且等于 `skill-creator`
- **AND** `description` 长度 ∈ [80, 600]，同时含中文与英文触发词
- **AND** `forked_from` 字段非空，格式形如 `anthropics/skills@<sha>`

#### Scenario: 正文不再引用 Write 工具

- **WHEN** 在 `classpath:/skills/skill-creator/SKILL.md` 正文中搜索字符串 `Write` 工具引用
- **THEN** 所有指向"创建 SKILL.md 文件"的操作 MUST 引导调用 `datatalk_skill_create` Action
- **AND** 无任何指引让 AI 直接用 Write 工具

#### Scenario: SkillResourceSyncer 注册一致

- **WHEN** 在测试运行时反射或文本扫描 `OpenCodeGatewayBeans` 中的 `syncSkill` 调用
- **THEN** 调用参数集合 ⊇ `{ skill-creator, semantic-model-usage }`
- **AND** classpath `skills/skill-creator/` 与 `skills/semantic-model-usage/` 均存在
