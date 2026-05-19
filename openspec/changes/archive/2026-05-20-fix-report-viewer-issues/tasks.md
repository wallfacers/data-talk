## 1. Backend: ledger.css scrollbar styles

- [x] 1.1 Add `::-webkit-scrollbar` rules to `server/data-talk-adapter/src/main/resources/skills/ledger/assets/styles/ledger.css`: `::-webkit-scrollbar { width: 10px; height: 10px }`, `::-webkit-scrollbar-track { background: transparent }`, `::-webkit-scrollbar-thumb { background: var(--ledger-text-faint); background-clip: padding-box; border: 3px solid transparent; border-radius: 999px }`, `::-webkit-scrollbar-thumb:hover { background: var(--ledger-text-muted); background-clip: padding-box }`, `::-webkit-scrollbar-button { display: none }`, `::-webkit-scrollbar-corner { background: transparent }`

## 2. Frontend: remove report_viewer payload gate in stage-tab-content

- [x] 2.1 Replace `stage-tab-content.tsx:171-180` report_viewer branch: remove `const payload = ...` and `if (!payload.reportId) return null`; instead pass `tab={tab}` to `<ReportViewerTab>`, matching the `dashboard` branch pattern at lines 130-137

## 3. Frontend: refactor ReportViewerTab to receive tab and self-hydrate

- [x] 3.1 Change `ReportViewerTab` signature from `({ reportId }: { reportId: string })` to `({ tab }: { tab: StageTab })`, import `StageTab` from `@/stores/stage-store` and `coordinator` from `@/features/stage/persistence/stage-persistence-bootstrap` and `TabContentLoader` from `./tab-content-loader`
- [x] 3.2 Extract `reportId` from tab payload, wrap iframe src URL in `useMemo(() => \`${reportDownloadUrl(reportId, 'html')}?_t=${Date.now()}\`, [reportId])`
- [x] 3.3 On mount: if `reportId` is falsy, call `void coordinator.ensureHydrated(tab.tabId)` and render `<TabContentLoader />`; once payload arrives via `__hydratePayload` the component re-renders with a valid `reportId`
- [x] 3.4 When `reportId` is present, render the existing header + iframe layout as before

## 4. Verification

- [x] 4.1 Run `cd client && npx tsc --noEmit` — confirm zero type errors
- [x] 4.2 Run `cd client && npx vitest run` — 0 regressions in report, report-viewer, stage-tab-content tests; 5 pre-existing failures in unrelated files
- [x] 4.3 E2E smoke with Playwright: open a report → verify in Network panel that the report HTML is fetched exactly once → press Ctrl+R → verify the report content recovers within 5s and is not a blank page
- [x] 4.4 Visual verification: open a report in Tauri → confirm scrollbar has no arrow buttons and thumb is styled consistently with the main app
