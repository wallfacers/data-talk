## Context

DataTalk 已有的 agent 体系（参见 `openspec/specs/agent-skill-routing/spec.md`）由两部分组成：(1) `classpath:/agents/AGENTS.md` 350 行骨架，包含 Trigger Gate 与 Skill Index；(2) `classpath:/skills/<name>/SKILL.md` 13 个独立 skill 文件，由 `SkillResourceSyncer` 启动期同步到 OpenCode CWD。`AgentPromptBuilder.render(rawAgentsMd)` 负责替换 `{{STAGE_TAB_DIGEST}}` 与 `{{ACTIVE_SESSION_DIR}}` 两个占位符。

`ConnectionDeletionService` 已实现二阶段删除（`force=false` 阻塞 + `force=true` 级联），通过 `BlockedByResources` 返回阻塞统计；`~/.data-talk/_trash/` 已是用户态软删除归档目录；`HousekeepingScheduler` 已实施周期清理任务。

`OpenCodeGatewayBeans` 当前注册 13 个 skill（含 `bezel`、`data-ingestion`，后者将被 `script-runner` change 移除）。`StageTab` 已支持多种 tab type（`query_editor`、`er_inspector`、`er_designer`、`dashboard` 等），新 tab type 注册需修改 `tab-type-registry.ts`。

Hermes Agent 的"四层记忆 + Semantic Model"架构在 DataTalk 现有体系上的映射：L1 热记忆 ↔ AGENTS.md 占位符注入；L2 冷记忆 ↔ 后端 SQLite metadata（本变更不动）；L3 程序记忆 ↔ Semantic Model（本变更核心）；L4 用户画像 ↔ preference 模块（本变更只做静态字段）。

## Goals / Non-Goals

**Goals:**

- 为每个 connection 提供一份本地 YAML Semantic Model，承载业务实体 / 维度 / 度量 / 指标 / 字面值映射 / verified queries
- 让 OpenCode AI 能通过新 Action 查询、追加、提议 Semantic Model 内容，并在用户确认后沉淀
- AGENTS.md 渲染期注入 ≤2000 字符的 Semantic Model 摘要，让 AI 不必每次都从 schema 开始探索
- 删除 connection 时，Semantic Model 数据级联清理，与现有二阶段删除契约一致
- 用户能在 `semantic_model_editor` tab 中审核 AI 提议、编辑 yaml、标记 verified query
- 首发附带 2 个 builtin 模板，并支持 AI 在首次连接到陌生 schema 时推断初稿
- 支持 OpenCode AI 通过 `skill-creator`（本地化版） + `datatalk_skill_create` Action 自我编写新业务域 skill（实际是 Semantic Model yaml）

**Non-Goals:**

- 不实现 L2 冷记忆（session 历史检索）—— 留给后续 change
- 不实现向量检索 —— Verified Query Layer 2 靠 LLM 候选选择
- 不实现跨 connection 共享 model —— `shared/` 路径预留但不解析引用
- 不实现 yaml 结构性变更的自动合并 —— AI 只能追加，结构改必须走 `pending/` + 用户审
- 不实现跨设备同步 —— 单机本地优先
- 不替换或修改现有 13 个 skill 的内容（与 `script-runner` change 解耦）

## Decisions

### D1: per-connection scope，目录布局对齐 ingestion artifacts

**决策**：每个 connection 一个目录 `~/.data-talk/semantic/<connectionId>/`，与现有 `~/.data-talk/ingestion/<jobId>/` 风格一致。

**理由**：
- DataTalk 是多 connection 产品，不同 connection 的 schema 完全不同，全局 Semantic Model 无意义。
- 与 `_trash` 软删除目录同根，复用 `ConnectionDeletionService` 的清理路径。
- per-(connection, database) 粒度更精细但管理复杂度爆炸——把 database 维度作为 yaml 内 `physical.database` 字段处理，而非目录层级。

**目录结构：**

```
~/.data-talk/semantic/<connectionId>/
├── _index.json                          # 文件清单 + 元信息 + 最后扫描时间
├── _user_profile.json                   # L4 静态字段：default_time_range / limit / preferred_tables
├── orders.model.yaml                    # 业务域 yaml（domain 名 = 文件名前缀）
├── orders.model.yaml.patches.jsonl      # 增量补丁（仅追加，启动期 merge）
├── users.model.yaml
├── verified_queries.jsonl               # 跨域 VQ 池，单行 JSON，hit_count 索引
├── pending/                             # AI 提议但未审核的 yaml
│   └── after_sales.model.yaml
└── shared/                              # 预留，本变更不解析
```

### D2: DSL 采用 dbt MetricFlow 子集 + DataTalk 扩展

**决策**：核心四要素（entity / dimension / measure / metric）按 dbt MetricFlow 子集风格，扩展两块 DataTalk 特有字段（`literal_mappings` 与 `verified_query_refs`）。

**理由**：
- dbt 语义层是开放标准，LLM 训练语料中出现频繁，AI 理解最好。
- 区分 measure（原子量）与 metric（派生量）便于复用，避免 SQL 复制到处都是。
- 区分 entity 与 physical table 让一个表能承载多业务实体（例如 `orders` 表既是 `order` 也是 `order_item` 的载体），物理与业务解耦。

**最小 yaml schema：**

```yaml
name: <domain-name>
version: <int>
description: <string>
last_modified: <iso8601>
authored_by: ai_inferred | user_authored | hybrid

entities:
  - name: <entity-name>
    type: primary | secondary
    physical:
      database: <db>
      schema: <schema>
      table: <table>
    primary_key: [<col>, ...]
    foreign_keys:                    # 可选
      - column: <col>
        ref: <entity>.<col>
    description: <string>

dimensions:
  - name: <dim-name>
    entity: <entity-name>
    expr: <sql-expression>           # 通常是列名，也可以是 CASE
    type: categorical | time | numeric
    time_granularity: day|week|month # type=time 时必填
    label_zh: <string>
    label_en: <string>

measures:
  - name: <measure-name>
    entity: <entity-name>
    agg: sum | count | count_distinct | avg | max | min
    expr: <sql-expression>
    filter: <sql-where-fragment>     # 可选，度量自带过滤
    label_zh: <string>
    label_en: <string>
    description: <string>

metrics:
  - name: <metric-name>
    type: ratio | derived | cumulative
    # ratio 类型必填
    numerator: <measure-name>
    denominator: <measure-name>
    # derived 类型必填
    base: <measure-name|metric-name>
    time_offset: <duration>          # e.g. "1y", "1mo"
    # 通用
    label_zh: <string>
    label_en: <string>

literal_mappings:
  <dimension-name>:
    <natural-language-value>: <db-value>
  # 例：
  order_status:
    已完成: COMPLETED
    已退款: REFUNDED

verified_query_refs:
  - id: <vq-id>                      # 真实内容在 verified_queries.jsonl
```

JSON Schema 完整定义放在 `server/data-talk-application/src/main/resources/semantic/model-schema.json`，加载器在反序列化时校验。

### D3: Patches 仅允许增量追加，结构改必须走 pending

**决策**：`<domain>.model.yaml.patches.jsonl` 单行 JSON，只允许三类 op：

```json
{"op":"ADD_VERIFIED_QUERY","payload":{...vq object...},"by":"ai|user","at":"iso8601"}
{"op":"INC_HIT_COUNT","vq_id":"vq_001","delta":1,"at":"iso8601"}
{"op":"ADD_LITERAL_MAPPING","dimension":"order_status","map":{"待发货":"PENDING_SHIP"},"by":"ai|user","at":"iso8601"}
```

任何**结构性变更**（改 measure 定义、加 entity、改 entity.physical）必须走：
1. AI 调用 `datatalk_semantic_propose_change`，后端写到 `pending/<domain>.model.yaml`
2. UI 推送 `SemanticPendingCreated` 事件
3. 用户在 `semantic_model_editor` tab 中审核 diff，accept 后文件移到正式路径并 bump `version`，reject 后 pending 文件被移到 `_trash`

**理由**：
- 增量追加无字段级冲突，零审核成本，让 AI 能自然沉淀 VQ 和字面值映射。
- 结构改对业务影响大，必须人工审，避免 AI 错误推断污染语义模型。
- patches.jsonl 形态便于审计与回滚（删行即可）。

**Compaction**：`HousekeepingScheduler` 每天扫描，行数 > 200 时把 patches 合并回 yaml 并清空。

### D4: Verified Query 四层路由（不引入向量检索）

**决策**：

| Layer | 命中条件 | LLM 调用 | 实现位置 |
|---|---|---|---|
| L0 精确字面 | question 严格相等 | 0 次 | `VerifiedQueryRouter.findExact()` |
| L1 规范化 | trim/大小写/全半角/同义词替换后相等 | 0 次 | `VerifiedQueryRouter.findNormalized()` |
| L2 候选选择 | Top-K（按 hit_count）注入 prompt，LLM 决定复用/修改/新写 | 1 次 | `SemanticModelDigester.includeTopKVQ()` + AI prompt |
| L3 全量生成 | 兜底，走 schema 探索 + measure 查找 | 多次 | 现有 sql-execution skill 流程 |

**理由**：
- 本地嵌入模型（onnx / sentence-transformers）增加依赖、增加首次启动成本，收益有限。
- L0/L1 已经能命中"用户用同样话再问一次"的高频场景。
- L2 把候选直接喂给 LLM，省的是"重新探索 schema、重新查 measure 定义"那部分多轮工具调用——这才是大头。
- 真实分布：L0 ~30%、L1 ~10%、L2 ~30%、L3 ~30%（粗估，需观测验证）。

### D5: AGENTS.md 占位符注入 ≤2000 字符 Semantic Model 摘要

**决策**：`AgentPromptBuilder.render()` 新增第三个占位符 `{{SEMANTIC_MODEL_DIGEST}}`，由 `SemanticModelDigester` 渲染：

```
## Semantic Model Snapshot

Current connection: <name> / database: <db-or-multi>
Domains loaded: <comma-separated-list>

Top measures (with synonyms):
- <label_zh> / <label_en> / <synonym> → measures.<name>
...

Common literal mappings:
- <natural> / <natural-en> → <db-value>
...

Top verified queries (use datatalk_verified_query_find for full list):
- "<question>" (hits: N)
...

User defaults: limit=<n>, time_range="<phrase>"
```

**预算规则**：硬上限 2000 字符（与 STAGE_TAB_DIGEST 的 1500 同量级）。超出时按优先级裁剪：先裁 Top-K VQ 到 K=5，再裁 measures 到 Top-N=8，再裁 literal mappings 到 Top-M=5。

**无 connection 绑定**时渲染 `<no semantic model — please bind a connection>`，对齐现有 `<no active session>` 风格。

### D6: ConnectionDeletion 联动 + 阻塞策略

**决策**：

```java
// ConnectionDeletionService.delete(connectionId, force)
if (!force) {
    var counts = fileArtifacts.countResourcesByConnection(connectionId, childSessionIds);
    var vqCount = semanticModelRepository.countVerifiedQueriesByConnection(connectionId);
    if (counts.sessions() > 0 || ... || vqCount > 0) {
        return new DeleteOutcome.BlockedByResources(connectionId, counts.withSemantic(vqCount));
    }
    connections.deleteById(connectionId);
    return new DeleteOutcome.Ok();
}
// force = true
fileArtifacts.detachArchivedFromConnection(...);
semanticModelRepository.moveToTrash(connectionId, clock.millis()); // 新增
// ... 现有 cascade 逻辑
```

`BlockedByResources` record 新增 `verifiedQueries` 字段（int），前端 UI 显示"该连接有 N 条已确认查询，删除将同时清除业务语义模型"。

`semanticModelRepository.moveToTrash` 把 `~/.data-talk/semantic/<connectionId>/` 整体改名为 `~/.data-talk/_trash/semantic/<ts>-<connectionId>/`，30 天后由 `HousekeepingScheduler` 物理删除（复用现有清理任务）。

### D7: skill-creator 本地化 + datatalk_skill_create

**决策**：
- 把 `anthropics/skills` 的 `skill-creator/SKILL.md` 取下来放到 `server/data-talk-adapter/src/main/resources/skills/skill-creator/SKILL.md`。
- 本地化改造：
  - 把所有"使用 Write 工具创建 SKILL.md"的指引替换为"调用 `datatalk_skill_create` Action"
  - 输出 schema 限定为 Semantic Model yaml DSL（D2）
  - 示例从通用 skill 改为业务域 yaml 模板
  - 保留 frontmatter `name: skill-creator` + 中英双语 description（符合 `agent-skill-routing` 现有契约）
- 新增 Action `datatalk_skill_create(name: string, yaml_text: string)`，把 `yaml_text` 校验通过 JSON Schema 后写到 `~/.data-talk/semantic/<currentConnectionId>/pending/<name>.model.yaml`。
- 在 `OpenCodeGatewayBeans` 调用 `skillSyncer.syncSkill("skill-creator", cwd)`。

**理由**：
- 直接装 `anthropics/skills` 原版会指引 AI 用 Write 工具，但 OpenCode 端没有 Write——必须改路径。
- 通过 Action 代理写入保留了用户审权（写 `pending/` 而非正式路径）。
- 复用现有 skill 注册体系，无需新机制。

### D8: Stage tab `semantic_model_editor`

**决策**：新 tab type `semantic_model_editor`，参照 `er_designer` 实现风格：
- 注册到 `client/src/features/stage/registry/tab-type-registry.ts`，`scope: 'workspace'`（与 connection 绑定的 workspace tab，不绑特定 session）
- 提供三种视图：
  1. **Pending 列表**：列出当前 connection 下 `pending/` 中所有 AI 提议，点击进入 diff 视图
  2. **Diff 视图**：左旧右新（旧可能不存在），按钮：`Accept` / `Reject` / `Edit before accept`
  3. **VQ 标记**：列出 chat 历史中的 AI SQL 回答，用户标记"正确"沉淀为 verified query
- Zustand store `useSemanticModelStore`：`{pendingByConnection, vqDraftsBySession}`
- Tauri / 后端 API：`GET /api/semantic/pending`、`POST /api/semantic/pending/:name/accept`、`POST /api/semantic/verified-query`

### D9: Builtin templates + AI 推断初稿 双轨

**决策**：
- 首发附带两个模板：
  - `ecommerce.model.template.yaml`：orders / users / products 三域，覆盖电商通用指标（GMV / AOV / 复购率 / 留存）
  - `saas.model.template.yaml`：subscriptions / users / events 三域，覆盖 SaaS 通用指标（MRR / ARR / churn / DAU/MAU）
- 模板存于 classpath `server/data-talk-adapter/src/main/resources/semantic-models/builtin/_templates/`，首次访问 connection 时**不自动复制**，仅在用户选择"从模板创建"时复制到 `~/.data-talk/semantic/<connectionId>/`。
- **AI 推断初稿**：当 connection 下 `~/.data-talk/semantic/<connectionId>/` 为空时，UI 在 `semantic_model_editor` tab 提供"让 AI 推断初稿"按钮。点击后 AI 通过 `datatalk_read_schema` 扫表，按 schema 形状 + 列名启发推断初稿写到 `pending/`，用户审。
- 模板与 AI 推断**互斥但可后续合并**——用户选模板后 AI 也能继续推断遗漏域并提议到 `pending/`。

### D10: 多语言 yaml 内置 label_zh / label_en

**决策**：yaml 内每个 dimension / measure / metric 都有 `label_zh` 与 `label_en` 字段，由 `SemanticModelDigester` 在注入 prompt 时同时输出两种语言（用 `/` 分隔）。

**理由**：
- per-domain yaml 是 AI 与用户共同编辑的，本地化字段随 yaml 走最直接，AI 不必跨文件解析 i18n key。
- 不污染全局 `messages.properties`（那是 UI 字串，Semantic Model 是业务术语，两者维护节奏不同）。
- 未来如果需要更多语言，yaml 加 `label_<locale>` 字段即可平滑扩展。

## Risks / Trade-offs

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| AI 写错 yaml 结构污染正式文件 | 中 | 中 | `pending/` 隔离 + JSON Schema 校验 + 用户审 |
| `.patches.jsonl` 行数失控 | 中 | 低 | `HousekeepingScheduler` compaction，阈值 200 |
| Schema 漂移（用户改表结构）→ yaml 过期 | 高 | 中 | 启动期校验 `physical.table` 是否存在；不存在时标记 `stale=true`，digest 中过滤 |
| VQ 在 schema 变更后失效 | 高 | 中 | 同上 stale 机制；失效 VQ 从 prompt digest 中过滤但保留文件以备恢复 |
| AGENTS.md digest 超 2000 字符截断错位 | 低 | 低 | `SemanticModelDigester` 单元测试覆盖裁剪优先级 |
| 用户态目录跨设备同步缺失 | 低 | 低 | 文档明示单机优先，留作后续 cloud-sync change |
| `BlockedByResources` 新字段 break 前端 | 低 | 中 | 前端先放宽解析（缺字段默认 0），再更新 UI |
| skill-creator 本地化偏离上游导致维护成本 | 中 | 低 | 在 SKILL.md 内 frontmatter 注明 `forked_from: anthropics/skills@<sha>`，便于追源 |
| 删除 connection 误删用户精心维护的 model | 中 | 高 | 默认 `force=false` 阻塞 + verified queries 计数显示 + `_trash` 30 天保留期可恢复 |
| AI 提议过多导致 pending 列表无限增长 | 中 | 低 | pending 中条目 30 天未审自动归档到 `_trash` |

## Migration Plan

- **数据迁移**：本变更不修改任何现有数据库表，只新增 `~/.data-talk/semantic/` 用户态目录。无 Flyway migration 必需。
- **现有用户首启动**：检测到 `~/.data-talk/semantic/` 不存在时创建空目录；对每个已存在 connection 创建空 `<connectionId>/_index.json`，用户后续可选模板/AI 推断。
- **回滚**：若需回滚本变更，删除 `~/.data-talk/semantic/` 整个目录即可（不影响其他数据）；代码层面回退 `AgentPromptBuilder` 的第三占位符渲染分支即可。

## Open Questions

- `_user_profile.json` 的字段集需要在实施期细化（D2 已定结构但未列字段全集）。
- VQ 路由 L1 的"同义词词表"由谁维护？建议初版硬编码常见同义词（销售额↔GMV↔revenue），后续接入用户在 chat 中纠正的反馈。
- `semantic_model_editor` tab 是否应该支持 yaml 全文编辑（带 Monaco + JSON Schema 校验）还是只暴露结构化表单？倾向**双模**：默认表单，"高级"按钮切 yaml。
- AI 推断初稿调用哪个 LLM？建议使用当前 OpenCode 配置的同一个 model，不引入新 provider。
- 是否需要为 Semantic Model 增加 `version_history/` 追踪 yaml 历次修改？建议**不做**，依赖 git（用户态目录虽不在仓库，但 `_trash` + 30 天保留已足够）。
