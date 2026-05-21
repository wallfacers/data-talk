## Why

Each `Connection` in DataTalk can carry a configured default `databaseName` (set by the user on the connection edit form), but that value is never consumed by the SQL editor context resolution path. When a user manually opens a new SQL editor and only picks a connection — or when AI calls `set_context` with a `connectionId` but no `database`, or when the chat "Run SQL" button opens a query editor seeded from the session — the resulting editor context has `database = null`, leaving the run target ambiguous. Users redundantly re-pick the same database every time, even though the system already knows the intended default.

## What Changes

- Introduce a "connection default" fallback: when a `contextOverride` (or initial open payload) is written with `connectionId` set but `database` **unspecified**, the system fills `database` from the target `Connection`'s `databaseName`.
- Fallback fires at **write time**, not at every resolve — once a value is written, it is the user's choice and is not silently rewritten on subsequent reads.
- Fallback re-fires when `connectionId` changes: switching to a different connection re-seeds `database` from the new connection's default (preserving the "free to switch" semantic).
- Disambiguate "unspecified" vs "explicit null": passing `database: null` (the user picks "无 / None", AI explicitly sends `null`, or the call site explicitly clears it) is preserved; passing `undefined` / omitting the field is the trigger for fallback.
- Apply uniformly across **all** editor open / context-write entry points:
  - User toolbar `+` button (`stage-tab-bar-add-button.tsx` → `openQueryEditor`)
  - Empty-state SQL button (`stage-window.tsx` → `openQueryEditor`)
  - AI `QueryEditorAdapter.set_context`
  - Chat "Run SQL" button on a SQL code block (`open-direct-sql-query-editor-tab.ts`)
  - Chat `execute_sql` tool renderer (`execute-sql.tsx`)
  - ER designer "open generated DDL" (`ErDesignerAdapter.ts`)
  - Connection-dropdown change in the SQL workbench toolbar (already calls `setQueryEditorContext`, no caller change needed once the helper is wired)
- Three of those call sites currently coerce `undefined → null` at the boundary (e.g. `database: sessionContext?.database ?? null`), which would silently bypass the fallback. They must change to `?? undefined` (or omit the field) so the "unspecified" signal survives.
- `schema` is **not** in scope: the `Connection` record has no `schemaName` field today, so there is no source-of-truth default to fall back on. `schema` continues to be selected explicitly per editor.

## Capabilities

### New Capabilities
- `query-editor-connection-defaults`: Rules for seeding `database` from a `Connection`'s configured `databaseName` default when a SQL editor's `contextOverride` does not specify one. Covers both write-time and connection-switch behavior, both manual and AI-driven entry points, plus the chat "Run SQL" / `execute_sql` / ER designer DDL-handoff entry points.

### Modified Capabilities

None. The new capability is independent — `query-editor-context-binding` (canonical) covers session binding; this change layers connection-default fallback on top without changing binding requirements.

## Impact

- **Frontend code**:
  - `client/src/features/stage/utils/apply-connection-default-database.ts` (new) — pure helper.
  - `client/src/features/stage/utils/query-editor-actions.ts` — `setQueryEditorContext` consumes the helper before persisting.
  - `client/src/stores/stage-store.ts` — `resolveQueryEditorOpenContext` consumes the helper on initial open.
  - `client/src/features/stage/adapters/QueryEditorAdapter.ts` — local `effectiveDatabase` calculation (used by the schema/database guards) routes through the helper so guards reflect the post-fallback value.
  - `client/src/features/stage/utils/open-direct-sql-query-editor-tab.ts:34` — `database: sessionContext?.database ?? null` → `?? undefined`.
  - `client/src/features/chat/components/tools/renderers/execute-sql.tsx:68` — same change.
  - `client/src/features/stage/adapters/ErDesignerAdapter.ts:274` — `database: payload.targetDatabase ?? null` → `?? undefined`.
- **No backend changes**: `Connection.databaseName` already flows through `listConnections`. No new API, no JDBC change.
- **No schema/data-source-type change**: layered on the existing `Connection` record. `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` is N/A.
- **Persistence**: `contextOverride` shape unchanged. The change is purely in *when* the system writes a non-null `database`.
- **AI protocol**: `QueryEditorAdapter.set_context` input contract clarifies `undefined` vs `null` for `database`. The action's JSON Schema already supports omitting `database`, so no schema change. Existing AI callers that send explicit `null` to clear the field are unaffected; callers that omit the field now get the fallback (previously got `null`).
- **Tests**: vitest coverage for the new helper, `setQueryEditorContext`, `QueryEditorAdapter.set_context` undefined-vs-null + guard-alignment cases, the three updated call sites, and the connection-dropdown re-seed in `sql-workbench-tab.test.tsx`.

## Design Inputs

`client/DESIGN.md` constraints applied:

- **Precision First** + **Dense but Breathable**: this change reduces a redundant click-through-default step in editor setup, tightening the path without adding UI chrome — no new component, no new tokens, no visual surface.
- **Motion as Confirmation**: N/A — no animation.
- **Calm in Light, Crisp in Dark**: N/A — no visual change.

## Risks

- **Risk: Connection defaults changing after editor is opened.** Edit to a Connection's `databaseName` does not retroactively update editors that were opened earlier with the old value. *Intentional:* silently rewriting open editors when settings change is more surprising than the stale value. Workaround: user can re-pick the connection in the toolbar dropdown to re-trigger fallback. Document this in the spec.
- **Risk: Chat / ER-designer call sites that previously got `null` now get a real database value.** Behavior change for `open-direct-sql-query-editor-tab.ts`, `execute-sql.tsx`, `ErDesignerAdapter.ts`. The prior behavior (database = null, run usually blocked) was a dead end, not a feature — switching to fallback strictly improves outcomes.
- **Risk: AI callers that previously relied on `omit database` ≡ `null`.** Update `server/.../resources/agents/AGENTS.md` to document the new contract: omit = fall back to connection's configured default; explicit `null` = clear.
- **Risk: Race with connections cache.** The helper needs the connection list; if connections haven't loaded yet, the helper no-ops (does not write a partial fallback). Mitigated by reading from the `connection-store` cache and skipping fallback on cache miss.
- **Risk: Adapter guard rejecting valid AI requests.** `QueryEditorAdapter.set_context`'s pre-write guard for `schema requires effective database` currently uses the *pre-fallback* `effectiveDatabase`. If AI sends `{connectionId: 'B', schema: 'x'}` where current ctx has `database = null` but B's `databaseName = "foo"`, the guard would falsely reject. Mitigation: route the adapter's `effectiveDatabase` through the same helper so the guard reflects post-fallback value.
- **Open BUGs overlap**: grepped `docs/bugs/` for `query-editor`, `sql-editor`, `connection-default`, `database` — no open BUGs in this module. BUG-0012 (dashboard widget default-connection-id resolution) is fixed and is a useful precedent for the "fall back to connection's configured default" pattern.
- **Interaction with archived `ai-editor-session-binding`** (now in `query-editor-context-binding` canonical spec): session-rebind path clears `contextOverride` entirely — fallback does not fire on rebind, because rebinding means "use the session's context", not "re-seed from connection defaults". The two systems compose cleanly.
