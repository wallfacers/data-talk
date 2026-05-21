## Why

Today the SQL query editor binds to `originSessionId` regardless of whether the tab was opened by an AI tool action or by the user. After switching to a new chat session, an AI-opened editor keeps reading the originating session's `data-uat / 未设置 / 未设置` context and the run button stays enabled because `canRun` only requires `connectionId`. The "use session context" toggle stays visually ON even though the user is no longer in that session, producing a confusing "I didn't set anything but it still runs" UX. The architecture intent of `query_editor` as workspace-scoped is correct for *user-opened* editors (a persistent scratch pad), but wrong for *AI-opened* editors which should live and die with the conversation that spawned them.

## What Changes

- **Introduce a tab-level `boundSessionId` field** as the single source for context resolution:
  - Both `source = 'ai'` and `source = 'user'` editors read their effective context from `boundSessionId`'s session, preserving the displayed connection/db/schema across active-session switches
  - `source = 'ai'` editors permit re-binding via "manual ON" (user clicks the toggle), which sets `boundSessionId := activeSessionId` and refreshes context
  - `source = 'user'` editors have no toggle and `boundSessionId` is fixed at creation
- **Make the "use session context" toggle a derived display**, not a stored boolean:
  - `toggle ON ⟺ (boundSession == activeSessionId) AND (contextOverride == null)`
  - Removes user-stored `useSessionContext` field; replaced by derived rendering
- **Manual ON action = re-bind**: clicking the OFF toggle re-binds `boundSessionId` to the active session and clears any override, immediately synchronizing with the new session's context
- **AI editor in mismatched state shows a "来自会话: <title>" badge** so the user understands it is currently tracking a different session than the active one
- **Remove the toggle for `source = 'user'` editors** — they have no "follow session" semantic, only direct connection/db/schema selection
- **`canRun` rule unchanged**: still `Boolean(effectiveContext.connectionId) && sql.trim().length > 0` (db/schema remain optional because connection-default-db and fully-qualified names are legitimate). The fix is in the binding model, not the canRun gate.
- **BREAKING (internal contract only)**: `payload.useSessionContext` is no longer authoritative; readers must derive from `boundSessionId` vs `activeSessionId` and `contextOverride`. Persistence migration converts legacy `useSessionContext` into the new shape on hydrate.

## Capabilities

### New Capabilities
- `query-editor-context-binding`: how a query editor tab resolves its execution context (connection/database/schema) across session switches, including source-based binding rules and the derived-toggle UI contract

### Modified Capabilities
<!-- None — no existing capability covers query-editor context binding -->

## Impact

**Affected code (client):**
- `client/src/features/stage/utils/normalize-query-editor-payload.ts` — add `boundSessionId` (replacing legacy `useSessionContext`)
- `client/src/features/stage/utils/resolve-tab-data-context.ts` — accept `boundSessionId` semantically as the bound session id (the resolver's existing `originSessionId` field name keeps for back-compat at call sites; behavior unchanged at resolver level)
- `client/src/features/stage/components/sql-workbench-tab.tsx` — `contextSessionId` resolution; pass derived toggle state; pass binding-badge data
- `client/src/features/stage/components/sql-context-toolbar-controls.tsx` — toggle becomes derived; hide for `source='user'`; add "来自会话" badge
- `client/src/features/stage/utils/query-editor-actions.ts` — `setQueryEditorContext` semantics: manual ON triggers re-bind; override write flow
- `client/src/features/stage/adapters/QueryEditorAdapter.ts` — `set_context` action; `read('state')` surfaces `boundSessionId` and binding status
- `client/src/features/stage/persistence/*` — migration of stored payloads (legacy `useSessionContext` → derived; introduce `boundSessionId`)
- `client/src/i18n/messages.ts` — strings: "来自会话: {title}", "已脱离同步" hint, removed toggle copy

**Tests:**
- `resolve-tab-data-context.test.ts` — source-based branching matrix
- `query-editor-actions.test.ts` — manual ON re-bind; override interactions
- `sql-workbench-tab.test.tsx` — toggle derivation; badge visibility; canRun gating
- `QueryEditorAdapter.test.ts` / `WorkspaceAdapter.test.ts` — exposed state shape

**Not affected:**
- Backend SQL execute API (request shape unchanged)
- Stage shell, tab list, session store (stage state remains global per `client/DESIGN.md`)
- `sql-confirmation` spec (L2/L3 dialog flow unchanged)

**Design Inputs (per CLAUDE.md frontend gate):**
- `client/DESIGN.md` — applicable constraints:
  - Stage state is **global, not per-session** — this change keeps that invariant; only tab-level *binding* changes, not stage-level state
  - Toolbar density: SQL editor toolbar uses `bg.soft`, compact rows; binding badge must fit the existing toolbar without adding a second row
  - State communication: do not rely on color alone for the "out of sync" signal — must combine toggle OFF + textual "来自会话" badge + (optional) icon
  - Semantic tokens only: `text.muted` for badge body, `accent.warn` (amber) reserved for a soft attention cue if any; no raw primitive colors
  - `focusRing` for keyboard focus on the toggle; toggle must remain keyboard-operable

**Risks / Known Issues:**
- `docs/bugs/index.md` reviewed: no open BUGs in `stage` / `sql-editor` modules overlap with this change. BUG-0008 (stage trash) and BUG-0011 (sql-result test) are unrelated.
- Persistence migration risk: existing tabs with `useSessionContext = true` and `originSessionId = X` need to map cleanly. Default migration: `boundSessionId := originSessionId` for both source types; that preserves user-editor behavior exactly and only changes AI-editor behavior on next session switch.
- Test coverage risk: today's tests assert `useSessionContext` as ground truth; tests need rewrites alongside the implementation.

**Data Source Type Compatibility:** N/A — this change does not touch JDBC connection handling, schema discovery, SQL execution paths, or dialect logic. It changes only the *which-context-do-I-show-and-send* resolution on the client.
