---
id: BUG-0024
title: Upstream 401 → `INGESTION_FETCH_FAILED` instead of `INGESTION_AUTH_FAILED`
status: fixed
priority: P1
source: e2e-playwright
modules: [ingestion]
discovered: 2026-05-13
discoveredBy: agent
testRunId: null
fixCommit: "40a9a3d4 (Batch 2)"
fixPlanRef: docs/exec-plans/2026-05-13-ingestion-e2e-bugs-fix-plan.md
duplicateOf: null
regression: false
---

## Summary
`http_request` 携带错误凭据，上游返回 HTTP 401，但 `HttpRequestActionHandler` 把 `HttpClientErrorException.Unauthorized` 当作 generic `Exception` 处理，emit `errorCode: INGESTION_FETCH_FAILED`。E2E 期望 `INGESTION_AUTH_FAILED` 作为 callers 的判别码。

## Reproduction Steps
1. 创建一个 Bearer 凭据，但 secret 设为错误的 token。
2. Mock 服务对带 `Authorization: Bearer test-token-42` 返回 200，其他 token 返回 401。
3. 调用 `http_request` 携带该错误凭据。
4. 检查 `res.result.errorCode`。

## Expected vs Actual
- **Expected**: `errorCode === 'INGESTION_AUTH_FAILED'`
- **Actual**: `errorCode === 'INGESTION_FETCH_FAILED'`

## Environment
- Backend commit: c3730a10
- Frontend commit: c3730a10

## Evidence
- Code: `RestHttpFetchClient.java` lines 30-51 — `.retrieve()` without `.onStatus()` rethrows 4xx as `HttpClientErrorException`.
- Code: `HttpRequestActionHandler.java` lines 113-116 — generic `catch (Exception e)` → `INGESTION_FETCH_FAILED`.
- Spec: `client/tests/e2e/ingestion-error-paths.spec.ts` lines 26-44 (`INGESTION_AUTH_FAILED for wrong credentials`).

## Root Cause
Spring `RestClient.retrieve()` 默认把 4xx/5xx 转抛 `HttpClientErrorException`，不区分状态码语义。`HttpRequestActionHandler` 的 catch 链没有针对 401 / 403 的特化，直接落到末端 generic 桶。

## Fix
1. 在 `RestHttpFetchClient` 加 `.onStatus(HttpStatusCode::is4xxClientError, (req, res) -> { ... })`，把 401 包装成新建的 `IngestionAuthFailedException`。其它 4xx 仍走 generic。
2. 在 `application/ingestion/` 新增 `IngestionAuthFailedException extends RuntimeException`。
3. 在 `HttpRequestActionHandler.handle()` 的 catch 链里：
   ```java
   } catch (IngestionAuthFailedException e) {
       return CompletableFuture.completedFuture(
           errorNode("INGESTION_AUTH_FAILED", e.getMessage(),
                     "Check the credential secret matches the upstream's expected value."));
   } catch (Exception e) {
       ...
   }
   ```

## Verification
- Unit: `HttpRequestActionHandlerTest.authFailedReturns401MappedError` — mock fetcher throws `IngestionAuthFailedException`; assert top-level `errorCode == "INGESTION_AUTH_FAILED"` and nested `error.code` match. Pass.
- New domain class `IngestionAuthFailedException` in `data-talk-domain`.
- E2E: pending backend restart; expect `ingestion-error-paths.spec.ts` `INGESTION_AUTH_FAILED for wrong credentials` test to turn green.

## Notes
403 单独处理是后续扩展（例如 `INGESTION_FORBIDDEN`）。本次只处理 401，避免过度建模。`RestHttpFetchClient` 通过 `.onStatus(status -> status.value() == 401, ...)` 将 401 转换为 `IngestionAuthFailedException`；其他 4xx 仍走默认 `HttpClientErrorException` → `INGESTION_FETCH_FAILED`。
