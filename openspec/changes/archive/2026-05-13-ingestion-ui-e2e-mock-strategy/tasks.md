## 1. Preflight & Baseline

- [x] 1.1 Verify backend tree clean and develop branch up-to-date (`git status`, `git log -1`)
- [x] 1.2 Verify backend starts cleanly under `SPRING_PROFILES_ACTIVE=e2e` (record startup log, confirm logback `e2e` profile is honored after the BUG-0029 fix)
- [x] 1.3 Verify mock ingestion server fixture (`startMockIngestionServer`) still binds to 127.0.0.1 successfully — smoke run of `ingestion-fetch-mcp.spec.ts` (single test)

## 2. T1 — Credentials UI baseline

- [x] 2.1 Read `client/tests/e2e/ingestion-credentials-ui.spec.ts` end-to-end; confirm no `seedIngestionJob` references and no ghost endpoints
- [x] 2.2 Add strategy header comment: `// Strategy: real backend — exercises /api/ingestion/credentials CRUD directly`
- [x] 2.3 Run the spec under `SPRING_PROFILES_ACTIVE=e2e`: `cd client && npx playwright test ingestion-credentials-ui --project=chromium` (note: project name is `chromium`, not `e2e`)
- [x] 2.4 If any test fails: file a BUG under `docs/bugs/` per the BUG Tracking Gate, do NOT modify spec to work around — fix the product, then re-run **(Resolution: 2 initial failures were test isolation issues — orphan `e2e_cred_*` credentials from prior api-spec interrupted runs. Not a product bug. Broadened `beforeEach` cleanup prefix from `e2e_ui_` to `e2e_`. 7/7 passed after fix.)**
- [x] 2.5 Record passing run output to `tmp/ingestion-ui-e2e-mock-strategy/credentials-ui-run.txt`

## 3. T2 — Library Tab UI cleanup

- [x] 3.1 Open `client/tests/e2e/ingestion-library-tab-ui.spec.ts`
- [x] 3.2 Remove the stale BUG-0013 fixme banner from the header docblock (lines 5–12 area)
- [x] 3.3 Replace with strategy comment: `// Strategy: page.route() mocking — see openspec/specs/ingestion-ui-e2e-testing/spec.md`
- [x] 3.4 Tighten `mockJob()` typing — import the shared `IngestionJobView` type from `@/features/ingestion/api/ingestion-api` and type the helper return as `IngestionJobView` (cast unknown fields if necessary; do NOT widen to `any`)
- [x] 3.5 Run the spec under `SPRING_PROFILES_ACTIVE=e2e`: `cd client && npx playwright test ingestion-library-tab-ui --project=chromium` **(6/6 passed. Findings absorbed: a) `openTab()` needs a fully-formed `StageTab` — added `tabId/title/payload/createdAt`. b) `page.route` URLs must be host-agnostic (`**/api/...`) because the app uses `VITE_API_BASE_URL`. c) Single regex handler covers both `/jobs` and `/jobs/{id}` to avoid glob/regex layering. d) `beforeEach` must purge `ingestion_*` stage_tabs from backend — they persist server-side via `/api/stage/tabs` and accumulate.)**
- [x] 3.6 Record passing run output to `tmp/ingestion-ui-e2e-mock-strategy/library-tab-ui-run.txt`

## 4. T3 — Job Tab UI rewrite

- [x] 4.1 Read `client/src/features/ingestion/ingestion-job-tab.tsx` and `client/src/features/ingestion/api/ingestion-api.ts` to confirm: phase-router status strings, confirm/cancel return shape, and `IngestionJobView` field set
- [x] 4.2 Add strategy header comment to `ingestion-job-tab-ui.spec.ts`: `// Strategy: page.route() mocking — see openspec/specs/ingestion-ui-e2e-testing/spec.md`
- [x] 4.3 Add a top-of-file `mockJob(overrides?: Partial<IngestionJobView>): IngestionJobView` helper imported from `@/features/ingestion/api/ingestion-api`
- [x] 4.4 Remove the `startMockIngestionServer()` / `mock` block from the spec
- [x] 4.5 Rewrite test `Tab opens in Stage via __DT_E2E__`
- [x] 4.6 Rewrite test `Phase router shows fetching phase`
- [x] 4.7 Rewrite test `Mapping phase shows MappingEditor`
- [x] 4.8 Rewrite test `MappingEditor seeds from job.mapping via Phase A hydration`
- [x] 4.9 Rewrite test `Confirm button disabled when no columns`
- [x] 4.10 Rewrite test `Confirm button calls API and Tab phase transitions to writing` (closure-state phase var; mock GET reads phase, mock POST /confirm flips it; uses `page.addInitScript(localStorage.clear)` + server-side stage_tab cleanup in `beforeEach`)
- [x] 4.11 Rewrite test `Cancel button flips status to cancelled` (closure flips to `cancelled`)
- [x] 4.12 Rewrite test `Failed phase shows error message`
- [x] 4.13 Rewrite test `Completed phase shows target table + row count`
- [x] 4.14 Rewrite test `Phase stepper dots visible (6 dots)`
- [x] 4.15 Rewrite test `SourceSummaryCard shows URL, format, bytes`
- [x] 4.16 Rewrite test `DDL preview visible when columns exist`
- [x] 4.17 Rewrite test `PayloadPreviewTable populated from /payload-preview endpoint` (tightened locator to "Showing N of N rows" + "Bob" to avoid mapping-row "Alice" collision)
- [x] 4.18 Run `cd client && npx tsc --noEmit` — zero errors
- [x] 4.19 Run the spec — **13/13 passed (21.3s)**
- [x] 4.20 No product BUGs found; one test-side strict-mode locator collision fixed in 4.17
- [x] 4.21 Record passing run output to `tmp/ingestion-ui-e2e-mock-strategy/job-tab-ui-run.txt`

## 5. Consolidated Verification

- [x] 5.1 Run full client typecheck: `cd client && npx tsc --noEmit` — **zero errors**
- [x] 5.2 Run all three ingestion UI specs together — **26/26 passed (39.0s)**
- [x] 5.3 Run remaining ingestion API/MCP specs (regression) — **47 passed / 7 skipped (4.5s); zero failures**
- [x] 5.4 Record final consolidated test report to `tmp/ingestion-ui-e2e-mock-strategy/consolidated-run.txt`
- [x] 5.5 Confirm zero new BUGs registered — all 33 BUG files are `status: fixed` (28) or `status: verified` (5); no new open BUGs introduced by this change

## 6. Archive prep

- [x] 6.1 Verify all checklist items above are checked
- [x] 6.2 Confirm `openspec validate ingestion-ui-e2e-mock-strategy --type change --strict` passes (must run from project root, not from `client/`)
- [x] 6.3 Confirm `docs/bugs/index.md` has no new open ingestion-ui BUGs introduced by this change
- [x] 6.4 Ready to invoke `/opsx:archive ingestion-ui-e2e-mock-strategy` — this will merge the `ingestion-ui-e2e-testing` capability spec into `openspec/specs/`

<!-- Notes:
- This is a test-only, fixture-only change. No backend modules edited, so no `mvn install -pl ... -am -DskipTests` step is required.
- Design Inputs (per Frontend Plan Gate in CLAUDE.md): client/DESIGN.md applies because ingestion-*-ui specs touch UI surfaces; this change does NOT modify components, control states, design tokens, layouts, or shells — verified via spec scope (test files only).
- All temporary files (run logs, traces) MUST land under `tmp/ingestion-ui-e2e-mock-strategy/` per the MCP / Skill Temporary Files rule.
- Tasks 4.5 → 4.17 are independent test rewrites within the same file. Per the OpenSpec Apply & Parallel Execution rule, dispatch them in parallel only if they touch distinct, non-overlapping sections; otherwise (more likely given they share `test.describe()` scope) execute sequentially with a single consolidated `tsc --noEmit` + Playwright run at 4.18 → 4.19.
-->
