# Workspace And Backend I18n Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改协议结构与动态业务数据的前提下，补齐工作台相关前端页面/组件的全部用户可见静态文案，并让后端静态元数据、SQL 结果标题与错误返回支持 `zh-CN` / `en-US`。

**Architecture:** 继续复用现有双端 i18n 底座。前端仅扩展 `client/src/i18n/messages.ts` 并将工作台相关组件全部改为 `t()` 驱动；后端继续复用 `MessageSource + Translator`，把用户可见静态元数据与 API 错误路径收口到 message keys，不改变 JSON 字段名、SSE/RPC method 名、`kind/type/status` 等机器消费常量。

**Tech Stack:** React 19, TypeScript, Vitest, Spring Boot 3.5, Java 21, JUnit 5, MockMvc

---

## Spec Mapping

- [2026-04-22-workspace-i18n-design.md](../product-specs/2026-04-22-workspace-i18n-design.md)
  - “范围” → 前端 `workspace / stage / session` 静态文案、后端静态元数据与错误返回
  - “前端设计” → 继续使用 `messages.ts`、`useI18n()`、`translateMessage()`
  - “后端静态文案设计” → `action.*`、`object.*`、`sql.*` 统一进入 bundles
  - “后端错误出口设计” → 优先改会透出到前端的异常路径，不改协议结构
  - “测试与验收” → 前端静态扫描 + vitest，后端 locale 断言 + compile

## File Structure

### Frontend Dictionary / Workbench UI

- Modify: `client/src/i18n/messages.ts`
- Modify: `client/src/features/stage/components/activity-rail/rail-panel-shell.tsx`
- Modify: `client/src/features/stage/components/activity-rail/stage-activity-rail.tsx`
- Modify: `client/src/features/stage/components/activity-rail/schema-panel.tsx`
- Modify: `client/src/features/stage/components/activity-rail/history-panel.tsx`
- Modify: `client/src/features/stage/components/activity-rail/outline-panel.tsx`
- Modify: `client/src/features/stage/components/sql-context-chip.tsx`
- Modify: `client/src/features/stage/components/sql-limit-select.tsx`
- Modify: `client/src/features/stage/components/sql-editor-toolbar.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-status-bar.tsx`
- Modify: `client/src/features/stage/components/sql-error-result-panel.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Modify: `client/src/features/stage/utils/open-direct-sql-query-editor-tab.ts`

### Frontend Tests

- Modify: `client/src/features/stage/components/activity-rail/stage-activity-rail.test.tsx`
- Modify: `client/src/features/stage/components/activity-rail/schema-panel.test.tsx`
- Modify: `client/src/features/stage/components/activity-rail/history-panel.test.tsx`
- Modify: `client/src/features/stage/components/activity-rail/outline-panel.test.tsx`
- Modify: `client/src/features/stage/components/sql-context-chip.test.tsx`
- Modify: `client/src/features/stage/components/sql-editor-toolbar.test.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.test.tsx`
- Modify: `client/src/features/stage/components/stage-window.test.tsx`
- Modify: any directly affected test helpers/assertions that still depend on old hardcoded strings

### Backend Bundles / Static Metadata

- Modify: `server/data-talk-adapter/src/main/resources/messages.properties`
- Modify: `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/registry/ActionRegistry.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/SessionObjectType.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ActionInvocationObjectType.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ArtifactObjectType.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`

### Backend Error Paths / Tests

- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/api/GlobalExceptionHandler.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/TableContextAutoResolver.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionDataContextService.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/registry/ActionRegistry.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/registry/OntologyRegistry.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ListConnectionTargetsAction.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UpdateConnectionConfirmableAction.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/HistoryController.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/service/QueryApplicationService.java`
- Modify: targeted tests under:
  - `server/data-talk-application/src/test/java/com/datatalk/application/registry/`
  - `server/data-talk-application/src/test/java/com/datatalk/application/session/`
  - `server/data-talk-application/src/test/java/com/datatalk/application/sql/`
  - `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/`
  - `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/`

## Task 1: 计划登记与范围冻结

**Files:**
- Create: `docs/exec-plans/2026-04-22-workspace-backend-i18n-plan.md`
- Modify: `docs/exec-plans/index.md`

- [x] 将已批准 spec 转为执行计划，并固定边界：仅处理用户可见静态文案与错误消息，不改协议常量和动态业务数据。
- [x] 在 `docs/exec-plans/index.md` 的活跃计划中登记本计划。

## Task 2: 补齐前端工作台静态文案

**Files:**
- Modify: `client/src/i18n/messages.ts`
- Modify: `client/src/features/stage/components/activity-rail/rail-panel-shell.tsx`
- Modify: `client/src/features/stage/components/activity-rail/stage-activity-rail.tsx`
- Modify: `client/src/features/stage/components/activity-rail/schema-panel.tsx`
- Modify: `client/src/features/stage/components/activity-rail/history-panel.tsx`
- Modify: `client/src/features/stage/components/activity-rail/outline-panel.tsx`
- Modify: `client/src/features/stage/components/sql-context-chip.tsx`
- Modify: `client/src/features/stage/components/sql-limit-select.tsx`
- Modify: `client/src/features/stage/components/sql-editor-toolbar.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-status-bar.tsx`
- Modify: `client/src/features/stage/components/sql-error-result-panel.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Modify: `client/src/features/stage/utils/open-direct-sql-query-editor-tab.ts`

- [x] 先为工作台 rail / toolbar / context / status / error / direct-tab 标题新增或补齐 `stage.*` key，保持 `zh-CN` 与 `en-US` 双语同步。
- [x] 为 `rail-panel-shell`、`schema-panel`、`history-panel`、`outline-panel` 接入 `useI18n()`，去掉 `Schema / History / Outline / Close panel / High risk / No history yet` 等硬编码。
- [x] 为 `sql-context-chip`、`sql-limit-select`、`sql-editor-toolbar`、`sql-workbench-status-bar`、`sql-error-result-panel` 接入翻译，去掉 `Tab override / Session context / Execution limit / Run / Cancel / Format / Idle / Running / Risk blocked / SQL execution failed` 等硬编码。
- [x] 调整 `open-direct-sql-query-editor-tab.ts` 与 `sql-workbench-tab.tsx` 的标题/静态文案生成方式，确保不在 util 中残留固定中文标题。
- [x] 用 `rg` 复扫 `client/src/features/stage client/src/features/workspace client/src/features/session`，确认没有新的工作台用户可见硬编码漏项。

## Task 3: 更新前端测试与断言

**Files:**
- Modify: `client/src/features/stage/components/activity-rail/stage-activity-rail.test.tsx`
- Modify: `client/src/features/stage/components/activity-rail/schema-panel.test.tsx`
- Modify: `client/src/features/stage/components/activity-rail/history-panel.test.tsx`
- Modify: `client/src/features/stage/components/activity-rail/outline-panel.test.tsx`
- Modify: `client/src/features/stage/components/sql-context-chip.test.tsx`
- Modify: `client/src/features/stage/components/sql-editor-toolbar.test.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.test.tsx`
- Modify: `client/src/features/stage/components/stage-window.test.tsx`
- Modify: any directly affected frontend test helpers

- [x] 先为每个受影响组件补 failing tests 或更新现有断言，使其覆盖新的翻译 key 和 aria label。
- [x] 让测试通过 `translateMessage()` 或 `I18nContext` 取文案，不再硬编码依赖旧中文/英文文字。
- [x] 至少补一组 `en-US` 断言，证明工作台新文案能够切换语言。
- [x] 运行 `cd client && npx vitest run src/features/stage/components/activity-rail/stage-activity-rail.test.tsx src/features/stage/components/activity-rail/schema-panel.test.tsx src/features/stage/components/activity-rail/history-panel.test.tsx src/features/stage/components/activity-rail/outline-panel.test.tsx src/features/stage/components/sql-context-chip.test.tsx src/features/stage/components/sql-editor-toolbar.test.tsx src/features/stage/components/sql-workbench-tab.test.tsx src/features/stage/components/stage-window.test.tsx`
- [x] 运行 `cd client && npx tsc --noEmit`

## Task 4: 补齐后端静态元数据与 SQL 结果标题

**Files:**
- Modify: `server/data-talk-adapter/src/main/resources/messages.properties`
- Modify: `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/registry/ActionRegistry.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/SessionObjectType.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ActionInvocationObjectType.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ArtifactObjectType.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/registry/ActionRegistryTest.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/registry/OntologyRegistryTest.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SqlExecuteControllerIT.java`

- [x] 为缺失的 `action.*.description`、`object.*`、`sql.result.*` / `sql.dmlSummary.*` key 补齐双语 bundles。
- [x] 让 `ActionRegistry` 继续通过 `Translator` 返回本地化 description，并覆盖当前缺失的 UI action descriptions。
- [x] 为 4 个 ontology object type 接入翻译后的 `displayName()`，不再直接返回英文硬编码。
- [x] 让 `SqlExecuteService` 用 `Translator` 生成 `Result Set N`、`Error N`、`DML Summary N` / `N-M` 这类标题与默认失败消息。
- [x] 更新对应 application / adapter tests，覆盖 description、display name、SQL 结果标题的双语行为。

## Task 5: 收口后端错误出口国际化

**Files:**
- Modify: `server/data-talk-adapter/src/main/resources/messages.properties`
- Modify: `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/api/GlobalExceptionHandler.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/TableContextAutoResolver.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionDataContextService.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/registry/ActionRegistry.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/registry/OntologyRegistry.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ListConnectionTargetsAction.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UpdateConnectionConfirmableAction.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/HistoryController.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/service/QueryApplicationService.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/session/SessionDataContextServiceTest.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/sql/TableContextAutoResolverTest.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ReadSchemaActionIT.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/SessionDataContextActionsIT.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ConnectionManagementActionsIT.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionControllerIT.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/ConnectionControllerIT.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SqlExecuteControllerIT.java`

- [x] 为 `request body is required`、`no active connection in current session`、`unknown action/object type`、`sql required`、`connectionId required`、`target is required`、`confirmation token required`、自动定位表失败等用户可见错误补齐 message keys。
- [x] 把当前直接 `throw new IllegalArgumentException("...")` 的用户可见路径切到 `Translator` 或等价翻译辅助方法，同时保持 HTTP status 与响应结构不变。
- [x] 审查 `GlobalExceptionHandler` 对 `NoSuchElementException` / `IllegalArgumentException` / `DataTalkException` 的 message 输出路径，确保不会回落到未翻译的裸字符串。
- [x] 为关键 API / action 路径增加 `Accept-Language: zh-CN` 与 `en-US` 断言，证明错误消息随请求头切换。
- [x] 运行 `cd server && mvn -q -pl data-talk-application -am test -Dtest=ActionRegistryTest,OntologyRegistryTest,SessionDataContextServiceTest,ConnectionTargetDiscoveryServiceTest,UseTargetResolverTest,TableContextAutoResolverTest,QueryApplicationServiceTest,SqlExecuteServiceSplitterSelectionTest -Dsurefire.failIfNoSpecifiedTests=false`
- [x] 运行 `cd server && mvn -q -pl data-talk-adapter -am test -Dtest=DiscoveryControllerIT,OntologyRegistrationIT,SessionControllerIT,SqlExecuteControllerIT,SessionDataContextActionsIT,ReadSchemaActionIT,ConnectionManagementActionsIT -Dsurefire.failIfNoSpecifiedTests=false`
- [x] 运行 `cd server && mvn compile -q`

## Task 6: 收尾、索引与文档同步

**Files:**
- Modify: `docs/exec-plans/2026-04-22-workspace-backend-i18n-plan.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/I18N.md` only if implementation changes conventions materially

- [x] 在 fresh verification 全绿后，把本计划所有 checklist 更新为实际状态与命令结果。
- [x] 将 `docs/exec-plans/index.md` 中本计划从 Active 移到 Completed，并写入一句完整摘要。
- [x] 如果 bundles 命名约定、前后端 i18n 使用边界有实质变化，再同步更新 `docs/I18N.md`；没有变化则明确保持现状。

## Decisions

- 本计划不重新设计前端 i18n API，也不重构后端错误码体系；目标是补齐遗漏项而不是替换底座。
- “静态数据都要”按用户要求解释为：前端静态 UI 文案、后端 action 描述、object displayName、SQL 结果标题都要国际化。
- 协议字段、内部 `kind/type/status`、日志文本、数据库对象名、AI 生成内容不在本计划内。

## Self-Review

- Spec coverage: 前端工作台静态文案、后端静态元数据、后端错误出口和验证收尾均已覆盖。
- Placeholder scan: 无 TODO/TBD/“后续补充”等占位语。
- Type consistency: 全计划统一使用现有 `Translator`、`messages.properties`、`messages_zh_CN.properties`、`useI18n()`，未引入第二套术语。

## Verification Notes

- Frontend vitest: `cd client && npx vitest run src/features/stage/components/activity-rail/stage-activity-rail.test.tsx src/features/stage/components/activity-rail/schema-panel.test.tsx src/features/stage/components/activity-rail/history-panel.test.tsx src/features/stage/components/activity-rail/outline-panel.test.tsx src/features/stage/components/sql-context-chip.test.tsx src/features/stage/components/sql-editor-toolbar.test.tsx src/features/stage/components/sql-error-result-panel.test.tsx src/features/stage/components/sql-workbench-status-bar.test.tsx src/features/stage/components/sql-workbench-tab.test.tsx src/features/stage/components/stage-window.test.tsx` 通过，10 个测试文件 / 46 个用例全绿。
- Frontend types: `cd client && npx tsc --noEmit` 通过。
- Backend application: `export JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9 && export PATH="$JAVA_HOME/bin:$PATH" && cd server && mvn -q -pl data-talk-application -am test -Dtest=ActionRegistryTest,OntologyRegistryTest,SessionDataContextServiceTest,ConnectionTargetDiscoveryServiceTest,UseTargetResolverTest,TableContextAutoResolverTest,QueryApplicationServiceTest,SqlExecuteServiceSplitterSelectionTest -Dsurefire.failIfNoSpecifiedTests=false` 通过。
- Backend adapter: `export JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9 && export PATH="$JAVA_HOME/bin:$PATH" && cd server && mvn -q -pl data-talk-adapter -am test -Dtest=DiscoveryControllerIT,OntologyRegistrationIT,SessionControllerIT,SqlExecuteControllerIT,SessionDataContextActionsIT,ReadSchemaActionIT,ConnectionManagementActionsIT -Dsurefire.failIfNoSpecifiedTests=false` 通过；PostgreSQL Docker 用例在无可用 Docker 环境时按既有策略跳过。
- Backend compile: `export JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9 && export PATH="$JAVA_HOME/bin:$PATH" && cd server && mvn compile -q` 通过。
- Docs sync: 本次仅补齐既有 i18n 漏项，未引入新的 bundle 命名规则或双端边界约定，因此 `docs/I18N.md` 保持不变。
