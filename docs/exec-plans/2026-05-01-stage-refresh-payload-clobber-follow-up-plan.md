# Stage Refresh Payload Clobber Follow-up Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent refresh-time metadata hydration from overwriting persisted stage-local payloads with placeholder `{}` content, which leaves artifact preview tabs stuck on `AI 正在准备…`.

**Architecture:** Keep the existing metadata-first stage hydration flow, but explicitly treat stage-tab payload placeholders as non-authoritative during `hydrating`. The bootstrap subscriber for `payloadSource='stage_tab'` must ignore metadata-only placeholders until real payload hydration or a local user action supplies authoritative content.

**Tech Stack:** React 19, Zustand, Vitest

## Design Inputs

- `client/DESIGN.md`
- Constraints applied:
  - Workbench restore must preserve continuity; refresh cannot silently degrade persisted tabs into empty shells.
  - Precision First: placeholder state can be rendered, but placeholder data must never be persisted as truth.

## Compatibility Gate

- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`: N/A. Client-only stage hydration/persistence follow-up; no datasource capability change.

---

### Task 1: Reproduce With A Failing Test

**Files:**
- Modify: `client/src/features/stage/persistence/__tests__/stage-persistence-bootstrap.stage-tab-payload.test.ts`

- [x] **Step 1: Add a failing test proving metadata hydrate for `artifact_preview` does not schedule a payload write while the payload is still the bootstrap placeholder**
- [x] **Step 2: Run the targeted test and confirm it fails for the expected reason**
  - `npm test -- --run src/features/stage/persistence/__tests__/stage-persistence-bootstrap.stage-tab-payload.test.ts` initially failed because `scheduleContentWrite("artifact_preview_1", { payload: {}, contentText: "", expectedVersion: 7 })` was invoked during `hydrating`.

### Task 2: Implement The Hydration Guard

**Files:**
- Modify: `client/src/features/stage/persistence/stage-persistence-bootstrap.ts`

- [x] **Step 1: Detect metadata-only placeholder payloads for `payloadSource='stage_tab'` tabs during `hydrating`**
- [x] **Step 2: Skip scheduling content writes for those placeholder payloads**
- [x] **Step 3: Keep live user-created payload writes intact**
  - Added a narrow guard in `stage-persistence-bootstrap.ts`: only `hydrating`-phase, `payloadVersion`-bearing, empty-object stage-local payload placeholders are skipped. Live tabs with authoritative payloads still persist normally.

### Task 3: Verify And Housekeep

**Files:**
- Modify: `docs/exec-plans/2026-05-01-stage-refresh-payload-clobber-follow-up-plan.md`
- Modify: `docs/exec-plans/index.md`

- [x] **Step 1: Run `cd client && npm test -- --run src/features/stage/persistence/__tests__/stage-persistence-bootstrap.stage-tab-payload.test.ts`**
- [x] **Step 2: Run `cd client && npx tsc --noEmit`**
- [x] **Step 3: Mark the plan completed with outcome notes**
- [x] **Step 4: Move the index entry from Active to Completed**

## Outcome

- Root cause confirmed and fixed: metadata-first hydration was treating `artifact_preview` placeholder payload `{}` as authoritative stage-local content and queuing a write-back, which could overwrite the real persisted payload before the user reopened the artifact tab.
- Regression coverage now includes the hydrate-placeholder case plus the existing live-open persistence case.
- Verification completed on 2026-05-01:
  - `cd client && npm test -- --run src/features/stage/persistence/__tests__/stage-persistence-bootstrap.stage-tab-payload.test.ts`
  - `cd client && npx tsc --noEmit`
