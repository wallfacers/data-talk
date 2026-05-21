## ADDED Requirements

### Requirement: mockJob() type contract MUST cover new lifecycle fields

The shared `mockJob()` typed constructor used by Playwright UI E2E specs SHALL accept and round-trip the new ingestion-job lifecycle fields: `name`, `createdBy: { kind, sessionId, label }`, and `heartbeatAt`. The frontend `IngestionJob` TypeScript type SHALL include these fields so `npx tsc --noEmit` catches drift in any spec file constructing a `mockJob({...})` with stale or missing properties.

#### Scenario: Spec asserts library list renders name column

- **GIVEN** a Playwright spec sets up `page.route('**/api/ingestion/jobs', ...)` returning a list containing `mockJob({ name: 'Stripe customers — 2026-05', ... })`
- **WHEN** the spec asserts that the library list cell for that row contains `"Stripe customers — 2026-05"`
- **THEN** the test SHALL pass without backend interaction, and removing `name` from `IngestionJob` TS type SHALL cause `npx tsc --noEmit` to fail in this spec

#### Scenario: Spec asserts creator column renders label and clickability

- **GIVEN** `mockJob({ createdBy: { kind: 'ai', sessionId: 'sess_x', label: 'AI · Q2 reporting' } })`
- **WHEN** the spec asserts the Creator cell text equals `"AI · Q2 reporting"` and is clickable
- **THEN** the test SHALL pass, and `mockJob({ createdBy: { kind: 'ai', sessionId: null, label: 'AI' } })` SHALL render a non-clickable cell

### Requirement: Stop UI flows MUST be testable via page.route() mocking

Playwright UI E2E specs covering Stop / Force-stop UX SHALL mock the backend stop endpoint at the browser boundary and assert UI state transitions, without invoking the real `IngestionRunRegistry` or DROP TABLE path.

#### Scenario: Stop button appears for non-terminal status

- **GIVEN** `mockJob({ status: 'writing', ... })` is returned from `page.route('**/api/ingestion/jobs', ...)`
- **WHEN** the spec hovers the library row
- **THEN** the Stop button (`[data-testid="ingestion-stop-btn-<jobId>"]`) SHALL be visible

#### Scenario: Force-stop button replaces Stop after 30s of no status change

- **GIVEN** the spec clicks Stop and `page.route('**/api/ingestion/jobs/{id}/stop', ...)` returns 202; subsequent `GET /jobs/{id}` continues to return `status: 'writing'`
- **WHEN** the spec waits 30 s (using `page.clock.runFor(30_000)` or equivalent fake-timers)
- **THEN** the button SHALL switch label to "Force stop" with `bg-destructive` style, and clicking it SHALL issue `POST /stop?force=true` — verified by `page.waitForRequest(req => req.url().endsWith('/stop?force=true'))`

#### Scenario: Stop on terminal job is rejected via mocked 409

- **GIVEN** `mockJob({ status: 'completed' })` is returned, and `page.route('**/stop', ...)` returns 409
- **WHEN** the spec attempts to render the row
- **THEN** the Stop button SHALL NOT be present (UI hides it for terminal status without any need to reach the backend)
