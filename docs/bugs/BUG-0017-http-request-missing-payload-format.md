---
id: BUG-0017
title: http_request action output missing `payloadFormat` field
status: fixed
priority: P1
source: e2e-playwright
modules: [ingestion]
discovered: 2026-05-13
discoveredBy: agent
testRunId: null
fixCommit: "40a9a3d4 (Batch 1)"
fixPlanRef: docs/exec-plans/2026-05-13-ingestion-e2e-bugs-fix-plan.md
duplicateOf: null
regression: false
---

## Summary
`http_request` 成功路径返回 `{jobId, payloadArtifactId, status, rowsFetched, bytesFetched, pagesFetched}`，但**缺少 `payloadFormat`**。E2E 测试断言 `res.result.payloadFormat === 'jsonl' / 'csv' / 'html' / 'json'`，全部返回 `undefined`，3 个测试失败。

## Reproduction Steps
1. 后端 `SPRING_PROFILES_ACTIVE=e2e mvn spring-boot:run -pl data-talk-adapter`。
2. `npx playwright test tests/e2e/ingestion-fetch-mcp.spec.ts`。
3. 观察 `fetches JSONL successfully` / `fetches CSV successfully` / `fetches HTML successfully` 三例失败。

## Expected vs Actual
- **Expected**: `res.result.payloadFormat === 'jsonl'` (lowercase string)
- **Actual**: `res.result.payloadFormat === undefined`

## Environment
- Backend commit: c3730a10
- Frontend commit: c3730a10
- OS / Browser: WSL2 / Chromium
- Data source: N/A (HTTP ingestion mock server)

## Evidence
- Playwright spec: `client/tests/e2e/ingestion-fetch-mcp.spec.ts` lines 25 / 31 / 37
- Handler: `server/data-talk-adapter/.../HttpRequestActionHandler.java` lines 92-99 (output map construction)
- Fetcher record: `IngestionPayloadFetcher.java` lines 81-88 (`FetchResult` record — no `format` field)

## Root Cause
`IngestionPayloadFetcher.FetchResult` record exposes 6 fields but no `payloadFormat`. `HttpRequestActionHandler.handle()` only puts those 6 in the result map. `outputSchema()` also doesn't declare `payloadFormat`. The format is computed by the fetcher (from `Content-Type` header / file extension) but discarded after parser dispatch.

## Fix
1. Add `PayloadFormat format` to the `FetchResult` record in `IngestionPayloadFetcher.java`; populate at return site.
2. In `HttpRequestActionHandler.handle()` add `out.put("payloadFormat", result.format().name().toLowerCase())` to the success result.
3. Extend `outputSchema()` to declare `payloadFormat: { type: string }` in properties.

## Verification
- Unit: `HttpRequestActionHandlerTest.payloadFormatEmittedLowercaseForEachFormat` — iterates all four `PayloadFormat` values and asserts the lowercase mapping; happy-path test also extended to assert `payloadFormat == "json"` and `rowCount == 100`. 10 / 10 tests pass.
- Unit: `IngestionPayloadFetcherTest` — existing fetch tests adjusted to the new 7-field `FetchResult` record; 15 / 15 pass.
- E2E: pending backend restart with `SPRING_PROFILES_ACTIVE=e2e`; expect `ingestion-fetch-mcp.spec.ts` lines 25 / 31 / 37 to turn green.

## Notes
Also added `rowCount` as an alias for `rowsFetched` in the output map, so pagination tests that assert `res.result.rowCount` (lines 102 / 113 / 124 of `ingestion-fetch-mcp.spec.ts`) match the contract. Both fields are emitted.
