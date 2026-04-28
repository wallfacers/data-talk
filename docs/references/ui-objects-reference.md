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
| `state` | `{ tabs: Array<{tabId, type, title, connectionId, contextOverride?}>, activeTabId: string \| null }` |
| `schema` | `{ type: 'object', properties: { tabs: array, activeTabId: string\|null } }` |
| `actions` | exec actions listed below |
| `full` | merged `{ state, schema, actions }` |

For `query_editor` rows, `connectionId` reflects the currently effective context. `contextOverride` remains the explicit override metadata.

#### `patch`

Not supported. `workspace` is read-only through `patch`; use `exec`.

#### Exec Actions

| action | Required params | Optional params | Effect |
|--------|-----------------|-----------------|--------|
| `open` | `type: string` | `title`, `connection_id`, `database`, `schema`, `payload` | Open a new tab; the adapter registry determines the concrete tab type |
| `focus` | `target: tabId` | — | Ensure the tab is in the workset and make it active; archived targets return `tab_archived` |
| `detach` | `target: tabId` | — | Remove the tab from the top-tab workset only; it remains in the library |
| `archive` | `target: tabId` | `archived?: boolean = true` | Archive when `true`; unarchive when `false` |
| `trash` | `target: tabId` | — | Permanently delete the tab |
| `close` | `target: tabId` | — | **Deprecated alias** for `archive(archived=true)`; scheduled for removal after three release cycles |
| `choose_connection` | — | `preferredConnectionId: string` | Open the connection chooser |

---

### 2. `query_editor`

**Source file**: `client/src/features/stage/adapters/QueryEditorAdapter.ts`
**objectId**: `tabId`
**Description**: the unified SQL workbench tab for manual SQL, resource-tree SQL, AI-prefilled SQL, and direct SQL entry.

#### `read` output

| mode | Returns |
|------|---------|
| `state` | `{ tabId, title, content, language: 'sql', version, dirty, cursor, selection, connectionId, connectionName, database, schema, contextOverride, entryMode, autoRun, executeStatus, results, activeResultId, limit, inWorkset }` |
| `schema` | `{ type: 'object', properties: { tabId, title, content, language, version, dirty, cursor, selection, connectionId, connectionName, database, schema, contextOverride, entryMode, autoRun, executeStatus, results, activeResultId, limit, inWorkset } }` |
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
- `connectionId / connectionName / database / schema`: currently effective execution context
- `contextOverride`: explicit override metadata, separate from the effective context fields
- `entryMode / autoRun`: open source metadata and auto-run behavior
- `executeStatus / results / activeResultId / limit`: runtime execution state
- `results`: summary only, without row payloads; each item exposes `{ resultId, statementIndex, columns, rowCount, durationMs, truncated, error? }`
- `inWorkset`: whether this tab is currently open in the top-tab workset for this app instance

#### `patch`

Supported whitelist paths:

| path | ops | Required fields | Effect |
|------|-----|-----------------|--------|
| `/content` | `replace` | `baseVersion: number` | Replace the full SQL text; a stale base version returns `version_conflict` |
| `/connectionId` | `replace` | — | Change the connection |
| `/database` | `replace` | — | Change the database |
| `/schema` | `replace` | — | Change the schema |

#### Exec Actions

| action | Params | Effect |
|--------|--------|--------|
| `apply_text_edits` | `{ baseVersion, edits: [{ range, expectedText, text }] }` | Apply precise SQL edits by range; every edit must include `expectedText`, and any mismatch rolls back the whole batch |
| `set_context` | `{ connectionId?, database?, schema? }` | Update execution context in one call; provide at least one field |
| `run_sql` | `{ limit? }` | Execute the current SQL and write results back into query editor runtime state |
| `format_sql` | — | Format the current SQL and update `content/version` |
| `focus` | — | Focus this tab |
| `close` | — | Close this tab |

---

> **2026-04-28 update — Shared Stage Workbench Phase 1**: the `scope` concept is removed; tabs are workspace-wide; `apply_text_edits` requires `expectedText`; `/content` patch requires `baseVersion`; `workspace.detach/archive/trash` are the new verbs; `close` is deprecated. See [Shared Stage Workbench Design](../product-specs/2026-04-28-shared-stage-workbench-design.md).

## Adapter Change Checklist

When adding a UI object adapter or changing an existing one, complete these steps:

- [ ] Implement the adapter under `client/src/features/stage/adapters/`
- [ ] Register it in `useUIObjectRegistry.ts` or the relevant initialization path
- [ ] Update this file
- [ ] Update `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` to keep the runtime prompt aligned
