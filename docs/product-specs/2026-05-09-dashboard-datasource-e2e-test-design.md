# Dashboard / FileArtifact + Data Source E2E Test Design

- **Date**: 2026-05-09
- **Status**: Draft
- **Owner**: agent
- **Source prompt**: migrated from `tmp/e2e-playwright-testing-prompt.md`
- **Scope**: Playwright / Vitest test design for Dashboard-FileArtifact integration and Dameng / OceanBase first-class data-source support

## 1. Background

`tmp/e2e-playwright-testing-prompt.md` captured the right test surface, but as a temporary prompt it mixed several kinds of validation into one execution instruction: backend API contract, current UI flows, component behavior, accessibility, visual design checks, AI routing assumptions, file-system reconciliation, and real database smoke tests.

That shape is useful for review, but risky as an implementation artifact. A single Playwright run would produce failures that are hard to classify: product regression, missing feature, stale prompt expectation, environment gap, or known design debt. This spec turns that prompt into a stable product design for test coverage. The core design decision is to split validation by dependency level and readiness, with explicit gates before any suite runs.

The canonical test design is this document. The temporary prompt should not remain as a competing source of truth after migration.

## 2. Related Documents

- [client/DESIGN.md](../../client/DESIGN.md) — frontend design contract and accessibility rules
- [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md) — mandatory data-source compatibility gate
- [docs/bugs/index.md](../bugs/index.md) and [docs/bugs/README.md](../bugs/README.md) — BUG tracking contract for E2E deviations
- [docs/product-specs/2026-05-08-report-dashboard-design.md](./2026-05-08-report-dashboard-design.md) — Report / Dashboard product design
- [docs/product-specs/2026-05-09-dashboard-file-artifact-integration-design.md](./2026-05-09-dashboard-file-artifact-integration-design.md) — Dashboard ↔ File Artifact integration design
- [docs/product-specs/2026-05-08-data-source-coverage-oceanbase-design.md](./2026-05-08-data-source-coverage-oceanbase-design.md) — OceanBase Day-1 design
- [docs/product-specs/2026-05-08-data-source-coverage-dameng-design.md](./2026-05-08-data-source-coverage-dameng-design.md) — Dameng Day-1 design
- [docs/product-specs/2026-05-06-er-module-e2e-test-design.md](./2026-05-06-er-module-e2e-test-design.md) — closest prior UI-only E2E design pattern
- [docs/product-specs/2026-05-05-sql-editor-mcp-e2e-test-design.md](./2026-05-05-sql-editor-mcp-e2e-test-design.md) — full-stack + AI/MCP E2E pattern

## 3. Goals

1. Define executable, independently runnable suites for Dashboard-FileArtifact and Dameng / OceanBase validation.
2. Keep fast suites deterministic in default CI by avoiding real AI models and real database services.
3. Treat real database tests as opt-in smoke tests gated by environment variables.
4. Validate frontend design obligations, not just happy-path business behavior.
5. Prevent known gaps and unfinished active plans from being logged as runtime BUGs.
6. Make all Playwright evidence and temporary artifacts obey the repository `tmp/` rule.
7. Produce a test structure that can later be converted directly into an execution plan.

## 4. Non-Goals

- This spec does not implement the tests.
- This spec does not change Dashboard, FileArtifact, or data-source product behavior.
- This spec does not require real OpenCode model calls in the default test path.
- This spec does not require real Dameng or OceanBase servers in CI.
- This spec does not define a visual regression screenshot baseline system. It defines visual and accessibility assertions that can run with Playwright and DOM/CSS checks.
- This spec does not register BUGs from static analysis alone. BUG registration is triggered by actual E2E/runtime deviation, per `docs/bugs/`.

## 5. Design Inputs From `client/DESIGN.md`

The E2E design must enforce these frontend constraints:

- DataTalk is a dense AI-native data research workbench, not a landing page or marketing UI.
- Stage is global, not per-session. Dashboard and file-related Stage assertions must not assume session-scoped tabs.
- New UI assertions must verify semantic behavior across light and dark themes instead of raw color literals.
- State cannot be communicated by color alone. Status must have text, icon, ARIA state, or structural affordance.
- Icon-only actions require accessible names.
- Focus rings must remain visible in both themes.
- Keyboard access must cover dialogs, tabs, toolbars, menus, pickers, and Stage surfaces.
- Components must not use raw primitive colors directly in feature code.
- Motion confirms state change; tests must not depend on decorative animation timing and must respect `prefers-reduced-motion`.

Additional UI/UX review guidance applied here:

- Use lucide/SVG icons for tool actions and statuses rather than emoji-as-icon.
- Test at least desktop and narrow viewport behavior for dense workbench surfaces.
- Prefer stable, professional data-tool layouts over hero, card-heavy, or decorative compositions.

## 6. Data Source Compatibility Gate

Because the test scope includes Dameng and OceanBase, every execution plan derived from this design must read `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` before writing or running tests.

Applicable current compatibility facts:

| Kind | E2E stance |
|---|---|
| `oceanbase` | First-class Day-1, MySQL-mode only. UI should expose compatibility mode, tenant, cluster, default port 2881. Oracle/PG modes are not Day-1 executable modes. |
| `dameng` | First-class Day-1 for DM 8. Single-mode. UI should expose host, port 5236, username, password, and optional schema using the existing database field semantics. |

Not applicable sections for this E2E design:

| Compatibility area | Reason |
|---|---|
| JDBC driver implementation | Tests verify behavior and request contracts; they do not add or change drivers. |
| SQL splitter implementation | Tests can assert externally visible risk and execution behavior, but implementation changes belong to data-source plans. |
| Schema discovery internals | UI/API suites validate returned behavior; real metadata discovery belongs to opt-in smoke or backend integration tests. |
| MCP action schema changes | This design does not change runtime MCP schemas. It may later add tests that assert existing schemas. |

If a future implementation plan discovers a new database-kind branch while writing these tests, that plan must update `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` in the same change.

## 7. BUG Gate And Evidence Rules

Every Playwright or browser-driven execution derived from this design must read `docs/bugs/index.md` first.

Runtime deviation handling:

- If a test run finds a product behavior deviation, create or update a BUG document under `docs/bugs/` and register it in `docs/bugs/index.md`.
- The final test report must state: `本次发现 N 个 BUG，已登记到 ...`; `N=0` must also be explicit.
- Known gaps marked by this spec are not automatically BUGs. They become BUGs only if an implementation plan declares the behavior complete and runtime testing still fails.

Evidence storage:

- Playwright traces, HAR, HTML reports, screenshots used only for diagnosis, console dumps, and scratch logs go under `tmp/`.
- BUG evidence PNGs may be copied to `docs/bugs/assets/<BUG-ID>/` only when they are part of a registered BUG and each PNG is at most 500 KB.
- Temporary files must not be created in repo root, `client/`, `server/`, `docs/`, system `/tmp`, or home directories.

## 8. Brainstormed Approaches

### Approach A: Single Mega E2E Suite

One Playwright file runs Dashboard, FileArtifact, Dameng, OceanBase, visual checks, and real DB smoke in sequence.

Benefits:

- Easy to invoke.
- Mirrors the original temporary prompt.

Costs:

- Fails noisily when any dependent system is not ready.
- Hard to classify failures.
- Encourages sleeps and permissive POM helpers.
- Mixes CI-safe tests with real environment tests.
- Makes BUG registration ambiguous.

Decision: rejected.

### Approach B: Split Suites With Readiness Gates

Create small suites by dependency level:

1. preflight gates
2. API contract
3. current UI E2E
4. data-source form E2E
5. component tests
6. visual/accessibility/responsive tests
7. opt-in real DB smoke

Benefits:

- Each failure has a clear owner and dependency profile.
- Default CI can run fast deterministic suites.
- Real DB and AI model behavior stay explicit and opt-in.
- Known active-plan gaps can be marked as readiness failures or `fixme`, not confused with runtime regressions.
- Matches existing ER and SQL E2E design style.

Costs:

- More files and fixtures.
- Requires discipline in naming, tagging, and POM boundaries.

Decision: recommended.

### Approach C: Product Conformance Harness

Build a general test harness that reads product specs, discovers available features, and dynamically marks readiness.

Benefits:

- Long-term scalable across every feature.
- Could reduce repeated preflight code.

Costs:

- Too large for this feature slice.
- Adds infrastructure before the current Dashboard / data-source gaps are closed.
- Could hide concrete assertions behind framework abstraction.

Decision: defer. Extract common preflight helpers only after this suite proves repetition.

## 9. Recommended Architecture

Use Approach B.

The test system is split into independent suites that can run in this order:

```text
Suite 0  Preflight / readiness gates
Suite 1  API contract: Dashboard + FileArtifact
Suite 4  Component tests: Vitest, no browser
Suite 3  UI E2E: Dameng / OceanBase forms
Suite 2  UI E2E: Dashboard current reachable flows
Suite 5  Visual / accessibility / responsive checks
Suite 6  Manual / real database smoke
```

The numbering preserves the source prompt's six suites while adding Suite 0 as a mandatory execution gate.

## 10. Suite 0: Preflight / Readiness Gate

Purpose: fail early or mark tests appropriately when product plans are still active, known BUGs exist, or required test infrastructure is missing.

Recommended file:

- `client/tests/e2e/preflight.spec.ts`

Implementation form:

- Implement Suite 0 as normal Playwright tests in `preflight.spec.ts`, not as a hidden `beforeAll` hook or separate shell script.
- Each test title should include one of the preflight tags below so CI can run `npx playwright test --grep @preflight` or narrower variants such as `--grep @preflight-api`.
- Browser endpoint checks must live in `@preflight-e2e` tests; API-only readiness must live in `@preflight-api` tests.

Tags:

- `@preflight` for all preflight checks.
- `@preflight-api` for checks needed by request/API suites.
- `@preflight-e2e` for checks needed only by browser UI suites.
- These tags are also listed in §19; §10 defines Suite 0 behavior, while §19 is the global tag registry.

Checks:

| Gate | Assertion |
|---|---|
| Design contract | `client/DESIGN.md` was read by the executing plan; test report records applicable constraints. |
| Data-source compatibility | `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` was read; report records `dameng` and `oceanbase` current support snapshot. |
| BUG registry | `docs/bugs/index.md` was read; report records current open/in-progress BUGs touching `stage`, `dashboard`, `files`, `settings`, or `connection`. |
| Exec plan status | `docs/exec-plans/index.md` was read; report records whether Dashboard/FileArtifact integration and Dameng/OceanBase plans are Active or Completed. |
| Temp artifact directory | `tmp/playwright/` exists or is created before tests emit traces/screenshots. |
| API runtime endpoint | `@preflight-api` checks the backend URL only when a request/API suite is selected. It must not block Vitest-only runs. |
| Browser runtime endpoints | `@preflight-e2e` checks both backend and Vite/Tauri dev URLs only when browser UI suites are selected. It must not block API-contract-only or component-only runs. |

Readiness output should be a small JSON or Markdown run note under `tmp/playwright/<run-id>/preflight.md`, not under tracked docs.

## 11. Suite 1: API Contract - Dashboard And FileArtifact REST

Purpose: validate backend REST behavior without relying on frontend UI, AI models, chat rendering, or drag/drop.

Recommended file:

- `client/tests/e2e/dashboard-api-contract.spec.ts`

Driver:

- Playwright `request` fixture.
- Test data created and cleaned per test.
- No browser context required.

Coverage:

| Area | Required assertions |
|---|---|
| `POST /api/dashboards/promote` | Valid minimal dashboard creates an id/version pair. Full dashboard creates a retrievable document. Empty payload returns the implemented validation error. Missing required fields return schema validation. Payloads over the current `DashboardArtifactService.MAX_PAYLOAD_BYTES = 256 * 1024` cap return `413 Payload Too Large`. |
| `GET /api/dashboards/{id}` | Existing id returns full JSON. Missing id returns not found. |
| `PATCH /api/dashboards/{id}` | Valid `baseVersion` + patch increments version and persists. Stale `baseVersion` returns conflict. Invalid op returns validation error. Missing baseVersion must match the actual DTO/controller behavior and must not be guessed. |
| FileArtifact dashboard rows | When dashboard external/file artifact integration is complete, dashboard file rows are returned by connection/file listing and include `kind: "dashboard"`. |
| discard/archive behavior | Discarding dashboard rows updates lifecycle consistently with FileArtifact design. Behavior must be derived from the integration spec and current implementation. |
| reconciliation behavior | Reconciler tests must run through an explicit test-only hook or backend integration test helper. If no hook exists, mark this part as not automatable in Playwright and cover it in backend tests. |

Important rule: this suite is a contract suite, not an implementation fantasy suite. If source prompt expectations disagree with current controller behavior, the execution plan must inspect the controller and product spec before locking the expected status code.

## 12. Suite 2: UI E2E - Dashboard Current Reachable Flows

Purpose: test Dashboard UI surfaces that are actually reachable in the current product, while isolating AI routing and server-side promote assumptions.

Recommended file:

- `client/tests/e2e/dashboard-ui-e2e.spec.ts`

Coverage:

| Area | Required assertions |
|---|---|
| Chat dashboard block | Inject or seed a session turn with a dashboard fence. Assert preview renders title, widget count, limited widget list, streaming skeleton, and parse-error state. |
| Open to workbench | Clicking the button opens Stage and creates/focuses a dashboard tab when the product has registered dashboard as a Stage tab type. If registration is still incomplete, mark the test `fixme` with the plan/spec reference. |
| Stage persistence | Reload preserves dashboard tab metadata only after dashboard tab persistence is implemented and declared ready. |
| Dashboard canvas | Inject dashboard payload into store through a deliberate E2E hook. Assert canvas renders, viewer mode hides editor controls, editor toggle works, and invalid payload shows an error surface. |
| Editor readiness | Add widget, query editing, resize persistence, and debounce PATCH tests are not enabled until those controls exist in UI and API support is complete. |

Allowed test hooks:

- `window.__DT_E2E__` for deterministic store seeding.
- `page.evaluate` only for setup and state reads that do not have user-facing UI paths.

Current E2E global in `client/src/main.tsx` exposes:

```ts
interface DataTalkE2EGlobal {
  stage(): ReturnType<typeof useStageStore.getState>
  er(): ReturnType<typeof useErTabsStore.getState>
  session(): ReturnType<typeof useSessionStore.getState>
  coordinator(): typeof coordinator
}
```

Dashboard Suite 2 needs one of these explicit paths before implementation:

1. Prefer a product UI/API path when one exists.
2. If deterministic store seeding is still required, extend `__DT_E2E__` with `dashboard(): ReturnType<typeof useDashboardTabsStore.getState>` in the same implementation plan.
3. Put the Playwright-facing TypeScript declaration in `client/tests/e2e/types/datatalk-e2e.d.ts` and import/use it from `client/tests/e2e/fixtures/dashboard-fixtures.ts`. Only add a production declaration under `client/src/types/` if production code stops using `(window as any)` and needs the `Window` augmentation at app compile time.
4. Do not use ad hoc `window` spelunking outside the documented `__DT_E2E__` surface.

Forbidden test behavior:

- Do not call private store methods as a replacement for clicking a button when a real button exists.
- Do not silently accept a missing button.
- Do not use `waitForTimeout` for dashboard debounce. Wait on version, request, DOM state, or store observable.

## 13. Suite 3: UI E2E - Dameng And OceanBase Connection Forms

Purpose: verify frontend form behavior and request payloads without requiring real databases.

Recommended file:

- `client/tests/e2e/datasource-forms.spec.ts`

POM stance:

- Extend the existing `client/tests/e2e/pom/connection-manager.page.ts` instead of creating a competing connection-manager abstraction.
- Before using it for this suite, remove silent no-op behavior from required methods and replace `waitForTimeout` waits with web-first assertions or request/DOM waits.
- A new `data-sources.page.ts` POM is acceptable only if it models the Settings > Data Sources page surface and delegates shared connection-dialog behavior to the existing POM.

Coverage:

| Area | Dameng |
|---|---|
| Kind picker | Shows `Dameng (DM 8)` and selects canonical `dameng`. |
| Defaults | Port auto-fills `5236`. |
| Fields | Host, Port, optional Schema, Username, Password, Connect Timeout. |
| Mode | No multi-mode picker. |
| Payload | Save request uses `kind: "dameng"`, `port: 5236`, and schema/database field according to the Day-1 design. |
| Edit | Existing Dameng connection reloads kind, port, and schema values. |

| Area | OceanBase |
|---|---|
| Kind picker | Shows `OceanBase` and selects canonical `oceanbase`. |
| Defaults | Port auto-fills `2881`. |
| Mode picker | MySQL mode is available and selected by default. Oracle/PG unsupported modes are visibly disabled or absent according to product decision. |
| Accessibility | Unsupported modes must have a clear non-color signal and correct ARIA semantics. If focusable disabled-like chips are required, use `aria-disabled` without native `disabled` and guard clicks in code. If native disabled is used, they must not be expected in tab order. |
| Fields | Tenant required, cluster optional, database optional according to OceanBase design. |
| Payload | Save request includes `kind: "oceanbase"`, `compatibilityMode: "mysql"`, tenant, optional cluster, and port. |
| Validation | Missing tenant blocks submit with a visible, accessible validation state. |
| Edit | Existing OceanBase connection reloads kind, port, mode, tenant, and cluster. |

Contract-adjacent tests:

- Kind normalization belongs in API/request tests. UI tests may assert that the form emits canonical lower-case kind strings.
- Rejected aliases such as `dm`, `dm8`, `达梦`, `ob`, or `oceanbase-ce` should be asserted through API/service tests if the endpoint is reachable from Playwright request fixtures.
- OceanBase username composition should be tested at backend service or integration level. Browser UI may assert tenant/cluster request payload, not internal JDBC username unless a test endpoint exposes it.

## 14. Suite 4: Component Tests - Vitest

Purpose: verify isolated React behavior without browser E2E overhead.

Recommended locations:

- `client/src/features/stage/components/__tests__/`
- `client/src/features/settings/data-sources/__tests__/`
- `client/src/features/dashboard/__tests__/`

Coverage:

| Component area | Required assertions |
|---|---|
| FileArtifact status badge | Current baseline tests assert the implemented emoji icons (`📄`, `📌`, `📦`), token classes, text, and ARIA label. A separate visual cleanup task should replace emoji with lucide/SVG icons; that cleanup must update tests in the same change. |
| Files Library grouping | `dashboard` kind is grouped and rendered once dashboard file artifact support is complete. Before that implementation is ready, test the current kind map as a known gap only in a `fixme` or implementation-specific test. |
| Multi-mode fields | Enabled chip calls `onModeChange`; unsupported chip does not. ARIA and keyboard behavior match the explicit product decision. |
| Icon mapping | Every `FileArtifactKind` has a deterministic icon mapping. The mapping uses component icons and accessible labels. |
| Dashboard canvas | Viewer/editor mode affordances, error state, empty state, and widget shell states render without needing a browser. |

Component tests should prefer Testing Library role/name queries. They may use `data-testid` only when canvas/grid structure has no semantic role.

## 15. Suite 5: Visual / Accessibility / Responsive

Purpose: enforce `client/DESIGN.md` on the affected surfaces.

Recommended file:

- `client/tests/e2e/visual-a11y.spec.ts`

Coverage:

| Area | Required assertions |
|---|---|
| Light/dark theme | Connection forms, Files Library, dashboard preview, and dashboard canvas remain readable in both themes. |
| Focus | Every interactive control in connection forms, mode picker, dashboard toolbar, Files Library rows, and icon buttons has visible focus. |
| Keyboard | Tab order reaches every enabled control and skips or correctly announces unavailable controls. Escape closes dialogs/menus. Enter/Space activates buttons and chips. |
| Icon buttons | Icon-only actions have `aria-label`, `title`, or visible tooltip with accessible name. |
| Text overflow | Long dashboard titles, file names, connection names, tenant names, and tab titles truncate or wrap without overlapping adjacent controls. |
| State semantics | Selected/disabled/warning/success/error states are not communicated by color alone. |
| Motion | Tests run with reduced motion enabled and assert core state transitions without animation timing dependencies. |
| Viewports | At minimum run at 1440x900 and 375x812. Add 768px tablet viewport if layout changes are implemented. |

Avoid screenshot pixel matching as the first line of defense. Prefer DOM, CSS computed style, visibility, bounding-box non-overlap, ARIA, and keyboard assertions. Screenshots are evidence for failures, not the sole oracle.

## 16. Suite 6: Manual / Real DB Smoke

Purpose: verify actual Dameng and OceanBase connections, metadata discovery, and SQL risk behavior with real database servers.

Recommended file:

- `client/tests/e2e/real-db-smoke.spec.ts`

Default behavior:

```ts
test.skip(!process.env.DATATALK_REAL_DB_TEST, 'requires real database')
```

Environment variables:

| Variable | Meaning |
|---|---|
| `DATATALK_REAL_DB_TEST` | Must be `true` to run this suite. |
| `DATATALK_DAMENG_HOST` / `DATATALK_DAMENG_PORT` / `DATATALK_DAMENG_USER` / `DATATALK_DAMENG_PASSWORD` / `DATATALK_DAMENG_SCHEMA` | Dameng smoke target. |
| `DATATALK_OCEANBASE_HOST` / `DATATALK_OCEANBASE_PORT` / `DATATALK_OCEANBASE_USER` / `DATATALK_OCEANBASE_PASSWORD` / `DATATALK_OCEANBASE_TENANT` / `DATATALK_OCEANBASE_CLUSTER` / `DATATALK_OCEANBASE_DATABASE` | OceanBase smoke target. |

Fixture option:

- OceanBase may use the documented `oceanbase/oceanbase-ce:4.2.1-lts` Testcontainers fixture if the local environment can start it reliably; otherwise skip with a clear fixture-unavailable reason.
- Dameng has no default CI container requirement. Use an explicitly supplied DM 8 environment or skip. Do not add an offline/commercial driver jar to the repository.

Dameng smoke coverage:

- Create connection.
- Test connection succeeds.
- Execute safe SELECT.
- Validate optional schema behavior.
- Validate metadata discovery and system schema filtering.
- Validate L3 examples such as admin DDL are guarded.
- Validate unsupported PL/SQL/procedure/export-import patterns are rejected before JDBC execution.

OceanBase smoke coverage:

- Create MySQL-mode OceanBase connection.
- Test connection succeeds.
- Execute safe `SHOW TABLES` or SELECT.
- Validate metadata discovery through MySQL-protocol path.
- Validate tenant/cluster username composition indirectly through successful connection.
- Validate L1/L2/L3 risk examples according to current `DATA_SOURCE_TYPE_COMPATIBILITY.md`.

The suite must clean up any connections and database objects it creates.

## 17. Locator And POM Contract

Playwright selectors follow this priority order:

1. `getByRole` with accessible name.
2. `getByLabel` / `getByPlaceholder`.
3. `getByText` for stable user-visible text only when the test locale is fixed and the i18n key/value is part of the test contract.
4. `getByTestId` for canvas, grid, virtualized rows, or internal state anchors.
5. CSS locators only when no semantic or test-id locator is possible.

POM rules:

- POM methods must fail when required controls are missing.
- POM methods must not silently no-op.
- POM methods must not catch selector failures and proceed.
- Avoid `waitForTimeout`. Use web-first assertions, request waits, URL waits, store version waits, or `expect.poll`.
- POM methods should model user intent, not implementation trivia.
- Setup-only helpers may seed Zustand stores through `__DT_E2E__`; interaction assertions must use real UI when a UI path exists.

Naming guidance:

- `DashboardPage` for dashboard UI flows.
- `FilesLibraryPage` for file artifact lifecycle UI.
- `DataSourcesPage` for settings/data-source form flows.
- Keep API request helpers separate from browser page objects.

## 18. Test Data And Isolation

Default suites should not rely on the user's real DataTalk metadata.

Recommended isolation strategy:

- Use a test metadata database or resettable test profile when the app supports it.
- Create test connections through API helpers or controlled UI steps.
- Prefix created objects with `e2e_`.
- Delete created connections, dashboard documents, and file artifact rows in `afterEach`.
- Keep real DB smoke destructive operations out of shared schemas. Use dedicated test schemas/databases.
- Do not assume test execution order across files.

For Dashboard tests:

- Minimal dashboard payloads should use stable ids and simple widgets.
- Widget SQL should point to mocked or known test data only when executing queries is in scope.
- UI preview tests can use non-executing dashboard documents.

For data-source form tests:

- Intercept save/test requests where possible to assert payloads without opening real sockets.
- Real connection success belongs to Suite 6.

## 19. Execution Tags

Recommended tags:

| Tag | Meaning |
|---|---|
| `@preflight` | Any preflight readiness check. |
| `@preflight-api` | Backend/API readiness checks required for request suites. |
| `@preflight-e2e` | Backend + browser dev server readiness checks required for UI suites. |
| `@contract` | Request/API contract, no browser UI. |
| `@ui` | Browser UI, no real AI/model. |
| `@component` | Vitest component tests. |
| `@a11y` | Accessibility and keyboard assertions. |
| `@visual` | Theme/layout/responsive checks. |
| `@real-db` | Real Dameng/OceanBase smoke, opt-in only. |
| `@slow` | Long-running browser or real service test. |

Default CI should run `@contract`, `@ui`, `@component`, `@a11y`, and selected `@visual` checks. It should skip `@real-db` unless explicitly enabled.

## 20. CI And Artifact Policy

Current `client/playwright.config.ts` baseline is:

```ts
reporter: [['list'], ['html', { outputFolder: 'tmp/playwright/report' }]],
use: {
  trace: 'on-first-retry',
  screenshot: 'only-on-failure',
  video: 'off',
}
```

The future implementation plan must make the artifact location unambiguous. If Playwright is run from `client/`, `tmp/playwright/report` resolves under `client/tmp/`, which conflicts with the repository rule that generated artifacts stay under the project-root `tmp/`. Therefore the recommended config cleanup is:

```ts
outputDir: '../tmp/playwright/test-results',
reporter: [['list'], ['html', { outputFolder: '../tmp/playwright/report', open: 'never' }]],
use: {
  trace: 'on-first-retry',
  screenshot: 'only-on-failure',
  video: 'off',
}
```

The intentional policy is: keep video off unless a later debugging plan explicitly enables it, preserve trace/screenshot behavior, and resolve every generated artifact under `/home/wushengzhou/workspace/github/data-talk/tmp/`.

## 21. Failure Classification

| Failure type | Action |
|---|---|
| Implemented behavior deviates from spec | Register BUG. |
| Known active-plan gap | Mark `fixme` or skip with plan/spec link; do not register BUG. |
| Missing environment variable for real DB | Skip; do not register BUG. |
| Selector cannot find a required implemented button | Register BUG if the UI surface is declared complete. |
| Prompt expectation conflicts with current controller/product spec | Update the test design or execution plan before running; do not guess. |
| Visual/a11y violation against `client/DESIGN.md` | Register BUG if observed in runtime test. |
| Flaky infrastructure or server not started | Report test infrastructure failure; do not register product BUG unless app behavior is the root cause. |
| `adapterClient` or E2E helper fails before reaching product behavior | Treat as test infrastructure failure and fix the helper/config first; do not register a product BUG unless investigation proves app behavior caused the helper failure. |

## 22. Expected Files In The Future Implementation Plan

This design defines the target structure for a later execution plan. Some component-test files already exist and should be extended rather than recreated.

New or expected E2E files:

```text
client/tests/e2e/preflight.spec.ts
client/tests/e2e/dashboard-api-contract.spec.ts
client/tests/e2e/dashboard-ui-e2e.spec.ts
client/tests/e2e/datasource-forms.spec.ts
client/tests/e2e/visual-a11y.spec.ts
client/tests/e2e/real-db-smoke.spec.ts
client/tests/e2e/pom/dashboard.page.ts
client/tests/e2e/pom/files-library.page.ts
client/tests/e2e/pom/data-sources.page.ts
client/tests/e2e/fixtures/dashboard-fixtures.ts
client/tests/e2e/fixtures/e2e-preflight.ts
```

Existing component-test baselines to extend:

| File | Current role |
|---|---|
| `client/src/features/stage/components/__tests__/file-artifact-status-badge.test.tsx` | Existing badge baseline; currently asserts emoji icons + semantic token classes + ARIA label. |
| `client/src/features/stage/components/__tests__/files-library-tab.test.tsx` | Existing Files Library grouping/rendering baseline. Extend for dashboard once FileArtifact dashboard support is complete. |
| `client/src/features/settings/data-sources/__tests__/data-sources-page.test.tsx` | Existing settings data-source page baseline. Extend for shared table/action behavior if needed. |
| `client/src/features/settings/data-sources/__tests__/tidb-connection-form.test.tsx` | Existing kind-specific form baseline pattern. Reuse pattern for Dameng/OceanBase form tests. |
| `client/src/features/dashboard/__tests__/dashboard-canvas.test.tsx` | Existing dashboard canvas baseline. Extend for additional viewer/editor/error cases. |

Potential new component-test files:

```text
client/src/features/settings/data-sources/__tests__/multi-mode-connection-fields.test.tsx
client/src/features/settings/data-sources/__tests__/dameng-connection-form.test.tsx
client/src/features/settings/data-sources/__tests__/oceanbase-connection-form.test.tsx
```

## 23. Acceptance Criteria

The future implementation is complete only when:

1. Suite 0 records design, compatibility, BUG, exec-plan, and temp-artifact gates.
2. Fast deterministic suites run without real Dameng/OceanBase servers.
3. Real DB smoke is skipped by default and runs only with explicit environment variables.
4. POM helpers fail loudly on missing required controls and contain no silent no-op paths.
5. Tests use role/label locators first and test ids only for non-semantic surfaces.
6. Dashboard tests distinguish current reachable UI from future editor/persistence behavior.
7. Dameng and OceanBase tests align with `DATA_SOURCE_TYPE_COMPATIBILITY.md`.
8. Visual/a11y tests assert focus, keyboard access, accessible names, non-color state, theme readability, and text overflow.
9. All Playwright artifacts are emitted under `tmp/`.
10. Any runtime product deviations found by E2E are registered in `docs/bugs/` and reported with an explicit count.

## 24. Review Notes

This spec intentionally preserves the original prompt's six-suite coverage but changes its execution semantics:

- API, UI, component, visual/a11y, and real DB smoke are separate.
- Readiness is checked before execution.
- Known implementation gaps are not treated as automatic BUGs.
- Emoji/icon expectations from the prompt are not locked as design requirements; the desired frontend direction is lucide/SVG icons with accessible labels.
- Real database validation is opt-in.
- `tmp/e2e-playwright-testing-prompt.md` is superseded by this document.
