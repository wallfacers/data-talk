# Diagnostics Follow-up Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 diagnostics 首轮实现中评审确认的高优先级契约与行为问题（上下文透传、错误语义、前后端类型契约）。

**Architecture:** 保持现有四层依赖方向不变，在 application 层修正 `DiagnosticResult` 传播语义，在 infrastructure 层补齐 diagnostics 的 database/schema 上下文应用，在 client 层对齐后端响应联合类型并补齐 error 渲染分支。

**Tech Stack:** Java 21, Spring Boot 3.5, JUnit 5, React 19, TypeScript, Vitest.

## Design Inputs

- 来源：`client/DESIGN.md`
- 约束 1：保持现有语义 token 与双主题契约，不新增或偏离主题语义。
- 约束 2：本次前端改动限定为类型契约与错误态渲染，不改变现有信息密度/布局结构。
- 约束 3：沿用既有组件与交互模式（`BasicTool` + shadcn/ui），不引入新的视觉语言。

---

### Task 1: DiagnosticsService 错误语义修复（TDD）

**Files:**
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/diagnostics/DiagnosticsServiceTest.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/diagnostics/DiagnosticsService.java`

- [x] **Step 1: 新增失败测试，覆盖 `indexHints` 在 EXPLAIN 失败时的语义**
- [x] **Step 2: 运行 application 定向测试并确认新增用例先失败**
- [x] **Step 3: 最小实现修复（unsupported 透传、diagnosticError 透传）**
- [x] **Step 4: 重跑定向测试并确认通过**

### Task 2: Provider 执行上下文修复（TDD）

**Files:**
- Modify: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/MySqlDiagnosticsProviderTest.java`
- Modify: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/PostgreSqlDiagnosticsProviderTest.java`
- Modify: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/H2DiagnosticsProviderTest.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/MySqlDiagnosticsProvider.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/PostgreSqlDiagnosticsProvider.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/H2DiagnosticsProvider.java`

- [x] **Step 1: 新增失败测试，覆盖 database 覆盖与 schema 应用 helper 行为**
- [x] **Step 2: 运行 infrastructure 定向测试并确认先失败**
- [x] **Step 3: 最小实现修复（连接 URL 使用 database override；PG/H2 应用 schema）**
- [x] **Step 4: 重跑定向测试并确认通过**

### Task 3: 前端 diagnostics 响应契约收敛（TDD）

**Files:**
- Modify: `client/src/features/chat/components/tools/renderers/diagnostics-card.test.tsx`
- Modify: `client/src/features/stage/types/diagnostics.ts`
- Modify: `client/src/features/chat/components/tools/renderers/diagnostics-card.tsx`

- [x] **Step 1: 新增失败测试，覆盖 diagnostics error 输出渲染**
- [x] **Step 2: 运行前端定向测试并确认先失败**
- [x] **Step 3: 最小实现修复（`ExplainResult/IndexHintsResponse` 联合类型补齐 error 分支，对齐 `summary` 字段）**
- [x] **Step 4: 重跑定向测试并确认通过**

### Task 4: 统一验证与文档收尾

**Files:**
- Modify: `docs/exec-plans/2026-04-28-diagnostics-followup-fixes-plan.md`
- Modify: `docs/exec-plans/index.md`

- [x] **Step 1: 运行后端编译验证：`cd server && mvn compile -q`**
- [x] **Step 2: 运行前端类型验证：`cd client && npx tsc --noEmit`**
- [x] **Step 3: 在计划文件中勾选任务并记录验证结果**
- [x] **Step 4: 将本计划从 Active 移动到 Completed**

## Verification Evidence

- `cd server && mvn -q -pl data-talk-application test -Dtest=DiagnosticsServiceTest`（先红后绿）
- `cd server && mvn -q -pl data-talk-infrastructure test -Dtest=MySqlDiagnosticsProviderTest,PostgreSqlDiagnosticsProviderTest,H2DiagnosticsProviderTest`（先红后绿）
- `cd client && npx vitest run src/features/chat/components/tools/renderers/diagnostics-card.test.tsx`（先红后绿）
- `cd server && mvn -q -pl data-talk-application,data-talk-infrastructure test -Dtest=DiagnosticsServiceTest,MySqlDiagnosticsProviderTest,PostgreSqlDiagnosticsProviderTest,H2DiagnosticsProviderTest`（绿）
- `cd server && mvn compile -q`（绿）
- `cd client && npx tsc --noEmit`（绿）
