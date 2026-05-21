---
name: ui-contract
description: Use when invoking the four UI tools — `datatalk_ui_find` / `datatalk_ui_read` / `datatalk_ui_patch` / `datatalk_ui_exec` — to discover, inspect, edit, or execute actions on stage tabs (workspace / query_editor / er_inspector / er_designer). Triggers on `ui_find` / `ui_read` / `ui_patch` / `ui_exec` / `apply_text_edits` / `set_context` / `操作 UI` / `编辑 tab` / `查找 tab` / `读取 tab 状态` / `修改 SQL 编辑器`. Defines parameter shapes, required fields per action, top-level `object`/`target`/`mode`/`action`/`params` semantics, and the post-edit re-read verification rule.
---

# UI Contract Skill

## When to use

- "Find every query_editor in this session and read the first 50 lines"
- "Read the state of the active ER designer tab"
- "Patch the SQL editor's `/content` to a new draft"
- "Apply a targeted text edit at lines 12-14 of the active editor"
- "Rebind the active editor to follow the current session"
- "Open three query_editor tabs in one batch call"
- "在工作区里把这几个 tab 归档"
- "重命名当前 tab 标题为 'sales-2026Q1'"

## When NOT to use

- Running SQL or reading rows → [[sql-execution]]
- Opening a brand-new connection / switching `use xxx` → [[connection-management]]
- Two-phase confirmable mutation (`confirm=true` + `confirmationToken`) → [[connection-management]]
- Version-conflict error recovery (`version_conflict` / `expected_text_mismatch` / `expectedVersion` retry loop) → [[concurrency-contract]]
- ER tab structural payload, patch path whitelist, exec verbs → [[er-tabs]]
- Tab reuse heuristics (when to reuse vs open new), library vs workset views → [[tab-management]]
- Continuation vs new-task query editor workflow rules → [[query-editor-workflow]]

## Tool surface

| MCP Tool | Purpose | Top-level keys |
|----------|---------|----------------|
| `datatalk_ui_find` | Discover, search, optionally pre-read tabs across all sessions | `filter`, `query`, `read`, `output` |
| `datatalk_ui_read` | Read tab `state` / `schema` / `actions` / `full` | `object`, `target?`, `mode?` |
| `datatalk_ui_patch` | JSON-Patch a `query_editor` / `er_inspector` / `er_designer` | `object`, `target?`, `ops`, `baseVersion?` |
| `datatalk_ui_exec` | Execute a named `action` on a UI object | `object`, `target?`, `action`, `params` |

Supported `object` types (the only ones today): `workspace`, `query_editor`, `er_inspector`, `er_designer`.

When `target` is omitted on `ui_read` / `ui_patch` / `ui_exec`, the client defaults to `active`.

## datatalk_ui_find

Four optional top-level sections — `filter`, `query`, `read`, `output`:

- **`filter`**: `type`, `connectionId`, `objectId`, `originSessionId`, `lastTouchedAfter`, `lastTouchedBefore`, `includeArchived`, `pinned`.
- **`query`**: `{ mode, pattern, caseInsensitive, multiline }` — `mode` is `fts`, `substring`, or `regex`.
- **`read`**: `{ tabIds, range, contextLines }` — `range` is `"full"` or `{ lineStart, lineEnd }`.
- **`output`**: `{ mode, headLimit, maxTabs }` — `mode` is `metadata` (default), `matches`, `tabs_only`, or `count`.

Return shape by `output.mode`:

| `mode` | Return |
|--------|--------|
| `metadata` (default) | `{ items, totalMatched, truncated }` |
| `matches` | `{ items: [{ tab, matches, matchScore? }], totalMatched, truncated }` |
| `tabs_only` | `{ tabIds, totalMatched, truncated }` |
| `count` | `{ totalMatched, tabsMatched }` |

The `truncated` field above is `ui_find`'s result-set-budget flag (`output.headLimit` / `output.maxTabs` exceeded). For `datatalk_read_schema`'s `truncated=true` policy (narrow `pattern`/`limit`/business keyword), see [[sql-execution]].

If `read` is provided, the response also adds a top-level `reads` field with the read slices.

## datatalk_ui_read

Top-level fields: `object`, optional `target`, optional `mode`.

- `mode` is one of `state`, `schema`, `actions`, or `full`.
- `mode=full` returns `state`, `schema`, and `actions`. For `query_editor`, `full` also includes `capabilities`.
- `mode=actions` returns `{ "items": [...] }` where each item has `name`, `description`, and `paramsSchema`.

Object-specific state highlights:

- `workspace.state` includes `open`, `maximized`, `tabs`, and `activeTabId`. `open` = is the stage panel visible; `maximized` = expanded to full height. Each query-editor tab entry in `tabs` exposes `tabId`, `type`, `title`, `connectionId`, `connectionName`, `database`, `schema`, `source`, `boundSessionId`, `isMismatched`, `useSessionContext`, `contextSource`, `contextOverride`, and `limit`.
- `query_editor.state` includes `tabId`, `title`, `content`, `version`, `source`, `boundSessionId`, `isMismatched`, `useSessionContext`, `connectionId`, `connectionName`, `database`, `schema`, `contextSource`, `contextOverride`, `results`, `activeResultId`, `limit`, and `inWorkset`. `inWorkset` tells whether a persisted tab is currently open in the top tab bar.
- `er_designer.state` includes the current version field needed for structural patches.
- `query_editor.source` is `'user'` (user manually opened) or `'ai'` (you opened it via `workspace/open` with `type=query_editor`). `boundSessionId` is the session this editor tracks for context resolution. `isMismatched=true` means the editor's bound session is no longer the active one.

## datatalk_ui_patch

Top-level fields: `object` (`query_editor`, `er_inspector`, or `er_designer`), optional `target`, `ops`, and optional top-level `baseVersion`.

- Each op is shaped `{ op, path, value }`. `op=add` and `op=replace` require `value`; `op=remove` omits `value`.
- **Query editor patch paths**: `op=replace` on `/content`, `/connectionId`, `/database`, `/schema`.
  - `/content` requires top-level `baseVersion: number` from the latest `datatalk_ui_read object=query_editor mode=state`. `baseVersion: "auto"` is **not** valid for query-editor content.
- **ER designer structural paths** (`/tables`, `/relations`, `/dialect`, `/targetConnectionId`, `/targetDatabase`, `/targetSchema`) require top-level `baseVersion: number` from the latest `datatalk_ui_read object=er_designer mode=state`. View paths (`/positions`, `/collapsed`, `/viewport`) may omit it.
- **ER tab patch path whitelist** is defined by the ER tab protocol — see [[er-tabs]].
- `baseVersion` is required for any version-guarded path. Conflict handling (HTTP 409 retry, error code semantics, `expectedVersion`) → [[concurrency-contract]].

## datatalk_ui_exec

Top-level fields: `object`, `action`, `params` (and optional `target`).

Query editor actions: `apply_text_edits`, `set_context`, `run_sql`, `format_sql`, `focus`. Query editor actions and state use camelCase (e.g. `connectionId`, `baseVersion`). The workspace uses snake_case (e.g. `params.connection_id`).

### Required `params` by action

| `object` / `action` | Required `params` |
|---------------------|-------------------|
| `workspace/open` | `type`, `title` (or batch via `tabs` array; each element requires `title`) |
| `workspace/focus`, `workspace/detach` | `target` |
| `workspace/archive`, `workspace/trash` | `target` (or batch via `targets` array) |
| `workspace/rename` | `target`, `title` |
| `workspace/pin` | `target` (optional `pinned`, defaults to `true`) |
| `workspace/choose_connection` | (optional `preferredConnectionId`) |
| `workspace/open_er_inspector` | `connectionId`, `tables`, `title` |
| `workspace/open_er_designer` | `dialect`, `title` |
| `query_editor/apply_text_edits` | `edits[]`; each edit requires `oldText`, `newText` (optional `hint.line`; optional advisory `baseVersion`) |
| `query_editor/set_context` | at least one of `useSessionContext`, `connectionId`, `database`, `schema`, `limit` |
| `er_inspector/add_neighbors` | `table` |
| `er_designer/bind_target` | `connectionId` |

### Workspace verbs (snake_case payload)

- `open` (`params.type=query_editor`): opens a tab. Required `title`. Optional `connection_id`, `database`, `schema`, `payload`. `params.payload` belongs to the query-editor open request and may include SQL text via `initialSql`, `content`, or legacy `sql` (precedence: `initialSql` > `content` > `sql`), plus `autoRun`, `connectionId`, `connectionName`, `database`, `schema` for initial execution/context metadata. **Batch**: pass `params.tabs` (array of open specs) — each element requires `title`; returns `{ tabIds }`.
- `choose_connection`: prompts the connection chooser. Optional `preferredConnectionId`.
- `focus(target)`: ensures the tab is in the workset and active, and reveals the stage panel if the user had it hidden. Archived tabs return `tab_archived`.
- `detach(target)`: removes from workset, keeps in library.
- `archive(target, archived?=true)`: hides the tab; pass `archived=false` to unarchive. **Batch**: `params.targets` (array of tab IDs); returns `{ succeeded }`.
- `trash(target)`: permanent delete; only when the user explicitly asks. **Batch**: `params.targets`; returns `{ succeeded, failed }` (per-item errors).
- `rename(target, title)`: returns `{ success: true }`.
- `pin(target, pinned?=true)`: pin/unpin; returns `{ success: true }`.
- `open_er_inspector` / `open_er_designer`: open the corresponding ER tab — for ER tab payload shape and structural ops see [[er-tabs]].

### Query editor verbs

- `apply_text_edits` — first-class targeted SQL edits via **anchored search/replace**. Provide an `edits[]` array; **every** edit entry carries `oldText` (the snippet to find in the current content) and `newText` (its replacement). **No line/column coordinates.** The server locates `oldText` in the live content and replaces that span — you never compute or count line numbers.
  - **Make `oldText` uniquely identify one location.** Copy it from the `content` returned by `datatalk_ui_read mode=state`, including enough surrounding context that it appears exactly once. Indentation/whitespace may differ from the live text (leading indent and internal spacing are matched flexibly), but **non-whitespace tokens must match exactly**.
  - If `oldText` matches **more than one** place, the edit fails with `anchor_ambiguous` — add more surrounding context, or pass `hint.line` (1-based line of the intended match) to disambiguate. If it matches **nothing**, it fails with `anchor_not_found` — re-read and base `oldText` on the live content. See [[concurrency-contract]].
  - **`baseVersion` is optional and advisory.** If you pass it and the tab has since advanced, the edit still applies as long as every `oldText` still uniquely matches (the result reports `rebased: true`). You do **not** need to re-read just because the version changed.
- `set_context` — patch one or more of `useSessionContext`, `connectionId`, `database`, `schema`, `limit`.
  - `set_context({ useSessionContext: true })` re-binds an editor to follow the currently active session. On `source='ai'` editors this rebinds `boundSessionId` and clears any prior `contextOverride`. On `source='user'` editors this is a no-op and returns `{ success: true, data: { noop: true, reason: 'source=user editor cannot follow session' } }` — user editors are pinned to their originating session.
  - `useSessionContext=true` cannot be combined with `connectionId`, `database`, or `schema`.
  - Linked parameter rules: `database` requires an effective `connectionId`; `schema` requires an effective `connectionId` and `database`; omitted fields keep the current editor context when those effective fields already exist; `limit` may be set independently.
  - **`set_context.database` — omitted vs explicit null**:
    - **Omit** `database` (or send `undefined`): falls back to the target `Connection`'s configured `databaseName`. Use this for the connection's default database.
    - **Explicit `null`**: clears `database`. Rare; only for kinds where database is optional.
    - **String**: pins that exact database.
    - On `connectionId` change without `database`, the fallback re-fires from the *new* connection's default. To preserve the old value across a connection switch, pass `database: "<old value>"` explicitly.
    - Schema-with-connection-change in one call: `{ connectionId: 'B', schema: 'public' }` with no `database` — the schema guard evaluates the post-fallback database (B's `databaseName`), so the call succeeds as long as B has a configured `databaseName`.
  - SQLite query-editor context is file-scoped: use the SQLite file path in `database`, leave `schema` unset, and do not ask the user to pick a separate schema.
- `run_sql` — execute the editor's current content against its bound context.
- `format_sql` — format the editor content in place.
- `focus` — focus the editor in the stage panel.

Full SQL replacement uses `datatalk_ui_patch` on `/content` with `baseVersion`. Context replacement (`/connectionId`, `/database`, `/schema`) also goes through `ui_patch`. Targeted edits go through `ui_exec` `apply_text_edits`. Use [[query-editor-workflow]] to decide which path applies to your case (continuation of an existing task vs starting a new one) and [[tab-management]] for reuse vs new-tab decisions.

## Post-edit verification

After a successful `ui_patch` on `/content`, the agent **MUST** re-read the editor with `datatalk_ui_read object=query_editor mode=state` before the next `/content` patch, to pick up the new `version` required as `baseVersion`. After `apply_text_edits` a re-read is **recommended but not required for versioning** (anchors locate edits regardless of version, and `baseVersion` is advisory). Re-read in these cases:

1. **Confirm the resulting text** — compare the returned `content` (echoed in the success result) against the intent. If it diverges, do not chain another edit; surface the divergence to the user.
2. **Refresh `boundSessionId` / `contextSource` / `isMismatched`** — context may have shifted (e.g. session switch) between edits.

The same `/content` versioning rule applies after structural `ui_patch` ops on `er_designer` — re-read with `object=er_designer mode=state` to refresh its `version` before the next structural patch.

## Object types

| `object` | What it represents | Where to look for details |
|----------|--------------------|---------------------------|
| `workspace` | The stage shell — tabs list, active tab, stage open/maximized flags, open/focus/archive/trash/rename/pin verbs | This skill (workspace verbs section above) |
| `query_editor` | A SQL editor tab with content, version, bound session, results, context | This skill + [[query-editor-workflow]] for workflow rules; [[tab-management]] for reuse |
| `er_inspector` | Read-only ER Diagram Viewer of a real schema with annotation overlay | [[er-tabs]] |
| `er_designer` | Independent ER schema draft that can generate DDL for a target connection | [[er-tabs]] |
