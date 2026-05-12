---
id: BUG-0013
title: http_request action returns null for required output fields causing schema validation failure
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

The `datatalk.http_request` action handler (`HttpRequestActionHandler`) returns `jobId: null` and `payloadArtifactId: null` when the fetch operation fails (any exception path). The `outputSchema()` declares these three fields (`jobId`, `payloadArtifactId`, `status`) as **required** strings. The `ActionDispatcher` validates the handler's return value against the output schema, and `null` fails the JSON Schema `type: string` check — producing error `datatalk.http_request.output invalid: [$.payloadArtifactId: null found, string expected, $.jobId: null found, string expected]`.

This means **any error** in the fetch path (SSRF block, payload too large, network failure, unsupported format) results in a secondary schema validation error that masks the original error, and the MCP tool returns `-32602 invalid params` instead of the intended error code.

## Reproduction Steps

1. Start backend with `SPRING_PROFILES_ACTIVE=e2e`
2. Call `http_request` MCP tool with any URL that triggers an error path (e.g., a URL that fails fetch, or without `payloadFormat` when required)
3. Observe the response: `{"code": -32602, "message": "datatalk.http_request.output invalid: [$.payloadArtifactId: null found, string expected, $.jobId: null found, string expected]"}`

## Expected vs Actual

- **Expected**: When fetch fails, the action returns a structured error with `status: "failed"`, `error: { code, reason }`, and the MCP tool surfaces this to the caller.
- **Actual**: The error node has `jobId: null, payloadArtifactId: null` which violates the output schema's `required` constraint, causing a `SchemaValidationException` that masks the original error.

## Environment

- Backend: Spring Boot 3.5, Java 21, `SPRING_PROFILES_ACTIVE=e2e`
- Frontend: Playwright E2E tests
- Data source: N/A (HTTP ingestion)

## Evidence

- Test output: `ingestion-fetch-mcp.spec.ts` all 12 tests fail with same error
- New test: `ingestion-ddl-mcp.spec.ts` fails at `fetchInferConfirm` with `TypeError: Cannot read properties of undefined (reading 'jobId')` because `f.result` is undefined (error path taken)
- Error message: `datatalk.http_request.output invalid: [$.payloadArtifactId: null found, string expected, $.jobId: null found, string expected]`

## Root Cause

`HttpRequestActionHandler.errorNode()` (line 159-170) sets `jobId` and `payloadArtifactId` to `null` for error responses. But `outputSchema()` (line 57-72) declares them as required strings. The `ActionDispatcher.validate()` call at line 84 rejects null-against-string.

Two possible fixes:
1. **Make `jobId` and `payloadArtifactId` optional in `outputSchema()`** (remove from `required` list) — simplest fix, consistent with error-path semantics
2. **Generate placeholder values** for `jobId`/`payloadArtifactId` in error path — less clean

Option 1 is recommended.

## Fix

Remove `jobId` and `payloadArtifactId` from the `required` list in `HttpRequestActionHandler.outputSchema()`, or add them as optional properties with `type: ["string", "null"]`.

## Verification

After fix, re-run:
- `cd client && npx playwright test ingestion-fetch-mcp.spec.ts --reporter=line`
- `cd client && npx playwright test ingestion-ddl-mcp.spec.ts --reporter=line`

## Notes

This bug blocks ALL ingestion E2E tests that use `http_request` (fetch, infer, create_table pipeline).
