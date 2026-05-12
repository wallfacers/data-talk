---
id: BUG-0014
title: SSRF deny list not blocking 169.254.169.254 with e2e profile
status: fixed
priority: P1
source: e2e-playwright
modules: [ingestion]
discovered: 2026-05-12
discoveredBy: agent
testRunId: null
fixCommit: "pending — code changes in working tree (IngestionConfig.java, IngestionUrlValidator.java, application.yml, application-e2e.yml)"
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
`IngestionConfig.hostDeny` mixed loopback hosts (localhost / 127.0.0.1) with cloud metadata endpoints (169.254.169.254 / metadata.google.internal / metadata.azure.com) into a single list gated by the single `ssrfDenyEnabled` boolean. The e2e profile flipped that boolean off so the loopback mock server was reachable — but that simultaneously released the metadata IPs, which must never be reachable regardless of environment.

`IngestionUrlValidator.validate` only consulted the conditional list, so once `ssrfDenyEnabled=false` was applied no host filtering happened.

## Fix
Split `IngestionConfig` into two deny lists with different enforcement semantics:

- `hostDeny` (default `localhost`, `127.0.0.1`): conditional — toggled off by `ssrfDenyEnabled=false` so the e2e profile can reach the loopback mock.
- `hostDenyAlways` (default `169.254.169.254`, `metadata.google.internal`, `metadata.azure.com`): always enforced, ignores `ssrfDenyEnabled`. Cloud metadata endpoints leak short-lived credentials and must not be opt-out.

`IngestionUrlValidator` now checks `hostDenyAlways` first (unconditionally) and then consults `hostDeny` only when `ssrfDenyEnabled=true`.

`application.yml` and `application-e2e.yml` updated to reflect the new structure. The e2e profile retains `ssrf-deny-enabled: false` but the metadata endpoints stay blocked because they live in `host-deny-always`.

## Verification
- Unit: `IngestionUrlValidatorTest` (9 tests pass) — covers `rejectsAwsMetadataEvenWhenSsrfDenyDisabled` and `allowsLoopbackWhenSsrfDenyDisabled`.
- E2E: `client/tests/e2e/ingestion-error-paths.spec.ts:12` (`INGESTION_SSRF_BLOCKED for deny-listed URL`) — `test.fixme` removed, **PASSED** against backend running with default profile (metadata IP `169.254.169.254` blocked by `hostDenyAlways`).

## Notes
The split also affects future config: any new "always deny" host (e.g. additional cloud provider metadata) should be added to `hostDenyAlways`. Loopback-style hosts that legitimately need to be reachable in tests stay in `hostDeny`.
