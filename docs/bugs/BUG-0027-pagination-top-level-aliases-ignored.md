---
id: BUG-0027
title: `http_request` ignores top-level pagination shortcuts (`param`, `initial`, `pageSize`)
status: fixed
priority: P1
source: e2e-playwright
modules: [ingestion]
discovered: 2026-05-13
discoveredBy: agent
testRunId: null
fixCommit: "uncommitted (Batch 4 follow-up)"
fixPlanRef: docs/exec-plans/2026-05-13-ingestion-e2e-bugs-fix-plan.md
duplicateOf: null
regression: false
---

## Summary
`HttpRequestActionHandler.buildPagination()` 只读 `pagination.params.*` 嵌套对象。E2E spec 与 MCP 友好格式常常把 `param` / `pageSize` / `initial` 放在 `pagination` 顶层，handler 默默忽略 → fetcher 用默认值（`limit=100`），导致分页停止条件偏移、测试断言失败。

## Reproduction Steps
1. 调 `http_request`，pagination 给：
   ```json
   { "type": "offset", "param": "offset", "initial": 0, "pageSize": 2 }
   ```
2. Mock 服务期望 `?offset=0&limit=2` 等参数。

## Expected vs Actual
- **Expected**: fetcher 用 `offsetParam=offset`、`offsetBase=0`、`limit=2`。
- **Actual**: 全部走默认（`offsetParam` 不设 / `limit=100`），分页器停不下来或行数计错。

## Environment
- Backend commit: 95e45c3f (working tree)

## Evidence
- Code: `HttpRequestActionHandler.java` `buildPagination` (改前) 只读 `params`。
- Spec: 多个 `ingestion-*` E2E spec 用 `{ param, initial, pageSize }` 顶层形态。

## Root Cause
canonical 形态是 `pagination.params.{pageParam, limit, offsetBase, ...}`；测试 / MCP 友好形态把这些字段提到顶层简写为 `{param, pageSize, initial}`。handler 没做映射。

## Fix
`buildPagination()` 把顶层 `param` / `pageSize` / `initial` `putIfAbsent` 到 `params`：
- `param` → `pageParam` / `offsetParam` / `cursorParam`（三种 alias 都映射，由 `PaginationType` 选取真正使用的）
- `pageSize` → `limit`
- `initial` → `offsetBase`

`putIfAbsent` 确保用户显式 `params` 优先级最高，不被简写覆盖。

## Verification
- E2E: pending backend restart；`ingestion-pagination-*.spec.ts` 应走通分页循环。

## Notes
此修复不引入新 schema 字段，仅在 adapter 层做兼容映射，domain 层 `PaginationSpec` 保持单一权威。
