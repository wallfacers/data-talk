---
id: BUG-0034
title: `executeSql` fixture 缺 `source` 字段，后端 `validateSource` 抛 `IllegalArgumentException` → 400
status: fixed
priority: P2
source: e2e-playwright
modules: [ingestion, testing]
discovered: 2026-05-13
discoveredBy: agent
testRunId: null
fixCommit: "uncommitted (Batch 5 hotfix)"
fixPlanRef: docs/exec-plans/2026-05-13-ingestion-e2e-bugs-fix-plan.md
duplicateOf: null
regression: false
---

## Summary
`tests/e2e/fixtures/adapter-client.ts` 的 `executeSql` 把 caller body 透传到 `POST /api/sql/execute`。Ingestion E2E 测试只传 `{connectionId, sql}` 做简单 SELECT 烟测，没传 `source`。后端 `SqlExecuteService.validateSource()` 要求 `source ∈ {'user','ai'}`，否则抛 `IllegalArgumentException("error.sql.source_invalid")` → HTTP 400。

## Reproduction Steps
1. 跑 `ingestion-ddl-mcp.spec.ts:28 H2 round-trip — CREATE TABLE then SELECT`。
2. 倒数第二步 `c.executeSql({ connectionId, sql: 'SELECT COUNT(*) FROM e2e_users' })` 返 400.

## Expected vs Actual
- **Expected**: SELECT smoke 检查通过。
- **Actual**: HTTP 400, `sel.ok() === false`。

## Environment
- Frontend commit: working tree

## Root Cause
SQL execute API 设计上区分 `source: 'user'` vs `source: 'ai'` 用于 audit / risk policy；fixture 没有默认值，所有调用方都得显式传，但 ingestion smoke check 调用没注意到。

## Fix
fixture 加默认 `source: 'user'`：
```ts
executeSql: (body) => request.post(`${BASE}/api/sql/execute`, { data: { source: 'user', ...body } })
```
spread 让 caller override 仍然生效。

## Verification
- E2E: `ingestion-ddl-mcp.spec.ts:28` 通过。
- E2E: `ingestion-execute-mcp.spec.ts:110` 倒数 SELECT 通过。

## Notes
另一种可选修复是后端在 source 缺省时 default `'user'`，但 risk policy 路径会变（user-source 默认走"无 ack 仅 L1"），改 API 默认行为风险大于改测试 helper。fixture 修改不影响产品代码。
