# Shared Stage Workbench · Phase 3.5 — Cleanup & Doc Housekeeping Implementation Plan

**Goal:** Close out residual P3 review findings — remove the now-zombie `StageTab.scope` field (final TD-030 closure), fix the `useActiveArtifactTitle('')` regression, sweep dead `*BySession` test fixtures, add tests for `seedWorkset` / `trashTab` rollback, and execute the post-execution document housekeeping mandated by `CLAUDE.md` for Phases 1–3.

**Architecture:** No runtime architecture change. `StageTab.scope` is verified zero-read in production logic — `WorkspaceAdapter` keeps semantic dispatch via `WORKSPACE_SCOPE_TYPES.has(p.type)` (param.type, not tab.scope). Removing the field is a pure data cleanup. The `useActiveArtifactTitle` fix re-routes the hook to read `useSessionStore.activeSessionId` rather than an empty string.

**Tech Stack:** React 19 · TypeScript · Zustand · vitest · Testing Library

**Design Inputs:** [client/DESIGN.md](../../client/DESIGN.md) — no visual changes; this is a state/test cleanup. Existing token contracts from P2 carry through unchanged.

---

## Why this plan exists

P3 (commit `0a6d21f`) shipped the `*BySession` collapse but left:

1. `StageTab.scope` field alive end-to-end (TD-030 second half, planned for "P2/P3" closure)
2. `useActiveArtifactTitle('')` placeholder in `stage-window.tsx:20` permanently breaks the artifact title indicator
3. `seedWorkset` behavior added without test coverage and not in P3 plan
4. `trashTab` rollback path untested
5. 21 dead `*BySession` references in two test fixture files (`as any`-masked)
6. **CLAUDE.md "Post-Execution Document Housekeeping" not executed for P2 or P3** (no checkboxes ticked, plans still in Active list, canonical docs not updated)

P3.5 closes all six.

---

## File Structure

### Files to modify

| Path | Change |
|---|---|
| `client/src/stores/stage-store.ts` | Remove `StageTab.scope` field + `QueryEditorOpenInput.scope`; drop `scope` writes in `openArtifactPreviewTab` and `openQueryEditor` |
| `client/src/features/stage/adapters/WorkspaceAdapter.ts` | Drop tab.scope writes; keep `WORKSPACE_SCOPE_TYPES.has(p.type)` for sid-required check |
| `client/src/features/stage/utils/open-or-focus-stage-tool-tab.ts` | Drop `scope:` from openQueryEditor call and tab literal |
| `client/src/features/stage/utils/open-or-focus-file-preview-tab.ts` | Drop `scope: 'session'` from tab literal |
| `client/src/features/stage/utils/open-direct-sql-query-editor-tab.ts` | Drop `scope: 'session'` from openQueryEditor call |
| `client/src/features/stage/components/stage-window.tsx` | Drop `scope: 'workspace'` from openQueryEditor call; fix `useActiveArtifactTitle('')` → read active session id |
| `client/src/features/stage/components/stage-tab-bar-add-button.tsx` | Drop `scope: 'workspace'` from openQueryEditor call |
| `client/src/features/chat/components/tools/renderers/execute-sql.tsx` | Drop `scope: 'session'` from openQueryEditor call |
| `client/src/features/stage/persistence/stage-persistence-bootstrap.ts` | Drop hardcoded `scope: 'workspace'` from `toStageTab` |
| `client/src/services/find/use-stage-find.ts` | Drop scope read/fallback in `toStageTab` |
| `client/src/features/stage/use-active-artifact-title.tsx` | Type signature already accepts `string \| null`; no change |
| `client/src/stores/stage-store.test.ts` | Add tests: `seedWorkset` (4 cases), `trashTab` rollback (1 case) |
| `client/src/features/chat/components/tools/__tests__/read-file.test.tsx` | Remove dead `*BySession` keys from `useStageStore.setState` fixture |
| `client/src/features/stage/utils/__tests__/open-direct-sql-query-editor-tab.test.ts` | Remove dead `*BySession` keys; remove `scope` from expected args |
| `client/src/features/stage/adapters/**/*.test.ts(x)` | Sweep `scope: 'session'/'workspace'` from tab fixtures |
| `client/src/features/stage/components/**/*.test.tsx` | Sweep `scope:` from tab fixtures |
| `client/src/features/stage/utils/**/*.test.ts` | Sweep `scope:` from tab fixtures and expected openQueryEditor args |
| `client/src/features/chat/components/tools/renderers/execute-sql.test.tsx` | Drop `scope: 'session'` from expected openQueryEditor args |
| `docs/exec-plans/2026-04-28-shared-stage-workbench-p2-frontend-layout-plan.md` | Tick all `[ ]` → `[x]` with status notes |
| `docs/exec-plans/2026-04-28-shared-stage-workbench-p3-state-globalization-plan.md` | Tick all `[ ]` → `[x]` with status notes |
| `docs/exec-plans/2026-04-28-shared-stage-workbench-p3.5-cleanup-plan.md` | This file; ticked at end |
| `docs/exec-plans/index.md` | Move P2/P3/P3.5 from Active to Completed |
| `docs/exec-plans/tech-debt-tracker.md` | Close TD-030; add deferral note for TD-029 |
| `CLAUDE.md` | Add Working Rule: stage UI state is global, not session-keyed |
| `client/DESIGN.md` | Note: stage state global; `StageTab.scope` removed; type-level scope from `tab-type-registry` only |

### No new files

Pure cleanup + test additions + doc edits.

---

## Tasks

### Task 1: Remove `StageTab.scope` from production code

- [x] **Step 1:** Drop `scope: 'session' | 'workspace'` from `StageTab` interface and `QueryEditorOpenInput` type in `stage-store.ts`. Drop `scope: input.scope` line in `openQueryEditor` body and `scope: 'session'` line in `openArtifactPreviewTab` body.
- [x] **Step 2:** Drop `scope: ...` writes and `const scope: StageTab['scope'] = ...` line in `WorkspaceAdapter.ts`. Replace `if (scope === 'session' && !sid)` with `if (!WORKSPACE_SCOPE_TYPES.has(p.type) && !sid)`.
- [x] **Step 3:** Drop `scope:` from openQueryEditor / tab literal in `open-or-focus-stage-tool-tab.ts`, `open-or-focus-file-preview-tab.ts`, `open-direct-sql-query-editor-tab.ts`, `stage-window.tsx`, `stage-tab-bar-add-button.tsx`, `execute-sql.tsx`.
- [x] **Step 4:** Drop `scope: 'workspace'` from `toStageTab` in `stage-persistence-bootstrap.ts` and `use-stage-find.ts`.
- [x] **Step 5:** Verify production grep returns 0 hits: `git grep -nE "StageTab\['scope'\]|scope:\s*['\"](session|workspace)" client/src/ -- ':!*.test.*' ':!*tab-type-registry*'`

### Task 2: Fix `useActiveArtifactTitle('')` regression

- [x] **Step 1:** In `stage-window.tsx:20`, replace `useActiveArtifactTitle('')` with `useActiveArtifactTitle(useSessionStore((s) => s.activeSessionId))`. The hook already handles `null` correctly.

### Task 3: Sweep dead `*BySession` keys from two test fixtures

- [x] **Step 1:** `client/src/features/chat/components/tools/__tests__/read-file.test.tsx` lines 21-32: drop all 10 dead `*BySession` keys; keep only `tabs: []` (and `as any` cast).
- [x] **Step 2:** `client/src/features/stage/utils/__tests__/open-direct-sql-query-editor-tab.test.ts` lines 35-45: drop all 10 dead `*BySession` keys; keep only `openQueryEditor` and `openStage` mocks.

### Task 4: Sweep `scope: 'session'/'workspace'` from all test fixtures

- [x] **Step 1:** Run `git grep -nE "scope:\s*'(session|workspace)'(\s+as\s+const)?" client/src/` to enumerate all hits.
- [x] **Step 2:** Mechanical removal in each test file. Keep `tab-type-registry.test.ts` `desc.scope` assertions (those are type-level, not StageTab.scope).
- [x] **Step 3:** Drop `scope: 'session'` / `scope: 'workspace'` from `expect(openQueryEditorMock).toHaveBeenCalledWith({ ... })` argument shapes.

### Task 5: Add `seedWorkset` / `__hydrateAll` behavior tests

- [x] **Step 1:** Add 4 cases to `stage-store.test.ts`:
  - non-archived hydrated tab is added to workset
  - archived hydrated tab is NOT added to workset
  - tab already in workset is not duplicated
  - existing workset entries stay before newly seeded tabs (tail append)

### Task 6: Add `trashTab` rollback test

- [x] **Step 1:** Add 1 case to `stage-store.test.ts` that mocks `coordinator.delete` to throw, asserts `openTabIds`/`openTabIdsOrdered`/`activeTabId` revert to pre-trash state and `tabs[]` is unchanged.

### Task 7: Run tsc + vitest

- [x] **Step 1:** `cd client && npx tsc --noEmit` — expect 0 new errors (only pre-existing `ts-morph` in `forbidden-direct-mutation.test.ts`).
- [x] **Step 2:** `cd client && npx vitest run` — expect all green except the pre-existing `ts-morph` suite-load failure.

### Task 8: Tick P2/P3/P3.5 plan checkboxes

- [x] **Step 1:** Mark every `[ ]` → `[x]` in P2 plan; add Status note for any deviation discovered during P2 review.
- [x] **Step 2:** Mark every `[ ]` → `[x]` in P3 plan; status notes for: `seedWorkset` added (post-plan), `trashTab` rollback added (post-plan), Task 10 verified no-op, `useActiveArtifactTitle('')` fixed in P3.5.
- [x] **Step 3:** Mark every `[ ]` → `[x]` in this P3.5 plan after execution.

### Task 9: Move P2/P3/P3.5 to Completed in index.md

- [x] **Step 1:** Edit `docs/exec-plans/index.md` — move P2, P3 entries from Active to Completed; add P3.5 directly to Completed.

### Task 10: Update tech-debt-tracker

- [x] **Step 1:** Move TD-030 to "已清除债务" — clearance note: "P3 收尾 (P3.5) 删除 `StageTab.scope` 字段及所有消费点；持久化与 hydrate 路径不再写入 scope；type-level scope 由 `tab-type-registry` 提供"
- [x] **Step 2:** Add deferral note on TD-029: "P3.5 未补 `StageTabConcurrencyIT`；登记到下一独立 plan，原因：P3 范围内无 backend 改动，concurrency IT 涉及 WireMock 双 session 编排，需独立工作量"

### Task 11: Propagate global stage convention to canonical docs

- [x] **Step 1:** `CLAUDE.md` — under "Key Conventions" or new "Frontend State" subsection, add one-line: `Stage UI state is global (not session-keyed); StageTab has no per-instance scope field; type-level scope is in tab-type-registry`.
- [x] **Step 2:** `client/DESIGN.md` — note in stage section that stage state is global and `StageTab.scope` removed.

---

## Self-Review

**Spec coverage**: P3.5 closes the six residuals from the P3 review punch list. No new architectural surface; pure cleanup + tests + docs. `WORKSPACE_SCOPE_TYPES` semantic gate in `WorkspaceAdapter.exec('open')` is preserved (driven by `p.type` payload, not the deleted `StageTab.scope` field), so the "session-scoped without active session" error path still fires correctly.

**Type consistency**: `StageTab` shape becomes one field shorter; all callers that wrote to it are touched in Task 1. `QueryEditorOpenInput.scope` removal cascades through every caller in Task 1 Step 3.

**Placeholder scan**: No `TBD` / `TODO`. The `useActiveArtifactTitle` hook signature already supports `string | null`; the fix is a single-site call-site change (Task 2).
