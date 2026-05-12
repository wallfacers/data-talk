---
title: Ingestion E2E BUGs Fix Plan (Batch BUG-0017 → BUG-0024)
date: 2026-05-13
status: active
owner: agent
related:
  - docs/exec-plans/2026-05-12-ingestion-e2e-playwright-plan.md
  - docs/bugs/BUG-0013-http-request-null-output-fields.md
  - docs/bugs/BUG-0014-ssrf-deny-list-not-blocking-169-254.md
  - docs/bugs/BUG-0015-oversized-payload-not-marked-failed.md
---

# Ingestion E2E BUGs Fix Plan

## Background

After fixing BUG-0013/0014/0015 and infrastructure issues (sqlite3 CLI, session seeding, e2e profile), the 11 ingestion E2E spec files now run **stably**. The remaining failures (22 out of 54 active tests in API/MCP suites, plus 19 UI tests with missing seed data) are **real backend defects** — field-name misalignment, auth encoding, pagination, parser type coercion, error-code surfacing. This plan groups them into 8 distinct BUGs (BUG-0017 → BUG-0024) and 5 execution batches.

UI tests (`ingestion-job-tab-ui.spec.ts`, `ingestion-library-tab-ui.spec.ts`) are **out of scope** — their failures stem from missing seed jobs, not product bugs. They will be revisited in a separate plan after the API layer is green.

## Goals

- Diagnose and fix 8 distinct backend BUGs revealed by the E2E suite.
- Pass at least 50 / 54 of the API + MCP tests (32 currently green).
- Each fix has a focused unit test before the production change lands (TDD).
- All 8 BUGs registered in `docs/bugs/` and transitioned to `fixed` with `fixCommit` populated.

## Scope (in)

- `server/data-talk-adapter` — `HttpRequestActionHandler`, `CreateIngestionTableActionHandler`, `IngestionController.confirm`.
- `server/data-talk-application` — `IngestionPayloadFetcher`, `HttpFetchClient` / `RestHttpFetchClient`, parsers (`CsvPayloadParser`, `HtmlTablePayloadParser`, `TypeInferrer`, `DotPathFlattener`), `IngestionConfirmedTokenStore`.
- BUG documents BUG-0017 → BUG-0024.

## Scope (out)

- UI-suite tests (`ingestion-{job,library}-tab-ui.spec.ts`) — separate seeding plan.
- 4 already-`test.fixme`'d tests (`FORMAT_UNSUPPORTED` Content-Type contract, Token TTL covered by unit, `delete in-use 409` cross-test lifecycle, AGENTS.md endpoint missing).
- Frontend changes (none expected — bugs are backend-only).

## Design Inputs

N/A (backend-only — `client/DESIGN.md` frontend design contract does not apply).

## Data Source Type Compatibility Gate

N/A — no new database/dialect types introduced, no JDBC routing changes. Existing handling stays.

## BUG Registry

| BUG | Title | Priority | Module |
|-----|-------|----------|--------|
| BUG-0017 | `http_request` action output missing `payloadFormat` field | P1 | ingestion |
| BUG-0018 | Basic auth header sent cleartext instead of Base64 | P0 | ingestion / security |
| BUG-0019 | PAGE pagination ignores `hasMore` termination signal | P1 | ingestion |
| BUG-0020 | OFFSET pagination ignores `nextOffset=null` termination | P1 | ingestion |
| BUG-0021 | CURSOR pagination misses top-level `next` key | P1 | ingestion |
| BUG-0022 | CSV / HTML parsers emit raw strings — no numeric / boolean coercion | P1 | ingestion |
| BUG-0023 | `INTEGER_64` promotion gap for values > 2^31 | P2 | ingestion |
| BUG-0024 | Upstream 401 → `INGESTION_FETCH_FAILED` instead of `INGESTION_AUTH_FAILED` | P1 | ingestion |

Two issues investigated and **excluded** from this plan:

- `confirm` NPE on null mapping — agent investigation shows `MappingHash.compute(null)` likely already null-safe via `MappingHash`; will be addressed only if the dedicated unit test reproduces the failure. Not registered as a BUG until evidence stands.
- Heterogeneous-value STRING fallback — analysis shows the existing widest-vote logic already produces `STRING_64` for `{int, string}` columns, which matches the test regex `/STRING/`. Not a bug.

## Execution Batches

Batches are **independent** unless marked otherwise. Per CLAUDE.md "Parallel Plan Execution", independent tasks within a batch are dispatched in parallel; cross-batch dependencies remain sequential because they share files.

### Batch 1 — BUG-0017 (output field) + BUG-0018 (Basic auth)

Independent edits across `HttpRequestActionHandler.java` (Batch 1a) and `IngestionPayloadFetcher.java` Basic auth branch (Batch 1b). Single `mvn compile` after both.

**Files:**
- `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionPayloadFetcher.java`
  - Add `PayloadFormat format` to `FetchResult` record + populate in `fetch()` return.
  - In `resolveHeaders()` Basic auth branch, treat the secret as `username:password` and Base64-encode before emitting the `Authorization` header.
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ingestion/HttpRequestActionHandler.java`
  - Emit `payloadFormat` (lowercased) in the success result map.
  - Add `payloadFormat` to `outputSchema()` properties.

**Tests:**
- `HttpRequestActionHandlerTest` — extend a JSONL / CSV / HTML success case to assert `payloadFormat`.
- New `IngestionPayloadFetcherBasicAuthTest` (or extend existing) — assert `Authorization: Basic <base64(user:pass)>`.

### Batch 2 — BUG-0024 (401 mapping)

Independent of Batch 1. Single file pair.

**Files:**
- `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/HttpFetchClient.java` (or `RestHttpFetchClient.java`)
  - Add `.onStatus(HttpStatusCode::is4xxClientError, …)` handler that wraps 401 → new `IngestionAuthFailedException`.
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ingestion/HttpRequestActionHandler.java`
  - Add `catch (IngestionAuthFailedException e)` branch before the generic `Exception` mapping → emit `errorCode: INGESTION_AUTH_FAILED`.
- New `IngestionAuthFailedException` in `application/ingestion/`.

**Tests:**
- `HttpRequestActionHandlerTest` — new test: mock 401 upstream, assert `errorCode == "INGESTION_AUTH_FAILED"`.

### Batch 3 — BUG-0019 / BUG-0020 / BUG-0021 (pagination)

Single file (`IngestionPayloadFetcher.java`) with three cohesive defects. Treated as one batch / one commit.

**Files:**
- `IngestionPayloadFetcher.java` — `resolveNextUrl(...)`:
  - For PAGE mode: return `null` if `pageResult.hasMore() == false`.
  - For OFFSET mode: return `null` if `pageResult.nextOffset() == null`.
- `IngestionPayloadFetcher.java` — `JsonAccumulator.extractCursor(...)`:
  - Add `"next"` to the top-level key list (currently only nested-scoped).

**Tests:**
- `IngestionPayloadFetcherPaginationTest` (new or extend existing) — three test cases, one per mode, asserting correct stop condition and total request count.

### Batch 4 — BUG-0022 (CSV/HTML coercion) + BUG-0023 (INTEGER_64)

Shared parser changes, single batch.

**Files:**
- `application/ingestion/parser/CsvPayloadParser.java` — replace raw `String val` append with a small `coerceCsvValue(String)` that tries `parseLong` / `parseDouble` / `parseBoolean` fall-through to `String`.
- `application/ingestion/parser/HtmlTablePayloadParser.java` — same coercer.
- `application/ingestion/parser/TypeInferrer.java` — keep widest-vote logic; ensure `INTEGER_64` chosen when `Long` votes alongside `Integer`. Verify boundary (`> Integer.MAX_VALUE`) is correctly producing `Long` from coercer.

**Tests:**
- Extend `CsvPayloadParserTest`, `HtmlTablePayloadParserTest` for numeric/boolean coercion.
- Extend `TypeInferrerTest` with explicit `INTEGER_64` boundary case.

### Batch 5 — Verification

- `mvn -pl data-talk-adapter,data-talk-application test` — full backend test suite green.
- Restart backend with `SPRING_PROFILES_ACTIVE=e2e`.
- `npx playwright test tests/e2e/ingestion-fetch-mcp.spec.ts tests/e2e/ingestion-infer-mcp.spec.ts tests/e2e/ingestion-execute-mcp.spec.ts tests/e2e/ingestion-ddl-mcp.spec.ts tests/e2e/ingestion-error-paths.spec.ts --reporter=line` — expected ≥ 50 / 54 passing.
- For each fixed BUG: update `status: fixed`, fill `fixCommit`, sync `docs/bugs/index.md`.

## Risks & Known Issues

- **Confirm NPE diagnosis uncertain**: Agent D flagged a possible NPE in `IngestionController.confirm` when `mapping` is null, but the code path may already short-circuit via `j.mappingHash()` being non-null after `infer_ingestion_schema`. Will verify with a targeted test before declaring a BUG.
- **CSV coercion regression risk**: existing CSV consumers may rely on values being all-strings; `coerceCsvValue` must preserve `null` and empty-string semantics exactly.
- **Basic auth credential model ambiguity**: depends on whether the vault stores `username:password` pre-concatenated or just the password. Will inspect `IngestionCredentialService` before fix and document in BUG-0018.
- **The 19 UI test failures are NOT bugs** — they require seeded ingestion jobs. Out of scope here; separate fixture plan needed.

## Verification Checklist

- [x] BUG-0017 → BUG-0024 created in `docs/bugs/`, registered in `index.md`
- [x] Batch 1 lands: `HttpRequestActionHandlerTest` (10 / 10) + `IngestionPayloadFetcherTest.basicCredentialInjectedAsBase64` + `…FallsBackToSecretAsUserPassWhenNoUsernameSet` green
- [x] Batch 2 lands: `HttpRequestActionHandlerTest.authFailedReturns401MappedError` green
- [x] Batch 3 lands: `IngestionPayloadFetcherTest` `pagePaginationStopsOnEmptyPage` / `offsetPaginationStopsOnEmptyPage` / `cursorPaginationFollowsTopLevelNextKey` green
- [x] Batch 4 lands: `TabularValueCoercerTest` (12) + `CsvPayloadParserTest` (5) + `HtmlTablePayloadParserTest` (3) green; `RowStreamTest.csvStreamsWithHeaderRow` updated for new typed coercion
- [x] `mvn -pl data-talk-application,data-talk-adapter test` overall green (170 adapter + full application module)
- [x] `mvn -pl data-talk-infrastructure test` overall green (343 / 343)
- [ ] E2E ingestion suite ≥ 50/54 API/MCP tests pass — **pending backend restart with `SPRING_PROFILES_ACTIVE=e2e`**
- [x] All 8 BUGs transitioned to `fixed` with `fixCommit: "pending — Batch N"` placeholder (will be retrofilled after the code commit)
- [x] `docs/bugs/index.md` reflects new state
- [ ] `docs/exec-plans/index.md` plan entry moved from Active → Completed — pending E2E verification

## Out-of-Plan Follow-ups (登记后续)

- UI suites: re-enable after adding a `seedIngestionJob()` fixture (separate plan).
- `FORMAT_UNSUPPORTED` Content-Type test: requires mock server enhancement (separate task).
- Delete-in-use 409 cross-test scenario: cross-test lifecycle harness needed.
