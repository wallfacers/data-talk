---
id: BUG-0018
title: Basic auth header sent cleartext instead of Base64-encoded
status: fixed
priority: P0
source: e2e-playwright
modules: [ingestion, security]
discovered: 2026-05-13
discoveredBy: agent
testRunId: null
fixCommit: "40a9a3d4 (Batch 1)"
fixPlanRef: docs/exec-plans/2026-05-13-ingestion-e2e-bugs-fix-plan.md
duplicateOf: null
regression: false
---

## Summary
针对 BASIC 凭据，`IngestionPayloadFetcher.resolveHeaders()` 直接把 vault 解密后的明文塞进 `Authorization: Basic <secret>`，未做 Base64 编码。任何符合 RFC 7617 的服务端都会拒绝。当前测试 mock 服务用 `Buffer.from('alice:p@ss').toString('base64')` 校验，header 不匹配 → 测试失败。

## Reproduction Steps
1. 创建 BASIC 凭据，secret 设为 `alice:p@ss`（或 vault 内任意明文密码）。
2. 调用 `http_request` 携带该 `credentialId`。
3. 抓包 / 检查 mock 服务收到的 `Authorization` header。

## Expected vs Actual
- **Expected**: `Authorization: Basic YWxpY2U6cEBzcw==` (Base64 of `alice:p@ss`)
- **Actual**: `Authorization: Basic alice:p@ss` (or `Basic p@ss` — cleartext)

## Environment
- Backend commit: c3730a10
- Frontend commit: c3730a10
- OS / Browser: WSL2 / Chromium
- Data source: N/A

## Evidence
- Code: `IngestionPayloadFetcher.java` line ~240 — `case BASIC -> headers.put("Authorization", "Basic " + secret);`
- Mock contract: `client/tests/e2e/fixtures/ingestion-fixtures.ts` line 111 — `'Basic ' + Buffer.from('alice:p@ss').toString('base64')`
- Spec: `client/tests/e2e/ingestion-fetch-mcp.spec.ts` Basic auth case (~line 79)

## Root Cause
The BASIC branch concatenates `secret` literally. RFC 7617 mandates `Authorization: Basic <base64(username:password)>`. The handler never invokes `Base64.getEncoder().encodeToString(...)`. Side-question: the credential model needs to clarify whether the vault secret is stored already as `user:pass` or just the password (with username elsewhere) — fix must handle whichever shape the service emits.

## Fix
1. Inspect `IngestionCredentialService.readSecret()` to confirm the canonical format. If it returns `user:pass`, just Base64 the whole thing. If it returns only `password`, look up the credential's `username` field and concatenate first.
2. Replace the offending line with:
   ```java
   case BASIC -> headers.put(
       "Authorization",
       "Basic " + Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8)));
   ```
   (adjust `secret` source per step 1).

## Verification
- Unit: `IngestionPayloadFetcherTest.basicCredentialInjectedAsBase64` — exercises `configNonSecret.username='alice'` + `secret='p@ss'` path and asserts the header equals `"Basic " + Base64(alice:p@ss)`. Pass.
- Unit: `IngestionPayloadFetcherTest.basicCredentialFallsBackToSecretAsUserPassWhenNoUsernameSet` — legacy path where the secret is already `user:pass`; asserts the header is Base64 of the secret verbatim. Pass.
- E2E: pending backend restart; expect `ingestion-fetch-mcp.spec.ts` Basic auth case to turn green.

## Notes
**Security-relevant** (priority P0): cleartext-in-header would leak credentials to upstream MITM observers and to upstream logs. The fix reads `username` from `configNonSecret`, concatenates with the vault-sealed secret, and Base64-encodes per RFC 7617. Backward-compat: if no `username` is set, the secret is treated as the pre-concatenated `user:pass` and Base64-encoded as-is.
