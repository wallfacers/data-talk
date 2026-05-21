---
id: BUG-0016
title: Credentials section missing from settings dropdown — unreachable via UI
status: verified
priority: P1
source: e2e-playwright
modules: [settings]
discovered: 2026-05-12
discoveredBy: agent
testRunId: null
fixCommit: 434d1ae9
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary
The Credentials section (`settings.credentials`) exists in the settings dialog sidebar (`settings-nav.tsx`) and has a fully implemented page (`credentials-page.tsx`), but is missing from the user nav dropdown (`nav-user.tsx`) that opens the settings dialog. Users cannot navigate to the Credentials page from the UI.

## Reproduction Steps
1. Open DataTalk app at `/`
2. Click the user avatar in the bottom-left sidebar to open the settings dropdown
3. Observe: Server group shows Data Sources, Providers, Models, Maintenance — no Credentials entry
4. No URL route exists for `/settings/credentials` either

## Expected vs Actual
- **Expected**: Credentials appears in the Server group of the settings dropdown alongside Data Sources, Providers, Models, and Maintenance
- **Actual**: Credentials is absent from the dropdown; the settings page exists but is unreachable

## Environment
- Backend commit: fbf28a52
- Frontend commit: fbf28a52
- OS / Browser: Linux / Chromium (Playwright)
- Data source: N/A

## Evidence
- Screenshot from test run: `tmp/playwright/test-results/ingestion-credentials-ui---56ebf-visible-with-no-credentials-chromium/test-failed-1.png`
- The screenshot shows the DataTalk home page — the settings dropdown (user avatar menu) has no Credentials entry

## Root Cause
`client/src/features/workspace/components/nav-user.tsx` — the `groups` array in the Server group (lines 43-51) includes data-sources, providers, models, and maintenance but omits `credentials`. The `settings-nav.tsx` sidebar correctly lists credentials, but the entry point is missing.

## Fix
Add `{ key: 'credentials', label: t('settings.credentials'), icon: KeyRoundIcon, section: 'credentials' as const }` to the Server group items in `nav-user.tsx`. Import `KeyRound` from `lucide-react`.

## Verification
Re-run `npx playwright test ingestion-credentials-ui.spec.ts` — all 7 tests should pass once the navigation works.

## Notes
This was discovered during T2.1 of the Ingestion E2E Playwright plan. The credentials UI is fully implemented (page, form, list, API hooks) — only the navigation entry point was missing.
