# Session Data Context & AI Data Source Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立 session 级 `connectionId + database + schema` 统一上下文，收敛 `use xxx` 的解析与提示规则，并打通 Composer `!use/!select`、AI 对话、Stage Query Editor、`read_schema` / SQL 执行以及 AI 数据源管理的同一闭环。

**Architecture:** 先在后端引入 `SessionDataContext` 持久化、resolver、validate 与执行上下文解析器，作为唯一真相源；再让前端 Composer / Stage 与 AI tool / prompt / UI adapter 全部消费这套 API。连接管理与 session 上下文分层：连接资源仍由现有 `ConnectionService` 管理，但新增 AI 可用的 create/test/select/update-confirmable 闭环，并在连接修改后触发 context validate 与前端刷新。

**Tech Stack:** Spring Boot 3.5 + Java 21 + JdbcTemplate + Flyway + JUnit 5 / MockMvc；React 19 + TypeScript + Zustand + TanStack Query + Vitest；OpenCode action registry + AGENTS.md prompt。

**Spec:** [../product-specs/2026-04-21-session-data-context-and-ai-datasource-management-design.md](../product-specs/2026-04-21-session-data-context-and-ai-datasource-management-design.md)

---

## Concurrency Strategy

本计划是一个总 plan，但执行时按 **1 个前置批次 + 3 个并行批次 + 1 个收尾批次** 推进：

- **Batch A（必须先完成）**：后端 `SessionDataContext` 持久化、resolver、validate、执行上下文注入
- **Batch B（可并行）**：前端 Composer / Stage 消费统一上下文
- **Batch C（可并行）**：AI tools、`AGENTS.md`、UI adapter / action bridge
- **Batch D（可并行）**：AI 数据源管理与连接修改后的同步一致性
- **Batch E（收尾）**：联调、冒烟、文档回写、计划收口

并行边界：

- Batch B/C/D 只能在 Batch A 的 API shape 稳定后启动
- Batch D 依赖 Batch A 的 `validate` 能力
- Batch C 与 Batch D 共享连接管理语义，但写文件应尽量分离，避免冲突

---

## Spec Mapping

| Spec 章节 | 实施落点 |
|----------|---------|
| §4 核心模型 | Task 1, Task 2 |
| §5 `use xxx` 统一语义 | Task 2, Task 3, Task 4 |
| §6 跨数据库兼容 | Task 2 |
| §7 表名推断与自动补全 | Task 2, Task 3 |
| §8 后端接口设计 | Task 1, Task 2 |
| §9 AI Tool 与 Prompt | Task 4 |
| §10 前端交互设计 | Task 3, Task 5 |
| §11 一致性与失效处理 | Task 5 |
| §12 错误处理与文案 | Task 2, Task 3, Task 4, Task 5 |
| §13 测试矩阵 | Task 1-6 |

---

## File Structure Map

### Create

- `server/data-talk-infrastructure/src/main/resources/db/migration/V10__session_data_context.sql`
- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionDataContextRecord.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionDataContextRepository.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionDataContextService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/UseTargetResolver.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/ResolvedExecutionContext.java`
- `server/data-talk-application/src/main/java/com/datatalk/dto/SessionDataContextDto.java`
- `server/data-talk-application/src/main/java/com/datatalk/dto/SessionDataContextUpdateRequest.java`
- `server/data-talk-application/src/main/java/com/datatalk/dto/ResolveUseTargetRequest.java`
- `server/data-talk-application/src/main/java/com/datatalk/dto/ResolveUseTargetResponse.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionDataContextController.java`
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionDataContextControllerIT.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/session/SessionDataContextServiceTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/session/UseTargetResolverTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/session/ConnectionTargetDiscoveryServiceTest.java`
- `client/src/services/api/session-data-context.ts`
- `client/src/features/session/hooks/use-session-data-context.ts`
- `client/src/features/session/hooks/__tests__/use-session-data-context.test.tsx`
- `client/src/features/stage/utils/resolve-tab-data-context.ts`
- `client/src/features/stage/utils/__tests__/resolve-tab-data-context.test.ts`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/GetDataContextAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/SetDataContextAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ResolveUseTargetAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ListConnectionTargetsAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ListConnectionsAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/CreateConnectionAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/TestConnectionAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/SelectConnectionAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UpdateConnectionConfirmableAction.java`
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/SessionDataContextActionsIT.java`
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ConnectionManagementActionsIT.java`

### Modify

- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRecord.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRepository.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java`
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionControllerIT.java`
- `server/data-talk-application/src/main/java/com/datatalk/command/ExecuteSqlCommand.java`
- `server/data-talk-application/src/main/java/com/datatalk/service/QueryApplicationService.java`
- `server/data-talk-application/src/test/java/com/datatalk/service/QueryApplicationServiceTest.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/repository/DynamicSqlExecutionRepository.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/connection/ConnectionServiceTest.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/connection/ConnectionController.java`
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/ConnectionControllerIT.java`
- `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java`
- `docs/references/ui-objects-reference.md`
- `client/src/services/api/session.ts`
- `client/src/features/session/prompt-composer.tsx`
- `client/src/features/session/__tests__/prompt-composer.test.tsx`
- `client/src/stores/session-store.ts`
- `client/src/features/stage/components/query-editor-tab.tsx`
- `client/src/features/stage/components/query-editor-tab.test.tsx`
- `client/src/features/stage/components/bang-query-tab.tsx`
- `client/src/features/stage/adapters/BangQueryAdapter.ts`
- `client/src/features/stage/adapters/WorkspaceAdapter.ts`
- `client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts`
- `client/src/features/actions/ui-handlers.ts`
- `client/src/features/actions/__tests__/ui-handlers.test.ts`
- `client/src/services/api/query.ts`
- `client/src/services/api/sql.ts`
- `client/src/features/stage/hooks/use-sql-execute.ts`
- `client/src/features/settings/data-sources/api.ts`
- `client/src/features/settings/data-sources/data-sources-page.tsx`
- `client/src/features/settings/data-sources/__tests__/data-sources-page.test.tsx`
- `client/src/features/settings/data-sources/connection-form-dialog.tsx`

### Likely Untouched

- `client/src/components/ui/**`
- `server/data-talk-domain/**`
- `docs/DESIGN.md` / `ARCHITECTURE.md` / `docs/FRONTEND.md` / `docs/BACKEND.md` 之外的大部分文档（仅在收尾回写时按结果更新）

---

## Task 1: Batch A — Session Data Context Persistence & Public API

**Files:**
- Create: `server/data-talk-infrastructure/src/main/resources/db/migration/V10__session_data_context.sql`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionDataContextRecord.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionDataContextRepository.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionDataContextService.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/dto/SessionDataContextDto.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/dto/SessionDataContextUpdateRequest.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionDataContextController.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionDataContextControllerIT.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/session/SessionDataContextServiceTest.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRepository.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java`

**Intent:** 先把 session 级上下文落到后端持久化与 REST API，提供 `GET/PUT /api/sessions/{id}/data-context`。这一步不处理 resolver 与 SQL 执行，只建立后续所有批次共同依赖的稳定 API 与存储模型。

- [ ] **Step 1.1: 写失败测试 — `SessionDataContextService` 默认返回空 context 且可 upsert**
- [ ] **Step 1.2: 新增 Flyway migration**
  - 建表 `session_data_contexts`
  - `session_id` 主键并 `REFERENCES sessions(id) ON DELETE CASCADE`
  - 字段：`connection_id`, `connection_name_snapshot`, `database_name`, `schema_name`, `selected_level`, `updated_at`
- [ ] **Step 1.3: 实现 `SessionDataContextRecord` 与 `SessionDataContextRepository`**
  - 提供 `findBySessionId`
  - 提供 `upsert`
  - 提供 `deleteBySessionId`
- [ ] **Step 1.4: 实现 `SessionDataContextService`**
  - `get(sessionId)`
  - `set(sessionId, updateRequest)`
  - 若 session 不存在，抛 `NoSuchElementException`
  - 若 `connectionId` 为空，清空 `database/schema/selectedLevel`
- [ ] **Step 1.5: 写失败测试 — `GET/PUT /api/sessions/{id}/data-context`**
- [ ] **Step 1.6: 实现 DTO 与 `SessionDataContextController`**
  - `GET` 返回完整 dto
  - `PUT` 返回更新后的 dto
  - 404 / 400 映射与现有 `SessionController` 风格保持一致
- [ ] **Step 1.7: 同步 `SessionService.delete()` 的上下文清理预期**
  - 依赖 FK cascade 删除，不额外手写 delete SQL
  - 在 IT 中补删除 session 后 context 级联删除断言
- [ ] **Step 1.8: 运行后端专项验证**
  - `cd server && mvn test -q -pl data-talk-application,data-talk-adapter -Dtest=SessionDataContextServiceTest,SessionDataContextControllerIT,SessionControllerIT`
- [ ] **Step 1.9: 运行后端编译**
  - `cd server && mvn compile -q`

---

## Task 2: Batch A — Resolver, Validate & Execution Context Injection

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/session/UseTargetResolver.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/session/ResolvedExecutionContext.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/dto/ResolveUseTargetRequest.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/dto/ResolveUseTargetResponse.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/session/UseTargetResolverTest.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/session/ConnectionTargetDiscoveryServiceTest.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionDataContextController.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionDataContextControllerIT.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/command/ExecuteSqlCommand.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/service/QueryApplicationService.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/repository/DynamicSqlExecutionRepository.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/service/QueryApplicationServiceTest.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SqlExecuteControllerIT.java`

**Intent:** 建立 `resolve-use` / `validate` / `ResolvedExecutionContext`，并把 `/api/query`、`/api/sql/execute`、`datatalk.read_schema`、`datatalk.execute_sql` 统一切到解析后的上下文，不再只吃裸 `connectionId`。

- [ ] **Step 2.1: 写失败测试 — resolver 支持 matched / ambiguous / not_found**
  - 覆盖：connection 命中、database 命中、schema 命中、当前连接内优先、歧义候选、未命中建议
- [ ] **Step 2.2: 实现 `ConnectionTargetDiscoveryService`**
  - 从连接元数据 / JDBC metadata 枚举 database 与 schema 候选
  - PG 下显式区分 database 与 schema
  - MySQL 下将 database 视为主切换层
- [ ] **Step 2.3: 实现 `UseTargetResolver`**
  - 输入 `sessionId + rawTarget`
  - 输出 `matched | ambiguous | not_found`
  - `matched` 时返回可直接写入的 `SessionDataContextRecord`
- [ ] **Step 2.4: 给 `SessionDataContextController` 增加**
  - `POST /api/sessions/{id}/data-context/resolve-use`
  - `POST /api/sessions/{id}/data-context/validate`
- [ ] **Step 2.5: 写失败测试 — 连接修改后 validate 清空失效 `database/schema`，保留 `connectionId`**
- [ ] **Step 2.6: 引入 `ResolvedExecutionContext`**
  - 统一解析 session context 与 tab/request override
  - 解析优先级：override > session context > connection default
- [ ] **Step 2.7: 改 `/api/query`**
  - `ExecuteSqlCommand` 增加 `sessionId?`, `database?`, `schema?`
  - `QueryApplicationService` 不再只靠 `connectionId`，而是走 `ResolvedExecutionContext`
  - 对 PostgreSQL / MySQL 注入对应 database/schema 执行语义
- [ ] **Step 2.8: 改 `/api/sql/execute` 与 action `read_schema` / `execute_sql`**
  - `SqlExecuteService` 支持 context override
  - `ReadSchemaAction` 在 PG 下返回 schema 感知的元数据
  - `ExecuteSqlAction` 在 session context 存在时使用解析后的上下文
- [ ] **Step 2.9: 补自动表定位与提示**
  - `select * from users` 在缺 schema/database 时先轻量探测
  - 唯一命中则自动补全并执行
  - 多命中则返回明确建议，不盲猜
- [ ] **Step 2.10: 运行后端专项验证**
  - `cd server && mvn test -q -pl data-talk-application,data-talk-adapter -Dtest=UseTargetResolverTest,ConnectionTargetDiscoveryServiceTest,QueryApplicationServiceTest,SqlExecuteControllerIT,SessionDataContextControllerIT`
- [ ] **Step 2.11: 运行后端编译**
  - `cd server && mvn compile -q`

---

## Task 3: Batch B — Frontend Composer & Stage Consume Session Data Context

**Files:**
- Create: `client/src/services/api/session-data-context.ts`
- Create: `client/src/features/session/hooks/use-session-data-context.ts`
- Create: `client/src/features/session/hooks/__tests__/use-session-data-context.test.tsx`
- Create: `client/src/features/stage/utils/resolve-tab-data-context.ts`
- Create: `client/src/features/stage/utils/__tests__/resolve-tab-data-context.test.ts`
- Modify: `client/src/services/api/session.ts`
- Modify: `client/src/features/session/prompt-composer.tsx`
- Modify: `client/src/features/session/__tests__/prompt-composer.test.tsx`
- Modify: `client/src/stores/session-store.ts`
- Modify: `client/src/services/api/query.ts`
- Modify: `client/src/services/api/sql.ts`
- Modify: `client/src/features/stage/hooks/use-sql-execute.ts`
- Modify: `client/src/features/stage/components/query-editor-tab.tsx`
- Modify: `client/src/features/stage/components/query-editor-tab.test.tsx`
- Modify: `client/src/features/stage/components/bang-query-tab.tsx`
- Modify: `client/src/features/stage/adapters/BangQueryAdapter.ts`

**Intent:** 让 Composer 的 `! use` / `! select` 和 Stage Query Editor/Bang Query 全部走同一套 session context API；Stage tab 允许临时 override，但不回写 session。

- [ ] **Step 3.1: 写失败测试 — `prompt-composer` 输入 `! use aaa` 时调用 `resolve-use`**
- [ ] **Step 3.2: 实现 `session-data-context.ts` 与 `use-session-data-context.ts`**
  - `getSessionDataContext`
  - `setSessionDataContext`
  - `resolveUseTarget`
  - `validateSessionDataContext`
- [ ] **Step 3.3: 在 `prompt-composer.tsx` 接入 `! use xxx`**
  - 有 active session 时直接 resolve + set
  - 无 active session 时先创建 / 复用 session，再落 context
  - `not_found` / `ambiguous` 走明确提示，不 silent fail
- [ ] **Step 3.4: 改 `! select` / `! with` 直查路径**
  - 请求体带 `sessionId`
  - 若 server 自动补全了 schema/database，则将最新 context 回写到 query result tab 快照或 session cache
- [ ] **Step 3.5: 为 Stage Query Editor 引入上下文继承**
  - 新开 tab 默认继承 session context
  - tab payload 支持 `database/schema` override
  - 执行时优先使用 tab override
- [ ] **Step 3.6: 为 Bang Query 展示更完整上下文**
  - badge / 状态区域显示 connection + database/schema
  - rerun 继续走 tab snapshot，不读当前全局连接
- [ ] **Step 3.7: 写失败测试 — Stage override 不污染 session**
- [ ] **Step 3.8: 实现 `resolve-tab-data-context.ts` 并接入 Query Editor / Bang Query**
- [ ] **Step 3.9: 运行前端专项验证**
  - `cd client && npx vitest run src/features/session/__tests__/prompt-composer.test.tsx src/features/session/hooks src/features/stage/components/query-editor-tab.test.tsx src/features/stage/utils`
- [ ] **Step 3.10: 运行前端类型检查**
  - `cd client && npx tsc --noEmit`

---

## Task 4: Batch C — AI Tools, Prompt & UI Adapter Integration

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/GetDataContextAction.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/SetDataContextAction.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ResolveUseTargetAction.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ListConnectionTargetsAction.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/SessionDataContextActionsIT.java`
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java`
- Modify: `docs/references/ui-objects-reference.md`
- Modify: `client/src/features/stage/adapters/WorkspaceAdapter.ts`
- Modify: `client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts`
- Modify: `client/src/features/actions/ui-handlers.ts`
- Modify: `client/src/features/actions/__tests__/ui-handlers.test.ts`

**Intent:** 给 AI 明确的“读 / 设 / 解析数据上下文”工具，不再只靠 `choose_connection`。`AGENTS.md` 要把 `use xxx` 的行为规则写死，保证 AI 不会口头切换但没调工具。

- [ ] **Step 4.1: 写失败测试 — `resolve_use_target` / `get_data_context` / `set_data_context` action 可被发现并执行**
- [ ] **Step 4.2: 实现数据上下文 actions**
  - action id 以 `datatalk.*` 命名
  - 复用 Batch A 服务，不在 action 中重复业务逻辑
- [ ] **Step 4.3: 实现 `list_connection_targets`**
  - 返回当前连接可见 databases / schemas
  - 供 AI 在未命中时组织建议
- [ ] **Step 4.4: 更新 `AGENTS.md`**
  - 用户说 `use xxx` 时先 `resolve_use_target`
  - `matched` 后再 `set_data_context`
  - `ambiguous/not_found` 时澄清或建议
  - 不允许“假装已切换”
- [ ] **Step 4.5: 评估 `WorkspaceAdapter` 是否需要新增 `choose_data_context`**
  - 如果 `choose_connection` 足够，本期只更新 docs 与测试
  - 如果前端确需补全 database/schema 选择弹框，再加新 action，但避免与 Batch B 冲突
- [ ] **Step 4.6: 更新 UI 协议文档**
  - `docs/references/ui-objects-reference.md`
  - 若新增 action，同步更新描述
- [ ] **Step 4.7: 运行后端 / 前端专项验证**
  - `cd server && mvn test -q -pl data-talk-adapter -Dtest=SessionDataContextActionsIT`
  - `cd client && npx vitest run src/features/actions/__tests__/ui-handlers.test.ts src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts`
- [ ] **Step 4.8: 运行编译与类型检查**
  - `cd server && mvn compile -q`
  - `cd client && npx tsc --noEmit`

---

## Task 5: Batch D — AI Connection Management & Consistency Sync

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ListConnectionsAction.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/CreateConnectionAction.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/TestConnectionAction.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/SelectConnectionAction.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UpdateConnectionConfirmableAction.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ConnectionManagementActionsIT.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/connection/ConnectionServiceTest.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/connection/ConnectionController.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/ConnectionControllerIT.java`
- Modify: `client/src/features/settings/data-sources/api.ts`
- Modify: `client/src/features/settings/data-sources/data-sources-page.tsx`
- Modify: `client/src/features/settings/data-sources/connection-form-dialog.tsx`
- Modify: `client/src/features/settings/data-sources/__tests__/data-sources-page.test.tsx`
- Modify: `client/src/features/connection/store.ts`

**Intent:** 让 AI 具备新增、测试、选择、修改连接的能力，同时把“连接修改后 session context 失效怎么办”这件事收束到 `validate + refresh` 机制。删除连接不给 AI。

- [ ] **Step 5.1: 写失败测试 — AI action 可创建 / 测试 / 选择连接，但没有 delete**
- [ ] **Step 5.2: 实现 create/test/select actions**
  - 直接复用 `ConnectionService`
  - `select_connection` 只更新 session data context 的 `connectionId` 与 snapshot
- [ ] **Step 5.3: 设计并实现 `UpdateConnectionConfirmableAction`**
  - 第一次调用返回 `confirm_required`
  - 第二次带 `confirm=true` 或 `confirmationToken` 才真正提交
  - 失败时不得部分落库
- [ ] **Step 5.4: 写失败测试 — 连接修改后 validate 清空失效 `database/schema`**
- [ ] **Step 5.5: 在连接更新成功后触发**
  - 后端 context validate
  - 前端连接列表刷新
  - 当前 session / Stage tab 展示名称刷新
- [ ] **Step 5.6: 前端设置页与 session 消费层补刷新**
  - AI 或手动修改连接后，若当前 session 正在使用该连接，主动 refetch data context
  - 若 `database/schema` 失效，展示明确提示
- [ ] **Step 5.7: 运行专项验证**
  - `cd server && mvn test -q -pl data-talk-application,data-talk-adapter -Dtest=ConnectionServiceTest,ConnectionManagementActionsIT,ConnectionControllerIT,SessionDataContextControllerIT`
  - `cd client && npx vitest run src/features/settings/data-sources`
- [ ] **Step 5.8: 运行编译与类型检查**
  - `cd server && mvn compile -q`
  - `cd client && npx tsc --noEmit`

---

## Task 6: Batch E — Consolidated Verification & Housekeeping

**Files:** all touched surfaces plus docs/index files

- [ ] **Step 6.1: 跑后端整体验证**
  - `cd server && mvn clean verify`
- [ ] **Step 6.2: 跑前端整体验证**
  - `cd client && npx vitest run src/features/session src/features/stage src/features/actions src/features/settings/data-sources`
  - `cd client && npx tsc --noEmit`
- [ ] **Step 6.3: 手工冒烟**
  - `! use aaa` 命中 connection/database/schema
  - 未命中时返回建议
  - 歧义时返回明确候选
  - `! select * from users` 在可唯一推断时自动补上下文并执行
  - PG 未选 schema 时给明确引导
  - Query Editor 继承 session context；tab override 不污染 session
  - AI 对话式 `use aaa` 走工具后才确认已切换
  - AI 创建 / 测试 / 选择连接成功
  - AI 修改连接先确认；修改后当前 session 自动 refresh，失效字段被清空并提示
- [ ] **Step 6.4: 文档回写**
  - 若最终协议稳定，回写 `docs/references/ui-objects-reference.md`
  - 如执行结果形成新的连接绑定约定，回写 `ARCHITECTURE.md` / `docs/FRONTEND.md` / `docs/BACKEND.md`
- [ ] **Step 6.5: 计划收尾**
  - 勾完本计划所有 checkbox
  - 在 `docs/exec-plans/index.md` 将本条目从 Active 移到 Completed
  - 将任何延期项写入 `docs/exec-plans/tech-debt-tracker.md`

---

## Dependency Notes

- Task 1 与 Task 2 必须按顺序完成，它们共同构成 Batch A
- Task 3 / Task 4 / Task 5 可并行，但开始前必须冻结 Batch A 的 API shape
- Task 4 与 Task 5 都会碰 `AGENTS.md` / 连接语义；执行时应先明确文件 ownership，避免冲突
- Task 6 只能在 Task 1-5 全部落地后执行

## Open Questions Already Resolved By Spec

- `use xxx` 统一语义，但只能匹配到真实存在的目标
- 自动匹配优先；匹配不到给建议；歧义时不盲猜
- 持久化粒度是 session，不是 connection
- Stage tab 可以临时覆盖，但不反向污染 session
- AI 不允许删除连接
- AI 修改连接必须二次确认
- 连接修改后若原 database/schema 失效：保留 `connectionId`，清空失效字段并提示重新选择
