# ingestion-library-ui Specification

## Purpose

Defines the expected UI behavior of the Ingestion Library tab — the data table listing all ingestion jobs with status, source URL, target table, row count, and creation timestamp. Covers table styling, date formatting, row interaction (selection and tab opening), and design token compliance.

## Requirements

### Requirement: Date format SHALL be yyyy-MM-dd HH:mm:ss

The Created column in the ingestion library table SHALL render job creation timestamps in `yyyy-MM-dd HH:mm:ss` format, independent of browser locale. The format SHALL use 24-hour time.

#### Scenario: Date renders in fixed format regardless of locale

- **GIVEN** a job with `createdAt = 1747068621000` (2026-05-12T18:50:21.000+08:00)
- **WHEN** the ingestion library table renders the Created column
- **THEN** the displayed text SHALL be `2026-05-12 18:50:21`

#### Scenario: Date format survives locale switch

- **GIVEN** the browser locale is set to `de-DE` or `ja-JP`
- **WHEN** the ingestion library table renders any job's Created column
- **THEN** the displayed text SHALL still match the pattern `\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}`

### Requirement: Double-clicking a job row SHALL open or focus the job tab

Double-clicking a job row in the ingestion library table SHALL implement "open or focus" semantics: if a tab with `tabId: ingestion_job_<job.id>` already exists, the action SHALL focus that tab; otherwise it SHALL create a new tab. This SHALL prevent duplicate tabs for the same job.

#### Scenario: First double-click creates a new tab

- **GIVEN** no tab with `tabId: ingestion_job_abc123` exists
- **WHEN** the user double-clicks the row for job `abc123`
- **THEN** a new `StageTab` SHALL be created with `tabId: ingestion_job_abc123`, `type: ingestion_job`, and `title: Job abc12345`

#### Scenario: Second double-click focuses the existing tab

- **GIVEN** a tab with `tabId: ingestion_job_abc123` already exists and is not the active tab
- **WHEN** the user double-clicks the row for job `abc123` again
- **THEN** no new tab SHALL be created, and `activeTabId` SHALL become `ingestion_job_abc123`

#### Scenario: Existing tab refocused even when in background workset

- **GIVEN** a tab with `tabId: ingestion_job_abc123` exists but is not in the current workset (`openTabIds` does not contain it)
- **WHEN** the user double-clicks the row for job `abc123`
- **THEN** the tab SHALL be added back to the workset and focused

### Requirement: Table styling SHALL comply with client/DESIGN.md data-table tokens

The ingestion library table SHALL use the semantic table tokens defined in `client/DESIGN.md`: `table.headerBg` (`bg.subtle`), `table.rowHover` (`interaction.hover`), `table.rowSelected` (`interaction.selected`). Technical columns SHALL use mono font. The header SHALL be sticky.

#### Scenario: Header uses bg.subtle and is sticky

- **GIVEN** the ingestion library table has at least one job row
- **WHEN** the user scrolls the table body
- **THEN** the header row SHALL remain visible (sticky), with `bg-bg-subtle` background

#### Scenario: Technical columns use mono font

- **GIVEN** the ingestion library table renders the Created and Rows columns
- **WHEN** inspecting the column cells
- **THEN** the Created cell text SHALL use `font-mono` or equivalent monospace font family

#### Scenario: Single-click selects a row

- **GIVEN** the ingestion library table has multiple job rows
- **WHEN** the user single-clicks a row
- **THEN** that row SHALL receive `bg-interaction-selected` background, and the previously selected row (if any) SHALL lose the selection highlight

#### Scenario: Selected row visual transition

- **GIVEN** a row is selected
- **WHEN** the selection state changes
- **THEN** the background transition SHALL animate with `transition-colors` (duration ≤ 120ms per `motion.fast`)
