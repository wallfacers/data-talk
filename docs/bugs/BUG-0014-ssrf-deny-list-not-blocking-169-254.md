---
id: BUG-0014
title: SSRF deny list not blocking 169.254.169.254 with e2e profile
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
当后端以 `SPRING_PROFILES_ACTIVE=e2e` 启动时，`ssrf-deny-enabled: false` 关闭了 SSRF 拦截，导致对 `http://169.254.169.254/latest/meta-data/`（AWS 元数据端点）的请求未被拦截。预期：即使 e2e profile 下，AWS metadata IP 仍应在默认 deny 列表中。

## Reproduction Steps
1. 后端以 `SPRING_PROFILES_ACTIVE=e2e` 启动
2. 调用 `POST /mcp` with `tools/call` → `http_request` → `url: 'http://169.254.169.254/latest/meta-data/'`
3. 观察请求未被拦截，返回了 HTTP 响应而非 `INGESTION_SSRF_BLOCKED`

## Expected vs Actual
- **Expected**: `INGESTION_SSRF_BLOCKED` error code
- **Actual**: Request proceeds and returns HTTP response (status depends on network)

## Environment
- Backend commit: f95036f6
- Frontend commit: f83b8074
- OS / Browser: WSL2 / Chromium
- Data source: N/A

## Evidence
- Playwright test: `client/tests/e2e/ingestion-error-paths.spec.ts:12`
- Trace: `tmp/playwright/test-results/ingestion-error-paths--e2e-73568-BLOCKED-for-deny-listed-URL-chromium/`

## Root Cause
TBD — `ssrf-deny-enabled: false` may disable the entire SSRF filter rather than just the loopback release.

## Fix
TBD — Consider splitting SSRF config into `loopback-allowed: true` (for e2e mock server) while keeping default deny list (169.254, 10.0.0.0/8, etc.) active.

## Verification
TBD

## Notes
TBD
