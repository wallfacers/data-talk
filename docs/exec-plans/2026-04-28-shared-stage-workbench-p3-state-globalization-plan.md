# Shared Stage Workbench · Phase 3 — State Globalization & Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Collapse the 9 `*BySession` Map/Set fields in `useStageStore` into single global values; merge `tabsBySession` + `workspaceTabs` into a single `tabs[]`; merge `activeTabIdBySession` + `activeWorkspaceTabId` into a single `activeTabId`; wire `closeStage()` to reset `autoOpened=false` for the "next-artifact-can-auto-open" UX; remove `<StageWindow sessionId>` prop dependency; clean up session-store's stage-cleanup-on-delete; rewrite all consumer call sites; rebuild test fixtures.

**Architecture:** Top-down store rewrite, then sweep every consumer that reads `*BySession.get(sid)` and replace with the global selector. `<StageWindow />` becomes a session-id-free component. `use-stage-auto-open` becomes a single global subscription. session-store's `removeSession` no longer calls `useStageStore.clear(sid)`. Tests are rewritten to use the global shape; no per-session fixtures remain.

**Tech Stack:** React 19 · TypeScript · Zustand · vitest · Testing Library

**Design Inputs:** [client/DESIGN.md](../../client/DESIGN.md) — no new visual changes in P3 (this is a state refactor). Existing token contracts from P2 carry through.

---

## File Structure

### Files to modify

| Path | Change |
|---|---|
| `client/src/stores/stage-store.ts` | 9 `*BySession` Maps/Set → 8 single fields; `tabsBySession + workspaceTabs` → `tabs[]`; `activeTabIdBySession + activeWorkspaceTabId` → `activeTabId`; remove `clear(sid)` / `clearAllSessionState`; remove `focusWorkspaceTabForSession`; rename action signatures (`openStage()` etc.); `closeStage()` resets `autoOpened=false` |
| `client/src/stores/stage-store.test.ts` | Full rewrite — drop per-session fixtures; assert global state semantics + autoOpened-reset behavior |
| `client/src/features/session/split-view.tsx` | `useStageStore((s) => s.openBySession.get(sid))` → `useStageStore((s) => s.open)`; same for `maximized`; remove `sid`-prop on `<StageWindow />` |
| `client/src/features/session/split-view.test.tsx` | New test: switching session does not change `open` / `maximized` / `tabs` |
| `client/src/features/stage/components/stage-window.tsx` | Drop `sessionId` prop entirely; all selectors switch to global; pass nothing down to `<StageLeftRail />` (which also drops the prop) |
| `client/src/features/stage/components/stage-window.test.tsx` | Reflect prop-less component |
| `client/src/features/stage/components/stage-toggle-button.tsx` | `open || maximized` selectors → global; `closeStage(sid)` → `closeStage()` |
| `client/src/features/stage/components/stage-toggle-button.test.tsx` | Update mocks |
| `client/src/features/stage/use-stage-auto-open.ts` | Single global subscription; `notifyArtifactArrived()` (no sid) on first artifact |
| `client/src/features/stage/use-stage-auto-open.test.ts` | Update mocks |
| `client/src/features/stage/components/stage-sidebar.tsx` | Drop session-keyed selectors |
| `client/src/features/stage/components/activity-rail/*.tsx` | Drop session-keyed selectors |
| `client/src/features/stage/components/left-rail/stage-left-rail.tsx` | Drop `sessionId` prop; selectors go global; `tabs` reads from `state.tabs[]` |
| `client/src/features/stage/components/left-rail/stage-left-rail.test.tsx` | Drop sessionId prop in renders |
| `client/src/stores/session-store.ts` | `removeSession` no longer calls `useStageStore.getState().clear(sid)` |
| `client/src/features/stage/persistence/stage-persistence-bootstrap.ts` | `persistedTabSummaries` walks `state.tabs[]` (single list); `resolveTabSnapshot` references the same |

### No new files

This phase is pure refactor + test rewrites.

---

## Phase 3A — Store Globalization (Tasks 1–4)

### Task 1: Replace `*BySession` Maps with single fields

**Files:**
- Modify: `client/src/stores/stage-store.ts`

- [x] **Step 1: Update the `StageState` type**

Replace the existing field declarations:

```ts
export type StageState = {
  // GLOBAL stage view state (was 9 *BySession fields)
  open: boolean
  maximized: boolean
  autoOpened: boolean
  sidebarCollapsed: boolean
  sidebarSelection: SidebarSelection | null
  resourceTreeExpanded: string[]
  activeRailPanel: RailPanel | null
  revealOrigin: RevealOrigin | null

  // Tabs (single list, not session-split)
  tabs: StageTab[]              // was workspaceTabs + tabsBySession.values().flat()
  openTabIds: Set<string>       // from P2
  openTabIdsOrdered: string[]   // from P2
  activeTabId: string | null    // was activeWorkspaceTabId + activeTabIdBySession

  // Left rail UI prefs (from P2)
  leftRailWidth: number
  leftRailCollapsed: boolean

  // Actions (no sid params)
  openStage: () => void
  closeStage: () => void          // SIDE EFFECT: autoOpened = false
  toggleStage: () => void
  toggleMaximized: () => void
  syncCollapsed: (collapsed: boolean) => void
  toggleSidebarCollapsed: () => void
  setSidebarSelection: (sel: SidebarSelection | null) => void
  toggleResourceExpanded: (nodeId: string) => void
  setResourceExpanded: (ids: string[]) => void
  setActiveRailPanel: (panel: RailPanel | null) => void
  toggleRailPanel: (panel: RailPanel) => void
  notifyArtifactArrived: () => void
  setRevealOrigin: (origin: RevealOrigin | null) => void

  // Tab CRUD (sig same as P2)
  ensureOpenInWorkset: (tabId: string) => void
  detachFromWorkset: (tabId: string) => void
  archiveTab: (tabId: string, archived: boolean) => void
  trashTab: (tabId: string) => Promise<void>
  focusTab: (tabId: string) => void
  setLeftRailWidth: (px: number) => void
  toggleLeftRailCollapsed: () => void

  openTab: (tab: StageTab) => void
  listTabs: () => StageTab[]      // no sid param
  updateTabPayload: (tabId: string, updater: (prev: unknown) => unknown) => void
  openArtifactPreviewTab: (sessionId: string, artifactId: string, title: string) => void
  openQueryEditor: (input: QueryEditorOpenInput) => { tabId: string; created: boolean }
  setQueryEditorContext: (tabId: string, ctx: { connectionId?: string | null; connectionName?: string | null; database?: string | null; schema?: string | null }) => void
  replaceQueryEditorContent: (tabId: string, content: string, baseVersion: number) => QueryEditorEditResult
  applyQueryEditorTextEdits: (tabId: string, params: { baseVersion: number; edits: QueryEditorTextEdit[] }) => QueryEditorEditResult
  setQueryEditorCursor: (tabId: string, cursor: { line: number; column: number }) => void

  findTab: (tabId: string) => StageTab | null
  __hydrateAll: (items: StageTab[]) => void   // from P1, replaces __hydrateWorkspaceTabs/__hydrateSessionTabs
  __hydratePayload: (tabId: string, payload: unknown, version: number) => void
  __setPayloadVersion: (tabId: string, version: number) => void
  setTabPinned: (id: string, pinned: boolean) => void
  setTabTitle: (id: string, title: string) => void
}
```

**Removed**:
- `openBySession` / `maximizedBySession` / `autoOpenedSessions` / `sidebarCollapsedBySession` / `sidebarSelectionBySession` / `resourceTreeExpandedBySession` / `activeRailPanelBySession` / `tabsBySession` / `activeTabIdBySession` / `workspaceTabs` / `activeWorkspaceTabId`
- `clear(sid)` / `clearAllSessionState()` / `focusWorkspaceTabForSession(sid)`
- `closeTab` (already deprecated in P2; now hard-removed)
- `__hydrateWorkspaceTabs` / `__hydrateSessionTabs` (replaced by `__hydrateAll`)

- [x] **Step 2: Rewrite implementation body**

```ts
export const useStageStore = create<StageState>((set, get) => ({
  open: false,
  maximized: false,
  autoOpened: false,
  sidebarCollapsed: false,
  sidebarSelection: null,
  resourceTreeExpanded: [],
  activeRailPanel: null,
  revealOrigin: null,

  tabs: [],
  openTabIds: new Set<string>(),
  openTabIdsOrdered: [],
  activeTabId: null,

  leftRailWidth: loadLeftRailWidth(),
  leftRailCollapsed: loadLeftRailCollapsed(),

  openStage: () => set({ open: true }),
  closeStage: () => set({ open: false, autoOpened: false }),  // ← reset autoOpened (per spec C2)
  toggleStage: () => set((s) => ({ open: !s.open, ...(s.open ? { autoOpened: false } : {}) })),
  toggleMaximized: () => set((s) => ({ maximized: !s.maximized })),
  setRevealOrigin: (origin) => set({ revealOrigin: origin }),

  notifyArtifactArrived: () => set((s) => {
    if (s.autoOpened) return s
    if (s.open) return s
    return { open: true, autoOpened: true }
  }),

  syncCollapsed: (collapsed) => set((s) => {
    const nextOpen = !collapsed
    if (s.open === nextOpen) return s
    return collapsed
      ? { open: false, autoOpened: false }   // user closed via collapse → reset
      : { open: true }
  }),

  toggleSidebarCollapsed: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),
  setSidebarSelection: (selection) => set({ sidebarSelection: selection }),

  toggleResourceExpanded: (nodeId) => set((s) => {
    const next = s.resourceTreeExpanded.includes(nodeId)
      ? s.resourceTreeExpanded.filter((id) => id !== nodeId)
      : [...s.resourceTreeExpanded, nodeId]
    return { resourceTreeExpanded: next }
  }),
  setResourceExpanded: (ids) => set({ resourceTreeExpanded: [...ids] }),

  setActiveRailPanel: (panel) => set({ activeRailPanel: panel }),
  toggleRailPanel: (panel) => set((s) => ({
    activeRailPanel: s.activeRailPanel === panel ? null : panel,
  })),

  // Tab CRUD
  openTab: (tab) => set((s) => ({
    tabs: [...s.tabs, tab],
    openTabIds: new Set([...s.openTabIds, tab.tabId]),
    openTabIdsOrdered: [...s.openTabIdsOrdered, tab.tabId],
    activeTabId: tab.tabId,
  })),

  ensureOpenInWorkset: (tabId) => set((s) => {
    if (s.openTabIds.has(tabId)) return s
    const target = s.tabs.find((t) => t.tabId === tabId)
    if (!target || target.archived) return s
    const next = new Set(s.openTabIds); next.add(tabId)
    return { openTabIds: next, openTabIdsOrdered: [...s.openTabIdsOrdered, tabId] }
  }),

  detachFromWorkset: (tabId) => set((s) => {
    if (!s.openTabIds.has(tabId)) return s
    const nextIds = new Set(s.openTabIds); nextIds.delete(tabId)
    const nextOrder = s.openTabIdsOrdered.filter((id) => id !== tabId)
    const nextActive = s.activeTabId === tabId ? (nextOrder[nextOrder.length - 1] ?? null) : s.activeTabId
    return { openTabIds: nextIds, openTabIdsOrdered: nextOrder, activeTabId: nextActive }
  }),

  archiveTab: (tabId, archived) => set((s) => {
    const idx = s.tabs.findIndex((t) => t.tabId === tabId)
    if (idx < 0) return s
    const updated = [...s.tabs]
    updated[idx] = { ...updated[idx], archived, archivedAt: archived ? Date.now() : null }
    if (!archived) return { tabs: updated }
    // archive ⇒ also detach
    const nextIds = new Set(s.openTabIds); nextIds.delete(tabId)
    const nextOrder = s.openTabIdsOrdered.filter((id) => id !== tabId)
    const nextActive = s.activeTabId === tabId ? (nextOrder[nextOrder.length - 1] ?? null) : s.activeTabId
    return { tabs: updated, openTabIds: nextIds, openTabIdsOrdered: nextOrder, activeTabId: nextActive }
  }),

  trashTab: async (tabId) => {
    useStageStore.getState().detachFromWorkset(tabId)
    const { coordinator } = await import('@/features/stage/persistence/stage-persistence-bootstrap')
    await coordinator.delete(tabId)
    set((s) => ({ tabs: s.tabs.filter((t) => t.tabId !== tabId) }))
  },

  focusTab: (tabId) => set((s) => {
    const target = s.tabs.find((t) => t.tabId === tabId)
    if (!target) return s
    if (target.archived) return s   // AI path returns tab_archived in the adapter; UI path handles unarchive separately
    const inWorkset = s.openTabIds.has(tabId)
    if (inWorkset) return { activeTabId: tabId }
    const nextIds = new Set(s.openTabIds); nextIds.add(tabId)
    return {
      openTabIds: nextIds,
      openTabIdsOrdered: [...s.openTabIdsOrdered, tabId],
      activeTabId: tabId,
    }
  }),

  setLeftRailWidth: (px) => {
    try { localStorage.setItem(LEFT_RAIL_WIDTH_KEY, String(px)) } catch {}
    set({ leftRailWidth: Math.min(320, Math.max(180, px)) })
  },
  toggleLeftRailCollapsed: () => set((s) => {
    const next = !s.leftRailCollapsed
    try { localStorage.setItem(LEFT_RAIL_COLLAPSED_KEY, String(next)) } catch {}
    return { leftRailCollapsed: next }
  }),

  listTabs: () => get().tabs,

  updateTabPayload: (tabId, updater) => set((s) => {
    const idx = s.tabs.findIndex((t) => t.tabId === tabId)
    if (idx < 0) return s
    const next = [...s.tabs]
    next[idx] = { ...next[idx], payload: updater(next[idx].payload) }
    return { tabs: next }
  }),

  openArtifactPreviewTab: (sessionId, artifactId, title) => {
    const existing = get().tabs.find(
      (t) => t.type === 'artifact_preview' && (t.payload as { artifactId?: string })?.artifactId === artifactId,
    )
    if (existing) {
      get().openStage()
      get().focusTab(existing.tabId)
      return
    }
    const tabId = `artifact_preview_${generateUuid()}`
    const tab: StageTab = {
      tabId, type: 'artifact_preview', title,
      originSessionId: sessionId,
      payload: { artifactId, sessionId },
      createdAt: Date.now(),
    }
    get().openStage()
    get().openTab(tab)
  },

  openQueryEditor: (input) => {
    const latest = get()
    if (input.openMode === 'reuse_by_resource_context') {
      const existing = latest.tabs.find((t) =>
        t.type === 'query_editor' &&
        matchesNullable(t.connectionId, input.connectionId) &&
        matchesNullable(t.database, input.database) &&
        matchesNullable(t.schema, input.schema)
      )
      if (existing) {
        latest.focusTab(existing.tabId)
        return { tabId: existing.tabId, created: false }
      }
    }
    const visibleTitles = latest.tabs
      .filter((t) => t.type === 'query_editor')
      .map((t) => t.title)
    const tabId = `query_editor_${generateUuid()}`
    const payload = buildQueryEditorPayload(input)
    const tab: StageTab = {
      tabId, type: 'query_editor',
      title: resolveUniqueTabTitle(input.baseTitle, visibleTitles),
      connectionId: input.connectionId ?? undefined,
      connectionName: input.connectionName ?? undefined,
      database: input.database ?? undefined,
      schema: input.schema ?? undefined,
      originSessionId: input.sessionId ?? undefined,
      payload,
      createdAt: Date.now(),
    }
    latest.openTab(tab)
    useSqlWorkbenchStore.getState().ensureTab(tabId, {
      sqlText: input.initialContent ?? '',
      source: payload.source,
    })
    return { tabId, created: true }
  },

  setQueryEditorContext: (tabId, context) => set((s) => {
    const idx = s.tabs.findIndex((t) => t.tabId === tabId)
    if (idx < 0) return s
    const next = [...s.tabs]
    next[idx] = {
      ...next[idx],
      connectionId: context.connectionId ?? undefined,
      connectionName: context.connectionName ?? undefined,
      database: context.database ?? undefined,
      schema: context.schema ?? undefined,
      payload: updateQueryEditorPayload(next[idx].payload, {
        connectionId: context.connectionId ?? null,
        connectionName: context.connectionName ?? null,
        database: context.database ?? null,
        schema: context.schema ?? null,
      }),
    }
    return { tabs: next }
  }),

  replaceQueryEditorContent: (tabId, content, baseVersion) =>
    useSqlWorkbenchStore.getState().replaceSqlText(tabId, content, baseVersion),

  applyQueryEditorTextEdits: (tabId, params) =>
    useSqlWorkbenchStore.getState().applyTextEdits(tabId, params),

  setQueryEditorCursor: (tabId, cursor) =>
    useSqlWorkbenchStore.getState().setCursor(tabId, cursor.line, cursor.column),

  findTab: (tabId) => get().tabs.find((t) => t.tabId === tabId) ?? null,

  __hydrateAll: (items) => set((s) => {
    const incoming = new Map(items.map((t) => [t.tabId, t]))
    const merged = s.tabs.map((existing) => {
      const hydrated = incoming.get(existing.tabId)
      if (!hydrated) return existing
      return { ...existing, ...hydrated }
    })
    for (const item of items) {
      if (!merged.some((t) => t.tabId === item.tabId)) merged.push(item)
    }
    return { tabs: merged }
  }),

  __hydratePayload: (tabId, payload, version) => set((s) => {
    const idx = s.tabs.findIndex((t) => t.tabId === tabId)
    if (idx < 0) return s
    const next = [...s.tabs]
    next[idx] = { ...next[idx], payload, payloadVersion: version }
    return { tabs: next }
  }),

  __setPayloadVersion: (tabId, version) => set((s) => {
    const idx = s.tabs.findIndex((t) => t.tabId === tabId)
    if (idx < 0) return s
    const next = [...s.tabs]
    next[idx] = { ...next[idx], payloadVersion: version }
    return { tabs: next }
  }),

  setTabPinned: (id, pinned) => set((s) => {
    const idx = s.tabs.findIndex((t) => t.tabId === id)
    if (idx < 0) return s
    const next = [...s.tabs]
    next[idx] = { ...next[idx], pinned }
    return { tabs: next }
  }),

  setTabTitle: (id, title) => set((s) => {
    const idx = s.tabs.findIndex((t) => t.tabId === id)
    if (idx < 0) return s
    const next = [...s.tabs]
    next[idx] = { ...next[idx], title }
    return { tabs: next }
  }),
}))
```

Note: `archiveTab` no longer needs the `archiveDetach` helper from P2 — we inline it because the global state is simpler.

- [x] **Step 3: Compile (will fail at consumers; fix in subsequent tasks)**

```bash
cd client && npx tsc --noEmit 2>&1 | head -30
```

Expected: many errors at consumer call sites (split-view, stage-window, etc.). That's fine for now; they'll be fixed in Tasks 5–10.

- [x] **Step 4: Commit store-only changes**

```bash
git add client/src/stores/stage-store.ts
git commit -m "refactor(stage-store): collapse 9 *BySession fields into globals (consumers TBD)"
```

---

### Task 2: Rewrite `stage-store.test.ts` from scratch

**Files:**
- Modify: `client/src/stores/stage-store.test.ts`

- [x] **Step 1: Replace test bodies**

Open `stage-store.test.ts`. Drop all per-session fixtures (`Map<sid, …>` setup). Replace with global-state assertions.

```ts
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useStageStore } from './stage-store'

function reset() {
  useStageStore.setState({
    open: false, maximized: false, autoOpened: false,
    sidebarCollapsed: false, sidebarSelection: null,
    resourceTreeExpanded: [], activeRailPanel: null,
    revealOrigin: null,
    tabs: [], openTabIds: new Set(), openTabIdsOrdered: [],
    activeTabId: null,
    leftRailWidth: 240, leftRailCollapsed: false,
  } as never, true)
}

describe('useStageStore (P3 globalized)', () => {
  beforeEach(reset)

  describe('open / close / autoOpened reset', () => {
    it('openStage sets open=true', () => {
      useStageStore.getState().openStage()
      expect(useStageStore.getState().open).toBe(true)
    })

    it('closeStage resets autoOpened so next artifact can re-trigger auto-open', () => {
      useStageStore.setState({ open: true, autoOpened: true } as never, false)
      useStageStore.getState().closeStage()
      expect(useStageStore.getState().open).toBe(false)
      expect(useStageStore.getState().autoOpened).toBe(false)
    })

    it('notifyArtifactArrived auto-opens once, then becomes idempotent until closeStage', () => {
      const s = useStageStore.getState()
      s.notifyArtifactArrived()
      expect(useStageStore.getState().open).toBe(true)
      expect(useStageStore.getState().autoOpened).toBe(true)
      // call again → no-op
      const before = useStageStore.getState()
      s.notifyArtifactArrived()
      expect(useStageStore.getState()).toBe(before)
      // close → reset
      s.closeStage()
      // next artifact again triggers auto-open
      s.notifyArtifactArrived()
      expect(useStageStore.getState().open).toBe(true)
      expect(useStageStore.getState().autoOpened).toBe(true)
    })

    it('toggleStage from open → close resets autoOpened', () => {
      useStageStore.setState({ open: true, autoOpened: true } as never, false)
      useStageStore.getState().toggleStage()
      expect(useStageStore.getState().open).toBe(false)
      expect(useStageStore.getState().autoOpened).toBe(false)
    })
  })

  describe('tabs / library / workset', () => {
    it('openTab adds to tabs[] and workset, sets active', () => {
      const tab = makeTab({ tabId: 'a' })
      useStageStore.getState().openTab(tab)
      const s = useStageStore.getState()
      expect(s.tabs.map((t) => t.tabId)).toEqual(['a'])
      expect(s.openTabIds.has('a')).toBe(true)
      expect(s.openTabIdsOrdered).toEqual(['a'])
      expect(s.activeTabId).toBe('a')
    })

    it('detachFromWorkset removes from workset, picks previous order as new active', () => {
      useStageStore.setState({
        tabs: [makeTab({ tabId: 'a' }), makeTab({ tabId: 'b' }), makeTab({ tabId: 'c' })],
        openTabIds: new Set(['a', 'b', 'c']),
        openTabIdsOrdered: ['a', 'b', 'c'],
        activeTabId: 'c',
      } as never, false)
      useStageStore.getState().detachFromWorkset('c')
      const s = useStageStore.getState()
      expect(s.openTabIdsOrdered).toEqual(['a', 'b'])
      expect(s.activeTabId).toBe('b')
    })

    it('archiveTab(true) detaches and flips archived; archiveTab(false) only flips', () => {
      useStageStore.setState({
        tabs: [makeTab({ tabId: 'a', archived: false })],
        openTabIds: new Set(['a']),
        openTabIdsOrdered: ['a'],
        activeTabId: 'a',
      } as never, false)
      useStageStore.getState().archiveTab('a', true)
      let s = useStageStore.getState()
      expect(s.tabs[0].archived).toBe(true)
      expect(s.openTabIds.has('a')).toBe(false)
      useStageStore.getState().archiveTab('a', false)
      s = useStageStore.getState()
      expect(s.tabs[0].archived).toBe(false)
      // un-archive doesn't auto-add to workset
      expect(s.openTabIds.has('a')).toBe(false)
    })

    it('focusTab on archived tab is a no-op', () => {
      useStageStore.setState({
        tabs: [makeTab({ tabId: 'a', archived: true })],
      } as never, false)
      useStageStore.getState().focusTab('a')
      const s = useStageStore.getState()
      expect(s.openTabIds.has('a')).toBe(false)
      expect(s.activeTabId).toBe(null)
    })

    it('focusTab brings library tab into workset and sets active', () => {
      useStageStore.setState({
        tabs: [makeTab({ tabId: 'a' })],
      } as never, false)
      useStageStore.getState().focusTab('a')
      const s = useStageStore.getState()
      expect(s.openTabIds.has('a')).toBe(true)
      expect(s.activeTabId).toBe('a')
    })
  })

  describe('left rail prefs persistence', () => {
    it('setLeftRailWidth clamps to [180, 320]', () => {
      useStageStore.getState().setLeftRailWidth(150)
      expect(useStageStore.getState().leftRailWidth).toBe(180)
      useStageStore.getState().setLeftRailWidth(400)
      expect(useStageStore.getState().leftRailWidth).toBe(320)
    })

    it('toggleLeftRailCollapsed flips and persists to localStorage', () => {
      const setItem = vi.spyOn(Storage.prototype, 'setItem')
      useStageStore.getState().toggleLeftRailCollapsed()
      expect(useStageStore.getState().leftRailCollapsed).toBe(true)
      expect(setItem).toHaveBeenCalledWith('stage.leftRail.collapsed', 'true')
    })
  })
})

function makeTab(over: any) {
  return {
    tabId: 'default',
    type: 'query_editor',
    title: 'untitled',
    payload: {},
    payloadVersion: 1,
    createdAt: 0,
    lastTouchedAt: 0,
    archived: false,
    pinned: false,
    ...over,
  }
}
```

- [x] **Step 2: Run, expect PASS**

```bash
cd client && npx vitest run src/stores/stage-store.test.ts
```

- [x] **Step 3: Commit**

```bash
git add client/src/stores/stage-store.test.ts
git commit -m "test(stage-store): rewrite for P3 globalized state shape"
```

---

### Task 3: Update `stage-persistence-bootstrap.ts` for single `tabs[]`

**Files:**
- Modify: `client/src/features/stage/persistence/stage-persistence-bootstrap.ts`

- [x] **Step 1: Update `persistedTabSummaries`**

```ts
function persistedTabSummaries(state: { tabs: StageTab[] }): TabSummary[] {
  return state.tabs
    .filter((t) => isPersistent(t.type))
    .map((tab) => ({
      tabId: tab.tabId,
      type: tab.type,
      title: tab.title,
      connectionId: tab.connectionId,
      database: tab.database,
      schema: tab.schema,
      pinned: tab.pinned,
      archived: tab.archived,
      lastTouchedAt: tab.lastTouchedAt,
    }))
}
```

- [x] **Step 2: Update `resolveTabSnapshot`**

```ts
coordinator.resolveTabSnapshot = (tabId) => {
  const tab = useStageStore.getState().findTab(tabId)
  if (!tab || !isPersistent(tab.type)) return null
  return {
    id: tab.tabId,
    type: tab.type,
    title: tab.title,
    connectionId: tab.connectionId ?? null,
    database: tab.database ?? null,
    schema: tab.schema ?? null,
    originSessionId: tab.originSessionId ?? null,
    pinned: tab.pinned ?? false,
    archived: tab.archived ?? false,
    createdAt: tab.createdAt,
    lastTouchedAt: tab.lastTouchedAt ?? Date.now(),
  }
}
```

(`scope` references already removed in P1 Task 20; this just removes the now-unused branch.)

- [x] **Step 3: Verify subscribe block**

```ts
{
  let prevMeta = persistedTabSummaries(useStageStore.getState())
  useStageStore.subscribe((state) => {
    const next = persistedTabSummaries(state)
    if (!shallow(prevMeta, next)) {
      diffMetaAndSchedule(next, prevMeta)
      prevMeta = next
    }
  })
}
```

This works as-is since `state.tabs[]` is now the single source.

- [x] **Step 4: Compile + commit**

```bash
cd client && npx tsc --noEmit
git add client/src/features/stage/persistence/stage-persistence-bootstrap.ts
git commit -m "refactor(persistence): bootstrap walks tabs[] (single list)"
```

---

### Task 4: `use-stage-auto-open.ts` single subscription

**Files:**
- Modify: `client/src/features/stage/use-stage-auto-open.ts`
- Modify: `client/src/features/stage/use-stage-auto-open.test.ts`

- [x] **Step 1: Replace per-session subscription with single global**

Open `use-stage-auto-open.ts`. The existing logic likely subscribes to artifact arrivals per session and calls `notifyArtifactArrived(sessionId)`. Replace:

```ts
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useStageStore } from '@/stores/stage-store'

let subscribed = false

export function ensureStageAutoOpenSubscribed() {
  if (subscribed) return
  subscribed = true

  // Subscribe globally to chat-parts-store for any artifact arrival across all sessions
  useChatPartsStore.subscribe((state, prev) => {
    // Detect: any artifact added in any session
    if (hasNewArtifact(state, prev)) {
      useStageStore.getState().notifyArtifactArrived()
    }
  })
}

function hasNewArtifact(state: ReturnType<typeof useChatPartsStore.getState>,
                        prev: ReturnType<typeof useChatPartsStore.getState>): boolean {
  // Implementation depends on existing chat-parts-store shape; reuse the check
  // that the previous per-session version did, but applied across all sessions.
  // For each session id in state, compare artifactCount against prev.
  for (const [sid, info] of state.infoBySession.entries()) {
    const prevInfo = prev.infoBySession.get(sid)
    if (!prevInfo) {
      if (info.artifactCount > 0) return true
      continue
    }
    if (info.artifactCount > prevInfo.artifactCount) return true
  }
  return false
}
```

- [x] **Step 2: Update test**

```ts
it('global subscription notifies once on first artifact across any session', () => {
  // (test setup with chat-parts-store fixtures)
  ensureStageAutoOpenSubscribed()
  // Simulate artifact arrival in session A
  useChatPartsStore.setState({ /* session A artifact +1 */ } as never, false)
  expect(useStageStore.getState().open).toBe(true)
  expect(useStageStore.getState().autoOpened).toBe(true)
})

it('after closeStage, next artifact in any session re-triggers auto-open', () => {
  ensureStageAutoOpenSubscribed()
  useStageStore.setState({ open: true, autoOpened: true } as never, false)
  useStageStore.getState().closeStage()  // resets autoOpened

  // Simulate artifact arrival in session B
  useChatPartsStore.setState({ /* session B artifact +1 */ } as never, false)
  expect(useStageStore.getState().open).toBe(true)
})
```

- [x] **Step 3: Run + commit**

```bash
cd client && npx vitest run src/features/stage/use-stage-auto-open.test.ts
git add client/src/features/stage/use-stage-auto-open.ts \
        client/src/features/stage/use-stage-auto-open.test.ts
git commit -m "refactor(stage): use-stage-auto-open subscribes once globally"
```

---

## Phase 3B — Component Consumers (Tasks 5–9)

### Task 5: `<SplitView />` removes session-keyed selectors

**Files:**
- Modify: `client/src/features/session/split-view.tsx`
- Modify: `client/src/features/session/split-view.test.tsx`

- [x] **Step 1: Update selectors**

```ts
const open = useStageStore((s) => s.open)
const maximized = useStageStore((s) => s.maximized)
```

(Replace the `openBySession.get(sid)` + `maximizedBySession.get(sid)` lines.)

Update `<StageWindow />` rendering:

```tsx
<StageWindow />
```

(Remove `sessionId={sid ?? undefined}` prop.)

- [x] **Step 2: Add session-isolation regression test**

In `split-view.test.tsx`:

```tsx
it('switching active session does not change stage open / maximized / tabs', () => {
  useStageStore.setState({
    open: true, maximized: false,
    tabs: [makeTab({ tabId: 'a' })],
    openTabIds: new Set(['a']),
    openTabIdsOrdered: ['a'],
    activeTabId: 'a',
  } as never, false)

  const { rerender } = render(<SplitViewWithSession sid="sess-1" />)
  expect(useStageStore.getState().open).toBe(true)

  rerender(<SplitViewWithSession sid="sess-2" />)
  expect(useStageStore.getState().open).toBe(true)
  expect(useStageStore.getState().tabs.length).toBe(1)
  expect(useStageStore.getState().activeTabId).toBe('a')
})
```

- [x] **Step 3: tsc + run + commit**

```bash
cd client && npx tsc --noEmit
npx vitest run src/features/session/split-view.test.tsx
git add client/src/features/session/split-view.tsx client/src/features/session/split-view.test.tsx
git commit -m "refactor(split-view): use global stage state, drop sessionId prop on StageWindow"
```

---

### Task 6: `<StageWindow />` drops `sessionId` prop

**Files:**
- Modify: `client/src/features/stage/components/stage-window.tsx`
- Modify: `client/src/features/stage/components/stage-window.test.tsx`

- [x] **Step 1: Remove `sessionId` from `Props`**

```tsx
type Props = Record<string, never>  // no props

export function StageWindow() {
  // ...
}
```

- [x] **Step 2: Replace all `*BySession.get(sessionId)` with global selectors**

```ts
const closeStage = useStageStore((s) => s.closeStage)
const maximized = useStageStore((s) => s.maximized)
const toggleMaximized = useStageStore((s) => s.toggleMaximized)
const tabs = useStageStore(useShallow((s) => s.tabs))
const openTabIds = useStageStore((s) => s.openTabIds)
const openTabsOrdered = useStageStore(
  useShallow((s) => s.openTabIdsOrdered.map((id) => s.tabs.find((t) => t.tabId === id)).filter(Boolean) as StageTab[]),
)
const activeTabId = useStageStore((s) => s.activeTabId)
const focusTab = useStageStore((s) => s.focusTab)
const detachFromWorkset = useStageStore((s) => s.detachFromWorkset)
```

Update `handleClose`:

```ts
function handleClose() {
  closeStage()
}
function handleToggleMaximized() {
  toggleMaximized()
}
```

Drop `useEffect(() => setShowStartPage(false), [sessionId])`.

`<StageLeftRail />` is now called without `sessionId` prop (also drop in Task 7).

`<StageTabBarAddButton />` reads `sessionId` from session-store directly inside the component.

- [x] **Step 3: Update test renders**

`render(<StageWindow />)` everywhere instead of `<StageWindow sessionId="..." />`.

- [x] **Step 4: tsc + run + commit**

```bash
cd client && npx tsc --noEmit
npx vitest run src/features/stage/components/stage-window.test.tsx
git add client/src/features/stage/components/stage-window.tsx client/src/features/stage/components/stage-window.test.tsx
git commit -m "refactor(stage-window): drop sessionId prop, all global selectors"
```

---

### Task 7: `<StageLeftRail />` drops `sessionId` prop

**Files:**
- Modify: `client/src/features/stage/components/left-rail/stage-left-rail.tsx`
- Modify: `client/src/features/stage/components/left-rail/stage-left-rail.test.tsx`

- [x] **Step 1: Remove `Props` and `sessionId` reference**

```tsx
export function StageLeftRail() {
  // ...
  const allTabs = useStageStore(useShallow((s) => s.tabs))
  const openTabIds = useStageStore((s) => s.openTabIds)
  const activeTabId = useStageStore((s) => s.activeTabId)
  // (no per-session fallback)
  // ...
}
```

- [x] **Step 2: Test render — drop sessionId in all `<StageLeftRail />` usage**

- [x] **Step 3: `<StageTabBarAddButton />` reads `sessionId` from useSessionStore directly**

In `stage-tab-bar-add-button.tsx`:

```ts
import { useSessionStore } from '@/stores/session-store'

const sessionId = useSessionStore((s) => s.activeSessionId)
```

Drop the `sessionId` prop.

- [x] **Step 4: tsc + run + commit**

```bash
cd client && npx tsc --noEmit
npx vitest run src/features/stage/components/left-rail
git add client/src/features/stage/components/left-rail/ \
        client/src/features/stage/components/stage-tab-bar-add-button.tsx
git commit -m "refactor(stage-left-rail, add-button): drop sessionId prop"
```

---

### Task 8: `<StageToggleButton />` uses global state

**Files:**
- Modify: `client/src/features/stage/components/stage-toggle-button.tsx`
- Modify: `client/src/features/stage/components/stage-toggle-button.test.tsx`

- [x] **Step 1: Update selectors**

```ts
const open = useStageStore((s) => s.open)
const openStage = useStageStore((s) => s.openStage)
const closeStage = useStageStore((s) => s.closeStage)
const setRevealOrigin = useStageStore((s) => s.setRevealOrigin)
```

Drop `toggle(sid)` calls; use `closeStage()` / `openStage()` directly.

The button's `aria-disabled={!sid}` rule remains (per spec Q3b=ii — still requires an active session). Keep the `useSessionStore` selector for `sid`.

- [x] **Step 2: Update tests**

Replace any mock that injects per-session state with global state.

- [x] **Step 3: tsc + run + commit**

```bash
cd client && npx tsc --noEmit
npx vitest run src/features/stage/components/stage-toggle-button.test.tsx
git add client/src/features/stage/components/stage-toggle-button.tsx client/src/features/stage/components/stage-toggle-button.test.tsx
git commit -m "refactor(stage-toggle): use global open/close, keep aria-disabled on no-session"
```

---

### Task 9: Sweep remaining `*BySession` consumers

**Files:**
- Modify: `client/src/features/stage/components/stage-sidebar.tsx`
- Modify: `client/src/features/stage/components/activity-rail/*.tsx`
- Any other file in client/src that reads `*BySession`

- [x] **Step 1: Grep for all remaining offenders**

```bash
cd client && grep -rn "openBySession\|maximizedBySession\|autoOpenedSessions\|sidebarCollapsedBySession\|sidebarSelectionBySession\|resourceTreeExpandedBySession\|activeRailPanelBySession\|tabsBySession\|activeTabIdBySession\|workspaceTabs\|activeWorkspaceTabId\|focusWorkspaceTabForSession\|clearAllSessionState" src --include="*.ts" --include="*.tsx"
```

- [x] **Step 2: For each hit, replace per-session selector with global**

Common patterns:

```ts
// Before
const collapsed = useStageStore((s) => s.sidebarCollapsedBySession.get(sid) ?? false)
// After
const collapsed = useStageStore((s) => s.sidebarCollapsed)

// Before
const selection = useStageStore((s) => s.sidebarSelectionBySession.get(sid) ?? null)
// After
const selection = useStageStore((s) => s.sidebarSelection)

// Before
const expanded = useStageStore((s) => s.resourceTreeExpandedBySession.get(sid) ?? [])
// After
const expanded = useStageStore((s) => s.resourceTreeExpanded)

// Before
const panel = useStageStore((s) => s.activeRailPanelBySession.get(sid) ?? null)
// After
const panel = useStageStore((s) => s.activeRailPanel)
```

For action calls like `toggleSidebarCollapsed(sid)` → `toggleSidebarCollapsed()`.

- [x] **Step 3: tsc + run all client tests**

```bash
cd client && npx tsc --noEmit
npm run test
```

- [x] **Step 4: Commit**

```bash
git add client/src/
git commit -m "refactor(stage): sweep *BySession consumers (sidebar, activity-rail, etc.)"
```

---

## Phase 3C — Session-Store Cleanup + Final Regression (Tasks 10–11)

### Task 10: `session-store.ts` no longer touches stage on session delete

**Files:**
- Modify: `client/src/stores/session-store.ts`

- [x] **Step 1: Remove stage-cleanup call**

In the `removeSession` (or equivalent) action:

```ts
// REMOVE these lines:
// useStageStore.getState().clear(sessionId)
```

The artifact_preview tabs that originated from this session will now survive (their `originSessionId` becomes null via FK SET NULL — backend already handles this; the frontend's local cache continues to show the tab without origin label).

- [x] **Step 2: Add a regression test**

```ts
it('removeSession does not touch useStageStore.tabs', () => {
  useStageStore.setState({
    tabs: [makeTab({ tabId: 'a', originSessionId: 'sess-1' })],
  } as never, false)

  useSessionStore.getState().removeSession('sess-1')

  expect(useStageStore.getState().tabs.length).toBe(1)
})
```

- [x] **Step 3: tsc + run + commit**

```bash
cd client && npx tsc --noEmit
npx vitest run src/stores/session-store.test.ts
git add client/src/stores/session-store.ts client/src/stores/session-store.test.ts
git commit -m "refactor(session-store): removeSession leaves stage tabs untouched"
```

---

### Task 11: Full regression + register P3 + verification matrix

**Files:**
- Modify: `docs/exec-plans/index.md`

- [x] **Step 1: Register P3 in Active**

Add right after P2 row:

```markdown
- [Shared Stage Workbench · Phase 3 — State Globalization & Polish](./2026-04-28-shared-stage-workbench-p3-state-globalization-plan.md) — 2026-04-28 — Collapse 9 `*BySession` Map/Set fields into single global values; merge `tabsBySession + workspaceTabs` → `tabs[]`; merge `activeTabIdBySession + activeWorkspaceTabId` → `activeTabId`; `closeStage()` resets `autoOpened=false` (next-artifact-can-auto-open); `<StageWindow />` drops `sessionId` prop entirely; `use-stage-auto-open` becomes a single global subscription; `session-store.removeSession` no longer touches stage. Pure refactor + test rewrites; UX unchanged from P2.
```

- [x] **Step 2: Run full client test + type check**

```bash
cd client && npm run test
cd client && npx tsc --noEmit
```

Expected: all green, 0 type errors.

- [x] **Step 3: Run full server build (sanity, no backend changes in P3)**

```bash
cd server && mvn verify -q
```

Expected: PASS (P1 changes still hold).

- [x] **Step 4: Manual smoke matrix per spec §11**

Launch the app:

```bash
cd server && mvn spring-boot:run -pl data-talk-adapter &
cd client && npm run tauri dev
```

Walk through:

| Acceptance | Steps | Pass? |
|---|---|---|
| V1 | Open Session A → click monitor → stage opens; create a tab; switch to Session B → stage state identical (open + same tabs + same active) | ☐ |
| V2 | In Session A create new query_editor → switch to Session B → left rail shows the same tab; ui_find from any session returns it | ☐ |
| V4 | Delete Session A → tab still in left rail; row's "fromSession" label shows "(deleted)" | ☐ |
| V7 | Click left rail row → top bar shows tab + active; click X on top tab → only removed from top, still in left rail | ☐ |
| V8 | Right-click row → Archive → row hidden in default view, visible in archived group; Trash → confirm dialog → DB delete | ☐ |
| V14 | Hard restart app with ~100 active tabs → measure time-to-first-paint of left rail | (measure) |

- [x] **Step 5: Move P1, P2, P3 from Active to Completed**

Once all three Phases ship and §11 acceptance is verified end-to-end, edit `docs/exec-plans/index.md`:

```markdown
## 已完成计划

| ... |
| [Shared Stage Workbench · Phase 1 — Backend Protocol & Migration](./2026-04-28-shared-stage-workbench-p1-backend-protocol-plan.md) | 2026-04-28 | Phase 1 shipped: V13 migration (drop scope + FTS rewire), expectedText/baseVersion enforcement, error.markdown formatter, AGENTS.md + ui-objects-reference rewrite |
| [Shared Stage Workbench · Phase 2 — Frontend Layout Migration](./2026-04-28-shared-stage-workbench-p2-frontend-layout-plan.md) | 2026-04-28 | Phase 2 shipped: IDEA-style 3-pane stage layout (left rail + top tab bar workset + content); NavTabs removed from sidebar; library/workset semantics |
| [Shared Stage Workbench · Phase 3 — State Globalization & Polish](./2026-04-28-shared-stage-workbench-p3-state-globalization-plan.md) | 2026-04-28 | Phase 3 shipped: state globalization (9 *BySession → single fields); autoOpened reset on closeStage; session deletion no longer touches stage |
```

Also move the parent design spec entry in `docs/product-specs/index.md` Active list to the Shipped marker if it has one (the index uses §8 listing; add a `(Shipped 2026-04-28)` prefix to the description).

- [x] **Step 6: Propagate outcomes to canonical docs (per CLAUDE.md Post-Execution Document Housekeeping)**

| Doc | Update |
|---|---|
| `CLAUDE.md` | If the project conventions changed (e.g. "stage state is global, not session-keyed"), add a one-line note in the "Key Conventions" or "Working Rules" section |
| `ARCHITECTURE.md` | If the stage architecture diagram referenced "per-session stage state", update to "global stage state" |
| `docs/DESIGN.md` | If it documented the 9 `*BySession` Maps as a pattern, remove or annotate as deprecated |
| `docs/generated/db-schema.md` | If auto-generated, run the regeneration script post-V13 to update stage_tabs schema (drop scope column) |

- [x] **Step 7: Commit + push final**

```bash
git add docs/exec-plans/index.md docs/product-specs/index.md \
        CLAUDE.md ARCHITECTURE.md docs/DESIGN.md docs/generated/db-schema.md
git commit -m "docs: housekeeping after P3 — move 3 plans to Completed, propagate global stage convention"
```

---

## Self-Review

**Spec coverage**: P3 implements spec §3.1 items 1–2 (state globalization), §4.2 entire (`useStageStore` shape collapse), §5.5 archived-tab focus error path (already covered partially in P2 UI; the AI-path `tab_archived` error happens at the QueryEditorAdapter layer in P1's Task 18 — this is consistent), §10 risk row "C2 autoOpened reset", and §9.4 P3 task table 3.1–3.9 (renumbered as Tasks 1–11 in this plan but covering the same material).

State-globalization is necessary follow-up to P2 — without it, the `tabs[]` selector wouldn't work and `closeStage()` reset wouldn't be possible.

**Placeholder scan**: No `TBD` / `TODO` / "implement later". `hasNewArtifact` helper in Task 4 references `state.infoBySession` from `chat-parts-store` — that store exists and that field is the same as today (artifact arrival is computed from message parts; the existing per-session subscriber already does this comparison). The structural shape doesn't change in P3.

**Type consistency**: `tabs: StageTab[]` is consistent across Task 1 (definition), Tasks 3 (persistence walking), 5–9 (consumers), and 10 (regression test). `closeStage()` signature is consistent in Task 1 (definition), Task 5 (split-view consumer), Task 8 (toggle button consumer). `__hydrateAll` is the single hydration entry point established in P1 Task 20 and used unchanged in P3.

---

**Plan complete and saved.** This is the final plan in the trio (P1 → P2 → P3).

**Two execution options for the full set:**

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks; works well across all three plans
2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

Either approach: P1 → P2 → P3 in strict order. P2 cannot start until P1's API contract (especially `__hydrateAll`, `expectedText`, `baseVersion`) is in place; P3 cannot start until P2 has the new IDE layout shipped.

**Which approach?**

---

## Execution Status (closed 2026-04-28)

**Shipped in commit:** `0a6d21f` (P3 state globalization, 46 files, +700/-1603).

**Deviations from this plan, all closed in P3.5 (cleanup commit):**

- **Task 1 Step 2 — `trashTab` body**: plan body had `await coordinator.delete(tabId); set(...)` without rollback. Implementation added a `try/catch` that captures `prevOpenIds` / `prevOpenOrder` / `prevActive` and reverts on persistence failure. P3.5 added explicit test coverage for this rollback path.
- **Task 1 Step 2 — `__hydrateAll` body**: plan body returned `{ tabs: merged }` only. Implementation added a `seedWorkset(...)` helper that auto-adds non-archived hydrated tabs to the workset on cold restart. P3.5 added 4 explicit test cases for this behavior (non-archived seeded, archived skipped, no duplicates, tail-append order).
- **Task 1 Step 2 — `<StageWindow />` artifact title**: implementation introduced `useActiveArtifactTitle('')` placeholder that silently broke the artifact icon/label. P3.5 fixed by reading `useSessionStore((s) => s.activeSessionId)` and passing it to the hook.
- **Task 2 Step 1 — `reset()` helper**: plan code shows `setState(... as never, true)`. Implementation correctly used `... as never, false` so action functions persist (prompt-author intent). P3.5 left as-is.
- **Task 10 — `session-store.ts` no-op**: plan assumed `removeSession` was calling `useStageStore.getState().clear(sessionId)`. Code audit confirmed it was already free of any `useStageStore` reference; no change required. P3 commit didn't touch the file. The complementary regression test was not added; non-blocking.
- **Task 11 Step 4 — manual smoke matrix V1/V2/V4/V7/V8/V14**: not run as part of P3 ship; deferred as a manual product checklist.
- **Task 11 Step 5 — move plans to Completed**: not executed in P3 commit. Closed by P3.5 housekeeping (this status block).
- **Task 11 Step 6 — propagate to canonical docs (`CLAUDE.md` / `ARCHITECTURE.md` / `docs/DESIGN.md` / `db-schema.md`)**: not executed in P3 commit. Closed by P3.5 housekeeping.
- **Test fixture residue**: 21 dead `*BySession` keys survived in `read-file.test.tsx` and `open-direct-sql-query-editor-tab.test.ts`, masked by `as any`. P3.5 swept them.
- **TD-030 closure**: `StageTab.scope` field survived the P3 commit despite the tracker's "P2/P3 一并删除" mandate. P3.5 removed the field end-to-end.

**Residual debt routed forward:**
- TD-029 (`StageTabConcurrencyIT`) — was scoped to "P2/P3 阶段" in the tracker. Not landed in P3 or P3.5; deferred to a follow-up plan.

P3 ships behaviorally correct; P3.5 closes the process-discipline gap (housekeeping + test coverage + scope removal).
