## Context

The SQL query editor tab today uses one binding rule for all editors: `contextSessionId = tab.originSessionId ?? activeSessionId`. The 2026-04-30 *Query Editor Session Context Truth Plan* established that the editor reads "latest session data context" — but interpreted as the latest context of the *originating* session. This is correct for editors a user opens as a workspace scratch pad, but wrong for editors AI tools spawn from a chat: those should live alongside the conversation that produced them.

The payload already carries `source: 'user' | 'ai'` (see `normalize-query-editor-payload.ts:39`), but no consumer branches on it for context resolution. The current `useSessionContext: boolean` field on the payload encodes a state ("am I synchronized?") that should be derived, not stored — every time today's code asks "are we synced?" it can be answered from `(boundSessionId == activeSessionId) AND (contextOverride == null)`.

User-facing symptom (the trigger for this change): with an AI-opened SQL editor active, switching to a new session leaves `data-uat / 未设置 / 未设置` displayed and the toggle visually ON, while `canRun` stays true because connection is set. User reports "I didn't set anything, but it still runs."

## Goals / Non-Goals

**Goals:**
- AI-opened editors track the active session's context live; switching sessions visibly changes the editor's context (or sets it to "未设置" + canRun false if the active session has no context)
- User-opened editors keep today's behavior (origin-session binding) so they remain useful as a workspace scratch pad
- The "use session context" toggle becomes a *derived* UI signal — never out of step with reality
- "Manual ON" is a single explicit user action that re-binds the editor to the active session and clears any override
- The mismatch state ("I'm looking at an editor from another session") is made visible via a textual badge, not only via a colored toggle (accessibility requirement from `client/DESIGN.md`)
- Persistence of existing tabs migrates without loss

**Non-Goals:**
- Not changing backend SQL execute API
- Not changing the `canRun` rule (still: connection present + SQL non-empty; db/schema remain optional)
- Not touching the L2/L3 confirmation flow
- Not introducing a per-session stage state model — stage state stays global per `client/DESIGN.md`
- Not creating a UI to "promote a user editor to follow session" or "demote an AI editor to pin" — the source is decided at creation time and stays
- Not changing the persisted `originSessionId` field semantics for analytics / history — only the *runtime resolution* changes

## Decisions

### D1. Use `payload.source` (already present) as a UI-affordance discriminator

**Decision**: Both source types use `boundSessionId` to resolve effective context — the difference is purely in UI affordances and re-bind permission:
- `source = 'ai'`: toolbar renders the toggle and mismatch badge; manual ON updates `boundSessionId := activeSessionId`
- `source = 'user'`: toolbar omits the toggle and badge; `boundSessionId` is fixed at creation

**Why**: The user explicitly clarified "上一个会话设置的，保留着" — switching session must preserve the displayed connection/db/schema for AI editors, not auto-flip to the new session. Both source types therefore share the same execution-context resolution. The discriminator drives UI capability (can the user re-bind?) and visual signal (toggle/badge), not context source.

**Alternatives considered**:
- *AI editors auto-switch to active session's context*: rejected after user revision — jarring UX, loses context the user was working with
- *A new `bindingMode: 'live' | 'pinned'` field*: rejected — duplicates `source` and creates two truths
- *Always-pinned (no re-bind capability)*: rejected — AI editors need an escape hatch when the user *does* want to sync to current session

### D2. Introduce `boundSessionId` on the tab; derive everything else from it

**Decision**: Add `boundSessionId: string | null` to the normalized query-editor payload. Resolution is uniform across source types:
```
contextSessionId = boundSessionId ?? originSessionId ?? activeSessionId
```
On creation, `boundSessionId := activeSessionId` (= the originating session). For `source = 'ai'`, manual ON re-binds `boundSessionId := activeSessionId` at the moment of the click. For `source = 'user'`, `boundSessionId` is never updated after creation.

**Why**: Keeps `originSessionId` honest as "session that created this tab" for analytics/history, while `boundSessionId` carries the runtime decision of *which session am I tracking right now*. Manual ON is just an assignment. Same field, same resolution rule, different mutation policy by source.

**Alternatives considered**:
- *Mutate `originSessionId`*: rejected — destroys the "who created this" record
- *Compute `boundSessionId` purely from `useSessionContext` flags*: rejected — that flag is exactly what we're removing
- *Source-based branching in resolver (`source==='ai' ? active : origin`)*: rejected after user revision; user explicitly wants both source types to preserve their displayed context across session switches

### D3. Toggle becomes a pure derived value

**Decision**: Stop persisting `useSessionContext`. UI renders:
```
toggle ON  ⟺  boundSessionId === activeSessionId  AND  contextOverride == null
toggle OFF otherwise
```
Clicking the toggle is only meaningful in one direction — turning ON. ON action:
1. `boundSessionId := activeSessionId`
2. `contextOverride := null`
3. (no other state change)

Clicking the toggle when it is already ON is a no-op. Turning OFF is achieved implicitly by setting an override (i.e., picking a connection/db/schema manually).

**Why**: Eliminates the "ghost ON" state in today's screenshot. The toggle can never lie because it is computed, not stored.

**Alternatives considered**:
- *Bidirectional toggle stored as bool*: rejected — that's today's bug
- *Replace toggle with a "Sync to current session" button*: viable; rejected for minimal-disruption — the toggle shape is familiar, only its semantics change

### D4. `source = 'user'` editors lose the toggle entirely

**Decision**: For user-opened editors, the toolbar shows connection/db/schema selects directly. No toggle. The selects are always enabled; user changes write to `contextOverride` (initialized at creation from origin-session snapshot).

**Why**: A user editor never "follows" anything dynamic — its semantic is "my private working surface." A toggle with one meaningful position is UI noise.

**Trade-off**: Slight visual divergence between AI and user editor toolbars. Mitigated by keeping the same toolbar layout, just hiding the toggle widget for user editors.

### D5. "来自会话: \<title\>" badge for mismatched AI editors

**Decision**: When `source = 'ai'` AND `boundSessionId != activeSessionId`, render a small text badge in the toolbar reading `来自会话: <session title>`. The badge:
- Sits next to the toggle (right-aligned with the existing context controls)
- Uses `text.muted` for the label, `text.base` for the title — no accent color
- Is non-interactive (informational only); the cure is clicking the toggle ON

**Why**: `client/DESIGN.md` says "State cannot be communicated by color alone." Today the only signal would be the toggle color; we need a textual signal too. The badge also resolves the user's confusion in the screenshot — they can now see "this is from session X, not the one you're in."

**Token usage** (per `client/DESIGN.md`):
- Badge container: no background; just inline text, density `compact`
- Label "来自会话:" → `text.muted`, type `ui-xs`
- Session title → `text.base`, type `ui-xs`, max width truncation with ellipsis
- No accent color; if any attention cue is needed at all, use `accent.warn` border only — but baseline ships with text-only badge

### D6. Persistence migration is a one-way map on hydrate

**Decision**: On loading a stored stage tab payload:
- If `boundSessionId` exists → use it as-is
- Else (legacy payload): `boundSessionId := originSessionId`. Drop `useSessionContext` after migration (or keep it as a deprecated read-only field that the normalizer ignores).

**Why**: For both source types, `boundSessionId := originSessionId` is *exactly* today's resolution (`tab.originSessionId ?? activeSessionId`). The behavior change for AI editors triggers only on the *next* session switch, which is the desired moment. No flag day. No data rewrite needed.

**Alternatives considered**:
- *Active migration that re-writes all stored payloads on app boot*: rejected — derived-on-hydrate is simpler and the new shape gets persisted naturally on the next tab payload update

### D7. Hold the `canRun` rule

**Decision**: Keep `canRun = Boolean(effectiveContext.connectionId) && sql.trim().length > 0`. Do not require db or schema.

**Why**: Many SQL workloads legitimately rely on the connection's default database or use fully-qualified names. Hardening `canRun` to require db/schema would break valid usage. The original "can run with nothing set" complaint is fully addressed by the binding-model fix: an AI editor in a new session that has no connection will have `effectiveContext.connectionId = null` and `canRun = false`.

### D8. AI's `set_context` action: scope and side-effects

**Decision**: AI's `set_context` action remains the same shape (`{useSessionContext?, connectionId?, database?, schema?, limit?}`), but semantics map onto the new model:
- `useSessionContext: true` → equivalent to manual ON: `boundSessionId := activeSessionId`, `contextOverride := null`
- `connectionId / database / schema` set → write `contextOverride` (today's behavior)

For `source = 'user'` editors, AI's `set_context` still works — AI can adjust the connection/db/schema of a user-opened editor by writing override. (Aligns with user's instruction: "AI 可以起改他们用到的，连接，库，shcema".) AI cannot, however, "follow active session" a user editor — `useSessionContext: true` on a user editor is a no-op with a warning event.

**Trade-off**: AI silently affecting a user-pinned editor could surprise the user; mitigation is the existing chat tool-call display which renders AI's `set_context` invocations.

## Risks / Trade-offs

- **[Risk] Existing tests assert `useSessionContext` as stored state** → Mitigation: rewrite assertions to check derived rendering; remove tests that pin the old contract. Use TDD: write failing tests for new model first, then migrate.
- **[Risk] `boundSessionId` adds a field to `StageTab.payload`** → Mitigation: covered by D6 migration; default value is `originSessionId`, preserving all current rendering on first hydrate.
- **[Risk] AI editor "live" mode could cause flicker if active session context changes mid-typing** → Mitigation: context is read on render of the toolbar (cheap), and run-time `effectiveContext` snapshot is captured at `runQueryEditorSql` call. The SQL text itself is owned by `sql-workbench-store`, unaffected by context churn. Validate with a vitest that toggles session-store while typing in the editor.
- **[Risk] User confused by AI editor going "empty" after switching to a session with no context** → Mitigation: empty state in toolbar shows `未设置 / 未设置 / 未设置`, run button disabled with tooltip "请选择数据连接". The mismatch badge does not appear (because there is no other session to point to — bound is already active).
- **[Risk] `source='ai'` editors that the user later wants to pin** → Acceptable trade-off: no UI to "pin" today. The user can open a fresh user editor with the same SQL via copy/paste; alternative is a future "Pin to current session" affordance, not in scope.
- **[Trade-off] Per `client/DESIGN.md`, toolbar density must stay compact** → Mitigation: badge is text-only, fits in the existing right-aligned controls; no second row.

## Migration Plan

1. **Field addition** (no breaking on disk): extend `NormalizedQueryEditorPayload` with `boundSessionId: string | null`. Normalizer back-fills it from `originSessionId` when absent.
2. **Resolution refactor**: change `contextSessionId` computation in `sql-workbench-tab.tsx` and `query-editor-actions.ts` to branch on `source`.
3. **Toggle derivation**: replace `useSessionContext` reads in UI with the derived check; keep field on stored payloads for one release as ignored-on-read.
4. **`setQueryEditorContext` action**: rewire `useSessionContext: true` to re-bind; rewire field changes to write override.
5. **Badge + i18n**: add toolbar badge component; new strings.
6. **Tests**: rewrite to assert new derived contract; add session-switch flow tests.
7. **Manual verification** (per CLAUDE.md "先浏览器自测再写文档" memory):
   - Open SQL editor from AI tool in session A → toggle ON, shows A's context
   - Switch to session B → toggle OFF, badge "来自会话: \<A's title\>" appears
   - Click toggle → toggle ON, shows B's context (or `未设置` if B has none)
   - Open SQL editor manually (toolbar +) in session A → no toggle, shows origin snapshot
   - Switch to session B → still shows A's snapshot, no badge, no toggle

**Rollback strategy**: The change is client-only and field-additive. Reverting requires only un-deploying the client; existing stored payloads with `boundSessionId` remain valid (the old normalizer would just ignore the unknown field).

## Open Questions

- *Should the toggle render at all in user editors, or is hiding it entirely the right call?* Current decision (D4): hide it. Open to revisit if usability testing suggests a "this is your private editor" indicator is needed.
- *Behavior when the bound session is deleted while the editor is open*: should the editor degrade to "active session" silently, or hold the orphaned binding? Proposal: when `getSession(boundSessionId)` returns null, treat as `boundSessionId := activeSessionId` on read with a one-time toast. Confirm during implementation.
- *Should the badge be clickable to navigate to the bound session?* Out of scope for this change; logged as a follow-up if user feedback requests it.
