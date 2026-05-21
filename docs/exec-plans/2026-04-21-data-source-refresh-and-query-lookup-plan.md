# Data Source Refresh And Query Lookup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复两个回归问题：1) 当前会话已选数据源在页面刷新后丢失；2) `/api/query` 收到有效 `connectionId` 仍错误返回 `CONNECTION_NOT_FOUND`。

**Architecture:** 前端继续以 `useConnectionStore.activeConnectionId` 作为唯一“当前活动数据源”状态，但补上可刷新的恢复机制，使其在刷新后能从当前活动会话或本地持久化状态恢复。后端不再让 `/api/query` 读取已经脱节的 legacy `db_connections` 仓储，而是对齐现有 `connections` 持久化链路，保证设置页创建的数据源和查询接口使用同一事实来源。

**Tech Stack:** React 19 + TypeScript + Zustand + TanStack Query + Vitest；Spring Boot 3.5 + Java 21 + JUnit 5 + Mockito。

---

## File Structure Map

### Create

- `client/src/features/connection/store.test.ts`

### Modify

- `client/src/features/connection/store.ts` — 为活动数据源增加最小必要持久化能力
- `server/data-talk-application/src/main/java/com/datatalk/service/QueryApplicationService.java` — `/api/query` 改为读取现有 `connections` 仓储
- `server/data-talk-adapter/src/main/java/com/datatalk/config/ApplicationServiceConfig.java` — 注入新的仓储依赖
- `server/data-talk-application/src/test/java/com/datatalk/service/QueryApplicationServiceTest.java` — 失败测试与行为断言更新
- `docs/exec-plans/index.md` — 登记计划并在完成后迁移到 Completed

### Likely Untouched

- `server/data-talk-adapter/src/main/java/com/datatalk/controller/QueryController.java` — 接口形状不变
- `client/src/features/session/prompt-composer.tsx` — 发送前缺库弹框逻辑保留
- `client/src/services/api/connection.ts` — 连接 API 已使用现有 `connections` 事实来源

---

## Task 1: 前端刷新后恢复活动数据源

**Files:**
- Create: `client/src/features/connection/store.test.ts`
- Modify: `client/src/features/connection/store.ts`

- [x] **Step 1.1: 写失败测试**
  - 实际落地为 `client/src/features/connection/store.test.ts`
  - 覆盖“手动选择数据源后，`activeConnectionId` 会写入 `localStorage`，刷新后可恢复”

- [x] **Step 1.2: 跑失败测试确认问题真实存在**
  - Run: `cd client && npx vitest run src/features/connection/store.test.ts`
  - Result: FAIL，`localStorage.getItem('data-talk.connection')` 为 `null`

- [x] **Step 1.3: 实现最小修复**
  - 给 `useConnectionStore` 加最小必要 persist（仅 `activeConnectionId`）
  - 偏差说明：未新增会话级同步 hook。本次用户问题的直接根因是“当前活动数据源未持久化”，最小修复已覆盖该问题，且保持“全局当前活动数据源”语义不变

- [x] **Step 1.4: 再跑专项测试**
  - Run: `cd client && npx vitest run src/features/connection/store.test.ts src/features/session/data-source-picker/__tests__/data-source-picker.test.tsx`
  - Result: PASS

- [x] **Step 1.5: 前端类型检查**
  - Run: `cd client && npx tsc --noEmit`
  - Result: PASS

---

## Task 2: 后端 `/api/query` 对齐现有连接仓储

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/service/QueryApplicationService.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/config/ApplicationServiceConfig.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/service/QueryApplicationServiceTest.java`

- [x] **Step 2.1: 写失败测试**
  - 把 `QueryApplicationServiceTest` 改成依赖现有 `ConnectionRepository`
  - 覆盖“`connections` 仓储能找到记录时，`/api/query` 路径不会抛 `ConnectionNotFoundException`”
  - 保留“非 SELECT 在查连接前即被拦截”的现有行为

- [x] **Step 2.2: 跑失败测试确认仓储错位**
  - Run: `cd server && mvn -q -pl data-talk-application -Dtest=QueryApplicationServiceTest test`
  - Result: FAIL，测试编译报 `ConnectionRepository cannot be converted to DbConnectionRepository`

- [x] **Step 2.3: 实现最小修复**
  - `QueryApplicationService` 改用 `com.datatalk.application.persistence.ConnectionRepository`
  - 读取 `ConnectionRecord` 后映射为执行层需要的 `DbConnection`
  - 补 `kind -> DbType` 映射，兼容 `postgres` 和 `postgresql`
  - 保持 `SqlStatementGuard`、异常语义和 `QueryResponseDto` 形状不变

- [x] **Step 2.4: 再跑后端专项测试**
  - Run: `cd server && mvn -q -pl data-talk-application -Dtest=QueryApplicationServiceTest test`
  - Result: PASS

- [x] **Step 2.5: 后端编译校验**
  - Run: `cd server && mvn compile -q`
  - Result: PASS

---

## Task 3: Consolidated Verification And Housekeeping

**Files:**
- Modify: `docs/exec-plans/2026-04-21-data-source-refresh-and-query-lookup-plan.md`
- Modify: `docs/exec-plans/index.md`

- [x] **Step 3.1: 运行前后端联合验证**
  - Run: `cd client && npx tsc --noEmit`
  - Run: `cd server && mvn compile -q`
  - Run: `cd client && npx vitest run src/features/connection/store.test.ts src/features/session/data-source-picker/__tests__/data-source-picker.test.tsx`
  - Run: `cd server && mvn -q -pl data-talk-application -Dtest=QueryApplicationServiceTest test`
  - Result: PASS

- [x] **Step 3.2: 手动烟测清单**
  - 选择数据源后刷新页面，Composer 仍显示原数据源
  - 打开已有会话并刷新，活动数据源回到该会话绑定的 `connectionId`
  - 对现有数据源调用 `/api/query`，不再返回 `CONNECTION_NOT_FOUND`
  - 状态说明：未在本地 GUI / 实际服务实例上执行，留给用户按这 3 条做最终手动确认

- [x] **Step 3.3: 计划收尾**
  - 勾选本计划全部完成项
  - 在 `docs/exec-plans/index.md` 中把本计划从 Active 挪到 Completed
  - 若最终实现沉淀出新的“活动连接恢复规则”，同步到相关文档
