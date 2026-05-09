# Dashboard / FileArtifact + Data Source E2E Test Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Playwright, Vitest, fixture, and Page Object coverage described in [Dashboard / FileArtifact + Data Source E2E Test Design](../product-specs/2026-05-09-dashboard-datasource-e2e-test-design.md), with suites for preflight, dashboard API contracts, dashboard UI promotion, data-source forms, file artifact integration, visual/accessibility checks, and optional real database smoke tests.

**Architecture:** Keep the test system layered: deterministic API fixtures and `adapterClient` for backend contracts, Playwright Page Objects for browser workflows, Vitest component tests for isolated UI behavior, and explicit tags for CI selection. E2E artifacts stay under repo-root `tmp/` through Playwright config paths.

**Tech Stack:** Playwright Test, Vitest, React Testing Library, TypeScript, Vite, Tauri React client, Spring Boot adapter REST API.

---

## Design Inputs

This implementation must apply the frontend constraints from [client/DESIGN.md](../../client/DESIGN.md):

- DataTalk is a dense AI-native data workbench, not a landing page; E2E assertions should prioritize task workflows, dense layout integrity, and repeated operations.
- Stage state is global, not per-session; dashboard promotion tests must assert Stage tab behavior instead of assuming session-scoped dashboard state.
- Feature UI should use semantic tokens, not raw primitive colors; visual/accessibility tests should flag state styles that depend on color alone.
- Light and dark themes are first-class; visual checks must run both themes for the covered surfaces.
- State must not be communicated by color alone; tests should look for accessible labels, text, icon labels, or badges.
- Icon-only actions require accessible names; suites must assert `aria-label` or role names for toolbar/icon buttons.
- Keyboard and focus behavior are part of the contract; form suites must verify tab order and focus visibility for the critical controls.
- Reduced-motion support is expected; visual suites should run with reduced motion enabled where Playwright project config permits it.

Data source compatibility constraints from [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md):

- `oceanbase` is first-class Day-1 only in MySQL mode. It uses `compatibilityMode`, `tenant`, `cluster`, default port `2881`, and must not imply Oracle-mode support.
- `dameng` is first-class Day-1 as DM8 single-mode only. It uses default port `5236`, optional schema through `databaseName`, and no multi-mode controls.
- This plan verifies UI/test behavior only. JDBC driver implementation, SQL splitter internals, schema discovery internals, and MCP action schema changes are `N/A` unless an implementation step discovers a product defect requiring code changes.

Known BUG input from [docs/bugs/index.md](../bugs/index.md):

- No open BUG is listed at plan creation time.
- If an E2E run discovers runtime product deviation, create or update a BUG document under `docs/bugs/` and register it in `docs/bugs/index.md` before reporting completion.

---

## Scope

- Align Playwright artifact output with repo-root `tmp/playwright/...`.
- Add Suite 0 preflight tests with `@preflight`, `@preflight-api`, and `@preflight-e2e`.
- Add deterministic dashboard fixtures and adapter REST helpers.
- Add dashboard API contract tests for promote, fetch, patch, validation, size limit, not-found, and version conflict behavior.
- Add browser tests for chat dashboard block promotion into Stage/dashboard tabs.
- Harden and extend the existing connection Page Object coverage instead of replacing it.
- Add component and browser coverage for TiDB, OceanBase MySQL mode, and Dameng connection forms.
- Extend file artifact and dashboard component baselines where they already exist.
- Add visual/accessibility smoke coverage for dashboard, file library, and data-source surfaces.
- Add optional real database smoke projects for first-class database types, with skips when runtime services are unavailable.

## Non-Goals

- Do not implement OceanBase Oracle mode.
- Do not change backend dashboard semantics unless tests reveal an implementation defect that must be filed or fixed under the BUG gate.
- Do not replace the existing shadcn/ui foundation or redesign the data-source pages.
- Do not store traces, screenshots, or reports outside repo-root `tmp/`.

---

## Current Baseline

Existing files to extend:

- `client/playwright.config.ts`
- `client/tests/e2e/fixtures/adapter-client.ts`
- `client/tests/e2e/fixtures/er-test-helpers.ts`
- `client/tests/e2e/pom/connection-manager.page.ts`
- `client/tests/e2e/dashboard.spec.ts`
- `client/src/main.tsx`
- `client/src/features/stage/components/__tests__/file-artifact-status-badge.test.tsx`
- `client/src/features/stage/components/__tests__/files-library-tab.test.tsx`
- `client/src/features/settings/data-sources/__tests__/data-sources-page.test.tsx`
- `client/src/features/settings/data-sources/__tests__/tidb-connection-form.test.tsx`
- `client/src/features/dashboard/__tests__/dashboard-canvas.test.tsx`
- `client/src/features/dashboard/__tests__/dashboard-tabs-store.test.ts`

New files expected:

- `client/tests/e2e/preflight.spec.ts`
- `client/tests/e2e/dashboard-api-contract.spec.ts`
- `client/tests/e2e/dashboard-ui.spec.ts`
- `client/tests/e2e/data-source-forms.spec.ts`
- `client/tests/e2e/visual-a11y.spec.ts`
- `client/tests/e2e/real-db-smoke.spec.ts`
- `client/tests/e2e/fixtures/dashboard-fixtures.ts`
- `client/tests/e2e/fixtures/preflight-report.ts`
- `client/tests/e2e/pom/dashboard.page.ts`
- `client/tests/e2e/pom/files-library.page.ts`
- `client/tests/e2e/pom/data-sources.page.ts`
- `client/tests/e2e/types/datatalk-e2e.d.ts`
- `client/src/features/settings/data-sources/__tests__/dameng-connection-form.test.tsx`
- `client/src/features/settings/data-sources/__tests__/oceanbase-connection-form.test.tsx`
- `client/src/features/settings/data-sources/__tests__/multi-mode-connection-fields.test.tsx`

---

## Implementation Steps

### 1. Align Playwright Artifact Paths

- [ ] Edit `client/playwright.config.ts`.
- [ ] Add or confirm `outputDir: '../tmp/playwright/test-results'`.
- [ ] Change HTML reporter output to repo-root tmp:

  ```ts
  reporter: [['list'], ['html', { outputFolder: '../tmp/playwright/report', open: 'never' }]],
  ```

- [ ] Keep the current evidence policy unless a failing workflow requires a documented change:

  ```ts
  trace: 'on-first-retry',
  screenshot: 'only-on-failure',
  video: 'off',
  ```

- [ ] Verify generated paths are relative to `client/` and resolve to `/home/wushengzhou/workspace/github/data-talk/tmp/playwright/...`.
- [ ] Run:

  ```bash
  cd client && npx playwright test --list
  ```

Expected result: Playwright loads all projects/specs without writing reports outside `tmp/`.

### 2. Implement Suite 0 Preflight as Playwright Tests

- [ ] Create `client/tests/e2e/preflight.spec.ts`.
- [ ] Create `client/tests/e2e/fixtures/preflight-report.ts` with:

  ```ts
  export type PreflightCheck = {
    name: string
    tag: '@preflight-api' | '@preflight-e2e'
    status: 'pass' | 'fail' | 'skip'
    detail: string
  }
  ```

- [ ] Implement a report writer that writes Markdown to:

  ```txt
  ../tmp/playwright/<run-id>/preflight.md
  ```

  from the `client/` working directory.

- [ ] Add Playwright tests with tags in the titles:

  ```ts
  test('adapter health is reachable @preflight @preflight-api', async ({ request }) => {})
  test('browser baseURL is reachable @preflight @preflight-e2e', async ({ page }) => {})
  ```

- [ ] Keep `@preflight-api` independent from the browser dev server.
- [ ] Keep `@preflight-e2e` scoped to browser E2E readiness only.
- [ ] Do not put these checks in global `beforeAll`; they must be independently runnable with `--grep @preflight`.
- [ ] Run:

  ```bash
  cd client && npx playwright test tests/e2e/preflight.spec.ts --grep @preflight
  ```

Expected result: API-only CI can run `--grep @preflight-api`; browser E2E CI can run `--grep @preflight-e2e`; failures produce `tmp/playwright/<run-id>/preflight.md`.

### 3. Add Dashboard Fixtures and Adapter Client Helpers

- [ ] Create `client/tests/e2e/fixtures/dashboard-fixtures.ts`.
- [ ] Export a valid dashboard factory:

  ```ts
  export function makeDashboardPayload(overrides = {}) {
    return {
      schemaVersion: 1,
      id: `dash_e2e_${Date.now()}`,
      title: 'E2E Dashboard',
      description: 'Dashboard created by Playwright contract tests',
      defaultConnectionId: null,
      parameters: [],
      widgets: [
        {
          id: 'chart_w_e2e1',
          type: 'chart',
          position: { x: 0, y: 0, w: 6, h: 4 },
          query: { sql: 'SELECT 1 AS value', paramRefs: {} },
          options: {
            title: 'Metric',
            echartsOption: { xAxis: { type: 'category' }, yAxis: { type: 'value' }, series: [{ type: 'bar', data: [1] }] },
            dataMapping: { rowsAsDataset: true },
          },
        },
        {
          id: 'markdown_w_e2e1',
          type: 'markdown',
          position: { x: 6, y: 0, w: 6, h: 3 },
          options: { text: '## Notes' },
        },
      ],
      layout: { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
      version: 1,
      createdAt: 0,
      updatedAt: 0,
      ...overrides,
    }
  }
  ```

- [ ] Export an oversized dashboard factory that exceeds `256 * 1024` bytes to exercise `DashboardArtifactService.MAX_PAYLOAD_BYTES`.
- [ ] Extend `client/tests/e2e/fixtures/adapter-client.ts` with typed helpers:

  ```ts
  dashboardPromote(body: unknown)
  dashboardGet(dashboardId: string)
  dashboardPatch(dashboardId: string, body: unknown)
  ```

- [ ] If file artifact REST endpoints are needed by later suites, add typed helpers only after confirming actual adapter routes:

  ```ts
  listConnectionFiles(connectionId: string)
  discardFile(fileArtifactId: string)
  ```

- [ ] Treat a failure inside `adapterClient` itself as test infrastructure failure, not product failure, unless the HTTP response proves a server behavior defect.

Expected result: Dashboard contract tests can use deterministic payloads without ad hoc object literals in each spec.

### 4. Implement Suite 1 Dashboard API Contracts

- [ ] Create `client/tests/e2e/dashboard-api-contract.spec.ts`.
- [ ] Use `adapterClient` plus `makeDashboardPayload`.
- [ ] Add contract cases:

  ```txt
  promote valid dashboard -> 200/201 and returns dashboard id/version/file artifact metadata
  get promoted dashboard -> 200 and stable payload
  patch with valid baseVersion -> success and version increment
  patch with stale baseVersion -> 409 version conflict
  get missing dashboard -> 404
  promote structurally invalid dashboard -> 422 validation error
  promote oversized dashboard -> 413 with maxSize detail
  promote missing dashboard body -> 400 invalid_request
  patch empty operations -> 400 invalid_request
  patch missing baseVersion -> assert current adapter behavior and document the observed status
  ```

- [ ] Do not assume missing `baseVersion` returns `400`; the current Java request has primitive `int baseVersion`, so the implementation may bind `0` and return conflict. The test must assert the actual contract after confirming the response body.
- [ ] Add `@contract` and `@dashboard` tags to titles.
- [ ] Run:

  ```bash
  cd client && npx playwright test tests/e2e/dashboard-api-contract.spec.ts --grep @contract
  ```

Expected result: API behavior is documented by executable tests, including the 256KB size gate.

### 5. Define the `__DT_E2E__` Dashboard Interface

- [ ] Create `client/tests/e2e/types/datatalk-e2e.d.ts`.
- [ ] Declare the existing global surface plus dashboard extension:

  ```ts
  import type { useDashboardTabsStore } from '../../../src/features/dashboard/store/dashboard-tabs-store'

  type DashboardStoreState = ReturnType<typeof useDashboardTabsStore.getState>

  declare global {
    interface Window {
      __DT_E2E__?: {
        stage: () => unknown
        er: () => unknown
        session: () => unknown
        coordinator: () => unknown
        dashboard: () => DashboardStoreState
      }
    }
  }
  ```

- [ ] Update `client/src/main.tsx` to expose:

  ```ts
  dashboard: () => useDashboardTabsStore.getState(),
  ```

- [ ] Import `useDashboardTabsStore` from the existing dashboard store.
- [ ] Prefer read-only store inspection in E2E tests. Add mutation helpers only when a browser workflow cannot seed state through UI/API.
- [ ] Run:

  ```bash
  cd client && npx tsc --noEmit
  ```

Expected result: E2E tests can deterministically inspect dashboard tab state without scattered `any` casts.

### 6. Implement Dashboard Page Object

- [ ] Create `client/tests/e2e/pom/dashboard.page.ts`.
- [ ] Include locators based on stable roles/test ids:

  ```ts
  class DashboardPage {
    dashboardPreview()
    skeleton()
    error()
    openToWorkbenchButton()
    canvas()
    widgetByTitle(title: string)
    stageTabByName(name: string)
    async expectWorkbenchOpen(title: string)
  }
  ```

- [ ] If the dashboard canvas lacks a stable test id, add `data-testid="dashboard-canvas"` to the smallest relevant container in `client/src/features/dashboard/dashboard-canvas.tsx`.
- [ ] Avoid `waitForTimeout`; use `expect(locator).toBeVisible()` and `expect.poll` for store state.
- [ ] Keep text locators constrained to fixed test language. When using `getByText`, assert the app/test fixture language is fixed and use exact text.

Expected result: Browser specs use one Page Object for dashboard promotion and workbench assertions.

### 7. Implement Suite 2 Dashboard UI Promotion

- [ ] Replace the current skeleton content in `client/tests/e2e/dashboard.spec.ts` or move it into `client/tests/e2e/dashboard-ui.spec.ts` and delete the empty skeleton.
- [ ] Build the fixture by rendering or routing to a chat message containing the dashboard block payload already supported by `dashboard-block.tsx`.
- [ ] Cover the following cases with `@e2e @dashboard` tags:

  ```txt
  streaming dashboard block shows skeleton
  invalid dashboard block shows error
  valid dashboard block shows preview
  Open to workbench promotes dashboard to global Stage
  promoted dashboard creates or selects a dashboard tab
  repeated promotion of the same dashboard does not create duplicate tabs
  chart widget and markdown widget render in the canvas
  ```

- [ ] Use `window.__DT_E2E__!.dashboard()` to assert tab count and active tab after promotion.
- [ ] Use `window.__DT_E2E__!.stage()` to assert Stage panel activation if the store exposes a stable field.
- [ ] Run:

  ```bash
  cd client && npx playwright test tests/e2e/dashboard-ui.spec.ts --grep @dashboard
  ```

Expected result: The chat-to-workbench path is covered at the browser level and no longer represented by an empty spec.

### 8. Harden Existing Connection Page Object

- [ ] Update `client/tests/e2e/pom/connection-manager.page.ts` rather than replacing it.
- [ ] Remove silent no-op methods. When a required button/input is missing, the method must fail with a meaningful Playwright assertion.
- [ ] Replace `waitForTimeout` with role/test-id assertions and `expect.poll` where state polling is required.
- [ ] Add methods for existing and new data-source form controls:

  ```ts
  openCreateDialog()
  chooseDatabaseType(type: 'mysql' | 'postgresql' | 'tidb' | 'oceanbase' | 'dameng')
  fillHost(host: string)
  fillPort(port: string)
  fillUsername(username: string)
  fillPassword(password: string)
  fillDatabaseName(databaseName: string)
  fillDisplayName(displayName: string)
  chooseOceanBaseMySqlMode()
  fillOceanBaseTenant(tenant: string)
  fillOceanBaseCluster(cluster: string)
  assertDamengDefaults()
  assertOceanBaseDefaults()
  save()
  assertValidationMessage(messageKeyOrText: string)
  ```

- [ ] Create `client/tests/e2e/pom/data-sources.page.ts` only if the new wrapper delegates to `connection-manager.page.ts` and keeps the existing methods available.
- [ ] Preserve existing callers by adding aliases instead of renaming public methods without migration.

Expected result: Data-source specs use deterministic locators and fail clearly when UI affordances regress.

### 9. Add Data Source Component Tests

- [ ] Add `client/src/features/settings/data-sources/__tests__/oceanbase-connection-form.test.tsx`.
- [ ] Add `client/src/features/settings/data-sources/__tests__/dameng-connection-form.test.tsx`.
- [ ] Add `client/src/features/settings/data-sources/__tests__/multi-mode-connection-fields.test.tsx`.
- [ ] Extend existing `tidb-connection-form.test.tsx` only for TiDB regressions.
- [ ] Assert OceanBase MySQL-mode behavior:

  ```txt
  default port is 2881
  compatibility mode is MySQL
  tenant and cluster fields are present
  Oracle mode is not offered as supported
  submit payload includes oceanbase-specific fields
  ```

- [ ] Assert Dameng behavior:

  ```txt
  default port is 5236
  no compatibility-mode selector is rendered
  databaseName is used as optional schema/database field
  submit payload uses type dameng and does not include OceanBase-only fields
  ```

- [ ] Assert `MultiModeConnectionFields` accessibility:

  ```txt
  enabled options are keyboard reachable
  disabled options have disabled semantics and are not selectable
  selected option has a non-color state indicator
  labels remain visible in light and dark theme containers
  ```

- [ ] Run:

  ```bash
  cd client && npx vitest run \
    src/features/settings/data-sources/__tests__/tidb-connection-form.test.tsx \
    src/features/settings/data-sources/__tests__/oceanbase-connection-form.test.tsx \
    src/features/settings/data-sources/__tests__/dameng-connection-form.test.tsx \
    src/features/settings/data-sources/__tests__/multi-mode-connection-fields.test.tsx
  ```

Expected result: Data-source form semantics are covered without relying only on browser E2E.

### 10. Implement Suite 3 Data Source Browser Forms

- [ ] Create `client/tests/e2e/data-source-forms.spec.ts`.
- [ ] Use `connection-manager.page.ts` or the delegating `data-sources.page.ts`.
- [ ] Add browser cases with `@e2e @datasource` tags:

  ```txt
  TiDB form keeps existing behavior and can submit valid input
  OceanBase defaults to port 2881 and MySQL compatibility
  OceanBase tenant and cluster are persisted in the outgoing request
  Dameng defaults to port 5236
  Dameng form has no compatibility selector
  required-field validation is visible and accessible
  keyboard tab order reaches type, host, port, username, password, database, name, save
  icon-only actions on the data-source table have accessible names
  ```

- [ ] Intercept create/update requests with Playwright `page.route` when the test only needs frontend payload verification.
- [ ] Use real adapter calls only in smoke tests that require backend persistence.
- [ ] Run:

  ```bash
  cd client && npx playwright test tests/e2e/data-source-forms.spec.ts --grep @datasource
  ```

Expected result: Browser-level data-source workflows match the compatibility contract and the frontend design contract.

### 11. Extend File Artifact and Dashboard Component Baselines

- [ ] Extend `client/src/features/stage/components/__tests__/file-artifact-status-badge.test.tsx`.
- [ ] For the current phase, assert the current emoji rendering behavior in `file-artifact-status-badge.tsx`.
- [ ] Add a separate note in the test description or plan comments that replacing emoji with lucide/SVG icons is a visual cleanup task, not part of this E2E implementation.
- [ ] Extend `client/src/features/stage/components/__tests__/files-library-tab.test.tsx` with:

  ```txt
  file status badge is present for dashboard artifacts
  discard/delete action is accessible by role name
  status text is available without relying on color alone
  ```

- [ ] Extend `client/src/features/dashboard/__tests__/dashboard-canvas.test.tsx` with:

  ```txt
  empty state remains visible when no dashboard is loaded
  chart widget container is rendered for chart widgets
  markdown widget container is rendered for markdown widgets
  editor mode does not remove accessible widget titles
  ```

- [ ] Run:

  ```bash
  cd client && npx vitest run \
    src/features/stage/components/__tests__/file-artifact-status-badge.test.tsx \
    src/features/stage/components/__tests__/files-library-tab.test.tsx \
    src/features/dashboard/__tests__/dashboard-canvas.test.tsx
  ```

Expected result: Existing component baselines become explicit coverage rather than duplicated new files.

### 12. Implement Suite 4 Visual and Accessibility Smoke

- [ ] Create `client/tests/e2e/visual-a11y.spec.ts`.
- [ ] Cover `@visual @a11y` cases for:

  ```txt
  dashboard workbench in light theme
  dashboard workbench in dark theme
  file library tab in light theme
  file library tab in dark theme
  data-source table and create dialog in light theme
  data-source table and create dialog in dark theme
  ```

- [ ] Assert layout integrity with Playwright locators before screenshots:

  ```txt
  main toolbar buttons are visible
  no visible text overflows its button bounds on target viewport
  no modal/dialog content exceeds viewport on mobile width
  icon-only buttons have accessible names
  status indicators have text/aria labels
  ```

- [ ] Keep screenshots under `../tmp/playwright/test-results` through Playwright output config.
- [ ] If axe-core is already available in the repo, use it. If not, use role/name/focus assertions without adding a new dependency unless the implementation owner approves the package change.
- [ ] Run:

  ```bash
  cd client && npx playwright test tests/e2e/visual-a11y.spec.ts --grep "@visual|@a11y"
  ```

Expected result: The covered surfaces meet the local frontend design contract in both themes, or defects are registered as BUG documents.

### 13. Implement Suite 5 File Artifact Browser Integration

- [ ] Create or extend `client/tests/e2e/file-artifacts.spec.ts` only if no suitable existing file covers the workflow.
- [ ] Use dashboard API promotion to create a dashboard-backed file artifact when backend endpoints expose it.
- [ ] Cover `@e2e @files @dashboard` cases:

  ```txt
  promoted dashboard appears in file library
  file artifact status badge matches current backend status
  selecting dashboard file opens or focuses the dashboard workbench tab
  discard action updates the file list or status
  failed discard response surfaces an accessible error
  ```

- [ ] If the adapter has no browser-consumable file artifact route for a case, mark that case `test.fixme` with the exact missing route and add the route to implementation notes.
- [ ] Do not create fake file state through undocumented store mutation when a REST contract exists.

Expected result: File artifact behavior is connected to dashboard promotion, not only tested as isolated components.

### 14. Implement Suite 6 Optional Real Database Smoke

- [ ] Create `client/tests/e2e/real-db-smoke.spec.ts`.
- [ ] Add `@realdb @datasource` tags.
- [ ] Use environment variables for externally managed services:

  ```txt
  DATATALK_E2E_MYSQL_URL
  DATATALK_E2E_TIDB_URL
  DATATALK_E2E_OCEANBASE_URL
  DATATALK_E2E_DAMENG_URL
  ```

- [ ] Skip each database case with `test.skip` when the required environment variable is absent.
- [ ] For Dameng and OceanBase, prefer official or maintained Testcontainers images only when available to the implementation environment. If no reliable image is available, keep those cases skipped with a clear annotation.
- [ ] Cover smoke behavior only:

  ```txt
  create connection
  test connection
  run SELECT 1 or the database-specific equivalent
  clean up connection metadata
  ```

- [ ] Do not make real database smoke part of default local Playwright runs.

Expected result: Real database coverage is opt-in and does not make default CI flaky.

### 15. Update Execution Tags and CI Commands

- [ ] Document the final tags in the plan implementation notes or a test README near `client/tests/e2e`.
- [ ] Ensure these tags are present:

  | Tag | Scope | Default CI |
  | --- | --- | --- |
  | `@preflight` | All readiness checks in `preflight.spec.ts` | Yes |
  | `@preflight-api` | Adapter/API readiness only | Yes for API jobs |
  | `@preflight-e2e` | Browser dev-server readiness only | Yes for browser E2E jobs |
  | `@contract` | REST/API contract tests | Yes |
  | `@dashboard` | Dashboard API/UI/workbench tests | Yes |
  | `@datasource` | Data-source form and workflow tests | Yes |
  | `@files` | File artifact integration tests | Yes |
  | `@visual` | Visual layout smoke checks | Scheduled or targeted |
  | `@a11y` | Accessibility smoke checks | Scheduled or targeted |
  | `@realdb` | External real database smoke tests | Opt-in only |
  | `@smoke` | Small cross-surface confidence checks | Yes |

- [ ] Recommended command set:

  ```bash
  cd client && npx playwright test --grep @preflight
  cd client && npx playwright test --grep @contract
  cd client && npx playwright test --grep "@dashboard|@datasource|@files"
  cd client && npx playwright test --grep "@visual|@a11y"
  cd client && npx playwright test --grep @realdb
  ```

- [ ] Keep `@realdb` excluded from default CI unless the environment provisions services explicitly.

Expected result: CI can run API readiness, browser readiness, contracts, UI E2E, visual/accessibility, and real DB smoke independently.

### 16. Register BUGs Found During E2E Runs

- [ ] During any Playwright execution, watch for product deviations:

  ```txt
  button does not respond
  incorrect data is displayed
  UI layout overlaps or clips critical text
  console error indicates product failure
  accessibility contract is violated
  backend returns a response outside the documented contract
  ```

- [ ] For each product deviation, create a BUG file under `docs/bugs/` with status `open`.
- [ ] Register the BUG in `docs/bugs/index.md`.
- [ ] Store only allowed archival screenshots under `docs/bugs/assets/<BUG-ID>/` when needed; keep traces, HAR files, HTML reports, and large artifacts under `tmp/`.
- [ ] In the final implementation report, state the exact number of BUGs found and registered. State `0` explicitly when none are found.

Expected result: E2E discoveries are tracked in canonical BUG docs, not only in chat.

### 17. Consolidated Verification

- [ ] Run frontend type check after all client edits:

  ```bash
  cd client && npx tsc --noEmit
  ```

- [ ] Run targeted Vitest suites:

  ```bash
  cd client && npx vitest run \
    src/features/settings/data-sources/__tests__/tidb-connection-form.test.tsx \
    src/features/settings/data-sources/__tests__/oceanbase-connection-form.test.tsx \
    src/features/settings/data-sources/__tests__/dameng-connection-form.test.tsx \
    src/features/settings/data-sources/__tests__/multi-mode-connection-fields.test.tsx \
    src/features/stage/components/__tests__/file-artifact-status-badge.test.tsx \
    src/features/stage/components/__tests__/files-library-tab.test.tsx \
    src/features/dashboard/__tests__/dashboard-canvas.test.tsx
  ```

- [ ] Run targeted Playwright suites:

  ```bash
  cd client && npx playwright test --grep "@preflight|@contract|@dashboard|@datasource|@files"
  ```

- [ ] Run visual/accessibility suite:

  ```bash
  cd client && npx playwright test --grep "@visual|@a11y"
  ```

- [ ] Run real DB smoke only when services are available:

  ```bash
  cd client && npx playwright test --grep @realdb
  ```

- [ ] If backend code is edited to satisfy a discovered defect, run:

  ```bash
  cd server && mvn compile -q
  ```

Expected result: Client type checks and targeted tests pass, with documented skips for unavailable real databases.

### 18. Documentation Housekeeping

- [ ] Mark completed checkboxes in this plan as implementation finishes.
- [ ] Move this plan entry from Active to Completed in [docs/exec-plans/index.md](index.md) after execution finishes.
- [ ] If the product spec is fully implemented, update [docs/product-specs/index.md](../product-specs/index.md) to reflect implementation status.
- [ ] Propagate durable conventions to canonical docs when they become established:

  ```txt
  Playwright tmp output paths -> docs/FRONTEND.md or client test README
  E2E tag taxonomy -> client test README
  __DT_E2E__ interface location -> client test README
  data-source compatibility discoveries -> docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
  ```

Expected result: The plan, indexes, and canonical docs reflect the executed state.

---

## Risk Controls

- API preflight and browser preflight are separated to avoid blocking API-only test jobs on a Vite/browser runtime.
- Existing component tests are extended rather than recreated, avoiding duplicate baselines.
- Existing `connection-manager.page.ts` is hardened and extended, preserving current callers.
- `getByText` is used only when the fixed test language and exact i18n text are known; prefer role, label, test id, and scoped text queries.
- Current emoji rendering in `file-artifact-status-badge.tsx` is asserted as current behavior. Lucide/SVG replacement is a separate visual cleanup task.
- Real database tests are opt-in and environment-gated.
- Test infrastructure errors from `adapterClient` are classified separately from product BUGs.

## Done Definition

- All expected specs, fixtures, Page Objects, and component tests are added or existing baselines are extended.
- Playwright artifacts resolve under repo-root `tmp/playwright/...`.
- Suite 0 is implemented as `preflight.spec.ts` Playwright tests with `@preflight`, `@preflight-api`, and `@preflight-e2e`.
- `__DT_E2E__` has a dashboard method and a TypeScript declaration under `client/tests/e2e/types/`.
- Dashboard API contract tests include the 256KB payload limit.
- Data-source tests cover TiDB baseline, OceanBase MySQL mode, and Dameng DM8 behavior.
- Browser workflow tests cover dashboard promotion, Stage activation, file artifact integration, and form payloads.
- Visual/accessibility smoke tests cover light and dark themes for the targeted surfaces.
- Real DB smoke tests skip cleanly when services are absent.
- Any product deviations discovered during E2E execution are registered under `docs/bugs/`.
- Verification commands in Step 17 have been run and recorded in the final implementation report.
- This plan and the exec-plan index are updated during completion housekeeping.
