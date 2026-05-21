# SQL Editor Selection Run And Result Scroll Design

## Status

Shipped on 2026-04-25.

## Problem

The SQL editor currently runs the full editor buffer from toolbar and `Ctrl/Cmd+Enter`. Users need to run exactly the selected SQL text when a selection exists, while preserving full-buffer execution when there is no selection.

The multi-result panel currently reuses table scroll position across result tabs. When a user switches between result sets, vertical and horizontal scroll offsets should belong to each result set independently.

## Design Inputs

- `client/DESIGN.md` defines the SQL workbench as a Stage / instrument-lane surface. This change keeps the existing dense workbench layout and does not add a new visual system.
- Tables must use stable headers, explicit interactive state, and predictable scrolling. This change keeps the existing result table shell and only adds state isolation for scroll offsets.
- `accent.primary` remains reserved for focus/selection/current object. This change does not introduce new color semantics.
- Keyboard access remains part of the editor contract. `Ctrl/Cmd+Enter` continues to run SQL, with selection-aware input.

## User-Facing Behavior

- If Monaco has a non-empty selection, Run executes exactly the selected text.
- If Monaco has no selection or only whitespace is selected, Run executes the full editor text.
- Toolbar Run and `Ctrl/Cmd+Enter` use the same selection-aware execution behavior.
- Limit injection, result handling, and history entries apply to the actual SQL submitted for execution.
- Each result tab stores its own `{ scrollTop, scrollLeft }`.
- Switching from result A to result B restores B's previous scroll offsets; switching back restores A's offsets.
- Newly returned results start at `{ scrollTop: 0, scrollLeft: 0 }`.

## Architecture

Selection state lives in the existing `sql-workbench-store.selection` field. `SqlMonacoEditor` reports Monaco selection changes using the existing line/column selection shape, and `SqlWorkbenchTab` derives the currently selected text from `tabState.sqlText`.

Execution remains centralized in `runQueryEditorSql`. The function accepts an optional SQL override and applies limit injection to that effective SQL, so UI and programmatic execution continue to share the same request, history, risk, and error paths.

Scroll state is owned by `SqlWorkbenchTab`, keyed by active `resultId`. `SqlResultPanel` passes the active result's saved position to `SqlResultTable`, and `SqlResultTable` restores the DOM scroll container after result changes while reporting user scroll updates back to the tab.

## Non-Goals

- No backend API changes.
- No new result virtualization or pagination behavior.
- No new toolbar controls or visible instructional copy.
- No change to current-result highlighting, result tabs, or table styling.

## Testing

- Add a regression test that selects an exact SQL slice in Monaco and verifies the executed SQL equals that slice.
- Add a regression test that switches between two result-set tabs and verifies `scrollTop` and `scrollLeft` are restored independently.
- Run focused vitest for modified SQL workbench/result tests.
- Run `cd client && npx tsc --noEmit`.
