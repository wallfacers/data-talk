## 1. 契约：YAML DSL + JSON Schema

- [ ] 1.1 在 `server/data-talk-application/src/main/resources/semantic/model-schema.json` 写 Semantic Model yaml 的完整 JSON Schema（entity / dimension / measure / metric / literal_mappings / verified_query_refs，详 design.md D2）
- [ ] 1.2 在 `server/data-talk-application/src/main/resources/semantic/patch-schema.json` 写 patches.jsonl 的单行 JSON Schema（3 种 op：`ADD_VERIFIED_QUERY` / `INC_HIT_COUNT` / `ADD_LITERAL_MAPPING`）
- [ ] 1.3 在 `server/data-talk-application/src/main/resources/semantic/verified-query-schema.json` 写 verified_queries.jsonl 单行 JSON Schema
- [ ] 1.4 在 `server/data-talk-application/src/main/resources/semantic/user-profile-schema.json` 写 `_user_profile.json` 的 JSON Schema（default_time_range / default_limit / preferred_tables / frequent_filters / question_templates）
- [ ] 1.5 **验证**：用 `everit-json-schema` 或 `networknt/json-schema-validator` 对 4 份 schema 自校验合法

## 2. Domain 层：record + sealed interface

- [ ] 2.1 创建 `domain/semantic/SemanticModel.java`（record：name / version / description / entities / dimensions / measures / metrics / literalMappings / verifiedQueryRefs / lastModified / authoredBy）
- [ ] 2.2 创建 `domain/semantic/Entity.java`、`Dimension.java`、`Measure.java`、`Metric.java`、`LiteralMapping.java` records
- [ ] 2.3 创建 `domain/semantic/VerifiedQuery.java` record（id / question / sql / modelRef / hitCount / lastHit / confirmedBy / confirmedAt / stale）
- [ ] 2.4 创建 `domain/semantic/PatchOp.java` sealed interface + 3 个 record（`AddVerifiedQuery` / `IncHitCount` / `AddLiteralMapping`）
- [ ] 2.5 创建 `domain/semantic/UserProfile.java` record
- [ ] 2.6 修改 `DtEvent.java`：新增 `SemanticPendingCreated` 和 `VerifiedQueryRecorded` 两个 record；同步更新 `typeName()` exhaustive switch
- [ ] 2.7 **验证**：`mvn install -pl data-talk-domain -am -DskipTests` 通过

## 3. Application 层：repository / loader / digester / router

- [ ] 3.1 创建 `application/semantic/SemanticModelRepository.java` 接口：`listDomains(connectionId)` / `loadDomain(connectionId, name)` / `saveDomain(...)` / `appendPatch(...)` / `listVerifiedQueries(connectionId, topK)` / `recordVerifiedQuery(...)` / `incHit(vqId)` / `listPending(connectionId)` / `acceptPending(connectionId, name)` / `rejectPending(connectionId, name)` / `countVerifiedQueriesByConnection(connectionId)` / `moveToTrash(connectionId, ts)` / `deleteAllByConnection(connectionId)`
- [ ] 3.2 创建 `application/semantic/SemanticModelLoader.java`：加载 yaml + 合并 patches（按 D3 op 类型分派），返回 in-memory `SemanticModel` 视图；校验 JSON Schema
- [ ] 3.3 创建 `application/semantic/PatchApplier.java`：纯函数式应用 `PatchOp` 列表到 `SemanticModel` 上
- [ ] 3.4 创建 `application/semantic/VerifiedQueryRouter.java`：实现 L0/L1 命中（L2 由 prompt digest 完成，无需独立类）；`findExact(question)` / `findNormalized(question)` / `topKByHits(K)`
- [ ] 3.5 创建 `application/semantic/SemanticModelDigester.java`：渲染 ≤2000 字符摘要（详 design.md D5），含裁剪优先级；为 `AgentPromptBuilder` 提供 `digest(connectionId): String`
- [ ] 3.6 创建 `application/semantic/StaleChecker.java`：启动期 + 周期任务校验 yaml 中 `physical.table` 是否存在于实际 schema，过期项标记 `stale=true`
- [ ] 3.7 单元测试：`SemanticModelLoaderTest`（patches 合并）、`VerifiedQueryRouterTest`（L0/L1 命中）、`SemanticModelDigesterTest`（裁剪优先级、字符上限、无连接渲染 sentinel）
- [ ] 3.8 **验证**：`mvn install -pl data-talk-application -am -DskipTests` 通过 + 3 个测试类全绿

## 4. Infrastructure 层：FsSemanticModelRepository

- [ ] 4.1 创建 `infra/semantic/FsSemanticModelRepository.java` 实现接口：YAML 用 `snakeyaml-engine`，JSONL 用 `Jackson ObjectMapper.readerFor(...).readValues(...)` 流式读
- [ ] 4.2 实现 `moveToTrash`：把 `~/.data-talk/semantic/<connectionId>/` 整体改名为 `~/.data-talk/_trash/semantic/<ts>-<connectionId>/`
- [ ] 4.3 实现 `deleteAllByConnection`：force delete trash + active 双目录（已确认场景）
- [ ] 4.4 集成测试：`FsSemanticModelRepositoryIT` 用 `@TempDir` 隔离，覆盖完整 CRUD + patches 追加 + moveToTrash
- [ ] 4.5 **验证**：`mvn install -pl data-talk-infrastructure -am -DskipTests` 通过

## 5. Adapter 层：6 个 Action handler + Controller

- [ ] 5.1 创建 `adapter/actions/semantic/SemanticLookupActionHandler.java`：`datatalk_semantic_lookup(query: string, kind?: 'entity'|'measure'|'metric'|'dimension')` 返回匹配项列表
- [ ] 5.2 创建 `adapter/actions/semantic/VerifiedQueryFindActionHandler.java`：`datatalk_verified_query_find(question: string, topK?: int)` 返回候选 SQL 列表（L0/L1 命中走前缀，未命中给 L2 候选）
- [ ] 5.3 创建 `adapter/actions/semantic/VerifiedQueryRecordActionHandler.java`：`datatalk_verified_query_record(question, sql, modelRef)` 写入 jsonl + 触发 `VerifiedQueryRecorded` 事件
- [ ] 5.4 创建 `adapter/actions/semantic/SemanticProposeChangeActionHandler.java`：`datatalk_semantic_propose_change(domain, yaml_text, reason)` 校验 JSON Schema 后写到 `pending/` + 触发 `SemanticPendingCreated`
- [ ] 5.5 创建 `adapter/actions/semantic/LiteralMappingAddActionHandler.java`：`datatalk_literal_mapping_add(domain, dimension, natural, dbValue)` 追加 patches.jsonl
- [ ] 5.6 创建 `adapter/actions/semantic/SkillCreateActionHandler.java`：`datatalk_skill_create(name, yaml_text)` —— 实质是 `propose_change` 的 alias，但语义"AI 自我创建新业务域 skill"，对应 `skill-creator` 的输出
- [ ] 5.7 创建 `adapter/controller/SemanticController.java`：暴露前端 REST API（GET pending list / accept / reject / record VQ / list VQ）
- [ ] 5.8 集成测试：每个 ActionHandler 一个 `@SpringBootTest` IT，覆盖正例 + JSON Schema 失败 + 无 active connection 失败
- [ ] 5.9 **验证**：`mvn install -pl data-talk-adapter -am -DskipTests` 通过

## 6. AgentPromptBuilder 注入 `{{SEMANTIC_MODEL_DIGEST}}`

- [ ] 6.1 修改 `application/stage/AgentPromptBuilder.java`：新增第三个占位符替换分支，依赖注入 `SemanticModelDigester`；保持现有两个占位符与异常签名不变
- [ ] 6.2 修改 `classpath:/agents/AGENTS.md`：插入 `{{SEMANTIC_MODEL_DIGEST}}` 字面占位符（位置在 Open Tabs Snapshot 之后），保持 350 行体积上限
- [ ] 6.3 修改 `adapter/agents/AgentPromptCustomizer.java`：把 `SemanticModelDigester` 接入 wiring，保持加载路径与异常签名不变
- [ ] 6.4 单元测试：`AgentPromptBuilderTest` 新增 case：有/无 connection 场景下三个占位符全部正确替换
- [ ] 6.5 **验证**：`mvn install -pl data-talk-application -am -DskipTests` + `mvn install -pl data-talk-adapter -am -DskipTests`

## 7. ConnectionDeletion 联动

- [ ] 7.1 修改 `application/session/DeleteOutcome.java`：`BlockedByResources` record 加 `verifiedQueries` 字段（int），向后兼容默认 0
- [ ] 7.2 修改 `application/connection/ConnectionDeletionService.delete(...)`：注入 `SemanticModelRepository`；`force=false` 检测 vqCount > 0 时阻塞；`force=true` 调用 `moveToTrash`
- [ ] 7.3 更新 `ConnectionDeletionServiceTest`：新增 case：有 VQ 时 force=false 阻塞、force=true 移到 trash 且 yaml 文件可在 trash 目录中找到
- [ ] 7.4 **验证**：`ConnectionDeletionServiceTest` 全绿

## 8. HousekeepingScheduler 扩展

- [ ] 8.1 修改 `application/housekeeping/HousekeepingScheduler.java`：新增 `compactPatches()` 周期任务（每日 1 次），扫描 `~/.data-talk/semantic/*/` 下 `.patches.jsonl`，行数 > 200 时调用 `SemanticModelLoader.compact(...)`
- [ ] 8.2 新增 `cleanupExpiredPending()` 周期任务：`pending/` 中条目 30 天未审自动移到 `_trash`
- [ ] 8.3 新增 `cleanupSemanticTrash()` 周期任务：`_trash/semantic/<ts>-*` 30 天后物理删除
- [ ] 8.4 单元测试覆盖 3 个新周期任务
- [ ] 8.5 **验证**：`mvn install -pl data-talk-application -am -DskipTests`

## 9. AGENTS.md & skill 注册 & 新 skill 文件

- [ ] 9.1 把 `anthropics/skills` 仓库的 `skill-creator/SKILL.md` 取下（通过 raw.githubusercontent.com / WebFetch / 手工下载），保存原文备份到 `openspec/changes/semantic-model-foundation/upstream/skill-creator.original.md`，便于追源
- [ ] 9.2 本地化改造为 `server/data-talk-adapter/src/main/resources/skills/skill-creator/SKILL.md`：替换 Write 工具引用为 `datatalk_skill_create`、输出 schema 改为 Semantic Model yaml DSL（D2）、frontmatter 加 `forked_from: anthropics/skills@<commit-sha>` 与 中英双语 description
- [ ] 9.3 创建 `server/data-talk-adapter/src/main/resources/skills/semantic-model-usage/SKILL.md`：给 OpenCode AI 看的使用契约，描述 6 个 Action 的调用顺序、L0-L3 路由判断、何时触发 `propose_change`、何时建议 `verified_query_record`；frontmatter 含中英双语 description
- [ ] 9.4 修改 `classpath:/agents/AGENTS.md`：
  - 在 Trigger Gate 表新增一行：`user asks for a business metric / uses business term ("销售额" / "GMV" / 自然语言度量) | skill:semantic-model-usage`
  - 在 Skill Index 章节新增 `skill:semantic-model-usage` 与 `skill:skill-creator` 两行（带一句话描述）
  - 在合适位置插入 `{{SEMANTIC_MODEL_DIGEST}}` 占位符（与 task 6.2 协调）
  - 保持 350 行体积上限
- [ ] 9.5 修改 `adapter/config/OpenCodeGatewayBeans.java`：新增 `skillSyncer.syncSkill("skill-creator", opencodeCwd)` 与 `syncSkill("semantic-model-usage", opencodeCwd)`
- [ ] 9.6 **验证**：在 `mvn install -pl data-talk-adapter` 通过 + 启动后扫描 OpenCode CWD 确认 2 个新 skill 目录被同步

## 10. Stage tab：semantic_model_editor

- [ ] 10.1 修改 `client/src/features/stage/registry/tab-type-registry.ts`：注册 `semantic_model_editor` tab type，`scope: 'workspace'`，icon / label / loader
- [ ] 10.2 创建 `client/src/features/semantic-model/` 目录：
  - `api/semantic-api.ts`：调 `/api/semantic/*` REST 端点
  - `hooks/use-semantic-pending-query.ts` + `use-semantic-vq-query.ts`（TanStack Query）
  - `stores/use-semantic-model-store.ts`（Zustand）
  - `components/pending-list.tsx`、`diff-view.tsx`、`vq-marker.tsx`
  - `semantic-model-editor-tab.tsx`（主组件）
- [ ] 10.3 在 `chat` 模块的 SQL 回答组件加"标记为正确"按钮，触发 `recordVerifiedQuery(...)` mutation
- [ ] 10.4 vitest 覆盖：pending list 渲染、accept/reject、vq marker
- [ ] 10.5 **验证**：`npx tsc --noEmit` + `vitest run`

## 11. Builtin 模板 + AI 推断初稿

- [ ] 11.1 创建 `server/data-talk-adapter/src/main/resources/semantic-models/builtin/_templates/ecommerce.model.template.yaml`：orders / users / products 三域，含 GMV / AOV / 复购率 / 留存等典型 measures + metrics
- [ ] 11.2 创建 `server/data-talk-adapter/src/main/resources/semantic-models/builtin/_templates/saas.model.template.yaml`：subscriptions / users / events 三域，含 MRR / ARR / churn / DAU/MAU
- [ ] 11.3 创建 `server/data-talk-adapter/src/main/resources/semantic-models/builtin/_templates/README.md`：使用指引 + 字段约定 + 二次开发提示
- [ ] 11.4 后端 API `POST /api/semantic/from-template` 接收 `{templateName, connectionId}`，复制模板到 `~/.data-talk/semantic/<connectionId>/`
- [ ] 11.5 在 `semantic_model_editor` tab "空状态"下提供两个按钮：`从模板创建` / `让 AI 推断初稿`
- [ ] 11.6 后端 API `POST /api/semantic/infer` 触发 AI 推断流程：通过 OpenCode 调用，结果写入 `pending/`
- [ ] 11.7 vitest + IT 覆盖
- [ ] 11.8 **验证**：手动跑通：选 `ecommerce` 模板 → 文件出现在 `~/.data-talk/semantic/<cid>/`；点 `让 AI 推断` → pending 中出现 yaml

## 12. 全链路 E2E

- [ ] 12.1 Playwright `tests/e2e/semantic-model-create-from-template.spec.ts`：UI 选模板 → 后端文件落地 → AGENTS.md digest 反映
- [ ] 12.2 Playwright `tests/e2e/semantic-model-ai-propose-and-accept.spec.ts`：触发 AI 提议 → pending tab 显示 → accept → 文件移到正式路径
- [ ] 12.3 Playwright `tests/e2e/semantic-model-verified-query-flow.spec.ts`：用户标记 SQL 正确 → 沉淀 VQ → 下次相同问题命中 L0
- [ ] 12.4 Playwright `tests/e2e/semantic-model-connection-delete-cascade.spec.ts`：force=false 阻塞（含 VQ 计数）+ force=true 级联到 _trash
- [ ] 12.5 **验证**：4 个 E2E spec 在 `e2e` profile 下全绿；按 BUG 跟踪规范登记任何发现的偏差到 `docs/bugs/`

## 13. 收尾

- [ ] 13.1 `mvn clean verify` 后端全栈测试通过
- [ ] 13.2 `cd client && npx tsc --noEmit && vitest run` 前端零错误
- [ ] 13.3 `openspec validate semantic-model-foundation` 通过
- [ ] 13.4 更新 `CLAUDE.md` "Knowledge Base Navigation" 表新增一行 Semantic Model 入口
- [ ] 13.5 准备 `/opsx:archive` 前的 PR：分批提交（schema + domain | application + infra | adapter + action | client | skills + AGENTS.md | tasks-7-to-11 各一个 commit）
