# Session Review Follow-Ups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 session 列表失效范围过窄，以及重启后后台 SSE 订阅可能因过期 streaming 标记而泄漏的问题。

**Architecture:** 仅调整前端会话事件消费与后台订阅恢复逻辑。`buildEventSink` 统一走已有的 session 列表广域失效入口；`useBackgroundSessionSubscribe` 在恢复持久化 streaming 标记前，先查询会话消息历史做一次活性判断，只在仍存在未完成 assistant turn 时恢复 SSE 订阅。

**Tech Stack:** React 19, TanStack Query, Zustand, Vitest, TypeScript

---

### Task 1: 扩大会话列表缓存失效范围

**Files:**
- Modify: `client/src/services/channel/use-channel.ts`
- Test: `client/src/services/channel/use-channel.test.ts`

- [ ] **Step 1: 写失败测试**
- [x] **Step 1: 写失败测试**
  在 `buildEventSink → session.created / session.deleted` 测试中，断言调用 `invalidateSessionLists(queryClient)` 对应的广域失效，而不是 `['sessions', connectionId]` 单 key 失效。

- [x] **Step 2: 运行测试确认失败**
  Run: `cd client && npx vitest run src/services/channel/use-channel.test.ts`
  Expected: `session.created / session.deleted` 相关断言失败。

- [x] **Step 3: 写最小实现**
  将 `session.created` / `session.deleted` 分支从定向 `invalidateQueries({ queryKey: ['sessions', connectionId ?? null] })` 改为调用已有 `invalidateSessionLists(queryClient)`。

- [x] **Step 4: 运行测试确认通过**
  Run: `cd client && npx vitest run src/services/channel/use-channel.test.ts`
  Expected: 目标断言通过。

### Task 2: 后台 SSE 恢复前增加 streaming 活性校验

**Files:**
- Modify: `client/src/features/session/hooks/use-background-session-subscribe.ts`
- Test: `client/src/features/session/hooks/__tests__/use-background-session-subscribe.test.tsx`

- [x] **Step 1: 写失败测试**
  增加两个场景：
  1. 历史消息显示 assistant turn 已完成时，不恢复后台订阅，并清掉该 session 的 `streamingBySession` 标记。
  2. 历史消息显示 assistant turn 未完成时，恢复后台订阅。

- [x] **Step 2: 运行测试确认失败**
  Run: `cd client && npx vitest run src/features/session/hooks/__tests__/use-background-session-subscribe.test.tsx`
  Expected: 新增场景至少一项失败。

- [x] **Step 3: 写最小实现**
  在 `useBackgroundSessionSubscribe` 内新增一次静默历史查询，解析消息列表是否存在未完成 assistant message。仅在“活跃”时建立 SSE；否则调用 `setStreaming(sessionId, false)` 清除过期标记。

- [x] **Step 4: 运行目标测试与类型检查**
  Run: `cd client && npx vitest run src/services/channel/use-channel.test.ts src/features/session/hooks/__tests__/use-background-session-subscribe.test.tsx`
  Expected: 两个测试文件全部通过。
  Run: `cd client && npx tsc --noEmit`
  Expected: exit 0.

## Outcome

- 已将 `session.created` / `session.deleted` 统一改为广域失效 `['sessions']`。
- 已为后台 SSE 恢复增加消息历史活性校验；历史已完成时清理过期 `streamingBySession`，避免重启后孤儿订阅。
- 验证结果：
  - `cd client && npx vitest run src/services/channel/use-channel.test.ts src/features/session/hooks/__tests__/use-background-session-subscribe.test.tsx`
  - `cd client && npx tsc --noEmit`
