## Why

Each Connection in DataTalk can carry a configured default `databaseName` (set by the user on the connection edit form), but that value is never consumed by the SQL editor context resolution path. When a user manually opens a new SQL editor and only picks a connection — or when AI calls `set_context` with a `connectionId` but no `database` — the resulting editor context has `database = null`, leaving the run target ambiguous. Users have to redundantly re-pick the same database every time, even though the system already knows the intended default.

## What Changes

- Introduce a "connection default" fallback: when a `contextOverride` is written with `connectionId` set but `database` **unspecified**, the system fills `database` from the target `Connection`'s `databaseName`.
- The fallback fires at **write time**, not at every resolve — once a value is written, it is treated as the user's choice and is not silently rewritten.
- The fallback re-fires when `connectionId` changes: switching to a different connection re-seeds `database` from the new connection's default (preserving the "free to switch" semantic the user asked for).
- Disambiguate "unspecified" vs "explicit null": passing `database: null` (the user picks "无 / None" from the dropdown, or AI explicitly sends `null`) is kept as-is; passing `undefined` / omitting the field is the trigger for fallback.
- Apply uniformly across both editor sources: user-opened editors (stage tab "+" or empty-state button) and AI-opened editors (`QueryEditorAdapter.set_context`) share one helper.
- Apply to user-driven UI events too: connection-dropdown change in the SQL workbench toolbar reseeds `database` from the newly chosen connection's default.
- `schema` is **not** in scope: the `Connection` record has no `schemaName` field today, so there is no source-of-truth default to fall back on. Schema continues to be selected explicitly per editor.

## Capabilities

### New Capabilities
- `query-editor-connection-defaults`: Rules for seeding `database` from a `Connection`'s configured `databaseName` default when a SQL editor's `contextOverride` does not specify one. Covers both write-time and connection-switch behavior, both manual and AI-driven entry points.

### Modified Capabilities
<!-- None: `query-editor-context-binding` (introduced by the in-flight `ai-editor-session-binding` change) is conceptually adjacent but covers binding to a session, not connection defaults. This change layers on top without modifying its requirements. -->

## Impact

- **Frontend code**:
  - `client/src/features/stage/utils/query-editor-actions.ts` — `setQueryEditorContext` injects the helper before writing `contextOverride`.
  - `client/src/stores/stage-store.ts` — `openQueryEditor` / `resolveQueryEditorOpenContext` applies the helper when no `database` is supplied on initial open.
  - `client/src/features/stage/adapters/QueryEditorAdapter.ts` — `set_context` distinguishes `undefined` vs `null` for `database` before delegating to `setQueryEditorContext`.
  - `client/src/features/stage/components/sql-workbench-tab.tsx` — connection-dropdown change handler relies on the helper through `setQueryEditorContext` (no per-component fallback).
  - New helper: `client/src/features/stage/utils/apply-connection-defaults.ts` (pure function, takes connection list + override patch, returns the patched override).
- **No backend changes**: `Connection.databaseName` / `schemaName` already flow through `listConnections`. No new API, no JDBC change.
- **No schema/data-source-type change**: this is a UI/contract change layered on the existing `Connection` record. `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` is N/A.
- **Persistence**: `contextOverride` shape is unchanged. The change is purely in *when* the system writes a non-null value into `database` / `schema`.
- **AI protocol**: `QueryEditorAdapter.set_context` input contract clarifies the semantic of `undefined` vs `null` for `database`. Existing AI callers that send explicit `null` to clear the field are unaffected; callers that omit the field now get the fallback (previously got `null`).
- **Tests**: vitest coverage added for the new helper, the `setQueryEditorContext` write path, the `QueryEditorAdapter.set_context` undefined-vs-null branch, and the connection-dropdown re-seed in `sql-workbench-tab.test.tsx`.

## Design Inputs

`client/DESIGN.md` constraints applied:

- **Precision First** + **Dense but Breathable**: this change reduces a redundant click-through-default step in editor setup, tightening the path without adding UI chrome — no new component, no new tokens, no visual surface.
- **Calm in Light, Crisp in Dark**: N/A — no visual change.

## Risks

- **Risk: Connection defaults changing after editor is opened.** If the user edits the Connection's default `databaseName` after a SQL editor has already been opened, the editor's persisted override keeps the old value. This is the *correct* behavior (editing the connection should not silently mutate open editors), but it must be explicit in the spec and the design doc.
- **Risk: AI callers that previously relied on `omit database` ≡ `null`.** Any AI prompt or adapter caller that omits `database` in `set_context` will now get fallback behavior. The mitigation is that the current observable behavior (database left empty, run blocked) was a dead end, not a feature — switching it to fallback strictly improves outcomes. `server/.../agents/AGENTS.md` will be updated to document the new contract.
- **Risk: Race with connections cache.** The helper needs the connection list to look up `databaseName`; if connections haven't loaded yet, the helper must no-op (do not write a partial fallback). Mitigated by reading from the existing `connection-store` cache and skipping fallback when the cache miss happens.
- **Open BUGs overlap**: grepped `docs/bugs/` for `query-editor`, `sql-editor`, `connection-default`, `database` — no open BUGs in this module. BUG-0012 (dashboard widget default-connection-id resolution) is fixed and a useful precedent for the "fall back to connection's configured default" pattern.
- **Interaction with `ai-editor-session-binding`** (in-flight): that change adds session-binding semantics on top of `contextOverride`. The new helper writes into `contextOverride`, so the two changes compose cleanly: session rebind triggers a `setQueryEditorContext` write, which now runs the helper, which seeds defaults from the (possibly new) connection. No coupling required; either change can land first.
