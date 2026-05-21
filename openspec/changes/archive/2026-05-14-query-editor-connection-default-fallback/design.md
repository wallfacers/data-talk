## Context

A `Connection` in DataTalk persists a configured `databaseName` (server-side `ConnectionRecord.databaseName`, client-side `Connection.databaseName`, nullable). The field already flows to the frontend via `listConnections`. It is read by `QueryEditorAdapter.availableDatabases` (`client/src/features/stage/adapters/QueryEditorAdapter.ts:145`) and by the ER designer for hydrating its target connection (`client/src/features/stage/components/er-designer-tab.tsx:227`), but **never** by the SQL editor's context-write path.

The SQL editor's `contextOverride` is the persisted shape `{connectionId, database, schema}`. `database` and `schema` are `string | null`. The write entry point is `setQueryEditorContext` (`client/src/features/stage/utils/query-editor-actions.ts:685`), which already uses `params.database === undefined ? effectiveContext.database : params.database` semantics — meaning **the `undefined`-vs-`null` contract is already in place at the input boundary**. We can hook in there without touching the persistence shape.

Two write entry points exist:
1. **`setQueryEditorContext`** (write/patch): called by the toolbar dropdown onChange, `QueryEditorAdapter.set_context`, and the session-rebind path. Receives a partial patch.
2. **`openQueryEditor` → `resolveQueryEditorOpenContext` → `buildQueryEditorPayload`** (initial open): called by stage tab `+`, empty-state SQL button, AI `workspace.open(type=query_editor)`, **the chat "Run SQL" button (via `open-direct-sql-query-editor-tab.ts`), the chat `execute_sql` tool renderer (via `execute-sql.tsx`), and the ER designer "open generated DDL" path (via `ErDesignerAdapter.ts`)**. Each caller builds a `QueryEditorOpenInput` object and hands it to the store.

The Connection list lives in `useConnectionStore.getState().connections` (Zustand, populated on app boot via `listConnections`). No new fetch is needed; reads are O(n) over a typically small list.

`schemaName` is **not** a field on `Connection`/`ConnectionRecord`. `schema` continues to be a per-editor choice with no source-of-truth default. This change is database-only.

## Goals / Non-Goals

**Goals:**
- When `connectionId` is set on a `contextOverride` write and `database` is unspecified, fill `database` from the target `Connection.databaseName` (if non-null).
- When `connectionId` changes on a write, treat `database` as unspecified by default — re-seed from the new connection's default (unless the caller explicitly sets `database` in the same patch).
- One helper, applied uniformly across user-driven (toolbar), AI-driven (`set_context`, `workspace.open`), chat-driven (`Run SQL` button, `execute_sql` tool), and ER-designer-driven (DDL handoff) entry points.
- Preserve user agency: explicit `database: null` (UI "无 / None" selection, AI explicit clear, ER designer with target db explicitly cleared) stays `null`; explicit `database: "foo"` stays `"foo"`.
- Persisted `contextOverride` shape is unchanged; only the values the system writes change.

**Non-Goals:**
- No `schema` fallback (no source field on `Connection`).
- No backend changes (no new API, no migration).
- No change to `resolveTabDataContext` resolver: this remains a pure read-side resolver. Fallback happens before the value lands in `contextOverride`.
- No change to the session-context resolution path: the session's own `connectionContext` is set by the session creation flow (a separate concern); this change does not retro-fix already-persisted session contexts.
- No persistent re-write of editors opened before this change ships. Existing `contextOverride` rows with `database: null` are left as-is unless the user/AI re-writes them.

## Decisions

### D1. Hook point: pure helper consumed by both write paths

A single pure helper, `applyConnectionDefaultDatabase`, lives at `client/src/features/stage/utils/apply-connection-default-database.ts`.

**Signature:**

```ts
type ConnectionDefaultsInput = {
  patch: { connectionId?: string | null; database?: string | null }
  current: { connectionId: string | null; database: string | null }
  connections: Array<{ id: string; databaseName: string | null }>
}

type ConnectionDefaultsResult = {
  database: string | null
  appliedFallback: boolean
}

export function applyConnectionDefaultDatabase(
  input: ConnectionDefaultsInput,
): ConnectionDefaultsResult
```

**Semantics** (the `=== undefined` check is load-bearing — `null` and `undefined` are distinct):

1. If `patch.database !== undefined` (string OR explicit `null`) → return `{database: patch.database, appliedFallback: false}`. Caller's choice wins.
2. Else if `patch.connectionId !== undefined` AND `patch.connectionId !== current.connectionId` AND `patch.connectionId !== null` → look up `connections.find(c => c.id === patch.connectionId)?.databaseName`:
   - non-null → `{database: <found>, appliedFallback: true}`.
   - null (connection has no default) OR connection-not-found → `{database: null, appliedFallback: false}`.
3. Else (no connection change and no patch.database) → `{database: current.database, appliedFallback: false}`.

The helper is pure: no store reads, no React, no side effects. Callers pass `connections` they already hold. This makes it trivially unit-testable and reusable from every write path.

Note on `{database: undefined}` vs "key absent": `Object.hasOwn(obj, 'database')` returns `true` even when callers spread `{database: undefined}` (e.g., `{database: p.database}` where `p.database` is undefined). The helper deliberately does **not** rely on `Object.hasOwn` — only on `=== undefined` — because upstream callers (e.g., `QueryEditorAdapter.set_context`) routinely spread possibly-undefined values. The `=== undefined` test handles both "key absent" and "key present with `undefined` value" identically.

**Rationale:**

- Write-time hook (not resolver) means one write = one definitive value. The resolver stays pure.
- Pure-function signature avoids coupling to Zustand; callers pass `connections`.
- Helper is reusable from every caller without duplicating logic.

**Alternative considered: hook at the resolver.** Rejected because the resolver runs every render, would need memoization on connection list, and the "user clears database" semantic becomes hard to express (if resolver always falls back, user can never see an empty database).

**Alternative considered: normalizer.** Rejected because `normalizeQueryEditorPayload` is a pure shape coercion and shouldn't read from `useConnectionStore`.

### D2. `setQueryEditorContext` integration

Inside `setQueryEditorContext` (`query-editor-actions.ts:685`):

- Before the existing `nextDatabase = params.database === undefined ? effectiveContext.database : params.database` (line 720), invoke `applyConnectionDefaultDatabase` with `patch = {connectionId: params.connectionId, database: params.database}` and `current = {connectionId: effectiveContext.connectionId, database: effectiveContext.database}`. Use the helper's `database` as `nextDatabase`.
- The session-rebind branch (`params.useSessionContext === true`, lines 700–715) intentionally **bypasses** the helper — rebind clears the override entirely; database is then resolved from the session's `connectionContext` via the resolver, not from connection defaults.

### D3. `openQueryEditor` (initial-open) integration

Inside `resolveQueryEditorOpenContext` (`stage-store.ts:216`):

- The function returns `{connectionId, database, schema, ...}`. After computing the raw `connectionId`/`database`/`schema` (from explicit input or session context), apply `applyConnectionDefaultDatabase` with the raw `input.database` value (preserving `undefined` vs `null` vs string) and `current = {connectionId: null, database: null}` (initial open has no current state).
- Use the returned `database` in `ResolvedQueryEditorOpenContext.database`.

Specifically:
- **Explicit-connectionId branch (lines 219–228)**: pass `input.database` directly to the helper. `undefined` → fallback fires; `null` → kept as null; string → kept.
- **Session-context branch (lines 235–243)**: `sessionContext?.database` is `string | null | undefined`. Forward `undefined` (do **not** `?? null`) so the helper substitutes the connection default. This means: if a session has connection `A` (databaseName `"x"`) but the session's `dataContext.database` is null/undefined, opening a new editor seeds it to `"x"`. Consistent with toolbar-switch and what users expect.

The helper's "patch.connectionId differs from current.connectionId" test must treat `current.connectionId = null` as "different from any non-null connectionId" — confirmed by D1 semantic 2.

### D3b. Call-site updates: preserve undefined at the boundary

Three callers currently coerce `undefined → null` before invoking `openQueryEditor`, defeating the fallback signal:

1. **`client/src/features/stage/utils/open-direct-sql-query-editor-tab.ts:34`** — chat "Run SQL" button on a SQL code block. Currently `database: sessionContext?.database ?? null`. Change to `database: sessionContext?.database ?? undefined`. Keep `schema: sessionContext?.schema ?? null` as-is (schema not in scope).
2. **`client/src/features/chat/components/tools/renderers/execute-sql.tsx:68`** — chat `execute_sql` tool renderer. Same `?? null` → `?? undefined` change.
3. **`client/src/features/stage/adapters/ErDesignerAdapter.ts:274`** — ER designer DDL-into-query-editor handoff. Currently `database: payload.targetDatabase ?? null`. Change to `?? undefined`. Semantic: when the user didn't pick a target database in the designer, fall back to the target connection's default; when they explicitly chose one, that wins. (For ER designer the `targetConnectionId` is set by the user; this caller is symmetric to user-toolbar-switching.)

`QueryEditorOpenInput.database` is already `string | null | undefined` (`stage-store.ts:39`), so the type checker is satisfied without further changes.

The toolbar `+` button (`stage-tab-bar-add-button.tsx:17`) and empty-state SQL button (`stage-window.tsx:101`) do not currently pass `database` at all — they're already correct under the new contract (omit = fall back).

### D4. `QueryEditorAdapter.set_context` boundary

Verified against current code (`QueryEditorAdapter.ts:485-533`):

- JSON-Schema `paramsSchema` (line 38-57) marks `database` as `type: ['string', 'null']` and `anyOf`-requires at least one of the input fields. Omitting `database` while requiring `connectionId` is legal and produces `p.database === undefined` after parse.
- The translation at line 524–530 spreads `database: p.database`, where `p.database` may be `undefined`. Downstream `setQueryEditorContext` (line 720) tests `params.database === undefined` and selects the fallback path. **The undefined-vs-null contract is already wired end-to-end.** No JSON-Schema change required.

What this change adds:

- The fallback case currently sinks to `effectiveContext.database` (the prior persisted value). After this change it routes through `applyConnectionDefaultDatabase`, which when `connectionId` changes substitutes the new connection's `databaseName`.

### D4b. Adapter-internal `effectiveDatabase` guard must also use the helper

Inside `set_context` (line 499–501) the adapter computes a local `effectiveDatabase` used by guards at line 502 / 505:

```ts
const effectiveDatabase = p.database === undefined
  ? currentContext?.database ?? null
  : p.database
if (p.schema && (!effectiveConnectionId || !effectiveDatabase)) { return execError(...) }
```

This is dead-reckoning the **pre-fallback** value. If AI sends `{connectionId: 'B', schema: 'public'}` and `B` has `databaseName = "warehouse"`, the write will succeed (fallback fires inside `setQueryEditorContext`), but this guard sees `effectiveDatabase = currentContext.database` (the old connection's database, possibly null) and may falsely reject.

Fix: thread the helper into the adapter's local calculation so the guard reflects the post-fallback value:

```ts
const projected = applyConnectionDefaultDatabase({
  patch: { connectionId: p.connectionId, database: p.database },
  current: {
    connectionId: currentContext?.connectionId ?? null,
    database: currentContext?.database ?? null,
  },
  connections: useConnectionStore.getState().connections,
})
const effectiveDatabase = projected.database
```

The downstream `setQueryEditorContext` call is unchanged — it runs the helper again internally. Running the helper twice is cheap (pure, O(n) over a small list) and keeps the two layers independent.

### D5. UI dropdown: no per-component fallback

The connection-dropdown onChange in `sql-workbench-tab.tsx` already calls `setQueryEditorContext({ connectionId: <new>, /* no database */ })`. Once D2 lands, fallback to the new connection's `databaseName` fires automatically. **No change to the component**.

This is the "后面也自由切换" requirement: switching connection auto-seeds the new default.

### D6. Connection cache miss = no-op fallback

If `useConnectionStore.getState().connections` is empty (e.g., during boot before `listConnections` resolves), the helper returns the raw `current.database` (no fallback). Better to leave `database` at user-specified-or-null than to write a stale/incomplete value.

The connection list is loaded eagerly at app boot. The window where it's empty is short. The user can't open SQL editors during it via UI; the only realistic path is AI-driven `set_context` firing before list-load. If that happens, the user can re-pick the connection after the list loads to re-trigger fallback.

### D7. Telemetry / observability

The helper returns `appliedFallback: boolean`. v1 logs it at `console.debug` from `setQueryEditorContext` when true. This makes it easy to verify in devtools that fallback fired on a given action without test scaffolding.

## Risks / Trade-offs

- **[Risk] Stale Connection defaults after edit.** If user edits a connection's `databaseName` after opening editors, existing editors keep the old value. → *Mitigation:* Document in the spec. Workaround: re-pick the connection in the toolbar.

- **[Risk] AI prompts that omit `database` to mean "clear".** Existing AI behavior may treat "omit field = clear". After this change, omit = fallback. → *Mitigation:* (1) Update `AGENTS.md`. (2) Document new contract: explicit `null` = clear; omit = fallback. The prior empirical behavior was ambiguous; the new contract is closer to typical REST patch semantics.

- **[Risk] Chat / ER-designer callers that previously yielded `database: null` now yield a real value.** Behavior change for `open-direct-sql-query-editor-tab.ts`, `execute-sql.tsx`, `ErDesignerAdapter.ts`. → *Mitigation:* Prior behavior was a dead end (run usually blocked); fallback strictly improves outcomes. Audit test fixtures to update expectations.

- **[Risk] Tests baking in old behavior.** Existing vitest assertions on `database: null` after these paths may break. → *Mitigation:* tasks.md includes an audit pass; most failures will be in `query-editor-actions.test.ts`, `sql-workbench-tab.test.tsx`, `QueryEditorAdapter.test.ts`, and `markdown.test.tsx` / `execute-sql.test.tsx`.

- **[Risk] Interaction with `query-editor-context-binding`.** That spec covers session rebind (clears override + rebinds boundSessionId). Rebind path bypasses fallback — correct, because rebind means "use session's context", not "re-seed from connection defaults".

## Migration Plan

No DB migration. No protocol break. Frontend-only patch.

Rollout:
1. Land helper + unit tests.
2. Land `setQueryEditorContext` + `QueryEditorAdapter.set_context` guard alignment.
3. Land `openQueryEditor` + three call-site `?? undefined` updates.
4. Update `AGENTS.md` + `docs/references/ui-objects-reference.md`.
5. Manual walkthrough (or playwright when available).
6. Rollback: revert the helper invocation lines; helper module is dead code at that point.

## Open Questions

None. The user has authorized "system decides defaults" with explicit "free to switch" semantics; all behavior decisions follow from that.

## Design Inputs

`client/DESIGN.md` constraints applied:

- **Precision First** + **Dense but Breathable**: eliminates a redundant user action without adding UI chrome.
- **Motion as Confirmation**: N/A — no animation.
- No design tokens introduced or changed.
