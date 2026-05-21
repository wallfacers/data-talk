---
id: BUG-0021
title: CURSOR pagination misses top-level `next` key
status: fixed
priority: P1
source: e2e-playwright
modules: [ingestion]
discovered: 2026-05-13
discoveredBy: agent
testRunId: null
fixCommit: "40a9a3d4 (Batch 3)"
fixPlanRef: docs/exec-plans/2026-05-13-ingestion-e2e-bugs-fix-plan.md
duplicateOf: null
regression: false
---

## Summary
`JsonAccumulator.extractCursor()` 在顶层只搜 `next_cursor / nextCursor / cursor / next_page_token / nextPageToken`，没有 `next`。但 mock 服务（和很多真实 API）就把游标值放在顶层 `next` 字段。结果首次抽取返回 null → 循环只跑 1 页就停。

## Reproduction Steps
1. Mock 服务对 `/paginated/cursor` 返回 `{ items: [...], next: 'B' }` (and `null` 结尾)。
2. 调用 `http_request` with `pagination: { type: 'cursor' }`。
3. 期待 3 个请求（cursors A → B → C）；实际只 1 个。

## Expected vs Actual
- **Expected**: 3 HTTP requests, 5 rows total.
- **Actual**: 1 HTTP request, 2 rows.

## Environment
- Backend commit: c3730a10
- Frontend commit: c3730a10

## Evidence
- Code: `IngestionPayloadFetcher.java` line 398 (top-level cursor key list — missing `"next"`) vs line 406 (nested list — includes `"next"`).
- Spec: `client/tests/e2e/ingestion-fetch-mcp.spec.ts` ~line 123 (cursor pagination, `expect(mock.requests).toBeGreaterThanOrEqual(3)`).
- Fixture: `client/tests/e2e/fixtures/ingestion-fixtures.ts` lines 134-138.

## Root Cause
Two key lists in `JsonAccumulator.extractCursor()` are inconsistent — the nested-object search includes `"next"`, the top-level search does not. The most common shape (`{ items, next }`) hits the top-level path and silently returns null.

## Fix
Add `"next"` to the top-level key array:
```java
for (String key : new String[]{
    "next_cursor", "nextCursor", "cursor",
    "next_page_token", "nextPageToken", "next" // add
}) { ... }
```
Optionally also add `"end_cursor"` / `"endCursor"` for symmetry with the nested list, but `next` is the test-driving one.

## Verification
- Unit: `IngestionPayloadFetcherTest.cursorPaginationFollowsTopLevelNextKey` — 3 mocked responses with top-level `next: 'B' / 'C' / null`; asserts 3 fetches and 5 rows total. Pass.
- E2E: pending backend restart; expect `ingestion-fetch-mcp.spec.ts` cursor pagination case (`mock.hits >= 3`, `rowCount === 5`) to turn green.

## Notes
Fixed in the same commit as BUG-0019 / BUG-0020 (single batch — all three live in `IngestionPayloadFetcher`).
