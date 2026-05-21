## Why

The three ingestion UI E2E specs (`ingestion-credentials-ui`, `ingestion-library-tab-ui`, `ingestion-job-tab-ui`) are currently unrunnable as a unit: `ingestion-job-tab-ui.spec.ts:83` calls a ghost endpoint `POST /api/ingestion/jobs` that does not exist (the backend creates jobs only through the MCP pipeline). `ingestion-library-tab-ui` carries a stale `BUG-0013` fixme banner although BUG-0013 is fixed. Without a coherent test strategy these specs accumulate dead code and block UI regression coverage.

The prior plan deferred this work as "needs a `seedIngestionJob()` fixture and separate plan." A grounded codebase audit shows that fixture is the wrong abstraction: 11 of 11 `job-tab-ui` tests are pure UI rendering assertions on `IngestionJob` DTO shape, and inserting jobs directly into SQLite would bypass the entire MCP pipeline — granting the costs of a real-backend integration test (DB cleanup, profile-gating risk, schema coupling) without any of its benefits.

## What Changes

- Adopt `page.route()` HTTP-boundary mocking as the canonical strategy for ingestion UI E2E specs that test pure rendering or UI-driven status transitions.
- Reserve real-backend E2E for specs that test backend CRUD with no MCP wrapper (e.g. `ingestion-credentials-ui`).
- **REMOVE** the ghost `POST /api/ingestion/jobs` seed call from `ingestion-job-tab-ui.spec.ts`.
- **REMOVE** the stale BUG-0013 fixme header from `ingestion-library-tab-ui.spec.ts`.
- Rewrite all 11 `ingestion-job-tab-ui` tests to set up job state via `page.route()` with a shared `mockJob()` helper typed as `Partial<IngestionJob>`.
- Explicitly **REJECT** adding `seedIngestionJob()` to `ingestion-fixtures.ts` and **REJECT** adding `POST /api/test/ingestion/jobs` to the backend.
- Codify the mocking strategy as a new capability spec so the rule survives future contributors.

## Capabilities

### New Capabilities
- `ingestion-ui-e2e-testing`: Rules for how ingestion UI E2E specs interact with the backend — when to mock at the HTTP boundary, when to hit the real backend, and what is forbidden (test-only seed endpoints, direct DB inserts in fixtures).

### Modified Capabilities
<!-- None: this is the first change in the OpenSpec repo and no prior canonical specs exist. -->

## Impact

- **Tests changed**: `client/tests/e2e/ingestion-credentials-ui.spec.ts` (verification only, no code edits expected), `client/tests/e2e/ingestion-library-tab-ui.spec.ts` (comment cleanup), `client/tests/e2e/ingestion-job-tab-ui.spec.ts` (full rewrite of 11 tests).
- **Fixtures unchanged**: `client/tests/e2e/fixtures/ingestion-fixtures.ts` keeps its existing exports. No `seedIngestionJob()` added.
- **Backend unchanged**: no new endpoints, no profile-gated routes, no controller edits.
- **Spec authored**: new `openspec/specs/ingestion-ui-e2e-testing/spec.md` after archive.
- **No DB / no data-source type compatibility** changes — `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` is N/A.
- **Design Inputs**: `client/DESIGN.md` applies because the specs touch ingestion UI surfaces; however this change is **test-only**, alters no UI components, control states, design tokens, layouts, shells, or shadcn primitives. Any new `data-testid` added in tests follows the existing `ingestion-*` naming convention.
- **Open BUGs overlapping**: none in the ingestion-ui module are open after the BUG-0017→0034 batch. Verified via `docs/bugs/index.md`.
- **Risks**:
  - Mock drift from real backend response shape. Mitigation: `mockJob()` is typed against the shared TypeScript `IngestionJob` type from the frontend API module; TypeScript will fail compilation if fields are renamed/removed.
  - No real-backend integration coverage for the job-tab UI. Accepted because (a) MCP API specs (`ingestion-execute-mcp`, `ingestion-infer-mcp`, `ingestion-ddl-mcp`, `ingestion-fetch-mcp`, `ingestion-sse-events`, `ingestion-error-paths`) already exercise the backend lifecycle, (b) the job-tab UI is a stateless phase router over a DTO.
