---
id: BUG-0015
title: Oversized payload not marked as failed with INGESTION_PAYLOAD_TOO_LARGE
status: open
priority: P1
source: e2e-playwright
modules: [ingestion]
discovered: 2026-05-12
discoveredBy: agent
testRunId: null
fixCommit: null
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
TBD — `IngestionPayloadFetcher` may cap and truncate but not set job status to `failed`, or the error path doesn't populate `errorCode` in the MCP output.

## Fix
TBD

## Verification
TBD

## Notes
TBD
