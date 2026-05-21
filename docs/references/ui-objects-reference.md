# UI Object Protocol — Registered Objects Reference

> Maintenance note: when a `UIObject` adapter under `client/src/features/stage/adapters/` is added or changed, update this file and `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` in the same change. The runtime agent prompt is sourced from that `AGENTS.md`.
>
> Scope: all UI object types registered to `UIRouter`, including their state shape, patch capabilities, and exec actions.

---

## Protocol Summary

Each UI object is addressed by `type + objectId` and exposes three verbs:

| Verb | Backend Action | Meaning |
|------|----------------|---------|
| `read(mode)` | `datatalk.ui.read` | Read state, schema, actions, or `full` |
| `patch(ops)` | `datatalk.ui.patch` | Apply JSON Patch updates |
| `exec(action, params)` | `datatalk.ui.exec` | Execute a named action |

Discovery entrypoint: `datatalk.ui.find`.

---

## Registered Objects

### 1. `workspace`

**Source file**: `client/src/features/stage/adapters/WorkspaceAdapter.ts`
**objectId**: fixed as `"workspace"`
**Description**: the workspace-wide stage container for all tabs.

#### `read` output

| mode | Returns |
|------|---------|
| `state` | `{ tabs: Array<{tabId, type, title, connectionId, connectionName?, database?, schema?, source?, boundSessionId?, isMismatched?, useSessionContext?, contextSource?, contextOverride?, limit?}>, activeTabId: string \| null }` |
| `schema` | `{ type: 'object', properties: { tabs: array, activeTabId: string\|null } }` |
| `actions` | exec actions listed below |
| `full` | merged `{ state, schema, actions }` |

For `query_editor` rows:
- `connectionId / connectionName / database / schema` reflect the currently effective context
- `source` is `'user'` or `'ai'` and records how this editor was opened
- `boundSessionId` is the session this editor tracks for context binding (always set after hydrate; AI editors re-bind on manual toggle, user editors are fixed at creation time)
- `isMismatched` is `true` when `boundSessionId !== activeSessionId` (AI editor whose bound session is no longer active); UI shows a "来自会话: <name>" badge in this case
- `useSessionContext` is a **derived** UI flag (`boundSessionId === activeSessionId && contextOverride == null`); it is not the persisted source of truth and should not be written back to storage
- `limit` mirrors the query editor state summary
- `contextSource` is one of `session`, `override`, or `tab`
- `contextOverride` remains the explicit override metadata

#### `patch`

Not supported. `workspace` is read-only through `patch`; use `exec`.

#### Exec Actions

| action | Required params | Optional params | Effect |
|--------|-----------------|-----------------|--------|
| `open` | `type: string` | `title`, `connection_id`, `database`, `schema`, `payload` | Open a new tab; for `query_editor`, prefill SQL with `payload.initialSql`, `payload.content`, or legacy `payload.sql` |
| `focus` | `target: tabId` | — | Ensure the tab is in the workset and make it active; archived targets return `tab_archived` |
| `detach` | `target: tabId` | — | Remove the tab from the top-tab workset only; it remains in the library |
| `archive` | `target: tabId` | `archived?: boolean = true` | Archive when `true`; unarchive when `false` |
| `trash` | `target: tabId` | — | Permanently delete the tab |
| `choose_connection` | — | `preferredConnectionId: string` | Open the connection chooser |
| `open_er_inspector` | `connectionId`, `tables: string[]` | `neighborDepth`, `database`, `schema`, `title` | Seed a read-only `er_inspector` tab from JDBC metadata |
| `open_er_designer` | `dialect: mysql \| postgresql \| h2 \| sqlite` | `title`, `targetConnectionId`, `targetDatabase`, `targetSchema`, `seedTables`, `seedRelations` | Open an ER designer draft; generated DDL lands in a query editor and is not executed automatically |

---

### 2. `query_editor`

**Source file**: `client/src/features/stage/adapters/QueryEditorAdapter.ts`
**objectId**: `tabId`
**Description**: the unified SQL workbench tab for manual SQL, resource-tree SQL, AI-prefilled SQL, and direct SQL entry.

#### `read` output

| mode | Returns |
|------|---------|
| `state` | `{ tabId, title, content, language: 'sql', version, dirty, cursor, selection, source, boundSessionId, isMismatched, useSessionContext, connectionId, connectionName, database, schema, contextSource, contextOverride, entryMode, autoRun, executeStatus, results, activeResultId, limit, inWorkset }` |
| `schema` | `{ type: 'object', properties: { tabId, title, content, language, version, dirty, cursor, selection, source, boundSessionId, isMismatched, useSessionContext, connectionId, connectionName, database, schema, contextSource, contextOverride, entryMode, autoRun, executeStatus, results, activeResultId, limit, inWorkset } }` |
| `actions` | exec actions listed below |
| `full` | merged `{ state, schema, actions, capabilities }` |

#### `capabilities`

```ts
{
  editableContent: true,
  acceptsTextEdits: true,
  runnable: true,
  formattable: true,
  supportsContextBinding: true,
  supportsResults: true,
}
```

#### `state` field notes

- `content`: current SQL text
- `version`: SQL content version, used with `baseVersion`
- `dirty`: whether unsaved document edits exist
- `cursor` / `selection`: editor caret and selection state
- `source`: `'user'` (manually opened) or `'ai'` (opened from chat / AI workspace.open); determines toolbar UI (user editors hide the session toggle)
- `boundSessionId`: session this editor tracks for execution context resolution; AI editors re-bind on manual toggle ON, user editors are fixed at creation time
- `isMismatched`: `true` when `boundSessionId !== activeSessionId`; UI surfaces a `来自会话: <name>` badge so the user sees why the connection doesn't follow the active session
- `useSessionContext`: **derived** UI flag (`boundSessionId === activeSessionId && contextOverride == null`); not a persisted source of truth, never written back to storage
- `connectionId / connectionName / database / schema`: currently effective execution context
- `contextSource`: where the effective context comes from; one of `session`, `override`, or `tab`
- `contextOverride`: explicit override metadata, separate from the effective context fields
- `entryMode / autoRun`: open source metadata and auto-run behavior
- `executeStatus / results / activeResultId / limit`: runtime execution state
- `availableDatabases / availableSchemas`: known static/current values from cached connection metadata and the current editor context, not an exhaustive live target list
- `results`: summary only, without row payloads; each item exposes `{ resultId, statementIndex, columns, rowCount, durationMs, truncated, error? }`
- `inWorkset`: whether this tab is currently open in the top-tab workset for this app instance

#### `patch`

Supported whitelist paths:

| path | ops | Required fields | Effect |
|------|-----|-----------------|--------|
| `/content` | `replace` | top-level `baseVersion: number` | Replace the full SQL text; `baseVersion: "auto"` is not valid for query editor content, and a stale base version returns `version_conflict` |
| `/connectionId` | `replace` | — | Change the connection |
| `/database` | `replace` | — | Change the database |
| `/schema` | `replace` | — | Change the schema |

#### Exec Actions

| action | Params | Effect |
|--------|--------|--------|
| `apply_text_edits` | `{ baseVersion, edits: [{ range, expectedText, text }] }` | Apply precise SQL edits by range; every edit must include `expectedText`, and any mismatch rolls back the whole batch |
| `set_context` | `{ useSessionContext?, connectionId?, database?, schema?, limit? }` | Update context mode, execution context, or result limit in one call; provide at least one field |
| `run_sql` | `{ limit? }` | Execute the current SQL and write results back into query editor runtime state |
| `format_sql` | — | Format the current SQL and update `content/version` |
| `focus` | — | Focus this tab |

`set_context` linked parameter rules:
- `useSessionContext=true` cannot be combined with `connectionId`, `database`, or `schema`.
- `useSessionContext=true` on a `source='user'` editor is a no-op (returns `{ success: true, data: { noop: true, reason: 'source=user editor cannot follow session' } }`); user editors are pinned to their originating session and cannot be re-bound by AI.
- `useSessionContext=true` on a `source='ai'` editor re-binds the editor to the currently active session and clears any prior `contextOverride`.
- `database` requires an effective `connectionId`, either from the current editor context or an explicit `connectionId`.
- `schema` requires an effective `connectionId` and `database`, either from the current editor context or explicit params. The effective `database` is evaluated **after** the connection-default fallback (see next bullet), so a single call that switches `connectionId` and sets `schema` will succeed if the new connection has a configured `databaseName`.
- `database` value semantics — *omitted* vs *explicit `null`*:
  - **Omit** (or send `undefined`): the system falls back to the target `Connection`'s configured `databaseName`. Use this to inherit the connection's default. If the target connection has no `databaseName`, the resulting effective database is `null`.
  - **Explicit `null`**: clears the editor's database. Use this only when you specifically need an empty database (rare).
  - **String**: pins that exact database.
  - On `connectionId` change without `database`, fallback re-fires from the *new* connection's default. To preserve the old database across a connection switch, pass `database` explicitly.
- `limit` may be set independently; accepted values are `10`, `100`, `1000`, or `null`.

The same `undefined` vs `null` contract applies to the **initial-open** path (`workspace.open` of a `query_editor`, plus chat "Run SQL" buttons and ER designer DDL handoff): omitting `database` triggers the connection-default fallback; explicit `null` clears.

---

### 3. `er_inspector`

**Source file**: `client/src/features/stage/adapters/ErInspectorAdapter.ts`
**objectId**: `tabId`
**Description**: a read-only ER browser backed by a captured JDBC metadata
snapshot plus local annotations, layout, viewport, and virtual relations.

#### `read` output

| mode | Returns |
|------|---------|
| `state` | The persisted `ErInspectorPayload`, or `null` when no payload has been hydrated |
| `schema` | `{ type: 'er_inspector', patchCapabilities }` |
| `actions` | exec actions listed below |
| `full` | merged `{ state, schema, actions }` |

State follows [ER Tab Protocol](./er-tab-protocol.md): `connectionId`,
`database`, `schema`, `selection`, `neighborDepth`, `tablesSnapshot`,
`positions`, `collapsed`, `virtualRelations`, `notes`, and `viewport`.

#### `patch`

Supported whitelist paths:

| path | ops | Effect |
|------|-----|--------|
| `/selection` | `replace` | Replace the selected table list |
| `/neighborDepth` | `replace` | Change neighbor expansion depth |
| `/positions` | `replace` | Replace all node positions |
| `/positions/<table>` | `replace`, `remove` | Update or clear one table position |
| `/collapsed` | `replace` | Replace collapsed table ids |
| `/virtualRelations` | `replace` | Replace all virtual relations |
| `/virtualRelations/-` | `add` | Append a virtual relation |
| `/virtualRelations[id=<id>]` | `replace`, `remove` | Update or remove one virtual relation |
| `/notes` | `replace` | Replace all table notes |
| `/notes/<table>` | `replace`, `remove` | Update or clear one table note |
| `/viewport` | `replace` | Replace canvas viewport |

#### Exec Actions

| action | Params | Effect |
|--------|--------|--------|
| `refresh` | — | Re-read selected tables from the source connection |
| `auto_layout` | — | Recompute node positions with dagre |
| `fit_view` | — | Reset viewport to fit the graph |
| `add_neighbors` | `{ table }` | Add direct FK neighbors for a table |
| `fork_to_designer` | — | Reserved for Plan B; returns an explicit unsupported result in Plan A |

---

> **2026-04-29 update — TD-033 cleanup**: the deprecated `close` alias was removed from `workspace` and `query_editor`; use `detach`, `archive`, or `trash` for workspace lifecycle changes. See [TD-033 Remove Close Alias](../exec-plans/2026-04-29-td033-remove-close-alias-plan.md).

## Adapter Change Checklist

When adding a UI object adapter or changing an existing one, complete these steps:

- [ ] Implement the adapter under `client/src/features/stage/adapters/`
- [ ] Register it in `useUIObjectRegistry.ts` or the relevant initialization path
- [ ] Update this file
- [ ] Update `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` to keep the runtime prompt aligned
