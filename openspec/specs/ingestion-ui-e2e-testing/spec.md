# ingestion-ui-e2e-testing Specification

## Purpose
TBD - created by archiving change ingestion-ui-e2e-mock-strategy. Update Purpose after archive.
## Requirements
### Requirement: HTTP-boundary mocking for stateless UI rendering specs

Playwright E2E specs that verify ingestion UI rendering (`client/tests/e2e/ingestion-*-ui.spec.ts`) SHALL use `page.route()` to intercept backend HTTP requests at the browser boundary when the test asserts UI rendering, phase routing, or UI-driven status transitions derived from `IngestionJob` DTO shape. The mocked data MUST be typed against the shared frontend `IngestionJob` TypeScript type so shape drift is caught at compile time.

#### Scenario: Rendering a job phase from a known status

- **GIVEN** a test asserts that `IngestionJob` with `status='mapped'` renders `<MappingPhase />`
- **WHEN** the test sets up `page.route('**/api/ingestion/jobs/{id}', ...)` returning a `mockJob({ status: 'mapped', mapping: {...} })`
- **THEN** the test SHALL NOT call any real-backend seed endpoint, MUST NOT depend on the MCP pipeline, and MUST NOT INSERT directly into `ingestion_job`

#### Scenario: UI-driven status transition via polling

- **GIVEN** a test asserts that clicking Confirm transitions the UI from `mapped` to `writing`
- **WHEN** the test stores phase in a closure variable, mocks `POST /api/ingestion/jobs/{id}/confirm` to flip the variable, and lets `useIngestionJobQuery` polling pick up the new state
- **THEN** the assertion `await expect(page.getByTestId('ingestion-phase-writing')).toBeVisible({ timeout: 10_000 })` SHALL pass without modifying production code or invoking the real backend

#### Scenario: mockJob() type drift detection

- **GIVEN** the backend renames a field on `IngestionJob` (e.g. `payloadFormat → format`) and the frontend `IngestionJob` TypeScript type is updated accordingly
- **WHEN** the developer runs `cd client && npx tsc --noEmit`
- **THEN** compilation SHALL fail on any spec file that constructs a `mockJob({ payloadFormat: ... })` with the obsolete field name

### Requirement: Real-backend coverage for CRUD surfaces with no MCP wrapper

Playwright UI E2E specs SHALL hit the real backend (no `page.route()`) when the UI surface under test directly drives a synchronous REST CRUD endpoint with no MCP-pipeline equivalent, e.g. `ingestion-credentials-ui.spec.ts` against `/api/ingestion/credentials`.

#### Scenario: Credentials UI exercises real backend

- **GIVEN** `ingestion-credentials-ui.spec.ts` tests credential create/list/delete flows
- **WHEN** the test clicks the create button and submits a form
- **THEN** the request SHALL hit the real `/api/ingestion/credentials` endpoint, the credential SHALL be persisted to SQLite, and the test SHALL clean up via the real `DELETE /api/ingestion/credentials/{id}?force=true` endpoint in `beforeEach`

### Requirement: Prohibited test-only backdoors

The codebase SHALL NOT introduce a test-only seed endpoint for ingestion jobs, regardless of profile gating. Specifically, no controller route matching `POST /api/test/ingestion/jobs` or `POST /api/ingestion/jobs` (as a creation endpoint) SHALL be added. The `ingestion-fixtures.ts` file SHALL NOT export a `seedIngestionJob()` helper that issues a direct DB INSERT or bypasses the MCP pipeline.

#### Scenario: Reviewer rejects a backdoor proposal

- **GIVEN** a developer proposes adding `@Profile("e2e")` + `POST /api/test/ingestion/jobs` to insert a job at arbitrary status
- **WHEN** the reviewer cites this spec
- **THEN** the proposal SHALL be rejected, and the developer SHALL be directed to use `page.route()` mocking instead

#### Scenario: ingestion-fixtures.ts integrity

- **GIVEN** a developer attempts to add `seedIngestionJob(request, { status: 'mapped', mapping: {...} })` to `client/tests/e2e/fixtures/ingestion-fixtures.ts`
- **WHEN** the change is reviewed against this spec
- **THEN** the addition SHALL be rejected; the helper belongs inline in the spec file as a typed `mockJob()` constructor, not in the shared fixture module that talks to a real backend

### Requirement: Spec-file header documentation

Each ingestion UI E2E spec file SHALL contain a header comment that explicitly declares its testing strategy — either `// Strategy: page.route() mocking — see openspec/specs/ingestion-ui-e2e-testing/spec.md` or `// Strategy: real backend — exercises /api/ingestion/credentials CRUD directly`. Header comments SHALL NOT reference closed BUG IDs as fixme gates.

#### Scenario: New contributor reads strategy

- **GIVEN** a new contributor opens `ingestion-job-tab-ui.spec.ts`
- **WHEN** they read the file header
- **THEN** they SHALL find an explicit one-line declaration of the mocking strategy and a pointer to this spec, with no stale BUG-XXXX gate comments

#### Scenario: Removing a stale BUG fixme

- **GIVEN** a closed BUG (e.g. BUG-0013) has a fixme comment in a spec file but no actual `.fixme()` marker on any test
- **WHEN** a contributor encounters the file
- **THEN** the stale fixme comment SHALL be removed, and the strategy declaration SHALL replace it

