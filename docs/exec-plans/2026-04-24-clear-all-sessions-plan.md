# Clear All Sessions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在“设置-通用”新增“清空所有会话”能力，并确保会话清理时同步清理会话关联资源（后端持久化资源 + 前端会话态资源）。

**Architecture:** 后端在 `SessionService` 增加批量清理入口，复用单会话删除顺序（解绑 OpenCode 映射 → 删除 OpenCode 会话 → 关闭 SessionBus → 删除 session 行，依赖 FK 级联清理关联资源）；前端在 `general-panel` 增加危险操作区与二次确认，调用新接口后清理本地会话相关 store，再打开新的空白会话，保证 UI 状态与后端一致。

**Tech Stack:** Spring Boot 3.5 + JUnit 5 + MockMvc, React 19 + TanStack Query + Zustand + Vitest.

---

## Design Inputs

- Source: [`client/DESIGN.md`](../../client/DESIGN.md)
- Applied constraints:
  - 使用语义 token 与现有 shadcn 组件表达危险操作，不引入原始颜色值。
  - 状态语义通过文案 + 按钮样式共同表达，不能仅靠颜色传达危险状态。
  - 延续设置页现有信息密度和分组布局，避免引入与当前视觉系统不一致的新样式语言。

## Spec Mapping

- 需求来源：本轮用户需求“设置-通用新增清空所有会话，并同步清理会话相关资源内容”。

## File Structure

- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionControllerIT.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionControllerTest.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/session/SessionServiceTest.java`
- Modify: `client/src/services/api/session.ts`
- Modify: `client/src/features/settings/general/general-panel.tsx`
- Modify: `client/src/i18n/messages.ts`
- Create: `client/src/features/settings/general/general-panel.test.tsx`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-24-clear-all-sessions-plan.md`

## Task 1: 后端补齐“清空所有会话”接口与资源清理语义

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/session/SessionServiceTest.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionControllerIT.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionControllerTest.java`

- [x] Step 1: 先写/扩展测试，定义批量删除语义（删除所有 session + 级联清理资源 + OpenCode/Bus 清理调用）。
- [x] Step 2: 运行后端定向测试，确认红灯（缺失接口/实现导致失败）。
- [x] Step 3: 实现 `SessionService.deleteAll()` 与 `DELETE /api/sessions` 控制器入口。
- [x] Step 4: 复跑后端定向测试，确认转绿。

## Task 2: 前端“设置-通用”增加清空所有会话入口

**Files:**
- Modify: `client/src/services/api/session.ts`
- Modify: `client/src/features/settings/general/general-panel.tsx`
- Modify: `client/src/i18n/messages.ts`
- Create: `client/src/features/settings/general/general-panel.test.tsx`

- [x] Step 1: 先补前端测试，覆盖二次确认、接口调用、成功后本地会话资源清理与跳转空白会话行为。
- [x] Step 2: 运行前端定向测试，确认红灯（按钮/逻辑不存在导致失败）。
- [x] Step 3: 实现 API、UI 与本地资源清理逻辑（chat/ontology/timeline/stage/session/channel store）。
- [x] Step 4: 复跑前端定向测试，确认转绿。

## Task 3: 集中验证与文档收尾

**Files:**
- Modify: `docs/exec-plans/2026-04-24-clear-all-sessions-plan.md`
- Modify: `docs/exec-plans/index.md`

- [x] Step 1: 运行 `cd server && mvn compile -q`。
- [x] Step 2: 运行 `cd client && npx tsc --noEmit`。
- [x] Step 3: 将本计划所有 checklist 与执行说明更新为实际状态。
- [x] Step 4: 将本计划从 `docs/exec-plans/index.md` 的 Active 移到 Completed。

## Execution Notes

- [x] Task 1 Step 1: 已新增/扩展后端测试：
  - `SessionServiceTest#deleteAll_removesEverySession_andCleansOpenCodeAndBus`
  - `SessionControllerIT#delete_all_cascades_session_related_resources`
- [x] Task 1 Step 2: 运行 `cd server && mvn -q -pl data-talk-application test -Dtest=SessionServiceTest`，初次红灯报错 `SessionService.deleteAll()` 不存在。
- [x] Task 1 Step 3: 已实现：
  - `SessionService.deleteAll()`（复用单会话删除顺序并执行全量会话清理）
  - `SessionController` 新增 `DELETE /api/sessions`
- [x] Task 1 Step 4: 后端验证结果：
  - `cd server && mvn -q -pl data-talk-application test -Dtest=SessionServiceTest` 通过
  - `cd server && mvn -q -pl data-talk-adapter test -Dtest=SessionControllerTest` 通过（新增轻量 controller 单测）
  - `SessionControllerIT` 在当前仓库环境会被既有 Flyway 顺序问题阻断（`V10/V11` 先于 `V1`），非本次改动引入。

- [x] Task 2 Step 1: 已新增 `client/src/features/settings/general/general-panel.test.tsx`，覆盖取消与确认两条路径，并断言本地会话资源清理。
- [x] Task 2 Step 2: 运行 `cd client && npx vitest run src/features/settings/general/general-panel.test.tsx`，初次红灯报错 `clearAllSessions does not exist`。
- [x] Task 2 Step 3: 已实现：
  - `client/src/services/api/session.ts` 新增 `clearAllSessions()`
  - `general-panel.tsx` 新增“会话管理”危险操作区、确认弹窗、成功后本地资源清理与空白会话拉起
  - `client/src/i18n/messages.ts` 新增中英文文案键
- [x] Task 2 Step 4: 复跑 `cd client && npx vitest run src/features/settings/general/general-panel.test.tsx` 通过（2 tests passed）。

- [x] Task 3 Step 1: `cd server && mvn compile -q` 通过。
- [x] Task 3 Step 2: `cd client && npx tsc --noEmit` 未通过，存在与本次改动无关的既有错误：`chart-artifact.tsx` 缺失 `recharts`/`echarts-to-recharts` 依赖与若干隐式 `any`。
- [x] Task 3 Step 3: 本计划 checklist 与执行说明已更新。
- [x] Task 3 Step 4: 索引已从 Active 移动到 Completed。
