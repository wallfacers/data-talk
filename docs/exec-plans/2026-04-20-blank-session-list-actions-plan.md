# Blank Session List Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent blank sessions in the sidebar session list from exposing rename/delete operations while keeping them selectable as fallback placeholders.

**Architecture:** Keep the change frontend-only and local to `NavSessions`. Treat `hasEverSent === false` as a UI capability flag: blank sessions render only the session button, while normal sessions keep the existing dropdown actions. Preserve the current delete-active-session fallback path.

**Tech Stack:** React 19, TanStack Query, Zustand, shadcn/ui, Vitest, Testing Library

---

### Task 1: Add regression tests for blank session action visibility

**Files:**
- Modify: `client/src/features/workspace/components/__tests__/nav-sessions.test.tsx`
- Test: `client/src/features/workspace/components/__tests__/nav-sessions.test.tsx`

- [x] **Step 1: Write the failing test**

Add tests that render one blank session and one normal session, then assert:

```tsx
expect(screen.queryByRole('button', { name: '更多' })).not.toBeInTheDocument()
expect(screen.getByText('新会话')).toBeInTheDocument()
```

and:

```tsx
expect(screen.getAllByRole('button', { name: '更多' })).toHaveLength(1)
fireEvent.click(screen.getByRole('button', { name: '更多' }))
expect(await screen.findByText('删除')).toBeInTheDocument()
```

- [x] **Step 2: Run test to verify it fails**

Run: `cd client && npx vitest run src/features/workspace/components/__tests__/nav-sessions.test.tsx`

Expected: FAIL because blank sessions still render the dropdown trigger today.

Status: Done. `npx vitest run src/features/workspace/components/__tests__/nav-sessions.test.tsx` failed with 2 expected assertions: blank session still showed the “更多” button, and mixed normal+blank sessions rendered 2 triggers instead of 1.

- [x] **Step 3: Commit**

```bash
git add client/src/features/workspace/components/__tests__/nav-sessions.test.tsx
git commit -m "test: cover blank session sidebar actions"
```

Status: Skipped. Changes were intentionally left uncommitted in the shared worktree.

### Task 2: Hide blank session management actions in NavSessions

**Files:**
- Modify: `client/src/features/workspace/components/nav-sessions.tsx`
- Test: `client/src/features/workspace/components/__tests__/nav-sessions.test.tsx`

- [x] **Step 1: Write minimal implementation**

Introduce a small local predicate and branch the row rendering:

```tsx
const isBlankSession = !s.hasEverSent
```

Only render `DropdownMenu` when `!isBlankSession`, and guard the existing rename/delete callbacks with the same condition before mutating UI state.

- [x] **Step 2: Run targeted test to verify it passes**

Run: `cd client && npx vitest run src/features/workspace/components/__tests__/nav-sessions.test.tsx`

Expected: PASS

Status: Done. Targeted test file passed with 5/5 tests green.

- [x] **Step 3: Run frontend verification**

Run: `cd client && npx tsc --noEmit`

Expected: zero type errors

Status: Done. `npx tsc --noEmit` exited successfully with zero output.

- [x] **Step 4: Commit**

```bash
git add client/src/features/workspace/components/nav-sessions.tsx client/src/features/workspace/components/__tests__/nav-sessions.test.tsx
git commit -m "fix: hide blank session management actions"
```

Status: Skipped. Changes were intentionally left uncommitted in the shared worktree.

### Task 3: Document completion housekeeping

**Files:**
- Modify: `docs/exec-plans/2026-04-20-blank-session-list-actions-plan.md`
- Modify: `docs/exec-plans/index.md`

- [x] **Step 1: Mark the plan checklist complete**

Update this plan so every executed step is checked off and note that verification used:

```text
cd client && npx vitest run src/features/workspace/components/__tests__/nav-sessions.test.tsx
cd client && npx tsc --noEmit
```

- [x] **Step 2: Move the plan index entry from Active to Completed**

Record the completion date and a one-line summary of the blank-session action restriction.

Status: Done. Entry moved to Completed in `docs/exec-plans/index.md`.
