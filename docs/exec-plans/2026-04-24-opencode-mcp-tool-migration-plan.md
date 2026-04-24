# OpenCode MCP Tool Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 DataTalk 从 legacy plugin-tool 注册/HTTP callback 链路切换到 MCP 单路径，并保留 session-scoped action、CLIENT 同步等待、prompt/tool renderer 与运行时 bootstrap 的现有语义。

**Architecture:** 继续以 `ActionRegistry` 作为唯一工具真源，在后端新增最小 `/mcp` JSON-RPC 入口，用 `McpNameMapper` 负责 `datatalk.*` ↔ `datatalk_*` naming，用 OpenCode plugin 注入隐藏 bridge 字段把 `tools/call` 重新桥接回现有 `ActionDispatcher`。embedded 模式改为 config-first 启动；external 模式改为 config patch + runtime reconcile + health-probe；前端仅切 MCP tool name 与 degraded surface，CLIENT handler id 保持 `datatalk.ui.*` 不变。

**Tech Stack:** Spring Boot 3.5, Java 21, WebClient, Spring async `DeferredResult`, WireMock, JUnit 5, React 19, TanStack Query, Vitest, TypeScript.

---

## Design Inputs

- Source: [`docs/product-specs/2026-04-24-opencode-mcp-tool-migration-design.md`](../product-specs/2026-04-24-opencode-mcp-tool-migration-design.md)
- Source: [`client/DESIGN.md`](../../client/DESIGN.md)
- Applied constraints:
  - MCP degraded 提示属于 Chat / Workbench 共用系统状态，不单独发明视觉语言；优先复用既有 semantic status surface。
  - 相关前端改动保持现有 `comfortable` / `compact` 密度，不新增重型 banner chrome 或与主界面脱节的警示层。
  - tool renderer rename 只改注册键和测试，不顺手重画工具卡片样式。

## Scope

- 新增后端 `/mcp` remote server、命名映射、nonce bridge、bootstrap writer/reconciler。
- 更新 embedded / external OpenCode 启动与 reconcile 顺序。
- 更新 prompt、前端 tool renderer 注册键、health/degraded surface 与自动化测试。
- 删除 legacy `/plugin/register-tool` / `/api/opencode-tool/*` / `shared-secret` callback 链路。

## Non-Goals

- 不保留 legacy 与 MCP 双注册窗口。
- 不重做 `ActionDispatcher` / `SessionBus` / CLIENT action 协议。
- 不重做现有工具卡片视觉或 Stage/Chat 布局。
- 不在本期引入 streaming MCP progress 通知。

## Spec Mapping

- §1 / §2 / §5: MCP naming、隐藏上下文桥接、prompt 与 renderer rename。
- §3: embedded config-first、external reconcile、`OpenCodeSessionMap` bind 时机。
- §6: `DeferredResult` + CLIENT executor 同步等待语义。
- §7: `/mcp` 访问控制与 nonce 防伪。
- §8: `docs/references/opencode-protocol.md` 前置钉死，作为任务 0 gate。
- §9: 新增 MCP 配置、移除 `plugin-callback-base` / `shared-secret`。

## File Structure

### Docs & Plan Registry

- Modify: `docs/references/opencode-protocol.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-24-opencode-mcp-tool-migration-plan.md`
- Optional modify: `docs/exec-plans/tech-debt-tracker.md`

### Domain & Registry Contract

- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/action/ActionDescriptor.java`
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/action/DataTalkAction.java`
- Modify: `server/data-talk-domain/src/test/java/com/datatalk/domain/action/ActionDescriptorTest.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/registry/ActionRegistry.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/registry/ActionRegistryTest.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeGateway.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeGatewayTest.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/McpNameMapper.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/McpNameMapperTest.java`

### MCP Runtime & Bootstrap

- Create: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/DataTalkMcpService.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/McpActionBridge.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeBridgeStatus.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/ActionDispatcher.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/PendingCallRegistry.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/session/ActionDispatcherTest.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/session/PendingCallRegistryTest.java`
- Delete: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/ToolCallBridge.java`
- Delete: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/ToolCallBridgeTest.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/McpActionBridgeTest.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/DataTalkMcpController.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeMcpProperties.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeConfig.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeHttpClient.java`
- Delete: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/ToolCallController.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeBootstrapWriter.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeBootstrapReconciler.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeProcessManager.java`
- Modify: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/OpenCodeHttpClientTest.java`
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/DataTalkMcpControllerTest.java`
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodeBootstrapWriterTest.java`

### Adapter Wiring, Prompt, Health Surface

- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/controller/QueryController.java`
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- Modify: `server/data-talk-adapter/src/main/resources/application.yml`
- Modify: `server/data-talk-adapter/src/test/resources/application.yml`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/smoke/EndToEndSmokeIT.java`

### Frontend Renderer & Degraded Banner

- Modify: `client/src/features/chat/components/tools/renderers/index.ts`
- Create: `client/src/features/chat/components/tools/__tests__/register-built-in-renderers.test.ts`
- Modify: `client/src/features/chat/components/tools/__tests__/read-file.test.tsx`
- Modify: `client/src/services/api/health.ts`
- Create: `client/src/features/session/hooks/use-opencode-health.ts`
- Modify: `client/src/features/session/split-view.tsx`
- Modify: `client/src/features/session/split-view.test.tsx`
- Modify: `client/src/i18n/messages.ts`

## Parallelization Notes

- 任务 0 是硬前置 gate；在 `docs/references/opencode-protocol.md` 没钉死前，不进入任何实现任务。
- 任务 1 完成后再分两批推进：Batch A 做 `/mcp` 运行时与 dispatcher 桥接；Batch B 做 bootstrap/reconcile/health surface。两批都依赖命名映射与 `exposeToMcp` 契约。
- Prompt / frontend rename 依赖任务 1 的 naming contract，但不依赖 bootstrap 代码落地；可与 Batch B 并行。
- legacy 删除必须放到最后一个 batch，避免把回归基线提前拆掉。
- 按仓库规则，代码批次内不做每次小改后的 compile/tsc；每个 batch 写完后再做 consolidated verification。

## Task 0: 钉死 OpenCode 协议输入并刷新参考文档

**Files:**
- Modify: `docs/references/opencode-protocol.md`

- [x] Step 1: 基于当前 OpenCode 版本源码/运行时实测，确认 spec §8 的 5 个前提：MCP transport、plugin hook 名、plugin 加载方式、`PATCH /config` 行为、`POST /mcp` runtime mount。
- [x] Step 2: 将确认后的 endpoint、hook 名、最小示例 request/response、与“本计划实现依赖哪些行为”写入 `docs/references/opencode-protocol.md`，替换掉当前 legacy plugin-tool 叙述。
- [x] Step 3: 在文档中补一条实现备注：若 `/mcp` 继续挂在现有 Spring Boot 端口上，则“loopback only”优先按 remote-address gate 实现；只有当 Task 0 证据表明必须单独 listener 时，才升级为独立 MCP listener。

## Task 1: 建立 MCP exposure contract 与稳定命名映射

**Files:**
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/action/ActionDescriptor.java`
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/action/DataTalkAction.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/registry/ActionRegistry.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeGateway.java`
- Modify: `server/data-talk-domain/src/test/java/com/datatalk/domain/action/ActionDescriptorTest.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/registry/ActionRegistryTest.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeGatewayTest.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/McpNameMapper.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/McpNameMapperTest.java`

- [x] Step 1: 在注解与 descriptor 层加入 `exposeToMcp`，默认 `true`；把 `datatalk.demo.echo` 这类非生产工具显式标成 `false`，并用 domain/application 测试锁定默认值与过滤行为。
- [x] Step 2: 引入 `McpNameMapper`，集中处理 `datatalk.execute_sql -> execute_sql -> datatalk_execute_sql`、`datatalk.ui.read -> ui_read -> datatalk_ui_read` 这类双向映射，并用独立测试覆盖 UI/action/tool 三类样例。
- [x] Step 3: 收缩 `OpenCodeGateway` 到“创建 session / 发消息 / 删 session / abort / list history”职责，去掉 tool-pusher/callbackBase 依赖，确保后续 bootstrap 改造不会再从这里偷偷走 legacy 注册。

## Task 2: 实现最小 `/mcp` server 与 dispatcher bridge

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/DataTalkMcpService.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/McpActionBridge.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/McpActionBridgeTest.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/ActionDispatcher.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/PendingCallRegistry.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/session/ActionDispatcherTest.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/session/PendingCallRegistryTest.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/DataTalkMcpController.java`
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/DataTalkMcpControllerTest.java`

- [x] Step 1: 用 `McpNameMapper + exposeToMcp` 实现 `initialize` / `notifications/initialized` / `tools/list` / `tools/call`，`tools/list` 只列 MCP 公开工具，且对外 schema 直接复用现有 `ActionDescriptor`。
- [x] Step 2: 在 `tools/call` 路径实现 `__dt*` 字段 strip、nonce 校验、`OpenCodeSessionMap` 反查、schema 校验、`ActionDispatcher` dispatch 的顺序，不让隐藏 bridge 字段污染 action schema。
- [x] Step 3: 用 `DeferredResult` 保留 CLIENT action 的同步等待；把 `missing session context`、`unauthenticated bridge`、`unknown opencode session`、`client action timed out`、`no client subscriber` 这几条路径都锁成自动化测试。
- [x] Step 4: 在现有 Spring Boot 端口上加 `/mcp` request 访问控制，覆盖 loopback / Origin / nonce 三层；若任务 0 证据不支持 per-route bind，则按 remote-address gate 实现并同步回 `docs/references/opencode-protocol.md`。

## Task 3: 落地 bootstrap writer、embedded config-first 启动与 external reconcile

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeBridgeStatus.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeMcpProperties.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeConfig.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeHttpClient.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeBootstrapWriter.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeBootstrapReconciler.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeProcessManager.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/controller/QueryController.java`
- Modify: `server/data-talk-adapter/src/main/resources/application.yml`
- Modify: `server/data-talk-adapter/src/test/resources/application.yml`
- Modify: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/OpenCodeHttpClientTest.java`
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodeBootstrapWriterTest.java`

- [x] Step 1: 引入 `datatalk.mcp.enabled`、managed config-dir 与 reconcile 所需配置，并在启动时对显式配置的 `datatalk.opencode.shared-secret` / `plugin-callback-base` 打 WARN，而不是继续消费这些 legacy 值。
- [x] Step 2: 实现 `OpenCodeBootstrapWriter`，负责生成 per-process nonce、plugin 文件、`AGENTS.md`、`opencode.json` 深合并、原子写、备份与幂等性测试。
- [x] Step 3: 把 embedded 启动改成“先 writer/reconcile，再起 `opencode serve`，最后起 `/global/event`”；把 external 路径改成 `PATCH /config -> POST /mcp -> health-probe`，并在 health-probe 失败时写入 `OpenCodeBridgeStatus` degraded 原因。
- [x] Step 4: 扩展 `GET /api/health` 响应，至少返回 `status`、`timestamp` 与可读 `message`/`reason`，给前端 degraded banner 一个稳定数据源。

## Task 4: 更新 prompt、tool renderer 与前端 degraded surface

**Files:**
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java`
- Modify: `client/src/features/chat/components/tools/renderers/index.ts`
- Create: `client/src/features/chat/components/tools/__tests__/register-built-in-renderers.test.ts`
- Modify: `client/src/features/chat/components/tools/__tests__/read-file.test.tsx`
- Modify: `client/src/services/api/health.ts`
- Create: `client/src/features/session/hooks/use-opencode-health.ts`
- Modify: `client/src/features/session/split-view.tsx`
- Modify: `client/src/features/session/split-view.test.tsx`
- Modify: `client/src/i18n/messages.ts`

- [x] Step 1: 将 runtime `AGENTS.md` 中所有工具引用从 `datatalk.*` 改为 `datatalk_*`，并把 `AgentPromptContractTest` 改成校验“prompt 中的 MCP tool 名 ↔ backend 暴露名集合”。
- [x] Step 2: 对 `registerBuiltInRenderers()` 做 hard rename，把内建专属 renderer 键切到 `datatalk_*`；新增 vitest 断言这些键全部以 `datatalk_` 开头，且与约定的专属 renderer 清单一致，同时只是 backend `tools/list` 的子集。
- [x] Step 3: 保持 `client/src/features/actions/ui-handlers.ts` 和现有 `datatalk.ui.*` client handler registration 不变，只补充测试确保 MCP rename 没误伤 CLIENT action id。
- [x] Step 4: 在 `SplitView` 上接入 health query 与 degraded banner，使用 `client/DESIGN.md` 的 warning surface / border token，不新增独立视觉体系；补齐 ok/degraded 渲染测试和中英文文案。

## Task 5: 拆除 legacy callback 链路并更新 smoke/契约测试

**Files:**
- Delete: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/ToolCallController.java`
- Delete: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/ToolCallBridge.java`
- Delete: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/ToolCallBridgeTest.java`
- Modify: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/OpenCodeHttpClientTest.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/smoke/EndToEndSmokeIT.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java`

- [x] Step 1: 删除 `/plugin/register-tool` push、`/api/opencode-tool/*` controller、shared-secret 校验和相关旧测试夹具，确保仓库里不再有运行时代码依赖 legacy callback base。
- [x] Step 2: 把 smoke 从“register tools + callback”改成“MCP single-path”: 验证 external reconcile 顺序、`tools/list` 生产工具面、`tools/call` nonce/session bridge、CLIENT timeout/no-subscriber；embedded config-first 由 `OpenCodeBootstrapWriterTest` / `OpenCodeBootstrapReconcilerTest` 继续锁定。
- [x] Step 3: 保留 `/global/event` 事件回路测试基线，确保 MCP cutover 没把 session/message SSE 消费打断。

## Task 6: Consolidated Verification 与文档收尾

**Files:**
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-24-opencode-mcp-tool-migration-plan.md`
- Optional modify: `docs/exec-plans/tech-debt-tracker.md`

- [x] Step 1: 运行 `cd server && mvn compile -q`。
- [x] Step 2: 运行后端定向测试，覆盖 registry/MCP bridge/bootstrap/smoke：`cd server && mvn -q -pl data-talk-application,data-talk-infrastructure,data-talk-adapter -am test -Dtest=ActionRegistryTest,OpenCodeGatewayTest,McpNameMapperTest,McpActionBridgeTest,PendingCallRegistryTest,ActionDispatcherTest,OpenCodeHttpClientTest,DataTalkMcpControllerTest,OpenCodeBootstrapWriterTest,OpenCodeBootstrapReconcilerTest,AgentPromptContractTest,EndToEndSmokeIT -Dsurefire.failIfNoSpecifiedTests=false`。
- [x] Step 3: 运行前端定向测试：`cd client && npx vitest run src/features/chat/components/tools/__tests__/register-built-in-renderers.test.ts src/features/chat/components/tools/__tests__/read-file.test.tsx src/features/session/split-view.test.tsx src/features/actions/__tests__/client-handler-registration.test.ts`。
- [x] Step 4: 运行 `cd client && npx tsc --noEmit`。
- [x] Step 5: 若执行过程中新增 material convention（例如 `/api/health` shape、managed config dir、deprecated opencode 配置），同步回 `docs/references/opencode-protocol.md`、`ARCHITECTURE.md` 或相关 canonical docs。
- [x] Step 6: 实现完成后将本计划 checklist 按实际结果回填，并把 `docs/exec-plans/index.md` 中的条目从 Active 移到 Completed。

## Execution Notes

- Task 0 gate 已完成；`docs/references/opencode-protocol.md` 现已替换为 MCP single-path 参考。
- `client/DESIGN.md` 只影响本期新增的 degraded surface，不授权顺手重做现有工具卡片或聊天布局。
- renderer 校验采用“专属 renderer 清单 ⊂ backend MCP tool 集合”的模型；未进入清单的工具继续由 `GenericTool` 承接，这是设计约束，不是临时兼容层。
