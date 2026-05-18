## Context

DataTalk 通过 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`（精简骨架，≤350 行）+ 自动加载的 `skill:connection-management` 给 OpenCode AI 路由意图。两类与"数据源"相关的工具长得很像但语义完全不同：

| 工具 | 作用域 | 返回内容 |
|------|--------|----------|
| `datatalk_get_data_context` | 当前 session | `{connectionId, database, schema}` — 当前会话激活的数据源 |
| `datatalk_list_connections` | 全局 | 所有已保存连接列表，无激活标记 |

当前 `ListConnectionsAction` 的输出（`server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ListConnectionsAction.java:65`）只有 `connections: [...]`，每条记录字段 `id/name/kind/host/port/databaseName/...`，**没有任何字段指示"这条是当前 session 激活的连接"**。AGENTS.md 工具表（L52）描述也只有 "List saved data source connections"。

观察到的幻觉链：
1. 用户问"有哪些数据库"——预期是问当前连接下的库列表（应路由到 `datatalk_list_connection_targets`，或先用 `datatalk_get_data_context` 确认）。
2. AI 选了 `datatalk_list_connections`（描述里仅提"saved connections"，AI 误以为这是"查看可用数据源"）。
3. 响应里没有 active 标记，AI 推断"没选数据源"。
4. 用户质疑，AI 才补调 `datatalk_get_data_context`。

会话上下文实际存在于 `SessionDataContextRecord`（`server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionDataContextRecord.java`），通过 `SessionDataContextStore`（待确认服务名）按 sessionId 读取。`ActionContext` 已包含 `sessionId()`，可在 ActionHandler 中直接取用。

## Goals / Non-Goals

**Goals:**

1. **彻底消除 list_connections 的语义歧义**——通过工具描述、Intent Routing Gate、connection-management Playbook、响应载荷四层叠加，让 AI 即使选错入口也能在同一次响应内自我纠正。
2. **保持 AGENTS.md 体积合规**——新增内容控制在 ≤8 行（当前 162 行 + 8 ≈ 170 行，远低于 `agent-skill-routing` spec 规定的 350 行硬上限）。
3. **后向兼容**——`ListConnectionsAction` 的 outputSchema 仅做"添加可选字段"扩展，不改既有字段名/类型；既有调用者（目前仅 AI）零迁移。
4. **可测试**——单元测试断言响应字段存在性 + AGENTS.md 体积；集成测试断言会话激活态正确反映。

**Non-Goals:**

- 不重构 `ConnectionService` / `SessionDataContextStore` 的领域模型。
- 不改前端：`list_connections` 的前端 REST 入口（如有独立 controller）不在本变更范围；本变更只针对 AI action 路径。
- 不引入新数据源类型、不改 Flyway 迁移、不改 JDBC 行为。
- 不在 `docs/bugs/` 注册——这是 AI 行为偏差，按 OpenSpec 流程处理，不属于产品功能缺陷。

## Decisions

### Decision 1：四层防御，而不是单层修复

**选择**：同时改 (a) 工具描述、(b) Intent Routing Gate、(c) connection-management Playbook、(d) 响应载荷。

**理由**：单层修复都有漏洞——
- 只改描述：依赖 AI 完全消化工具描述，模型选错入口的概率虽然降低但不为零。
- 只加 Routing Gate：AI 可能跳过 Gate 直接选工具。
- 只改 Playbook：Playbook 在 skill 里，AI 没主动触发 skill 加载时看不到。
- 只加响应字段：AI 仍可能在第一次回答时直接基于"未看见 active 字段"误判。

用户明确要求"彻底解决"，叠加四层后任何一层生效都能阻止幻觉。

**备选**：用 `@JsonInclude` 把 `isActiveInSession: false` 字段也输出（强信号）。已采纳——所有 connection 项都带这个字段（true/false），而不是仅在 true 时出现，避免 AI 因"字段缺失"再次幻觉。

### Decision 2：响应载荷加 `activeSessionConnectionId` 顶层字段 + 每项 `isActiveInSession`

**选择**：双重冗余。

**理由**：
- `activeSessionConnectionId`（root 顶层，nullable）—— 让 AI 一眼看到"全局激活态"，即使列表为空也能传达"当前 session 选了 X"或"没选"。
- 每项 `isActiveInSession: boolean`（非可选，永远输出 true 或 false）—— 让 AI 遍历列表时不需要做 ID 字符串比对。

冗余的好处：AI 即使只看其中一个字段也能得到正确答案，且都明示而非缺省。

**备选**：只加顶层字段——拒绝，因为 AI 仍需做 ID 比对，提高出错概率。

### Decision 3：description i18n 文本统一收紧

**选择**：同时更新 `messages.properties` (en) 与 `messages_zh_CN.properties` (zh-CN)。

**理由**：description 通过 i18n key `action.list_connections.description` 暴露给 AI（取决于 OpenCode 拉取语言），两个 locale 都要同步。新文本明确"全局列表 / 不反映会话激活态 / 先用 get_data_context"。

### Decision 4：Intent Routing Gate 用"会话归属类问题"分类，而非穷举关键词

**选择**：在 `## Intent Routing Gate` 段落新增一句话："If the user asks which connection/database is currently in use, what data source is active, or how to know what's selected (any session-attribution question, in any language), call `datatalk_get_data_context` first before any other connection tool."

**理由**：穷举关键词（"有哪些数据库"/"现在连了什么"/...）容易遗漏多语言变体；用语义分类（session-attribution question）更通用。

**备选**：在 Trigger Gate 表格新增一行——拒绝，Trigger Gate 是"必须加载 skill"的硬路由；本场景不需要触发额外 skill，只需要规定调用顺序，写在 Intent Routing Gate 更合适。

### Decision 5：ActionHandler 注入 SessionDataContextStore，不改 ActionContext

**选择**：`ListConnectionsAction` 构造参数新增 `SessionDataContextStore`（或现有等价 application service），通过 Spring 自动注入。

**理由**：保持 `ActionContext` 的简洁（它只携带 `sessionId`、`requestId` 等基础信息）。`SessionDataContextStore` 在 application 层已存在（被 `GetDataContextAction` 用），复用即可，不引入新依赖方向。

**备选**：把 `ActionContext` 扩展成包含完整 data context——拒绝，会让所有 ActionHandler 都拿到无关数据，破坏最小知识原则。

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| AGENTS.md 修改可能被既有单测断言行数/章节顺序卡住 | 修改前先跑既有 `AgentsMarkdownTest` 类（在 `agent-skill-routing` spec 下），确认新增条目不破坏 `## ` 顶级标题集合与 ≤350 行约束。新条目都加在已有 `## Intent Routing Gate` / Registered Actions 表中，不引入新顶级标题。 |
| `isActiveInSession` 在 session 不存在时（无 active connection）的处理 | `SessionDataContextStore.get(sessionId)` 返回 `Optional.empty()` 时，`activeSessionConnectionId = null`，每项 `isActiveInSession = false`。明示 null/false 而非省略字段。 |
| AI 训练分布里可能仍倾向于优先调 list_connections | 四层防御中"响应载荷"是最终保险——即使 AI 选错入口，新载荷里的 `activeSessionConnectionId` 让它当场自我纠正，不需要二次调用。 |
| ListConnectionsAction 测试可能依赖 deterministic 输出顺序 | 既有测试断言只检查字段存在性与值（基于 `ConnectionDto` mock），新增字段不破坏既有断言；新断言独立加在新测试方法中。 |
| OpenCode 端可能缓存了旧 description（i18n 拉取时机） | i18n 由 `messages.properties` 在 Spring 启动时加载，重启服务即生效；OpenCode 端读取的是 action 描述的运行时值，不存在客户端缓存问题。 |
| 后端模块依赖：`SessionDataContextStore` 在 `data-talk-application`，`ListConnectionsAction` 在 `data-talk-adapter`，已是允许方向（adapter → application） | 无需调整，但实施时确认 `mvn install -pl data-talk-application -am -DskipTests` 同步刷新 jar（适配 `spring-boot:run` 加载机制）。 |

## Migration Plan

无 Flyway 迁移、无数据迁移。变更纯属代码 + 资源文件修改。

**部署顺序**：
1. 后端：编译并测试 `data-talk-adapter`（含 ListConnectionsAction 单测）→ 启动新版本服务。
2. 前端：无变化，无需重新构建。
3. OpenCode AI：服务重启即生效（AGENTS.md / SKILL.md 在 classpath，启动时被 SkillResourceSyncer 同步到 OpenCode 工作目录）。

**回滚**：
- 还原 `ListConnectionsAction.java`、`AGENTS.md`、`SKILL.md`、`messages*.properties` 即可。
- 新字段是"可选添加"，旧客户端解析忽略未知字段；即使部分客户端用新版、部分用旧版也不会出错。

**验证清单**：
- [ ] `mvn -pl data-talk-adapter test` 全绿。
- [ ] `mvn -pl data-talk-adapter test -Dtest=AgentsMarkdownTest` 通过（agent-skill-routing 体积约束）。
- [ ] 手动通过 Playwright 端到端复现原幻觉场景：已绑定 connection 的 session 问"有哪些数据库"——AI 应直接回答当前 connection 下的库列表，而不是说"未选择"。

## Open Questions

- **Q1**: `SessionDataContextStore` 的精确类名与包路径需要在 apply 阶段 grep 确认（推断在 `com.datatalk.application.persistence` 或 `com.datatalk.application.session`），不影响设计正确性。
- **Q2**: 是否需要把同样的 `isActiveInSession` 标记加到 `ListConnectionTargetsAction` 的响应里？**初步判断不需要**——该 action 已经按 connectionId 查询，调用者已经知道范围；本变更聚焦解决 `list_connections` 的幻觉，避免范围蔓延。如果 apply 阶段发现该 action 也有类似问题，再单独提 change。
