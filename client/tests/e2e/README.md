# DataTalk E2E Tests

Playwright and Vitest test suites for the DataTalk client.

## Tag Taxonomy

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
| `@e2e` | Browser E2E workflow tests | Yes |

## Command Set

```bash
# Preflight
npx playwright test --grep @preflight
npx playwright test --grep @preflight-api
npx playwright test --grep @preflight-e2e

# API contracts
npx playwright test --grep @contract

# Dashboard + Data source + Files E2E
npx playwright test --grep "@dashboard|@datasource|@files"

# Visual / accessibility
npx playwright test --grep "@visual|@a11y"

# Real database smoke (opt-in)
npx playwright test --grep @realdb

# All default CI
npx playwright test --grep "@preflight|@contract|@dashboard|@datasource|@files"
```

## Artifact Output

Playwright artifacts are written to repo-root `tmp/playwright/`:
- Test results: `tmp/playwright/test-results/`
- HTML report: `tmp/playwright/report/`
- Traces: `tmp/playwright/test-results/` (on first retry)
- Screenshots: `tmp/playwright/test-results/` (on failure only)

## E2E Global Interface

`window.__DT_E2E__` is exposed in `main.tsx` during dev/test mode:
- `stage()` — Stage store state
- `er()` — ER tabs store state
- `session()` — Session store state
- `coordinator()` — Stage persistence coordinator
- `dashboard()` — Dashboard tabs store state

Type declarations are in `tests/e2e/types/datatalk-e2e.d.ts`.

## Fixtures

- `fixtures/adapter-client.ts` — typed REST helpers for the Spring Boot adapter
- `fixtures/dashboard-fixtures.ts` — deterministic dashboard payload factories
- `fixtures/preflight-report.ts` — preflight check report writer
- `fixtures/er-test-helpers.ts` — ER designer test utilities
- `fixtures/mcp-tool-recorder.ts` — MCP tool invocation recorder
- `fixtures/hybrid-session.ts` — hybrid session management
- `fixtures/h2-setup.ts` — H2 database test setup

## Page Objects

- `pom/connection-manager.page.ts` — connection form interactions
- `pom/data-sources.page.ts` — data sources settings page (delegates to connection-manager)
- `pom/dashboard.page.ts` — dashboard preview, canvas, workbench promotion
- `pom/chat-panel.page.ts` — chat panel interactions
- `pom/stage.page.ts` — Stage workbench panel
- `pom/er-designer.page.ts` — ER designer interactions
- `pom/er-inspector.page.ts` — ER inspector
- `pom/sql-workbench.page.ts` — SQL workbench
- `pom/helpers.ts` — shared POM helpers

## Component Tests (Vitest)

Component tests live alongside source files in `__tests__/` directories. Run with:

```bash
npx vitest run
```

Key suites:
- `data-sources/__tests__/` — TiDB, OceanBase, Dameng, MultiMode connection forms
- `stage/components/__tests__/` — file artifact status badge, files library tab
- `dashboard/__tests__/` — dashboard canvas, tabs store, schema
