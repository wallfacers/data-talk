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
| `@ingestion` | External data ingestion E2E (REST + MCP + UI + SSE) | Yes |
| `@visual` | Visual layout smoke checks | Scheduled or targeted |
| `@a11y` | Accessibility smoke checks | Scheduled or targeted |
| `@realdb` | External real database smoke tests | Opt-in only |
| `@smoke` | Small cross-surface confidence checks | Yes |
| `@e2e` | Browser E2E workflow tests | Yes |

## Test Suites

| File | Tag | Tests |
|------|-----|-------|
| `preflight.spec.ts` | `@preflight` | 2 |
| `dashboard-api-contract.spec.ts` | `@contract @dashboard` | 10 |
| `dashboard-ui.spec.ts` | `@e2e @dashboard` | 7 |
| `data-source-forms.spec.ts` | `@e2e @datasource` | 8 |
| `file-artifacts.spec.ts` | `@e2e @files @dashboard` | 6 (3 active + 3 fixme) |
| `visual-a11y.spec.ts` | `@visual @a11y` | 6 |
| `real-db-smoke.spec.ts` | `@realdb @datasource` | 4 (env-gated) |
| `ingestion-preflight.spec.ts` | `@preflight @ingestion @skill` | 4 |
| `ingestion-credentials-api.spec.ts` | `@e2e @ingestion @api` | 9 |
| `ingestion-credentials-ui.spec.ts` | `@e2e @ingestion @ui` | 7 |
| `ingestion-fetch-mcp.spec.ts` | `@e2e @ingestion @api` | 12 |
| `ingestion-infer-mcp.spec.ts` | `@e2e @ingestion @api` | 8 |
| `ingestion-ddl-mcp.spec.ts` | `@e2e @ingestion @api` | 5 |
| `ingestion-execute-mcp.spec.ts` | `@e2e @ingestion @api` | 10 |
| `ingestion-error-paths.spec.ts` | `@e2e @ingestion @api @error` | 5 |
| `ingestion-job-tab-ui.spec.ts` | `@e2e @ingestion @ui` | 13 |
| `ingestion-library-tab-ui.spec.ts` | `@e2e @ingestion @ui` | 6 |
| `ingestion-sse-events.spec.ts` | `@e2e @ingestion @ui @sse` | 8 |

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

# Ingestion epic E2E (requires backend with SPRING_PROFILES_ACTIVE=e2e)
npx playwright test --grep @ingestion
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
