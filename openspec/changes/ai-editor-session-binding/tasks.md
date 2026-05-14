## 1. Data model & normalizer (no UI yet)

- [x] 1.1 Extend `NormalizedQueryEditorPayload` in `client/src/features/stage/utils/normalize-query-editor-payload.ts` with `boundSessionId: string | null`; keep `useSessionContext` on shape as a deprecated derived value (= `contextOverride == null`) for back-compat readers
- [x] 1.2 In the normalizer, back-fill `boundSessionId = payload.boundSessionId ?? payload.originSessionId ?? null`
- [x] 1.3 Update `isNormalizedQueryEditorPayload` to recognize `boundSessionId`
- [x] 1.4 Vitest: extend `normalize-query-editor-payload.test.ts` for new derivation + back-fill cases (13/13 passing)

## 2. Context resolution refactor (uniform — no source branching at resolver)

- [x] 2.1 In `sql-workbench-tab.tsx`, compute `contextSessionId = actualTabState?.boundSessionId ?? payload.boundSessionId ?? tab.originSessionId ?? activeSessionId`. Same rule for both source types
- [x] 2.2 In `query-editor-actions.ts` `resolveQueryEditorContexts`, use the same `boundSessionId`-first resolution rule
- [x] 2.3 Leave `resolve-tab-data-context.ts` API unchanged (its `originSessionId` parameter receives the resolved `contextSessionId`)
- [x] 2.4 Existing `resolve-tab-data-context.test.ts` still passes (8/8). New spec scenarios are covered indirectly via the workbench-tab and actions tests
- [x] 2.5 `query-editor-actions.test.ts` passes (14/14) including session-prefer behavior

## 3. Toggle becomes derived; manual ON re-binds

- [x] 3.1 Update `sql-workbench-tab.tsx` `useSessionContext` reads to compute derived value `boundSessionId === activeSessionId && override == null`; pass to `SqlContextToolbarControls`
- [x] 3.2 `handleUseSessionContextChange(true)` already routes through `setQueryEditorContext({useSessionContext: true})`; updated to mean "re-bind to active"
- [x] 3.3 `setQueryEditorContext` in `query-editor-actions.ts`: `useSessionContext === true` → `rebindToSession(active)` + clear override; fields set → write override only
- [x] 3.4 `boundSessionId` persists via `updateQueryEditorPayloadBoundSession` on re-bind; `ensureTab`/`hydrateTab` carry it through the store
- [ ] 3.5 Vitest: dedicated manual-ON re-bind test (covered indirectly by existing setQueryEditorContext tests)

## 4. User-editor toolbar simplification

- [x] 4.1 `sql-context-toolbar-controls.tsx` accepts `showSessionToggle?: boolean` (default true); hides Switch widget when false
- [x] 4.2 `sql-workbench-tab.tsx` passes `showSessionToggle={payload.source === 'ai'}`
- [x] 4.3 Selects always enabled for user editors via `selectsDisabled = showSessionToggle && useSessionContext`
- [ ] 4.4 Vitest update for toggle visibility (deferred — see Stage 6 test cleanup batch)

## 5. "来自会话" mismatch badge

- [x] 5.1 Badge rendered inline in `sql-context-toolbar-controls.tsx` (not as separate component, kept density compact); `text-muted-foreground` label + `text-foreground` title + ellipsis truncation
- [x] 5.2 `mismatchBoundSessionTitle` computed in `sql-workbench-tab.tsx` from `useSessions('all')` cache lookup
- [x] 5.3 Title passed via toolbar controls prop; badge renders only when non-null
- [x] 5.4 i18n added `stage.queryEditor.fromSession` for zh-CN and en
- [ ] 5.5 Vitest update for badge visibility (deferred — see Stage 6 test cleanup batch)

## 6. Orphan binding fallback + toast

- [x] 6.1 `sql-workbench-tab.tsx` detects orphan via `useSessions('all')` cache; falls back to `activeSessionId` for context read
- [x] 6.2 One-time toast `stage.queryEditor.boundSessionMissing` via `sonner`; guarded by `orphanToastedRef` per `effectiveBoundSessionId`
- [x] 6.3 Mismatch badge gated by `!isOrphanedBoundSession`
- [ ] 6.4 Vitest update for orphan scenarios (deferred — see Stage 6 test cleanup batch)

## 7. AI adapter: `set_context` honors new model

- [x] 7.1 `QueryEditorAdapter.set_context` checks `source==='user'` and returns `{success: true, data: {noop: true}}` when `useSessionContext=true` is requested without field changes
- [x] 7.2 Field-set path passes through to `setQueryEditorContext` which writes override only
- [x] 7.3 `read('state')` returns `source`, `boundSessionId`, `isMismatched` (title resolution left to consumer; adapter lives outside React lifecycle and cannot hook into useSessions)
- [ ] 7.4 Vitest update for new state shape and source-gated re-bind (deferred — see Stage 6 test cleanup batch)

## 8. Workspace adapter surface

- [x] 8.1 `WorkspaceAdapter.read('state')` extended with `source`, `boundSessionId`, `isMismatched` in per-tab summary
- [ ] 8.2 Vitest update (deferred — see Stage 6 test cleanup batch)

## 9. Persistence layer

- [x] 9.1 `boundSessionId` is part of the payload shape; serializers pass it through via `updateTabPayload`
- [x] 9.2 Normalizer ignores stored `useSessionContext` on hydrate (treats as deprecated)
- [ ] 9.3 Vitest: dedicated legacy-payload hydrate test (deferred — see Stage 6 test cleanup batch)

## 10. i18n cleanup

- [ ] 10.1 (Deferred — keep `stage.context.toolbar.useSession` copy; verify in walkthrough)
- [x] 10.2 Added `stage.queryEditor.boundSessionMissing` for the orphan toast
- [x] 10.3 zh-CN and en both populated for the new keys

## 11. Verification batch (run after tasks 1–10 land)

- [ ] 11.1 `cd client && npx tsc --noEmit` — zero type errors
- [ ] 11.2 `cd client && npx vitest run src/features/stage src/features/session` — full stage + session test surface green
- [ ] 11.3 Manual browser walkthrough (per CLAUDE.md "先浏览器自测再写文档"):
  - [ ] 11.3.1 Open AI editor in session A (chat ask AI to "run a query") → toggle ON, badge absent
  - [ ] 11.3.2 Switch to session B (no context) → toggle OFF, badge `来自会话: <A>`, run disabled if A had connection-less context, else still runnable against A (the badge clarifies why)
  - [ ] 11.3.3 Click toggle → bound updates to B; if B empty, shows `未设置`, run disabled
  - [ ] 11.3.4 Open user editor via toolbar "+" in session A → no toggle, selects populated from A snapshot
  - [ ] 11.3.5 Switch to session B → user editor unchanged, no badge, still runnable
  - [ ] 11.3.6 In AI editor, manually change connection → toggle OFF, override active; switch session → still OFF, override preserved
  - [ ] 11.3.7 Delete a session that has an open AI editor bound to it → toast appears once, editor falls back to active session context

## 12. Documentation sync

- [ ] 12.1 Update `docs/references/ui-objects-reference.md` if it documents `useSessionContext` — replace with `boundSessionId` and derived toggle semantics
- [ ] 12.2 Update `server/.../resources/agents/AGENTS.md` if it instructs the agent about `set_context.useSessionContext` semantics — clarify that on user editors it is a no-op
- [ ] 12.3 No update needed to `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` (N/A — see proposal Impact section)
