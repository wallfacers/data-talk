## Context

A `Connection` in DataTalk persists a configured `databaseName` (server-side `ConnectionRecord.databaseName`, client-side `Connection.databaseName`, nullable). The field already flows to the frontend via `listConnections`. It is read by `QueryEditorAdapter.availableDatabases` (`client/src/features/stage/adapters/QueryEditorAdapter.ts:145`) and by the ER designer (`client/src/features/stage/components/er-designer-tab.tsx:227`), but **never** by the SQL editor's context-write path.

The SQL editor's `contextOverride` is the persisted shape `{connectionId, database, schema}`. `database` and `schema` are `string | null`. The write entry point is `setQueryEditorContext` (`client/src/features/stage/utils/query-editor-actions.ts:685`), which already uses `params.database === undefined ? effectiveContext.database : params.database` semantics — meaning **the `undefined`-vs-`null` contract is already in place at the input boundary**. We can hook in there without touching the persistence shape.

Two write entry points exist:
1. **`setQueryEditorContext`** (write/patch): called by the toolbar dropdown onChange, the `QueryEditorAdapter.set_context` adapter, and the session-rebind path. Receives a partial patch.
2. **`openQueryEditor` → `resolveQueryEditorOpenContext` → `buildQueryEditorPayload`** (initial open): called by stage tab "+", empty-state buttons, and AI's `workspace.open_query_editor`. Builds the initial payload, which can include a `contextOverride`.

The Connection list lives in `useConnectionStore.getState().connections` (Zustand, already populated on app boot via `listConnections`). No new fetch is needed; reads are O(n) over a typically small list.

`schemaName` is **not** a field on `Connection`/`ConnectionRecord`. `schema` continues to be a per-editor choice with no source-of-truth default. This change is database-only.

## Goals / Non-Goals

**Goals:**
- When `connectionId` is set on a `contextOverride` write and `database` is unspecified, fill `database` from the target `Connection.databaseName` (if non-null).
- When `connectionId` changes on a write, treat `database` as unspecified by default — re-seed from the new connection's default (unless the caller explicitly sets `database` in the same patch).
- One helper, applied uniformly across user-driven (toolbar) and AI-driven (`set_context`) entry points and at initial-open time.
- Preserve user agency: explicit `database: null` (UI "无 / None" selection or AI explicit clear) stays `null`; explicit `database: "foo"` stays `"foo"`.
- Persisted `contextOverride` shape is unchanged; only the *values* the system writes change.

**Non-Goals:**
- No `schema` fallback (no source field exists on `Connection`).
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
  appliedFallback: boolean   // true iff the helper substituted a connection-default value
}

export function applyConnectionDefaultDatabase(
  input: ConnectionDefaultsInput,
): ConnectionDefaultsResult
```

**Semantics** (the `=== undefined` check is load-bearing — `null` and `undefined` are distinct here):

1. If `patch.database !== undefined` (string OR explicit `null`) → return `{database: patch.database, appliedFallback: false}`. Caller's choice wins.
2. Else if `patch.connectionId !== undefined` AND `patch.connectionId !== current.connectionId` AND `patch.connectionId !== null` → look up `connections.find(c => c.id === patch.connectionId)?.databaseName`:
   - non-null → return `{database: <found>, appliedFallback: true}`.
   - null (connection has no default) OR connection-not-found → return `{database: null, appliedFallback: false}`.
3. Else (no connection change and no patch.database) → return `{database: current.database, appliedFallback: false}`.

Note on the `{database: undefined}` vs "key absent" question: JavaScript treats `Object.hasOwn(obj, 'database')` as `true` when a caller writes `{database: undefined}` explicitly. The helper deliberately does **not** rely on `Object.hasOwn` — only on `=== undefined` — because upstream callers (e.g., `QueryEditorAdapter.set_context`) spread `{database: p.database, ...}` where `p.database` may itself be `undefined`. The `=== undefined` test handles both "key absent" and "key present with `undefined` value" identically.

The helper is pure: no store reads, no React, no side effects. Connections are passed in. This makes it trivially unit-testable and reusable from both write paths.

**Rationale:**

- Write-time hook (not resolver) means **one write = one definitive value**. The resolver stays a read-only function. Past `useEffect`-based attempts to "fix it on read" tend to cause re-render loops and stale closures.
- Pure-function signature avoids coupling the helper to Zustand internals; callers pass `connections` they already hold.
- The `appliedFallback` flag is exposed so callers can log/telemetry-tag when fallback fired (not used in v1, but cheap to keep).

**Alternative considered: hook at the resolver.** Rejected because the resolver runs every render, would need to memo on connection list, and the "user clears database" semantic becomes hard to express (if resolver always falls back, user can never see an empty database).

**Alternative considered: normalizer.** Rejected because `normalizeQueryEditorPayload` is a pure shape coercion and shouldn't read from `useConnectionStore`. Injecting connections as a normalizer parameter would propagate that dependency across every call site.

### D2. `setQueryEditorContext` integration

Inside `setQueryEditorContext` (`query-editor-actions.ts:685`):

- Before the existing `nextConnectionId = ...` / `nextDatabase = ...` resolution lines (currently lines 719–721), invoke `applyConnectionDefaultDatabase` with:
  - `patch = { connectionId: params.connectionId, database: params.database }`
  - `current = { connectionId: effectiveContext.connectionId, database: effectiveContext.database }`
  - `connections = useConnectionStore.getState().connections`
- Use the returned `database` as the value for the existing `nextDatabase` variable.

This is a 4-line insertion. The rest of the function (rebind branch, null-connectionId branch, write to `sqlWorkbenchStore` + payload) is untouched.

The session-rebind branch (`params.useSessionContext === true`) intentionally **bypasses** the helper — rebind clears the override entirely; database is then resolved from the session's `connectionContext` via the resolver, not from connection defaults. This is correct: rebinding to a session means "use whatever the session has", not "re-seed from connection defaults".

### D3. `openQueryEditor` (initial-open) integration

Inside `resolveQueryEditorOpenContext` (`stage-store.ts:216`):

- The function returns `{connectionId, database, schema, ...}`. After computing the raw values (from explicit input or session context), apply `applyConnectionDefaultDatabase` with:
  - `patch = { connectionId: <resolved>, database: <resolved or undefined if caller didn't pass one> }`
  - `current = { connectionId: null, database: null }` (initial open has no current)
  - `connections = useConnectionStore.getState().connections`
- Use the returned `database`.

For the "explicit connectionId" branch (lines 219–228): if the caller passed `database` explicitly (even null), keep it; if `database` was undefined in input, run the fallback.
For the "session context" branch (lines 235–243): the session's `database` is already a non-undefined value (either a string or null). If null, run the fallback. This means: if the session has connection X with database `null`, but connection X has default db `"foo"`, opening a new editor from this session seeds it to `"foo"`. This is consistent with the toolbar-switch behavior and is what users expect.

To track "did caller pass database explicitly", introduce a sentinel: the helper looks at `Object.hasOwn(input, 'database')` on the original `QueryEditorOpenInput`, not at the value. Or — cleaner — change the input contract for `resolveQueryEditorOpenContext` so it preserves "was database specified" as a separate boolean. Decision: use a small wrapper type that carries `databaseSpecified: boolean` through `resolveQueryEditorOpenContext`. (Not a public API change; this is an internal helper.)

### D4. `QueryEditorAdapter.set_context` already preserves undefined-vs-null at the boundary

Verified against current code (`QueryEditorAdapter.ts:485-533`):

- JSON-Schema `paramsSchema` at line 38-57 marks `database` / `schema` / `connectionId` as `type: ['string', 'null']` and `anyOf`-requires at least one of the input fields. Omitting `database` while requiring `connectionId` is legal and produces `p.database === undefined` after parse.
- The translation at line 524–530 spreads `database: p.database`, where `p.database` may be `undefined`. Downstream `setQueryEditorContext` (line 720) tests `params.database === undefined` and selects the fallback path. The undefined-vs-null contract is **already wired end-to-end** — no schema change required.

What this change adds on top:

- The fallback case currently sinks to `effectiveContext.database` (the prior persisted value). After this change it routes through `applyConnectionDefaultDatabase`, which when `connectionId` changes substitutes the new connection's `databaseName`.
- No changes to the adapter's input schema, JSON-Schema, or Zod schema are needed.

### D4b. Adapter-internal `effectiveDatabase` guard must also use the helper

Inside `set_context` (line 499–501) the adapter computes a local `effectiveDatabase` used by guards at line 502 / 505:

```ts
const effectiveDatabase = p.database === undefined
  ? currentContext?.database ?? null
  : p.database
if (p.schema && (!effectiveConnectionId || !effectiveDatabase)) { return execError(...) }
```

This is dead-reckoning the **pre-fallback** value. If the AI sends `{connectionId: 'B', schema: 'public'}` and `B` has `databaseName = "warehouse"`, the write will succeed (fallback fires inside `setQueryEditorContext`), but this guard sees `effectiveDatabase = currentContext.database` (the *old* connection's database, possibly null), and may falsely reject the request.

Fix: thread `applyConnectionDefaultDatabase` into the adapter's local `effectiveDatabase` calculation so the guard reflects the post-fallback value. Specifically:

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

The connection-dropdown onChange in `sql-workbench-tab.tsx` already calls `setQueryEditorContext({ connectionId: <new>, /* no database */ })`. Once D2 lands, this automatically triggers fallback to the new connection's `databaseName`. **No change to the component**.

(This was the user's "后面也自由切换" requirement — switching connection auto-seeds the new default.)

### D6. Connection cache miss = no-op fallback

If `useConnectionStore.getState().connections` is empty (e.g., during boot, before the list is fetched), the helper returns the raw `current.database` (no fallback). Rationale:

- Better to leave `database` as user-specified-or-null than to write a stale/incomplete value.
- The connection list is loaded eagerly at app boot. The window where it's empty is < 200ms and the user can't open SQL editors during it. The only realistic path is AI-driven `set_context` firing before the list is loaded, which is itself unusual (AI typically reads connection list before deciding what to set).
- If the user re-opens the editor or re-picks the connection after the list loads, the helper fires correctly on that next write.

### D7. Telemetry / observability

The helper returns `appliedFallback: boolean`. v1 does not consume this flag, but it's logged at `console.debug` in `setQueryEditorContext` when true. This makes it easy to verify in browser devtools that fallback fired on a given action without adding test scaffolding.

## Risks / Trade-offs

- **[Risk] Stale Connection defaults after edit.** If user edits a connection's `databaseName` after opening editors, existing editors keep the old value. → *Mitigation:* Document this in the spec. The trade-off is intentional — silently rewriting open editors when settings change is more surprising than the stale value. Workaround: user can re-pick the connection in the toolbar dropdown to re-trigger fallback.

- **[Risk] AI prompts that omit `database` to mean "clear".** Existing AI behavior (in `server/.../agents/AGENTS.md` and prompt templates) may treat "omit field = clear". After this change, omit = fallback. → *Mitigation:* (1) Audit `AGENTS.md` and prompt strings for guidance on `set_context.database`. (2) Document the new contract: explicit `null` = clear, omit = fallback. (3) The empirical behavior was always ambiguous in the agent's mental model anyway, and the new contract is closer to typical "patch semantics" in REST.

- **[Risk] Resolver re-derivation surprises.** If the resolver runs after the write but reads the persisted override, fine. But if any code path reads `payload.database` (not `contextOverride.database`) directly, it might not see the new value. → *Mitigation:* `buildQueryEditorPayload` writes `database: context.database` alongside `contextOverride.database`. Both update in sync via the same `applyConnectionDefaultDatabase` call.

- **[Risk] Tests baking in old behavior.** Existing vitest assertions on `database: null` after `setQueryEditorContext({connectionId: 'X'})` may break. → *Mitigation:* tasks.md includes an audit pass. Most failures will be in `query-editor-actions.test.ts` and `sql-workbench-tab.test.tsx`; both already have fixture connections, easy to extend with `databaseName`.

- **[Risk] Interaction with in-flight `ai-editor-session-binding`.** That change rebinds `boundSessionId` and clears `contextOverride` when toggling ON. After rebind, the editor's database comes from the session's `connectionContext`, not from connection defaults — fallback does not fire on rebind. → *Mitigation:* This is the correct behavior. Rebind = "use the session's context"; fallback only applies to direct `contextOverride` writes.

## Migration Plan

No DB migration required. No protocol break. Ship as a frontend-only patch.

Rollout steps:
1. Land helper + unit tests.
2. Land `setQueryEditorContext` integration + adapter undefined/null distinction.
3. Land `openQueryEditor` integration.
4. Update `server/.../agents/AGENTS.md` to document the contract change.
5. Manual walkthrough (playwright-cli): create connection with default db, open SQL editor via toolbar "+", verify database is pre-filled; switch connection, verify re-seed; AI `set_context` without `database`, verify fallback; UI "无 / None", verify it sticks.
6. No rollback needed — this is purely additive. If a regression surfaces, revert the helper invocation lines; the helper module itself is dead code at that point.

## Open Questions

None. The user has authorized "system decides defaults" with explicit "free to switch" semantics; all behavior decisions follow from that.

## Design Inputs

`client/DESIGN.md` constraints applied:

- **Precision First** and **Dense but Breathable**: no new UI surface; this change eliminates a redundant user action without adding visual chrome.
- **Motion as Confirmation**: N/A — no animation.
- No design tokens introduced or changed.
