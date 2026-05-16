---
name: query-editor-workflow
description: Use when the user wants to browse table rows, inspect sample data, run a simple table preview or row count, write SQL, open a SQL editor, or execute SQL in the editor. Triggers on SQL 编辑器/查询编辑器/打开 SQL/写 SQL/跑 SQL/查表/看表数据/open query editor/open SQL/write SQL/run query/edit SQL/show rows from. Covers the full query_editor lifecycle (open → set_context → patch /content or apply_text_edits → run_sql → focus), the boundSessionId / source / useSessionContext context model, and the 5 Query Editor Rules.
---

# Query Editor Workflow Skill

## When to use

- "show 10 rows from users"
- "查询 orders 表" / "看一下 customers 表的数据"
- "open SQL for customers"
- "write and run a SELECT"
- "count rows in this table" / "users 表有多少行"
- "打开 SQL 编辑器写一个查询"
- Any generic browse verb ("query", "show", "view", "open", 查/看/打开) combined with a table name and **no** analytical signal (grouping, trend, comparison, chart, report, or keywords like analyze / summarize / compare / trend / explain).
- User explicitly references the SQL editor, current editor, workspace, or query editor result grid.

In this mode, the frontend query editor owns SQL editing, execution, and result rendering. Do **not** use `datatalk_execute_sql` to fetch rows or simple counts for chat rendering, and do **not** use `datatalk_execute_sql` as a pre-check to "verify whether the table has data" before opening the editor — the editor itself shows the empty state. Claiming "the table has no data" in chat under this workflow is a routing violation.

A *simple row count* means a single-table `COUNT(*)` without grouping, trend, comparison, or explanation.

## When NOT to use

- Analytical questions, reports, grouped or cross-table aggregates, trend explanations, metric comparisons, or chart generation — those belong to the server data workflow (`datatalk_read_schema` + `datatalk_execute_sql` + chart/report rendering). Grouped counts, time-bucketed counts, and comparisons are analytical, not simple row counts.
- Switching the session data context — see `[[connection-management]]` (`datatalk_set_data_context`, `datatalk_resolve_use_target`, `use xxx`).
- ER schema viewing / designing — see `[[er-tabs]]`.
- Dashboard / chart creation — see `[[charts-and-dashboards]]`.
- Fetching external data into a new table — see `[[data-collection]]`.

If the user explicitly asks for analysis, reporting, insight, trend explanation, or charting, the server data workflow wins; if the user explicitly asks for the SQL editor / current editor / workspace / query editor result grid, this workflow wins.

## Editor context model

A `query_editor` tab carries its own context, **separate** from the session data context (see `[[connection-management]]`). Changing the editor context does **not** by itself change the session data context.

A query_editor state (returned by `datatalk_ui_read object=query_editor`) includes: `tabId`, `title`, `content`, `version`, `source`, `boundSessionId`, `isMismatched`, `useSessionContext`, `connectionId`, `connectionName`, `database`, `schema`, `contextSource`, `contextOverride`, `results`, `activeResultId`, `limit`, and `inWorkset` (see `[[tab-management]]` for Library vs Workset).

### source

- `source='user'` — the user manually opened the editor. `boundSessionId` is **fixed at creation**; user editors are pinned to their originating session.
- `source='ai'` — you opened it via `workspace/open` with `type=query_editor`. `boundSessionId` is re-bindable.

### boundSessionId

`boundSessionId` is the session this editor tracks for context resolution. `isMismatched=true` means the editor's bound session is no longer the currently active session.

### useSessionContext / set_context semantics

The `set_context` action (on `object=query_editor`) takes `params.useSessionContext`, `params.connectionId`, `params.database`, `params.schema`, and `params.limit`. At least one must be present.

- `set_context({ useSessionContext: true })`:
  - On `source='ai'` editors: re-binds `boundSessionId` to the active session and clears any prior `contextOverride`.
  - On `source='user'` editors: **no-op**; returns `{ success: true, data: { noop: true, reason: 'source=user editor cannot follow session' } }`.
  - `useSessionContext=true` **cannot** be combined with `connectionId`, `database`, or `schema`.
- Linked parameter rules: `database` requires an effective `connectionId`; `schema` requires an effective `connectionId` and `database`; omitted fields keep the current editor context when those effective fields already exist; `limit` may be set independently.
- `set_context.database` — distinguish *omitted* vs *explicit null*:
  - **Omit** (or `undefined`): falls back to the target `Connection`'s configured `databaseName`. Use this when you want the connection's default database.
  - **Explicit `null`**: clears `database`. Use when you specifically want an empty database (rare; only for kinds where database is optional).
  - **String**: pins that exact database.
  - On `connectionId` change without `database`, the fallback re-fires from the *new* connection's default. To preserve the old database across a connection switch, pass `database: "<old value>"` explicitly.
  - Single-call `{ connectionId: 'B', schema: 'public' }` without `database`: the schema guard evaluates the post-fallback database (B's `databaseName`), so the call succeeds if B has a configured `databaseName`.
- SQLite query-editor context is file-scoped: use the SQLite file path in `database`, leave `schema` unset, and do not ask the user to pick a separate schema.

Editor state and actions use camelCase (`connectionId`, etc.).

## Editor lifecycle

The full chain a query_editor goes through:

1. **open** — `datatalk_ui_exec object=workspace action=open params.type=query_editor` (creates a new `source='ai'` tab). Tab reuse vs new tab decisions: see `[[tab-management]]`.
2. **set_context** — `datatalk_ui_exec object=query_editor action=set_context` to align the editor's `connectionId`/`database`/`schema`/`limit`, or to re-bind via `useSessionContext: true` (AI editors only).
3. **patch content** — either:
   - Full rewrite: `datatalk_ui_patch` on `/content`. See `[[ui-contract]]` for the patch envelope and `[[concurrency-contract]]` for version handling.
   - Targeted edits: the `apply_text_edits` action — see `[[ui-contract]]`.
4. **run_sql** — `datatalk_ui_exec object=query_editor action=run_sql` to execute the editor's current SQL. On execution errors see `[[sql-error-diagnostics]]`.
5. **focus** — when continuing on an existing tab via `apply_text_edits`, `set_context`, or `run_sql` only, finish the chain with `datatalk_ui_exec object=workspace action=focus params.target=<tabId>` so the stage panel becomes visible if the user had it closed. New-tab verbs (`open`) already reveal the panel on their own.

Query editor actions are: `apply_text_edits`, `set_context`, `run_sql`, `format_sql`, and `focus`.

After patching `/content` or running `apply_text_edits`, before claiming success or chaining `run_sql`, re-call `datatalk_ui_read object=query_editor mode=state` on the same `target` and verify the returned `content` matches your intent and `version` advanced. A successful patch response is necessary but not sufficient.

If large generated SQL or result snapshots need to be persisted, see `[[artifacts-output]]`.

## Editor rules

1. Read `query_editor` state before versioned text edits to obtain fresh `content` and `version`.
2. Do not replace unrelated SQL. Preserve the user's existing work unless they explicitly ask for a full rewrite.
3. Full rewrite via `datatalk_ui_patch` on `/content`; targeted edits via `apply_text_edits` — see `[[ui-contract]]` for envelope details and `[[concurrency-contract]]` for the version/expected-text fields.
4. On `expected_text_mismatch` or `version_conflict`, follow the recovery steps in `[[concurrency-contract]]`. Use the error's `currentState.version` as the new baseline when present; otherwise re-read with `datatalk_ui_read(mode='state')`.
5. Use `set_context` to change **one editor tab's** context; use `datatalk_set_data_context` from `[[connection-management]]` to change the **session** data context.
6. Use `run_sql` to execute the editor's current SQL; use `format_sql` only when the user asks to format.
7. When `datatalk_ui_find` returns more than one `query_editor` matching the user's intent, do not silently pick one. Disambiguate by `title`, `connectionId`, `database`, `schema`, or recency, and if still uncertain ask the user which tab to act on.
8. If no `query_editor` exists while the user is only asking whether one exists or wants the current/open editor inspected, say so clearly instead of pretending one is open. If the task itself requires a query editor, open one through the workspace open workflow.

## Recommended workflow

### Inspect the current SQL editor

1. `datatalk_ui_find` with `filter.type=query_editor`.
2. If one or more `query_editor` objects exist, identify the right tab id.
3. If needed, `datatalk_ui_read` with `object=workspace` to inspect `activeTabId`.
4. `datatalk_ui_read` with `object=query_editor`, the chosen target, and `mode=full`.
5. If no `query_editor` exists, tell the user there is no open SQL editor.

Notes: start with `datatalk_ui_find` and `filter.type=query_editor` when the user asks about the current or open SQL editor. If multiple `query_editor` tabs exist, prefer an exact tab id from `datatalk_ui_find`. If the active workspace tab is not a `query_editor`, `target=active` with `object=query_editor` will fail.

### Browse table rows or simple counts in query editor

1. Apply Tab Reuse vs New Tab from `[[tab-management]]` to pick or open a target editor.
2. If table or column names are unclear, follow schema reading rules (see `[[sql-execution]]`).
3. Read the target editor state, then `datatalk_ui_patch` on `/content` with the fresh version — see `[[concurrency-contract]]`.
4. Execute with `datatalk_ui_exec object=query_editor action=run_sql`.
5. On candidate-ambiguity errors, follow `[[sql-error-diagnostics]]` — do not claim there is no data.

### Open or reuse a SQL workspace

Apply Tab Reuse vs New Tab from `[[tab-management]]`.

- **New task**: `datatalk_ui_find filter.type=query_editor` to check for an empty or already-matching tab; if none, call `datatalk_ui_exec object=workspace action=open params.type=query_editor`.
- **Continuation**: follow the continuation steps in `[[tab-management]]` (reuse the most-recently-touched matching tab; opening a new tab in a continuation is a routing violation, not a safe fallback).

### Edit SQL in a query editor

1. `datatalk_ui_read object=query_editor mode=state` to fetch current `content` and `version`.
2. Full rewrite: `datatalk_ui_patch` on `/content` with the current version — see `[[ui-contract]]` and `[[concurrency-contract]]`.
3. Targeted edit: `datatalk_ui_exec object=query_editor action=apply_text_edits` — see `[[ui-contract]]`.
4. On conflict: follow the recovery steps in `[[concurrency-contract]]`.
5. If execution context must change, use `datatalk_ui_exec object=query_editor action=set_context`.

## Known Limits

- **User-source editors are pinned to their origin session.** Calling `set_context` with `useSessionContext: true` on a user-opened editor returns a graceful noop: `{success: true, data: {noop: true, reason: "user_editor_pinned_to_origin", detail: "..."}}`. This is by design — AI cannot make a user-bound editor follow the active session. Open a new AI-source editor instead, or ask the user to switch context manually.
- **`reason: "user_editor_pinned_to_origin"` is the structured signal.** When you see this reason, treat the call as successfully ignored (idempotent noop), not failed. The `detail` field carries the human-readable explanation for surfacing to the user.
- **You can still override connection/database/schema on user editors.** The pin only blocks "follow active session"; explicit `connectionId` / `database` / `schema` parameters in the same `set_context` call still apply.
