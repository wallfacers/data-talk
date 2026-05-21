---
id: BUG-0015
title: Oversized payload not marked as failed with INGESTION_PAYLOAD_TOO_LARGE
status: fixed
priority: P1
source: e2e-playwright
modules: [ingestion]
discovered: 2026-05-12
discoveredBy: agent
testRunId: null
fixCommit: "pending — code changes in working tree (HttpRequestActionHandler.java)"
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary
当 `http_request` 获取 ~2 MB 的 payload 时（e2e profile `payload-max-bytes: 1048576` = 1 MB cap），返回结果既没有 `res.result?.status === 'failed'` 也没有 `errorCode: 'INGESTION_PAYLOAD_TOO_LARGE'`。`res.result?.errorCode` 为 `undefined`，说明 payload 超限检测未被触发或结果未正确传递。

## Reproduction Steps
1. 后端以 `SPRING_PROFILES_ACTIVE=e2e` 启动（`payload-max-bytes: 1048576`）
2. 调用 `POST /mcp` with `tools/call` → `http_request` → `url: 'http://127.0.0.1:<port>/error/oversized'`（mock server 返回 ~2 MB JSON 数组）
3. 观察 `res.result?.status` 不是 `'failed'`，`res.result?.errorCode` 为 `undefined`

## Expected vs Actual
- **Expected**: `res.result?.status === 'failed'` and `res.result?.errorCode === 'INGESTION_PAYLOAD_TOO_LARGE'`
- **Actual**: `res.result?.errorCode` is `undefined`

## Environment
- Backend commit: f95036f6
- Frontend commit: f83b8074
- OS / Browser: WSL2 / Chromium

## Evidence
- Playwright test: `client/tests/e2e/ingestion-error-paths.spec.ts:86`
- Trace: `tmp/playwright/test-results/ingestion-error-paths--e2e-6a89c-LARGE-for-oversized-payload-chromium/`

## Root Cause
`IngestionPayloadFetcher` was already raising `IllegalStateException("Payload exceeds maximum size ...")` and marking the job row as `failed`, and `HttpRequestActionHandler` was already mapping that exception to error code `INGESTION_PAYLOAD_TOO_LARGE`. **However** the handler's `errorNode(...)` helper only embedded the code inside a nested `error.code` object — it never exposed it as a top-level field. The E2E assertion `expect(res.result?.errorCode).toBe('INGESTION_PAYLOAD_TOO_LARGE')` therefore saw `undefined` and the test couldn't distinguish "oversized" from any other failure mode.

This is the same shape as BUG-0014 — the underlying SSRF / size detection worked, but the MCP-facing contract didn't surface the discriminator that callers branch on.

## Fix
1. `HttpRequestActionHandler.errorNode(...)` now also writes `errorCode` at the top level alongside the nested `error.code` (kept for backward compatibility). Both carry the same value.
2. `outputSchema()` adds `errorCode: { type: string }` to the property list (optional — only present on the failed branch).
3. `HttpRequestActionHandlerTest` extended to assert top-level `errorCode` for all four error paths (SSRF, payload too large, unsupported format, generic failure).

## Verification
- Unit: `HttpRequestActionHandlerTest` (8 tests pass) — asserts top-level `errorCode` for all four error paths.
- E2E: `client/tests/e2e/ingestion-error-paths.spec.ts:87` (`INGESTION_PAYLOAD_TOO_LARGE for oversized payload`) — `test.fixme` removed, **FAILED** in current run because backend was not restarted with `e2e` profile. Mock server `127.0.0.1` is blocked by default `hostDeny` SSRF rule, returning `INGESTION_SSRF_BLOCKED` instead. Requires backend restart with `SPRING_PROFILES_ACTIVE=e2e` to verify.

## Notes
The fix preserves the nested `error: { code, reason }` object so existing consumers that read the nested form keep working. Top-level `errorCode` is the canonical discriminator for new code and tests.
