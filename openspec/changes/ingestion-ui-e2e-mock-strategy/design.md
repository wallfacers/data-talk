## Context

The ingestion UI module exposes three user-facing surfaces — a credentials settings page, a library tab listing all ingestion jobs, and a per-job tab routing across the lifecycle phases (`fetching → fetched → mapped → confirmed → writing → completed | failed | cancelled`). Each surface has a corresponding Playwright spec under `client/tests/e2e/`. Two of the three specs are currently inert: `ingestion-library-tab-ui` carries an obsolete BUG-0013 fixme banner, and `ingestion-job-tab-ui` invokes a non-existent `POST /api/ingestion/jobs` seed endpoint.

The backend creates ingestion jobs **only** via the MCP pipeline (`http_request` → `infer_ingestion_schema` → `create_ingestion_table` → `ingest_payload`). There is no production "create job" REST endpoint, and the pipeline is inherently asynchronous — a test cannot pin a job at intermediate states like `mapped` or `writing` without either (a) racing the pipeline, (b) introducing a backdoor INSERT path, or (c) intercepting at a higher layer.

The frontend reads job state via `useIngestionJobQuery` (TanStack Query polling every 3 s while status is in-flight). `ingestion-job-tab.tsx` is a stateless phase router: given a `Job` DTO with `status=X`, it renders component `<XPhase />`. The library tab is a similarly stateless list renderer over `GET /api/ingestion/jobs`.

Constraints already in the codebase that shape this design:
- `SPRING_PROFILES_ACTIVE=e2e` profile already exists and is strictly forbidden in production (`CLAUDE.md` Ingestion E2E Profile gate).
- `__DT_E2E__` window shim is already in place for opening stage tabs from tests.
- The existing `client/tests/e2e/fixtures/ingestion-fixtures.ts` exports `seedH2Connection`, `seedCredential`, `waitForJobStatus`, plus a `MockServer` for source HTTP URLs.
- API-level specs (`ingestion-credentials-api`, `ingestion-ddl-mcp`, `ingestion-execute-mcp`, `ingestion-fetch-mcp`, `ingestion-infer-mcp`, `ingestion-preflight`, `ingestion-sse-events`, `ingestion-error-paths`) already exercise the real backend through the MCP pipeline.

## Goals / Non-Goals

**Goals:**
- Make all three ingestion UI specs runnable and meaningful under `SPRING_PROFILES_ACTIVE=e2e`.
- Establish a documented, written-down rule for how UI E2E specs interact with the backend, so future contributors don't reach for `seedIngestionJob()` reflexively.
- Keep the production code base and the fixtures untouched — this change is pure test-side.

**Non-Goals:**
- Adding a `POST /api/ingestion/jobs` endpoint, even a `@Profile("e2e")`-gated one.
- Introducing a `seedIngestionJob()` helper to `ingestion-fixtures.ts`.
- Migrating other E2E specs (api, mcp, preflight, sse-events) to mocking — they keep their real-backend integration discipline.
- Changing UI components, control states, design tokens, layouts, or the `useIngestionJobQuery` polling cadence.
- Modifying the MCP pipeline or any backend code path.

## Decisions

### Decision 1 — Mock at the HTTP boundary via `page.route()` for `ingestion-job-tab-ui`

**Choice**: All 11 tests in `ingestion-job-tab-ui.spec.ts` use Playwright `page.route()` to intercept `GET /api/ingestion/jobs/{id}`, `POST /api/ingestion/jobs/{id}/confirm`, `POST /api/ingestion/jobs/{id}/cancel`, and `GET /api/ingestion/jobs/{id}/payload-preview`. Job state lives in test-closure variables that the route handler reads.

**Why not (B) a test-only seed endpoint** (`POST /api/test/ingestion/jobs` gated by `@Profile("e2e")`):
- The seed path would bypass `IngestionPayloadFetcher`, `JsonPayloadParser`/`CsvPayloadParser`/`HtmlTablePayloadParser`, `TypeInferrer`, `IngestionMappingBuilder`, and the entire MCP action chain. The seeded row exercises only `JdbcTemplate.update` and the SQLite CHECK constraint — i.e. it tests the DB driver, not DataTalk.
- Real-backend integration of those code paths is **already covered** by `ingestion-execute-mcp`, `ingestion-infer-mcp`, `ingestion-fetch-mcp`, and `ingestion-ddl-mcp`. Adding a parallel UI-side coverage would be duplicate.
- Profile gating adds a configuration-error risk surface (an accidental `e2e` profile in staging or prod would expose a backdoor that bypasses authorization, MCP validation, and audit hooks).
- The fixture would couple to the SQLite schema (column names, CHECK constraints, JSON serialization of `mapping`), making backend schema evolution a two-place change.

**Why not (C) a split (mock for renders, real backend for lifecycle)**:
- Even the "lifecycle" tests (`Confirm → writing`, `Cancel → cancelled`) verify **UI state propagation**, not backend lifecycle correctness. Backend lifecycle is API-spec covered. The UI test only needs: a click triggers the right HTTP request, and the next polled GET reflects the new status. Both are mockable with closure-state.
- A split creates two code paths to maintain and a fuzzy boundary about which test goes which way.

**Why (A) page.route()**:
- The boundary `useIngestionJobQuery` reads (REST GET response) is exactly the boundary Playwright `page.route` intercepts. Test setup is ~5 lines per test, all in the test file.
- Mock data is typed against the shared frontend `IngestionJob` TS type — TypeScript catches shape drift at compile time.
- No DB cleanup, no async polling races, no profile risk, no backend changes.
- Existing `ingestion-library-tab-ui.spec.ts` already uses this pattern successfully — the same shape applies cleanly to `job-tab-ui`.

### Decision 2 — Real backend for `ingestion-credentials-ui`

**Choice**: `ingestion-credentials-ui.spec.ts` continues hitting the real backend (no `page.route()`). It exercises credential CRUD which has no MCP wrapper.

**Why**: Credentials have a single REST controller path (`POST/GET/DELETE /api/ingestion/credentials`) with no asynchronous pipeline. The UI directly drives those endpoints, and credential CRUD is small enough that real-backend coverage is cheap and gives us audit / RBAC integration confidence. There is no equivalent "API spec" that overlaps coverage.

### Decision 3 — `mockJob()` helper typed as `Partial<IngestionJob>` from frontend types

**Choice**: Define a single test helper:
```ts
import type { IngestionJob } from '@/features/ingestion/api/ingestion-api'

function mockJob(overrides: Partial<IngestionJob> = {}): IngestionJob {
  return { id: 'job_mock', sourceUrl: 'http://x', status: 'mapped', ..., ...overrides }
}
```
Located inline in each spec file (not in `ingestion-fixtures.ts`, to keep the fixture file scoped to real-backend seeding).

**Why typed import over inline literal**: TypeScript compile-time catches `IngestionJob` shape changes. If the backend renames `payloadFormat → format`, the frontend TS type updates, and `tsc --noEmit` fails the test file before the test runs.

**Why inline (not in fixtures.ts)**: `ingestion-fixtures.ts` is for code that talks to a real backend (it imports `APIRequestContext`). The mock helper is pure data construction with no I/O. Keeping it inline avoids confusion about which fixture exports are backend-bound.

### Decision 4 — Toggle status via closure state + lean on polling

**Choice**: When a test needs `click button → status changes`, store the current status in a `let phase: string` closure variable. `page.route()` reads `phase` on each GET. The click handler's POST mock mutates `phase`. The next `useIngestionJobQuery` poll (3 s interval) picks up the new status. The test awaits the new phase test-id with a `10_000 ms` timeout.

**Why not** invalidate the query cache from the test via `__DT_E2E__.queryClient.invalidateQueries(...)`:
- Adds new surface area on the E2E window shim for a single use case.
- Polling-driven behavior is what real users see; testing it as-is is more faithful.
- 3 s + 10 s timeout has slack room and matches the library-tab-ui pattern.

### Decision 5 — Codify the rule as a capability spec

**Choice**: Create `openspec/specs/ingestion-ui-e2e-testing/spec.md` (via the change's delta) with explicit GIVEN/WHEN/THEN requirements stating when to mock vs hit real backend, and what is forbidden.

**Why**: The rule is the kind of thing that's discovered by trial-and-error otherwise. Three contributors writing three new ingestion UI tests would each independently rederive the trade-off. A spec gives a single citation: "tests under `ingestion-*-ui.spec.ts` MUST mock at the HTTP boundary, unless they fall in the credentials-style real-backend carve-out."

## Risks / Trade-offs

- **[Mock drift]** `mockJob()` data could lag real backend response shape if `IngestionJob` is loosely typed on the frontend. → **Mitigation**: import `IngestionJob` type directly from the frontend API module; rely on TypeScript compile to fail-fast. Run `cd client && npx tsc --noEmit` as part of T3 verification.
- **[Coverage gap — backend lifecycle not exercised through UI]** No UI test verifies that `clicking confirm` actually triggers backend state transition through `JdbcIngestionJobRepository`. → **Accepted**: covered by `ingestion-execute-mcp.spec.ts` and the controller-level confirm/cancel paths in `ingestion-error-paths.spec.ts`. UI test scope is "UI reacts correctly to HTTP responses."
- **[Stale spec-header comment drift]** Removing the BUG-0013 fixme leaves a fading historical reference. → **Acceptable**: BUG-0013 is fully fixed and documented at `docs/bugs/BUG-0013-*.md`. Test files are not the right place for historical archaeology; that's what `docs/bugs/` is for.
- **[Polling timeout sensitivity]** Tests rely on the 3 s `refetchInterval` + 10 s `expect.toBeVisible` budget. On a slow CI runner this could flake. → **Mitigation**: timeout already matches library-tab-ui norms; if flakes appear, the per-test `timeout` is the tuning knob, not a design escape hatch.
- **[New capability spec discoverability]** The codified rule lives in `openspec/specs/ingestion-ui-e2e-testing/spec.md` after archive. Newcomers may not see it. → **Mitigation**: spec-header comment in each ingestion UI spec links to the rule and quotes the one-line summary.

## Migration Plan

This is a test-only, fixture-only change. There is no production rollout, no rollback strategy needed:
1. Edit the three spec files (T1 verification, T2 comment cleanup, T3 rewrite).
2. Run all three specs under `SPRING_PROFILES_ACTIVE=e2e` locally. Each must pass.
3. Run the full E2E suite to confirm no regression in api / mcp / preflight / sse-events specs.
4. Archive the change. Capability spec lands at `openspec/specs/ingestion-ui-e2e-testing/spec.md`.

No production deploy. No flags. No telemetry. No database migration.

## Open Questions

- Whether to backfill the same `page.route()` mocking pattern into `ingestion-library-tab-ui` more rigorously (it already uses the pattern but its `mockJob()` is inline `Record<string, unknown>`, not typed). → **Out of scope** for this change; tracked as a future cleanup.
- Whether `useIngestionJobQuery` should expose its `queryClient` on `__DT_E2E__` to let tests force-refetch. → **Deferred**; only worth doing if Decision 4's polling-based approach proves flaky in practice.
