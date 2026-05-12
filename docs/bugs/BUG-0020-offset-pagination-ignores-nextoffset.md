---
id: BUG-0020
title: OFFSET pagination ignores `nextOffset=null` termination
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
OFFSET 模式与 PAGE 共用同一段终止条件 (`resolveNextUrl` 仅检查 `currentPage < maxPages`)，从不消费 `PageResult.nextOffset`。当服务端用 `nextOffset=null` 表示末尾时，循环仍继续。

## Reproduction Steps
1. Mock 服务对 `/paginated/offset` 返回 `{ items: [...], nextOffset: offset + 2 < 6 ? offset + 2 : null }`。
2. 调用 `http_request` with `pagination: { type: 'offset', maxPages: 5 }`。
3. 期待 3 个请求 (offsets 0, 2, 4)；实际 4 个。

## Expected vs Actual
- **Expected**: stop when `nextOffset == null`.
- **Actual**: continues until `maxPages` cap or empty payload.

## Environment
- Backend commit: c3730a10
- Frontend commit: c3730a10

## Evidence
- Code: `IngestionPayloadFetcher.java` line 299 — same line as BUG-0019.
- Spec: `client/tests/e2e/ingestion-fetch-mcp.spec.ts` ~line 112 (offset pagination assertion).
- Fixture: `client/tests/e2e/fixtures/ingestion-fixtures.ts` line 129 (`nextOffset: offset + 2 < 6 ? offset + 2 : null`).

## Root Cause
Same `resolveNextUrl()` switch branch as BUG-0019 — OFFSET mode shares the PAGE case with no offset-specific termination check. `pageResult.nextOffset()` is computed but ignored.

## Fix
Same branch as BUG-0019 — "stop on empty page" covers both PAGE and OFFSET:
```java
case PAGE, OFFSET -> pageResult.rowCount() <= 0 ? null : request.url();
```
No explicit `nextOffset` check needed — when the upstream returns `nextOffset=null` and the fetcher requests the next slice, the upstream responds with `{ items: [] }` and the empty-page guard terminates.

## Verification
- Unit: `IngestionPayloadFetcherTest.offsetPaginationStopsOnEmptyPage` — 4 mocked responses (3 with items, 4th empty), `maxPages=5`; asserts 4 fetches and 6 rows. Pass.
- E2E: pending backend restart; expect `ingestion-fetch-mcp.spec.ts` offset pagination case to turn green.

## Notes
Fixed in the same commit as BUG-0019 / BUG-0021 (single batch — all three live in `resolveNextUrl` / `extractCursor`).
