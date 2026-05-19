## MODIFIED Requirements

### Requirement: Report viewer iframe reloads on remount
The iframe in `ReportViewerTab` SHALL reload the full HTML content from the server when the component mounts, using a cache-busting timestamp that is stable across re-renders within the same mount.

#### Scenario: Tab closed and reopened
- **WHEN** a report viewer tab is closed (trashed) and the same report is reopened
- **THEN** the iframe SHALL issue a fresh HTTP request to the report HTML endpoint (not serve from cache)

#### Scenario: React re-render within the same mount
- **WHEN** `useReport` or `useSystemStatus` triggers a re-render of `ReportViewerTab` while the reportId remains unchanged
- **THEN** the iframe src URL SHALL remain identical and the iframe SHALL NOT reload

## ADDED Requirements

### Requirement: Report HTML scrollbar styling
The HTML generated for report viewing SHALL include scrollbar styles that hide native scrollbar buttons (up/down arrows) and render a rounded thumb consistent with the main application's scrollbar style.

#### Scenario: Report HTML renders in a scrollable container
- **WHEN** a report HTML page is rendered in a browser or iframe with scrollable content
- **THEN** the scrollbar SHALL display a rounded thumb without native arrow buttons

#### Scenario: Scrollbar thumb hover
- **WHEN** the user hovers over the scrollbar thumb
- **THEN** the thumb color SHALL darken to provide hover feedback

### Requirement: Report viewer tab payload hydration on mount
`ReportViewerTab` SHALL receive the full `StageTab` object and call `coordinator.ensureHydrated(tab.tabId)` when the payload is missing `reportId`, displaying a loading state until hydration completes.

#### Scenario: Page refresh with an open report viewer tab
- **WHEN** the user performs a browser refresh (Ctrl+R) while a report viewer tab is open and active
- **THEN** after hydration completes, the report viewer tab SHALL display the report content (not a blank page)

#### Scenario: Report viewer tab opened from scratch
- **WHEN** a report viewer tab is opened via `openTab` with an initial `payload: { reportId }`
- **THEN** the component SHALL render the iframe directly without showing the loading state, as `reportId` is already available

### Requirement: Stage tab content wrapper passes full tab to report viewer
The `stage-tab-content.tsx` dispatcher SHALL pass the full `StageTab` object to `ReportViewerTab` without gating on payload content, matching the pattern used by `dashboard-tab.tsx`.

#### Scenario: Hydrated tab with empty payload reaches the wrapper
- **WHEN** `stage-tab-content.tsx` receives a `report_viewer` tab whose payload is `{}` (post-refresh hydration state)
- **THEN** the wrapper SHALL render `<ReportViewerTab tab={tab} />` rather than returning `null`
