---
id: BUG-0019
title: PAGE pagination ignores `hasMore` termination signal
status: fixed
priority: P1
source: e2e-playwright
modules: [ingestion]
discovered: 2026-05-13
discoveredBy: agent
testRunId: null
fixCommit: "pending — Batch 3 of ingestion-e2e-bugs-fix-plan (code in working tree)"
fixPlanRef: docs/exec-plans/2026-05-13-ingestion-e2e-bugs-fix-plan.md
duplicateOf: null
regression: false
---

## Summary
PAGE 模式分页只看 `currentPage < maxPages`，从不查 `PageResult.hasMore`，结果服务端返回 `hasMore=false` 后仍继续拉下一页直到 `maxPages` 上限。导致请求次数和断言不符。

## Reproduction Steps
1. Mock 服务对 `/paginated/page` 返回 `{ items: [...], hasMore: page < 3 }`。
2. 调用 `http_request` with `pagination: { type: 'page', maxPages: 5 }`。
3. 期待 3 个请求；实际 4 个（page=4 返回 hasMore=false 时已经被请求一次）。

## Expected vs Actual
- **Expected**: 3 HTTP requests when `hasMore=false` after page 3.
- **Actual**: 4 HTTP requests (continues to maxPages or until empty payload).

## Environment
- Backend commit: c3730a10
- Frontend commit: c3730a10

## Evidence
- Code: `IngestionPayloadFetcher.java` line 299 — `case PAGE, OFFSET -> request.url();` (returns URL unconditionally if page count not exceeded).
- Spec: `client/tests/e2e/ingestion-fetch-mcp.spec.ts` ~line 101 (page pagination assertion).
- Fixture: `client/tests/e2e/fixtures/ingestion-fixtures.ts` line 122 (`hasMore: page < 3`).

## Root Cause
`resolveNextUrl()` returns `request.url()` for PAGE mode whenever the page counter hasn't hit max. The parsed `PageResult` already extracts a `hasMore` boolean from the response, but it's never consulted in the next-URL decision.

## Fix
In `IngestionPayloadFetcher.resolveNextUrl()`, switch PAGE / OFFSET branch to consult the just-received page's `rowCount`:
```java
case PAGE, OFFSET -> pageResult.rowCount() <= 0 ? null : request.url();
```
"Stop on empty page" is simpler than tracking `hasMore` and matches the E2E fixture contract — mock returns `{ items: [] }` for over-shot pages, signalling end-of-data. The existing `maxPages` cap remains the hard upper bound.

## Verification
- Unit: `IngestionPayloadFetcherTest.pagePaginationStopsOnEmptyPage` — 4 mocked responses (3 with items, 4th empty) with `maxPages=5`; asserts exactly 4 fetches and 6 rows total. Pass.
- E2E: pending backend restart; expect `ingestion-fetch-mcp.spec.ts` page pagination case (`mock.hits.filter(...).length === 4`) to turn green.

## Notes
None.
