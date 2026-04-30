# Stage Refresh Blank Page Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the workbench refresh regression so artifact preview tabs and the previously active stage tab restore with content instead of showing a blank page.

**Architecture:** Keep the current Stage metadata-first hydration flow, but close the gap for tabs whose content lives directly on `StageTab.payload`. Persist that payload when those tabs change, and lazily hydrate payloads on mount for stage surfaces that currently render before payload recovery completes. Use loading placeholders instead of `null` so the workbench never falls through to a visually blank content area.

**Tech Stack:** React 19, Zustand, TanStack Query, Vitest, Testing Library

## Design Inputs

- `client/DESIGN.md`
- Constraints applied:
  - Chat and Workbench are peer surfaces in one system, so refresh recovery must preserve continuity between the chat artifact and its workbench view.
  - Precision First: recovery failures should degrade into explicit loading/empty states, not visually blank panes.
  - `stage` semantics require stable active-tab signaling and canvas continuity after reload.

## Compatibility Gate

- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`: N/A. This fix only changes client-side stage persistence/hydration behavior and does not alter database kind support, JDBC handling, SQL dialect logic, diagnostics capability, or prompts.

---

### Task 1: Lock The Failure With Tests

**Files:**
- Modify: `client/src/features/stage/components/stage-tab-content.test.tsx`
- Modify: `client/src/features/stage/persistence/__tests__/stage-persistence-bootstrap.query-editor.test.ts`
- Create or Modify: `client/src/features/stage/components/artifact-preview-tab.test.tsx`

- [x] **Step 1: Add a failing test proving artifact preview tabs do not render blank while waiting for refresh hydration**
- [x] **Step 2: Add a failing test proving artifact preview payload changes are scheduled for persistence**
- [x] **Step 3: Add a failing test proving query-editor tabs request hydration on refresh**
- [x] **Step 4: Run the targeted tests and confirm they fail for the expected reasons**

### Task 2: Persist Stage-Local Payload Tabs

**Files:**
- Modify: `client/src/features/stage/persistence/stage-persistence-bootstrap.ts`
- Modify: `client/src/features/stage/registry/tab-type-registry.ts`

- [x] **Step 1: Teach stage persistence bootstrap to diff and persist payload/content for persistent tabs whose source of truth is `StageTab.payload`**
- [x] **Step 2: Ensure artifact preview content serialization preserves the `artifactId/sessionId` payload needed for refresh restore**
- [x] **Step 3: Keep query-editor and ER subscriptions untouched except where needed to avoid duplicate writes**
- [x] **Step 4: Re-run the relevant persistence tests**

### Task 3: Hydrate Visible Tabs On Demand

**Files:**
- Modify: `client/src/features/stage/components/artifact-preview-tab.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Modify: `client/src/features/stage/components/stage-tab-content.tsx`

- [x] **Step 1: Request payload hydration when artifact preview tabs mount**
- [x] **Step 2: Request payload hydration when query editor tabs mount on refresh**
- [x] **Step 3: Render explicit loading placeholders instead of returning `null` while payload is still unavailable**
- [x] **Step 4: Re-run the component tests**

### Task 4: Verify And Housekeep

**Files:**
- Modify: `docs/exec-plans/2026-05-01-stage-refresh-blank-page-fix-plan.md`
- Modify: `docs/exec-plans/index.md`

- [x] **Step 1: Run `cd client && npm test -- --run <targeted files>`**
- [x] **Step 2: Run `cd client && npx tsc --noEmit`**
- [x] **Step 3: Mark this plan completed with notes about final scope**
- [x] **Step 4: Move the index entry from Active to Completed**

## Completion Notes

- Fixed the root cause for artifact preview refresh blank pages by persisting stage-local payload tabs and by hydrating `artifact_preview` payloads on mount when only metadata was restored.
- Fixed refresh recovery for query editor tabs by hydrating persisted payloads on mount and by allowing query-editor rehydrate to populate already-mounted pristine tabs.
- Prevented blank content panes during payload fetch by rendering the existing `artifact.preparing` loading state instead of returning `null`.
- Deviation from the initial file list: no `stage-tab-content.tsx` change was needed once the non-blank loading state moved into `artifact-preview-tab.tsx`.
- Verification completed:
  - `cd client && npm test -- --run src/features/stage/components/artifact-preview-tab.test.tsx src/features/stage/persistence/__tests__/stage-persistence-bootstrap.stage-tab-payload.test.ts src/features/stage/registry/__tests__/tab-type-registry.test.ts src/features/stage/components/sql-workbench-tab.test.tsx`
  - `cd client && npx tsc --noEmit`
