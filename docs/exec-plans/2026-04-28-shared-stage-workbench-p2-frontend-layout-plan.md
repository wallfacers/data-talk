# Shared Stage Workbench · Phase 2 — Frontend Layout Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the IDEA-style A1 three-pane layout inside `<StageWindow />`: left rail (tab library) + top tab bar (workset) + right pane (content). Remove `<NavTabs />` from the sidebar. Wire library/workset semantics: close-from-top-bar = `detach` (DB unchanged); explicit archive/trash via row menu; archived tab focus by AI returns `tab_archived`, archived tab click in left rail prompts inline confirm-and-unarchive.

**Architecture:** New `client/src/features/stage/components/left-rail/` directory with 5 components (StageLeftRail / StageRailRow / StageRailSearch / StageRailGroup / StageRailRowMenu). `StageWindow` adds horizontal split (resizable divider, localStorage-persisted width, collapsible to 36px icon-only). New stage-store actions: `ensureOpenInWorkset`, `detachFromWorkset`, `archiveTab(id, archived)`, `trashTab(id)`. Top tab bar gets a `+` dropdown for new-workspace-tool selection. All new interactive controls map five states (idle/hover/focus/selected/disabled) to `client/DESIGN.md` semantic tokens — including the search input, the `+` button, the kebab trigger, and every menu item.

**Tech Stack:** React 19 · TypeScript · Zustand · TanStack Query · Tauri v2 · vitest · Testing Library · localStorage · shadcn/ui (DropdownMenu / ContextMenu / AlertDialog)

**Design Inputs:** [client/DESIGN.md](../../client/DESIGN.md) — applied tokens: `bg.subtle` / `bg.canvas` / `bg.panel` / `border.subtle` / `border.default` / `border.strong` / `text.muted` / `text.base` / `text.strong` / `text.soft` / `interaction.hover` / `interaction.selected` / `interaction.focusRing` / `interaction.disabled` / `accent.primary` / `status.danger` / `status.dangerSurface`. Compact density throughout. No glassmorphism. Dual theme: light + dark must both respect tokens.

---

## File Structure

### New files

| Path | Responsibility |
|---|---|
| `client/src/features/stage/components/left-rail/stage-left-rail.tsx` | Left rail container; toggles `leftRailCollapsed`; resizable handle |
| `client/src/features/stage/components/left-rail/stage-rail-search.tsx` | Search input bound to `useStageFind`; 5-state token mapping |
| `client/src/features/stage/components/left-rail/stage-rail-group.tsx` | Section header + collapsible body (Active / Archived) |
| `client/src/features/stage/components/left-rail/stage-rail-row.tsx` | Single tab row; 5 states (idle / hover / keyboard-focus / aria-pressed / archived); inWorkset visual mark |
| `client/src/features/stage/components/left-rail/stage-rail-row-menu.tsx` | Kebab trigger + DropdownMenu items (Open / Pin/Unpin / Archive/Unarchive / Trash + AlertDialog confirm) |
| `client/src/features/stage/components/left-rail/stage-left-rail.test.tsx` | vitest: search 5-state, row 5-state + inWorkset, kebab actions |
| `client/src/features/stage/components/stage-tab-bar-add-button.tsx` | The "+" button with DropdownMenu of new-workspace-tool entries |
| `client/src/features/stage/components/stage-tab-bar-add-button.test.tsx` | vitest: 5-state + disabled placeholder items |

### Files to modify

| Path | Change |
|---|---|
| `client/src/stores/stage-store.ts` | Add `openTabIds: Set<string>`, `openTabIdsOrdered: string[]`, `leftRailWidth`, `leftRailCollapsed`, `ensureOpenInWorkset`, `detachFromWorkset`, `archiveTab(id, archived)`, `trashTab(id)`, `focusTab` (= ensureOpen + setActive); update `closeTab` to be deprecated alias of `detachFromWorkset` |
| `client/src/stores/stage-store.test.ts` | Add tests for the 4 new mutators + library-vs-workset semantics |
| `client/src/features/stage/components/stage-window.tsx` | Embed left rail + divider + right pane; remove `sessionId` prop dependency for left rail; `closeTab` callbacks → `detachFromWorkset` |
| `client/src/features/stage/components/stage-window.test.tsx` | Update assertions for 3-pane structure |
| `client/src/features/stage/components/stage-tab-bar.tsx` | Render only `openTabs` subset; render `+` button at end of tab strip; close X → `detachFromWorkset`, not `closeTab` |
| `client/src/features/stage/components/stage-tab-bar.test.tsx` | Update for new render source + `+` button presence |
| `client/src/features/stage/components/stage-workbench-empty-state.tsx` | Empty state shows when `openTabIds=∅`; "+" CTA wires to add-button menu |
| `client/src/features/workspace/components/app-sidebar.tsx` | Remove `<NavTabs />` import + render |
| `client/src/i18n/messages.ts` | Rename `sidebar.tabs.*` → `stage.leftRail.*` (zh-CN + en); add `stage.leftRail.cta.openNew`, `stage.leftRail.empty.title`, `stage.leftRail.empty.body`, `stage.leftRail.confirmUnarchive.title`, `stage.leftRail.confirmTrash.*`, `stage.tabBar.addNew`, `stage.tabBar.addNew.menu.{sql,er,report,dashboard}` |
| `client/src/services/find/use-stage-find.ts` | (verify) supports filter against the unified library set; no scope filter |
| `docs/exec-plans/index.md` | Register P2 in Active section |

### Files to delete

| Path | Reason |
|---|---|
| `client/src/features/workspace/components/nav-tabs.tsx` | Sidebar Tabs group migrated into stage left rail |
| `client/src/features/workspace/components/nav-tabs-row.tsx` | Replaced by `stage-rail-row.tsx` |
| `client/src/features/workspace/components/nav-tabs-search.tsx` | Replaced by `stage-rail-search.tsx` |
| `client/src/features/workspace/components/__tests__/nav-tabs.test.tsx` | Tests moved to `stage-left-rail.test.tsx` |

---

## Phase 2A — Stage Store: Library vs Workset (Tasks 1–3)

### Task 1: Add `openTabIds` + `openTabIdsOrdered` state and helpers

**Files:**
- Modify: `client/src/stores/stage-store.ts`
- Modify: `client/src/stores/stage-store.test.ts`

- [ ] **Step 1: Write failing tests**

Append to `stage-store.test.ts`:

```ts
describe('Library vs Workset (Phase 2)', () => {
  beforeEach(() => {
    useStageStore.setState({
      workspaceTabs: [],
      tabsBySession: new Map(),
      openTabIds: new Set<string>(),
      openTabIdsOrdered: [],
      activeWorkspaceTabId: null,
      activeTabIdBySession: new Map(),
    } as never, true)
  })

  it('ensureOpenInWorkset adds an existing library tab into the open set without DB calls', () => {
    const tab = makeStageTab({ tabId: 'qe-1', type: 'query_editor', archived: false })
    useStageStore.setState({ workspaceTabs: [tab] } as never, false)

    useStageStore.getState().ensureOpenInWorkset('qe-1')

    expect(useStageStore.getState().openTabIds.has('qe-1')).toBe(true)
    expect(useStageStore.getState().openTabIdsOrdered).toEqual(['qe-1'])
  })

  it('ensureOpenInWorkset is idempotent — second call leaves order unchanged', () => {
    const tab = makeStageTab({ tabId: 'qe-1' })
    useStageStore.setState({ workspaceTabs: [tab] } as never, false)
    useStageStore.getState().ensureOpenInWorkset('qe-1')
    useStageStore.getState().ensureOpenInWorkset('qe-1')

    expect(useStageStore.getState().openTabIdsOrdered).toEqual(['qe-1'])
  })

  it('detachFromWorkset removes from open set but leaves library entry untouched', () => {
    const tab = makeStageTab({ tabId: 'qe-1' })
    useStageStore.setState({
      workspaceTabs: [tab],
      openTabIds: new Set(['qe-1']),
      openTabIdsOrdered: ['qe-1'],
    } as never, false)

    useStageStore.getState().detachFromWorkset('qe-1')

    expect(useStageStore.getState().openTabIds.has('qe-1')).toBe(false)
    expect(useStageStore.getState().workspaceTabs.find((t) => t.tabId === 'qe-1')).toBeDefined()
  })

  it('archiveTab(true) detaches from workset and flips archived flag', () => {
    const tab = makeStageTab({ tabId: 'qe-1', archived: false })
    useStageStore.setState({
      workspaceTabs: [tab],
      openTabIds: new Set(['qe-1']),
      openTabIdsOrdered: ['qe-1'],
    } as never, false)

    useStageStore.getState().archiveTab('qe-1', true)

    expect(useStageStore.getState().openTabIds.has('qe-1')).toBe(false)
    const updated = useStageStore.getState().workspaceTabs.find((t) => t.tabId === 'qe-1')
    expect(updated?.archived).toBe(true)
  })

  it('archiveTab(false) unarchives in place', () => {
    const tab = makeStageTab({ tabId: 'qe-1', archived: true })
    useStageStore.setState({ workspaceTabs: [tab] } as never, false)

    useStageStore.getState().archiveTab('qe-1', false)

    expect(useStageStore.getState().workspaceTabs.find((t) => t.tabId === 'qe-1')?.archived).toBe(false)
  })

  it('focusTab calls ensureOpenInWorkset then setActive', () => {
    const tab = makeStageTab({ tabId: 'qe-1' })
    useStageStore.setState({ workspaceTabs: [tab] } as never, false)

    useStageStore.getState().focusTab('qe-1')

    expect(useStageStore.getState().openTabIds.has('qe-1')).toBe(true)
    expect(useStageStore.getState().activeWorkspaceTabId).toBe('qe-1')
  })
})
```

`makeStageTab` helper (place at top of test file if missing):

```ts
import type { StageTab } from './stage-store'

function makeStageTab(overrides: Partial<StageTab> = {}): StageTab {
  return {
    tabId: 'qe-default',
    type: 'query_editor',
    title: 'untitled',
    scope: 'workspace',
    payload: {},
    payloadVersion: 1,
    createdAt: Date.now(),
    lastTouchedAt: Date.now(),
    archived: false,
    ...overrides,
  }
}
```

- [ ] **Step 2: Run tests, expect FAIL**

```bash
cd client && npx vitest run src/stores/stage-store.test.ts -t "Library vs Workset"
```

Expected: FAIL — these mutators don't exist yet.

- [ ] **Step 3: Add new state + mutator types to `StageState`**

In `stage-store.ts`, add to the `StageState` type:

```ts
export type StageState = {
  // … existing fields …

  // Workset (Phase 2)
  openTabIds: Set<string>
  openTabIdsOrdered: string[]
  leftRailWidth: number
  leftRailCollapsed: boolean

  ensureOpenInWorkset: (tabId: string) => void
  detachFromWorkset: (tabId: string) => void
  archiveTab: (tabId: string, archived: boolean) => void
  trashTab: (tabId: string) => Promise<void>
  focusTab: (tabId: string) => void
  setLeftRailWidth: (px: number) => void
  toggleLeftRailCollapsed: () => void
}
```

Note: `focusTab` already exists in the store but only does `setActive`; we override it to also `ensureOpenInWorkset`. Check existing definition and replace.

- [ ] **Step 4: Add initial state**

In the `create<StageState>(...)` body:

```ts
openTabIds: new Set<string>(),
openTabIdsOrdered: [],
leftRailWidth: loadLeftRailWidth(),
leftRailCollapsed: loadLeftRailCollapsed(),
```

Add helpers:

```ts
const LEFT_RAIL_WIDTH_KEY = 'stage.leftRail.width'
const LEFT_RAIL_COLLAPSED_KEY = 'stage.leftRail.collapsed'

function loadLeftRailWidth(): number {
  try {
    const v = localStorage.getItem(LEFT_RAIL_WIDTH_KEY)
    if (v) {
      const n = parseInt(v, 10)
      if (!isNaN(n) && n >= 180 && n <= 320) return n
    }
  } catch {}
  return 240
}

function loadLeftRailCollapsed(): boolean {
  try {
    return localStorage.getItem(LEFT_RAIL_COLLAPSED_KEY) === 'true'
  } catch {
    return false
  }
}
```

- [ ] **Step 5: Implement mutators**

```ts
ensureOpenInWorkset: (tabId) => set((s) => {
  if (s.openTabIds.has(tabId)) return s
  // Confirm tab exists (workspaceTabs ∪ tabsBySession; archived tabs allowed only via explicit unarchive elsewhere)
  const allTabs = [...s.workspaceTabs, ...[...s.tabsBySession.values()].flat()]
  const target = allTabs.find((t) => t.tabId === tabId)
  if (!target) return s            // silent no-op; AI path returns tab_not_found at adapter level
  if (target.archived) return s    // silent no-op; UI path handles unarchive confirm separately
  const next = new Set(s.openTabIds); next.add(tabId)
  return { openTabIds: next, openTabIdsOrdered: [...s.openTabIdsOrdered, tabId] }
}),

detachFromWorkset: (tabId) => set((s) => {
  if (!s.openTabIds.has(tabId)) return s
  const nextIds = new Set(s.openTabIds); nextIds.delete(tabId)
  const nextOrder = s.openTabIdsOrdered.filter((id) => id !== tabId)
  // If we just detached the active one, fall back to the previous in order, or null
  const wasActive = s.activeWorkspaceTabId === tabId
  const nextActive = wasActive ? (nextOrder[nextOrder.length - 1] ?? null) : s.activeWorkspaceTabId
  return { openTabIds: nextIds, openTabIdsOrdered: nextOrder, activeWorkspaceTabId: nextActive }
}),

archiveTab: (tabId, archived) => set((s) => {
  // Update the matching tab in whichever list it lives in
  const updateInList = (list: StageTab[]): StageTab[] | null => {
    const idx = list.findIndex((t) => t.tabId === tabId)
    if (idx < 0) return null
    const next = [...list]
    next[idx] = { ...next[idx], archived, archivedAt: archived ? Date.now() : null }
    return next
  }
  const ws = updateInList(s.workspaceTabs)
  if (ws) {
    // archive ⇒ also detach from workset
    const detached = archived ? archiveDetach(s, tabId) : { openTabIds: s.openTabIds, openTabIdsOrdered: s.openTabIdsOrdered, activeWorkspaceTabId: s.activeWorkspaceTabId }
    return { workspaceTabs: ws, ...detached }
  }
  for (const [sid, list] of s.tabsBySession.entries()) {
    const updated = updateInList(list)
    if (updated) {
      const map = new Map(s.tabsBySession); map.set(sid, updated)
      const detached = archived ? archiveDetach(s, tabId) : {}
      return { tabsBySession: map, ...detached }
    }
  }
  return s
}),

trashTab: async (tabId) => {
  // 1) Detach from workset and active
  useStageStore.getState().detachFromWorkset(tabId)
  // 2) DELETE on the server (coordinator handles it)
  const { coordinator } = await import('@/features/stage/persistence/stage-persistence-bootstrap')
  await coordinator.delete(tabId)
  // 3) Remove from local store lists
  set((s) => {
    const wsRemoved = s.workspaceTabs.filter((t) => t.tabId !== tabId)
    if (wsRemoved.length !== s.workspaceTabs.length) return { workspaceTabs: wsRemoved }
    for (const [sid, list] of s.tabsBySession.entries()) {
      if (list.some((t) => t.tabId === tabId)) {
        const next = list.filter((t) => t.tabId !== tabId)
        const map = new Map(s.tabsBySession); map.set(sid, next)
        return { tabsBySession: map }
      }
    }
    return s
  })
},

focusTab: (tabId) => {
  useStageStore.getState().ensureOpenInWorkset(tabId)
  set((s) => {
    if (s.workspaceTabs.some((t) => t.tabId === tabId)) {
      return { activeWorkspaceTabId: tabId }
    }
    for (const [sid, list] of s.tabsBySession.entries()) {
      if (list.some((t) => t.tabId === tabId)) {
        const map = new Map(s.activeTabIdBySession); map.set(sid, tabId)
        return { activeTabIdBySession: map, activeWorkspaceTabId: tabId }
      }
    }
    return s
  })
},

setLeftRailWidth: (px) => set(() => {
  try { localStorage.setItem(LEFT_RAIL_WIDTH_KEY, String(px)) } catch {}
  return { leftRailWidth: Math.min(320, Math.max(180, px)) }
}),

toggleLeftRailCollapsed: () => set((s) => {
  const next = !s.leftRailCollapsed
  try { localStorage.setItem(LEFT_RAIL_COLLAPSED_KEY, String(next)) } catch {}
  return { leftRailCollapsed: next }
}),
```

Helper at file top:

```ts
function archiveDetach(s: StageState, tabId: string) {
  const nextIds = new Set(s.openTabIds); nextIds.delete(tabId)
  const nextOrder = s.openTabIdsOrdered.filter((id) => id !== tabId)
  const wasActive = s.activeWorkspaceTabId === tabId
  const nextActive = wasActive ? (nextOrder[nextOrder.length - 1] ?? null) : s.activeWorkspaceTabId
  return { openTabIds: nextIds, openTabIdsOrdered: nextOrder, activeWorkspaceTabId: nextActive }
}
```

- [ ] **Step 6: Run tests, expect PASS**

```bash
cd client && npx vitest run src/stores/stage-store.test.ts -t "Library vs Workset"
```

- [ ] **Step 7: tsc check + commit**

```bash
cd client && npx tsc --noEmit
git add client/src/stores/stage-store.ts client/src/stores/stage-store.test.ts
git commit -m "feat(stage-store): add openTabIds + library/workset mutators"
```

---

### Task 2: Deprecate `closeTab` as alias of `detachFromWorkset`

**Files:**
- Modify: `client/src/stores/stage-store.ts`

- [ ] **Step 1: Update `closeTab` to delegate**

Replace the existing `closeTab` body:

```ts
closeTab: (tabId) => {
  // Phase 2 semantic: close = detach from workset (not DB delete).
  // Keep legacy callers from accidentally trashing tabs.
  useStageStore.getState().detachFromWorkset(tabId)
},
```

Add a JSDoc note on the method:

```ts
/**
 * @deprecated Phase 2: closeTab is now an alias of detachFromWorkset.
 * Use detachFromWorkset directly. archiveTab / trashTab handle persistence.
 */
closeTab: (tabId: string) => void
```

- [ ] **Step 2: Compile + commit**

```bash
cd client && npx tsc --noEmit
git add client/src/stores/stage-store.ts
git commit -m "refactor(stage-store): closeTab → alias of detachFromWorkset"
```

---

### Task 3: Stage tab bar renders openTabs only

**Files:**
- Modify: `client/src/features/stage/components/stage-window.tsx`
- Modify: `client/src/features/stage/components/stage-tab-bar.tsx`
- Modify: `client/src/features/stage/components/stage-tab-bar.test.tsx`

- [ ] **Step 1: Update `StageWindow` selectors**

Replace the existing `tabs` selector in `stage-window.tsx`:

```ts
const allTabs = useStageStore(
  useShallow((s) => {
    const sessionTabs = sessionId ? (s.tabsBySession.get(sessionId) ?? []) : []
    return [...s.workspaceTabs, ...sessionTabs]
  }),
)
const openTabIds = useStageStore((s) => s.openTabIds)
const openTabsOrdered = useStageStore(
  useShallow((s) => s.openTabIdsOrdered.map((id) => allTabs.find((t) => t.tabId === id)).filter(Boolean) as StageTab[]),
)
```

Pass `openTabsOrdered` to `<StageTabBar tabs={openTabsOrdered.map(...)}>` instead of the old `tabs` array.

Update `handleCloseTab`:

```ts
const handleCloseTab = (tabId: string) => useStageStore.getState().detachFromWorkset(tabId)
```

The other `handleCloseOthers` / `handleCloseLeft` / `handleCloseRight` should also call `detachFromWorkset`:

```ts
const handleCloseOthers = (tabId: string) =>
  openTabsOrdered.filter((t) => t.tabId !== tabId).forEach((t) => useStageStore.getState().detachFromWorkset(t.tabId))
const handleCloseAll = () =>
  openTabsOrdered.forEach((t) => useStageStore.getState().detachFromWorkset(t.tabId))
const handleCloseLeft = (tabId: string) => {
  const idx = openTabsOrdered.findIndex((t) => t.tabId === tabId)
  openTabsOrdered.slice(0, idx).forEach((t) => useStageStore.getState().detachFromWorkset(t.tabId))
}
const handleCloseRight = (tabId: string) => {
  const idx = openTabsOrdered.findIndex((t) => t.tabId === tabId)
  openTabsOrdered.slice(idx + 1).forEach((t) => useStageStore.getState().detachFromWorkset(t.tabId))
}
```

- [ ] **Step 2: Update existing test**

In `stage-tab-bar.test.tsx`, find the test that asserts close button calls `closeTab` callback. The callback is unchanged from `<StageTabBar />` perspective (still `onClose` prop). The behavioral test should still pass — `detachFromWorkset` is called by the parent. Add a new test:

```tsx
it('Close X on a tab calls onClose with the tabId (parent will detachFromWorkset)', () => {
  const onClose = vi.fn()
  render(<StageTabBar tabs={[{ tabId: 'qe-1', title: 'one' }]} activeId="qe-1" onClose={onClose} />)
  fireEvent.click(screen.getByRole('button', { name: /close/i }))
  expect(onClose).toHaveBeenCalledWith('qe-1')
})
```

- [ ] **Step 3: Run tests, expect PASS**

```bash
cd client && npx vitest run src/features/stage/components/stage-tab-bar.test.tsx src/features/stage/components/stage-window.test.tsx
```

If `stage-window.test.tsx` references the old `tabs` selector, update it to construct a fixture with `openTabIds`/`openTabIdsOrdered` populated.

- [ ] **Step 4: Commit**

```bash
git add client/src/features/stage/components/stage-window.tsx \
        client/src/features/stage/components/stage-tab-bar.tsx \
        client/src/features/stage/components/stage-tab-bar.test.tsx \
        client/src/features/stage/components/stage-window.test.tsx
git commit -m "feat(stage-window): tab bar renders openTabsOrdered, close = detach"
```

---

## Phase 2B — Add `+` Button + New-Workspace-Tool Menu (Tasks 4–5)

### Task 4: New `<StageTabBarAddButton />` component

**Files:**
- Create: `client/src/features/stage/components/stage-tab-bar-add-button.tsx`
- Create: `client/src/features/stage/components/stage-tab-bar-add-button.test.tsx`

- [ ] **Step 1: Write component**

```tsx
import { PlusIcon, DatabaseIcon, NetworkIcon, LineChartIcon, Table2Icon } from 'lucide-react'
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import { useStageStore } from '@/stores/stage-store'

type Props = {
  /** Currently active session for new query_editor tabs (originSessionId label only). */
  sessionId?: string | null
}

export function StageTabBarAddButton({ sessionId }: Props) {
  const { t } = useI18n()

  function openSqlEditor() {
    useStageStore.getState().openQueryEditor({
      sessionId: sessionId ?? null,
      scope: 'workspace',
      baseTitle: t('stage.tabBar.addNew.menu.sql'),
      openMode: 'always_new',
      entryMode: 'blank',
    })
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="icon-xs"
          aria-label={t('stage.tabBar.addNew')}
          className={[
            // idle: transparent + text.muted
            'text-text-muted',
            // hover: interaction.hover + text.base
            'hover:bg-interaction-hover hover:text-text-base',
            // focus: focus ring outer
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing',
            // active (data-state=open): interaction.selected + accent.primary
            'data-[state=open]:bg-interaction-selected data-[state=open]:text-accent-primary',
          ].join(' ')}
        >
          <PlusIcon className="size-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="bg-bg-elevated border border-border-default shadow-sm">
        <DropdownMenuItem onClick={openSqlEditor} className="text-text-base hover:bg-interaction-hover focus:bg-interaction-selected focus:text-text-strong">
          <DatabaseIcon className="size-4 mr-2 text-text-muted" />
          {t('stage.tabBar.addNew.menu.sql')}
        </DropdownMenuItem>
        <DropdownMenuItem disabled className="text-text-soft data-[disabled]:opacity-60">
          <NetworkIcon className="size-4 mr-2" />
          <span className="flex-1">{t('stage.tabBar.addNew.menu.er')}</span>
          <span className="ml-2 rounded bg-bg-subtle px-1.5 py-0.5 text-[10px] text-text-soft">{t('stage.empty.pending')}</span>
        </DropdownMenuItem>
        <DropdownMenuItem disabled className="text-text-soft data-[disabled]:opacity-60">
          <LineChartIcon className="size-4 mr-2" />
          <span className="flex-1">{t('stage.tabBar.addNew.menu.report')}</span>
          <span className="ml-2 rounded bg-bg-subtle px-1.5 py-0.5 text-[10px] text-text-soft">{t('stage.empty.pending')}</span>
        </DropdownMenuItem>
        <DropdownMenuItem disabled className="text-text-soft data-[disabled]:opacity-60">
          <Table2Icon className="size-4 mr-2" />
          <span className="flex-1">{t('stage.tabBar.addNew.menu.dashboard')}</span>
          <span className="ml-2 rounded bg-bg-subtle px-1.5 py-0.5 text-[10px] text-text-soft">{t('stage.empty.pending')}</span>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
```

- [ ] **Step 2: Add i18n keys**

In `client/src/i18n/messages.ts` (zh-CN block):

```ts
'stage.tabBar.addNew': '新建工作位',
'stage.tabBar.addNew.menu.sql': 'SQL 编辑器',
'stage.tabBar.addNew.menu.er': 'ER 图',
'stage.tabBar.addNew.menu.report': '报表',
'stage.tabBar.addNew.menu.dashboard': 'Dashboard',
```

(en block):

```ts
'stage.tabBar.addNew': 'New workspace tool',
'stage.tabBar.addNew.menu.sql': 'SQL editor',
'stage.tabBar.addNew.menu.er': 'ER diagram',
'stage.tabBar.addNew.menu.report': 'Report',
'stage.tabBar.addNew.menu.dashboard': 'Dashboard',
```

- [ ] **Step 3: Write component test**

```tsx
import { render, screen, fireEvent } from '@testing-library/react'
import { StageTabBarAddButton } from './stage-tab-bar-add-button'
import { I18nProvider } from '@/i18n/provider'

describe('StageTabBarAddButton', () => {
  function setup() {
    return render(<I18nProvider><StageTabBarAddButton sessionId={null} /></I18nProvider>)
  }

  it('idle state has muted icon and no background', () => {
    setup()
    const btn = screen.getByRole('button', { name: /new workspace tool|新建工作位/i })
    expect(btn.className).toMatch(/text-text-muted/)
    expect(btn.className).not.toMatch(/bg-interaction-selected/)
  })

  it('open state applies accent token', async () => {
    setup()
    const btn = screen.getByRole('button')
    fireEvent.click(btn)
    expect(btn).toHaveAttribute('data-state', 'open')
  })

  it('disabled menu items show Pending pill', () => {
    setup()
    fireEvent.click(screen.getByRole('button'))
    const erItem = screen.getByText(/ER diagram|ER 图/i).closest('[role="menuitem"]')!
    expect(erItem).toHaveAttribute('data-disabled')
  })

  it('SQL editor menu item invokes openQueryEditor', () => {
    const spy = vi.spyOn(useStageStore.getState(), 'openQueryEditor')
    setup()
    fireEvent.click(screen.getByRole('button'))
    fireEvent.click(screen.getByText(/SQL editor|SQL 编辑器/i))
    expect(spy).toHaveBeenCalled()
  })
})
```

- [ ] **Step 4: Run tests, expect PASS**

```bash
cd client && npx vitest run src/features/stage/components/stage-tab-bar-add-button.test.tsx
```

- [ ] **Step 5: Commit**

```bash
git add client/src/features/stage/components/stage-tab-bar-add-button.tsx \
        client/src/features/stage/components/stage-tab-bar-add-button.test.tsx \
        client/src/i18n/messages.ts
git commit -m "feat(stage-tab-bar): add + button with new-workspace-tool menu"
```

---

### Task 5: Wire `<StageTabBarAddButton />` into `StageTabBar`

**Files:**
- Modify: `client/src/features/stage/components/stage-tab-bar.tsx`

- [ ] **Step 1: Render at end of tab strip**

In `stage-tab-bar.tsx`, locate the JSX where individual tabs are rendered. After the last tab `<div>`, add:

```tsx
<StageTabBarAddButton sessionId={sessionIdProp ?? null} />
```

Pass `sessionId` down from `<StageWindow />` via a new prop on `<StageTabBar />` (or read from store directly if simpler).

- [ ] **Step 2: tsc + visual quick check**

```bash
cd client && npx tsc --noEmit && npm run dev
# manually verify: open chat, click monitor button → stage opens → "+" button visible at end of tab strip
```

- [ ] **Step 3: Commit**

```bash
git add client/src/features/stage/components/stage-tab-bar.tsx
git commit -m "feat(stage-tab-bar): render add button at end of strip"
```

---

## Phase 2C — Left Rail Components (Tasks 6–10)

### Task 6: `<StageRailSearch />` with 5-state token mapping

**Files:**
- Create: `client/src/features/stage/components/left-rail/stage-rail-search.tsx`

- [ ] **Step 1: Write component**

```tsx
import { SearchIcon, XIcon } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'

type Props = {
  value: string
  onChange: (next: string) => void
  disabled?: boolean
}

export function StageRailSearch({ value, onChange, disabled }: Props) {
  const { t } = useI18n()
  return (
    <div
      className={[
        // container
        'group relative flex h-8 items-center gap-1.5 rounded-md px-2',
        // idle surface + border
        'bg-bg-panel border border-border-default',
        // hover
        'hover:bg-interaction-hover',
        // focus-within (when input is focused) → strong border + focus ring
        'focus-within:border-border-strong focus-within:ring-2 focus-within:ring-interaction-focusRing',
        // disabled
        disabled ? 'opacity-50 pointer-events-none' : '',
      ].join(' ')}
    >
      <SearchIcon className="size-3.5 text-text-muted shrink-0" aria-hidden />
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        placeholder={t('stage.leftRail.search.placeholder')}
        className={[
          'flex-1 bg-transparent text-sm text-text-base outline-none',
          'placeholder:text-text-muted',
          'disabled:text-text-soft',
        ].join(' ')}
        aria-label={t('stage.leftRail.search.placeholder')}
      />
      {value ? (
        <button
          type="button"
          aria-label={t('common.clear')}
          onClick={() => onChange('')}
          className="text-text-muted hover:text-text-base focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing rounded"
        >
          <XIcon className="size-3.5" />
        </button>
      ) : null}
    </div>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/features/stage/components/left-rail/stage-rail-search.tsx
git commit -m "feat(stage-left-rail): add StageRailSearch with 5-state tokens"
```

---

### Task 7: `<StageRailRow />` with inWorkset visual mark

**Files:**
- Create: `client/src/features/stage/components/left-rail/stage-rail-row.tsx`

- [ ] **Step 1: Write component**

```tsx
import { useI18n } from '@/i18n/use-i18n'
import { getTabTypeDescriptor } from '@/features/stage/registry/tab-type-registry'
import type { StageTab } from '@/stores/stage-store'

type Props = {
  tab: StageTab
  active: boolean        // is this the activeTabId?
  inWorkset: boolean     // is this tab in openTabIds?
  onClick: () => void
  onMenuOpen?: () => void
  trailingMenu?: React.ReactNode  // <StageRailRowMenu /> instance
}

export function StageRailRow({ tab, active, inWorkset, onClick, trailingMenu }: Props) {
  const { t } = useI18n()
  const desc = getTabTypeDescriptor(tab.type)
  const Icon = desc.icon

  return (
    <li
      role="button"
      tabIndex={active ? 0 : -1}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick() }
      }}
      aria-pressed={active}
      data-in-workset={inWorkset}
      data-archived={tab.archived ? true : undefined}
      className={[
        'group relative flex h-8 items-center gap-2 rounded-md px-2',
        'transition-[background,color] duration-[180ms] ease-[var(--easing-standard)]',
        // idle (not in workset)
        'text-text-muted',
        // in-workset but not active: medium weight + base text
        inWorkset && !active ? 'font-medium text-text-base' : '',
        // hover
        'hover:bg-interaction-hover',
        // active = focused/aria-pressed
        active ? 'bg-interaction-selected text-text-strong' : '',
        // archived
        tab.archived ? 'opacity-60 text-text-soft' : '',
        // keyboard focus visible (non-active rows)
        !active ? 'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing' : '',
      ].filter(Boolean).join(' ')}
    >
      {/* active indicator bar */}
      {active && <span aria-hidden className="absolute inset-y-1 left-0 w-0.5 rounded bg-accent-primary" />}

      <Icon className={`size-4 shrink-0 ${active ? 'text-text-strong' : 'text-text-muted'}`} aria-hidden />

      <span className="flex-1 truncate text-sm">{tab.title}</span>

      {/* in-workset dot (non-active) */}
      {inWorkset && !active && (
        <span aria-hidden className="size-1.5 rounded-full bg-accent-primary opacity-60" />
      )}

      {/* type badge */}
      <span className="shrink-0 rounded bg-bg-subtle px-1.5 py-0.5 text-[10px] uppercase tracking-wide text-text-soft">
        {t(desc.labelKey as Parameters<typeof t>[0])}
      </span>

      {/* trailing kebab menu (visible on hover/focus) */}
      {trailingMenu ? (
        <span className="opacity-0 group-hover:opacity-100 group-focus-within:opacity-100 transition-opacity duration-[180ms]">
          {trailingMenu}
        </span>
      ) : null}
    </li>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/features/stage/components/left-rail/stage-rail-row.tsx
git commit -m "feat(stage-left-rail): add StageRailRow with inWorkset mark"
```

---

### Task 8: `<StageRailRowMenu />` with kebab + trash confirm

**Files:**
- Create: `client/src/features/stage/components/left-rail/stage-rail-row-menu.tsx`

- [ ] **Step 1: Write component**

```tsx
import { useState } from 'react'
import { MoreVerticalIcon, ExternalLinkIcon, PinIcon, PinOffIcon, ArchiveIcon, ArchiveRestoreIcon, Trash2Icon } from 'lucide-react'
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator,
} from '@/components/ui/dropdown-menu'
import {
  AlertDialog, AlertDialogContent, AlertDialogHeader, AlertDialogTitle, AlertDialogDescription,
  AlertDialogFooter, AlertDialogCancel, AlertDialogAction,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import { useStageStore } from '@/stores/stage-store'
import type { StageTab } from '@/stores/stage-store'

type Props = { tab: StageTab }

export function StageRailRowMenu({ tab }: Props) {
  const { t } = useI18n()
  const [confirmTrashOpen, setConfirmTrashOpen] = useState(false)

  const focusTab = useStageStore((s) => s.focusTab)
  const setTabPinned = useStageStore((s) => s.setTabPinned)
  const archiveTab = useStageStore((s) => s.archiveTab)
  const trashTab = useStageStore((s) => s.trashTab)

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            type="button"
            variant="ghost"
            size="icon-xs"
            aria-label={t('stage.leftRail.row.menu')}
            onClick={(e) => e.stopPropagation()}
            className={[
              'text-text-muted',
              'hover:bg-interaction-hover hover:text-text-base',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing',
              'data-[state=open]:bg-interaction-selected data-[state=open]:text-accent-primary',
            ].join(' ')}
          >
            <MoreVerticalIcon className="size-3.5" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="bg-bg-elevated border border-border-default shadow-sm">
          <DropdownMenuItem onClick={() => focusTab(tab.tabId)} className="hover:bg-interaction-hover focus:bg-interaction-selected focus:text-text-strong">
            <ExternalLinkIcon className="size-4 mr-2 text-text-muted" />
            {t('stage.leftRail.row.menu.open')}
          </DropdownMenuItem>
          <DropdownMenuItem
            onClick={() => setTabPinned(tab.tabId, !tab.pinned)}
            className="hover:bg-interaction-hover focus:bg-interaction-selected focus:text-text-strong"
          >
            {tab.pinned ? <PinOffIcon className="size-4 mr-2 text-text-muted" /> : <PinIcon className="size-4 mr-2 text-text-muted" />}
            {tab.pinned ? t('stage.leftRail.row.menu.unpin') : t('stage.leftRail.row.menu.pin')}
          </DropdownMenuItem>
          <DropdownMenuItem
            onClick={() => archiveTab(tab.tabId, !tab.archived)}
            className="hover:bg-interaction-hover focus:bg-interaction-selected focus:text-text-strong"
          >
            {tab.archived ? <ArchiveRestoreIcon className="size-4 mr-2 text-text-muted" /> : <ArchiveIcon className="size-4 mr-2 text-text-muted" />}
            {tab.archived ? t('stage.leftRail.row.menu.unarchive') : t('stage.leftRail.row.menu.archive')}
          </DropdownMenuItem>
          <DropdownMenuSeparator className="bg-border-subtle" />
          <DropdownMenuItem
            onClick={() => setConfirmTrashOpen(true)}
            className="text-status-danger hover:bg-status-dangerSurface focus:bg-status-dangerSurface focus:text-status-danger"
          >
            <Trash2Icon className="size-4 mr-2" />
            {t('stage.leftRail.row.menu.trash')}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <AlertDialog open={confirmTrashOpen} onOpenChange={setConfirmTrashOpen}>
        <AlertDialogContent className="bg-bg-elevated border border-border-default">
          <AlertDialogHeader>
            <AlertDialogTitle>{t('stage.leftRail.confirmTrash.title')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('stage.leftRail.confirmTrash.body', { title: tab.title })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel className="bg-bg-panel border border-border-default">
              {t('common.cancel')}
            </AlertDialogCancel>
            <AlertDialogAction
              onClick={() => { void trashTab(tab.tabId); setConfirmTrashOpen(false) }}
              className="bg-status-danger text-text-inverse hover:bg-status-danger/90"
            >
              {t('stage.leftRail.confirmTrash.confirm')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
```

- [ ] **Step 2: Add i18n keys**

zh-CN:

```ts
'stage.leftRail.row.menu': '更多',
'stage.leftRail.row.menu.open': '打开',
'stage.leftRail.row.menu.pin': '置顶',
'stage.leftRail.row.menu.unpin': '取消置顶',
'stage.leftRail.row.menu.archive': '归档',
'stage.leftRail.row.menu.unarchive': '解归档',
'stage.leftRail.row.menu.trash': '永久删除',
'stage.leftRail.confirmTrash.title': '永久删除此 Tab？',
'stage.leftRail.confirmTrash.body': '将永久删除 Tab "{{title}}"。FTS 索引、payload 也会一并清理，操作不可撤销。',
'stage.leftRail.confirmTrash.confirm': '确认删除',
'common.cancel': '取消',
'common.clear': '清除',
```

en:

```ts
'stage.leftRail.row.menu': 'More',
'stage.leftRail.row.menu.open': 'Open',
'stage.leftRail.row.menu.pin': 'Pin',
'stage.leftRail.row.menu.unpin': 'Unpin',
'stage.leftRail.row.menu.archive': 'Archive',
'stage.leftRail.row.menu.unarchive': 'Unarchive',
'stage.leftRail.row.menu.trash': 'Delete permanently',
'stage.leftRail.confirmTrash.title': 'Delete this tab permanently?',
'stage.leftRail.confirmTrash.body': 'Tab "{{title}}" will be deleted permanently. FTS index and payload will be cleaned up. This cannot be undone.',
'stage.leftRail.confirmTrash.confirm': 'Delete',
'common.cancel': 'Cancel',
'common.clear': 'Clear',
```

- [ ] **Step 3: Commit**

```bash
git add client/src/features/stage/components/left-rail/stage-rail-row-menu.tsx \
        client/src/i18n/messages.ts
git commit -m "feat(stage-left-rail): add row kebab menu with archive/unarchive/trash"
```

---

### Task 9: `<StageRailGroup />` + `<StageLeftRail />` shell

**Files:**
- Create: `client/src/features/stage/components/left-rail/stage-rail-group.tsx`
- Create: `client/src/features/stage/components/left-rail/stage-left-rail.tsx`

- [ ] **Step 1: Write `StageRailGroup`**

```tsx
import { useState } from 'react'
import { ChevronDownIcon, ChevronRightIcon } from 'lucide-react'

type Props = {
  label: string
  count: number
  defaultOpen?: boolean
  children: React.ReactNode
}

export function StageRailGroup({ label, count, defaultOpen = true, children }: Props) {
  const [open, setOpen] = useState(defaultOpen)
  return (
    <div className="flex flex-col">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className={[
          'flex h-7 items-center gap-1.5 px-1 text-[11px] font-medium uppercase tracking-wide text-text-soft',
          'hover:text-text-base',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing rounded',
        ].join(' ')}
      >
        {open ? <ChevronDownIcon className="size-3" /> : <ChevronRightIcon className="size-3" />}
        <span className="flex-1 text-left">{label}</span>
        <span className="text-text-soft tabular-nums">{count}</span>
      </button>
      {open ? <ul role="list" className="flex flex-col gap-0.5 px-1 pb-1">{children}</ul> : null}
    </div>
  )
}
```

- [ ] **Step 2: Write `StageLeftRail`**

```tsx
import { useMemo, useRef, useState } from 'react'
import { useShallow } from 'zustand/react/shallow'
import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { AlertDialog, AlertDialogContent, AlertDialogHeader, AlertDialogTitle, AlertDialogDescription,
         AlertDialogFooter, AlertDialogCancel, AlertDialogAction } from '@/components/ui/alert-dialog'
import { useStageStore } from '@/stores/stage-store'
import { useI18n } from '@/i18n/use-i18n'
import { StageRailSearch } from './stage-rail-search'
import { StageRailGroup } from './stage-rail-group'
import { StageRailRow } from './stage-rail-row'
import { StageRailRowMenu } from './stage-rail-row-menu'
import type { StageTab } from '@/stores/stage-store'

type Props = { sessionId?: string }

export function StageLeftRail({ sessionId }: Props) {
  const { t } = useI18n()

  const collapsed = useStageStore((s) => s.leftRailCollapsed)
  const toggleCollapsed = useStageStore((s) => s.toggleLeftRailCollapsed)

  const allTabs = useStageStore(
    useShallow((s) => {
      const sessionTabs = sessionId ? (s.tabsBySession.get(sessionId) ?? []) : []
      return [...s.workspaceTabs, ...sessionTabs]
    }),
  )
  const openTabIds = useStageStore((s) => s.openTabIds)
  const activeTabId = useStageStore((s) => {
    if (!sessionId) return s.activeWorkspaceTabId
    return s.activeTabIdBySession.get(sessionId) ?? s.activeWorkspaceTabId ?? null
  })
  const focusTab = useStageStore((s) => s.focusTab)
  const archiveTab = useStageStore((s) => s.archiveTab)

  const [query, setQuery] = useState('')
  const [pendingUnarchiveTab, setPendingUnarchiveTab] = useState<StageTab | null>(null)

  const { active, archived } = useMemo(() => {
    const term = query.trim().toLowerCase()
    const filter = (tab: StageTab) => !term || tab.title.toLowerCase().includes(term)
    const active = allTabs.filter((t) => !t.archived).filter(filter)
      .sort((a, b) => {
        if (!!b.pinned !== !!a.pinned) return (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0)
        return (b.lastTouchedAt ?? 0) - (a.lastTouchedAt ?? 0)
      })
    const archived = allTabs.filter((t) => t.archived).filter(filter)
    return { active, archived }
  }, [allTabs, query])

  function handleClick(tab: StageTab) {
    if (tab.archived) {
      setPendingUnarchiveTab(tab)
      return
    }
    focusTab(tab.tabId)
  }

  if (collapsed) {
    return (
      <div className="flex h-full w-9 flex-col bg-bg-subtle border-r border-border-subtle">
        <button
          type="button"
          aria-label={t('stage.leftRail.expand')}
          onClick={toggleCollapsed}
          className="h-8 w-full flex items-center justify-center text-text-muted hover:bg-interaction-hover hover:text-text-base"
        >
          <ChevronRightIcon className="size-4" />
        </button>
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col bg-bg-subtle border-r border-border-subtle">
      <div className="flex h-9 items-center gap-1 px-2 border-b border-border-subtle">
        <span className="flex-1 text-xs font-medium uppercase tracking-wide text-text-soft">
          {t('stage.leftRail.title')}
        </span>
        <Button
          type="button" variant="ghost" size="icon-xs"
          aria-label={t('stage.leftRail.collapse')}
          onClick={toggleCollapsed}
          className="text-text-muted hover:bg-interaction-hover hover:text-text-base focus-visible:ring-2 focus-visible:ring-interaction-focusRing"
        >
          <ChevronLeftIcon className="size-3.5" />
        </Button>
      </div>

      <div className="px-2 pt-2">
        <StageRailSearch value={query} onChange={setQuery} />
      </div>

      <div className="flex-1 overflow-y-auto px-1 py-2 flex flex-col gap-2">
        <StageRailGroup label={t('stage.leftRail.group.active')} count={active.length} defaultOpen>
          {active.length === 0 ? (
            <li className="px-2 py-3 text-center text-xs text-text-soft">
              {t('stage.leftRail.empty')}
            </li>
          ) : active.map((tab) => (
            <StageRailRow
              key={tab.tabId}
              tab={tab}
              active={tab.tabId === activeTabId}
              inWorkset={openTabIds.has(tab.tabId)}
              onClick={() => handleClick(tab)}
              trailingMenu={<StageRailRowMenu tab={tab} />}
            />
          ))}
        </StageRailGroup>

        {archived.length > 0 ? (
          <StageRailGroup label={t('stage.leftRail.group.archived')} count={archived.length} defaultOpen={false}>
            {archived.map((tab) => (
              <StageRailRow
                key={tab.tabId}
                tab={tab}
                active={false}
                inWorkset={false}
                onClick={() => handleClick(tab)}
                trailingMenu={<StageRailRowMenu tab={tab} />}
              />
            ))}
          </StageRailGroup>
        ) : null}
      </div>

      <AlertDialog open={!!pendingUnarchiveTab} onOpenChange={(o) => !o && setPendingUnarchiveTab(null)}>
        <AlertDialogContent className="bg-bg-elevated border border-border-default">
          <AlertDialogHeader>
            <AlertDialogTitle>{t('stage.leftRail.confirmUnarchive.title')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('stage.leftRail.confirmUnarchive.body', { title: pendingUnarchiveTab?.title ?? '' })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel className="bg-bg-panel border border-border-default">{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                if (!pendingUnarchiveTab) return
                archiveTab(pendingUnarchiveTab.tabId, false)
                focusTab(pendingUnarchiveTab.tabId)
                setPendingUnarchiveTab(null)
              }}
            >
              {t('stage.leftRail.confirmUnarchive.confirm')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
```

- [ ] **Step 3: Add i18n keys**

zh-CN:

```ts
'stage.leftRail.title': '工作台',
'stage.leftRail.empty': '尚无 Tab。请从对话中打开，或点击 +。',
'stage.leftRail.search.placeholder': '搜索 Tab...',
'stage.leftRail.collapse': '折叠工作台目录',
'stage.leftRail.expand': '展开工作台目录',
'stage.leftRail.group.active': '活跃',
'stage.leftRail.group.archived': '已归档',
'stage.leftRail.confirmUnarchive.title': '解归档并打开？',
'stage.leftRail.confirmUnarchive.body': 'Tab "{{title}}" 已被归档。确认后将解归档并加入顶 tab 栏。',
'stage.leftRail.confirmUnarchive.confirm': '解归档并打开',
'stage.leftRail.cta.openNew': '在工作台新建一个工作位',
```

en (mirror):

```ts
'stage.leftRail.title': 'Workbench',
'stage.leftRail.empty': 'No tabs yet. Open one from chat or click +.',
'stage.leftRail.search.placeholder': 'Search tabs...',
'stage.leftRail.collapse': 'Collapse workbench list',
'stage.leftRail.expand': 'Expand workbench list',
'stage.leftRail.group.active': 'Active',
'stage.leftRail.group.archived': 'Archived',
'stage.leftRail.confirmUnarchive.title': 'Unarchive and open?',
'stage.leftRail.confirmUnarchive.body': 'Tab "{{title}}" is archived. Confirm to unarchive and add it to the top tab bar.',
'stage.leftRail.confirmUnarchive.confirm': 'Unarchive and open',
'stage.leftRail.cta.openNew': 'Open a new workspace tool',
```

- [ ] **Step 4: Commit**

```bash
git add client/src/features/stage/components/left-rail/stage-left-rail.tsx \
        client/src/features/stage/components/left-rail/stage-rail-group.tsx \
        client/src/i18n/messages.ts
git commit -m "feat(stage-left-rail): assemble shell with active/archived groups + unarchive confirm"
```

---

### Task 10: `stage-left-rail.test.tsx` covering 5-state and inWorkset

**Files:**
- Create: `client/src/features/stage/components/left-rail/stage-left-rail.test.tsx`

- [ ] **Step 1: Write tests**

```tsx
import { render, screen, fireEvent, within } from '@testing-library/react'
import { I18nProvider } from '@/i18n/provider'
import { StageLeftRail } from './stage-left-rail'
import { useStageStore } from '@/stores/stage-store'

describe('StageLeftRail', () => {
  beforeEach(() => {
    useStageStore.setState({
      workspaceTabs: [],
      tabsBySession: new Map(),
      openTabIds: new Set<string>(),
      openTabIdsOrdered: [],
      activeWorkspaceTabId: null,
      activeTabIdBySession: new Map(),
      leftRailCollapsed: false,
    } as never, false)
  })

  function renderRail() {
    return render(<I18nProvider><StageLeftRail /></I18nProvider>)
  }

  it('shows empty state when no tabs', () => {
    renderRail()
    expect(screen.getByText(/No tabs yet|尚无 Tab/i)).toBeInTheDocument()
  })

  it('renders active tabs sorted by lastTouchedAt desc, pinned first', () => {
    useStageStore.setState({
      workspaceTabs: [
        makeTab({ tabId: 'a', title: 'old',     lastTouchedAt: 1, pinned: false }),
        makeTab({ tabId: 'b', title: 'newer',   lastTouchedAt: 5, pinned: false }),
        makeTab({ tabId: 'c', title: 'pinned',  lastTouchedAt: 2, pinned: true }),
      ],
    } as never, false)
    renderRail()
    const titles = screen.getAllByRole('button').map((b) => b.textContent ?? '')
    expect(titles[0]).toContain('pinned')
    expect(titles[1]).toContain('newer')
    expect(titles[2]).toContain('old')
  })

  it('inWorkset row applies medium font-weight + dot indicator', () => {
    useStageStore.setState({
      workspaceTabs: [makeTab({ tabId: 'a', title: 'one' })],
      openTabIds: new Set(['a']),
      openTabIdsOrdered: ['a'],
      activeWorkspaceTabId: null,
    } as never, false)
    renderRail()
    const row = screen.getByText('one').closest('[role="button"]')!
    expect(row.className).toMatch(/font-medium/)
    // dot exists
    expect(within(row as HTMLElement).getAllByRole('presentation', { hidden: true })
      .some((el) => el.className.includes('rounded-full'))).toBe(true)
  })

  it('active row uses interaction.selected + accent.primary', () => {
    useStageStore.setState({
      workspaceTabs: [makeTab({ tabId: 'a' })],
      openTabIds: new Set(['a']),
      openTabIdsOrdered: ['a'],
      activeWorkspaceTabId: 'a',
    } as never, false)
    renderRail()
    const row = screen.getByRole('button', { pressed: true })
    expect(row.className).toMatch(/bg-interaction-selected/)
  })

  it('clicking an archived row opens unarchive confirm dialog', () => {
    useStageStore.setState({
      workspaceTabs: [makeTab({ tabId: 'a', title: 'frozen', archived: true })],
    } as never, false)
    renderRail()
    fireEvent.click(screen.getByText('frozen'))
    expect(screen.getByText(/Unarchive and open|解归档并打开/i)).toBeInTheDocument()
  })

  it('search filters tabs by title (case-insensitive)', () => {
    useStageStore.setState({
      workspaceTabs: [
        makeTab({ tabId: 'a', title: 'users monthly' }),
        makeTab({ tabId: 'b', title: 'orders trend' }),
      ],
    } as never, false)
    renderRail()
    fireEvent.change(screen.getByPlaceholderText(/Search tabs|搜索 Tab/i), { target: { value: 'USER' } })
    expect(screen.getByText('users monthly')).toBeInTheDocument()
    expect(screen.queryByText('orders trend')).not.toBeInTheDocument()
  })

  it('search input gets focus ring on focus', () => {
    renderRail()
    const input = screen.getByPlaceholderText(/Search tabs|搜索 Tab/i)
    fireEvent.focus(input)
    const container = input.parentElement!
    expect(container.className).toMatch(/focus-within:ring-2/)
  })

  it('collapsed rail shows only chevron', () => {
    useStageStore.setState({ leftRailCollapsed: true } as never, false)
    renderRail()
    expect(screen.queryByPlaceholderText(/Search tabs|搜索 Tab/i)).not.toBeInTheDocument()
    expect(screen.getByLabelText(/Expand workbench list|展开工作台目录/i)).toBeInTheDocument()
  })
})

function makeTab(over: any) {
  return {
    tabId: 'default',
    type: 'query_editor',
    title: 'untitled',
    scope: 'workspace',
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

- [ ] **Step 2: Run tests, expect PASS**

```bash
cd client && npx vitest run src/features/stage/components/left-rail/stage-left-rail.test.tsx
```

- [ ] **Step 3: Commit**

```bash
git add client/src/features/stage/components/left-rail/stage-left-rail.test.tsx
git commit -m "test(stage-left-rail): cover 5-state, inWorkset, archive flow"
```

---

## Phase 2D — StageWindow Three-Pane Layout (Tasks 11–13)

### Task 11: Embed left rail + resizable divider into StageWindow

**Files:**
- Modify: `client/src/features/stage/components/stage-window.tsx`

- [ ] **Step 1: Update layout JSX**

Replace the `<div className="flex min-h-0 flex-1 overflow-hidden bg-background/88">…</div>` block:

```tsx
<div className="flex min-h-0 flex-1 overflow-hidden bg-background/88">
  {/* Left rail (library) */}
  <div
    style={{ width: leftRailCollapsed ? 36 : leftRailWidth }}
    className="shrink-0 transition-[width] duration-[180ms] ease-[var(--easing-standard)]"
  >
    <StageLeftRail sessionId={sessionId} />
  </div>

  {/* Resizable divider (only when not collapsed) */}
  {!leftRailCollapsed ? (
    <div
      className="w-1 cursor-col-resize hover:bg-accent-primary/20 transition-colors group"
      onPointerDown={handleDividerPointerDown}
      onPointerMove={handleDividerPointerMove}
      onPointerUp={handleDividerPointerUp}
    >
      <div className="h-full w-px bg-border-subtle group-hover:bg-accent-primary/50" />
    </div>
  ) : null}

  {/* Right pane: top tab bar + content */}
  <section className="flex min-w-0 flex-1 flex-col overflow-hidden">
    {openTabsOrdered.length > 0 && (
      <StageTabBar
        sessionId={sessionId ?? null}
        tabs={openTabsOrdered.map((t) => ({ tabId: t.tabId, title: t.title, type: t.type }))}
        activeId={activeTabId ?? undefined}
        onSelect={handleSelectTab}
        onClose={handleCloseTab}
        onCloseOthers={handleCloseOthers}
        onCloseAll={handleCloseAll}
        onCloseLeft={handleCloseLeft}
        onCloseRight={handleCloseRight}
        onOpenStartPage={() => setShowStartPage(true)}
      />
    )}
    <div data-testid="stage-workspace-pane" className="flex min-h-0 flex-1 flex-col overflow-hidden bg-background">
      {activeTabId && !showStartPage ? (
        <div className="flex min-h-0 flex-1 overflow-hidden">
          <StageTabContent />
        </div>
      ) : (
        <div className="flex min-h-0 flex-1 overflow-hidden">
          <StageWorkbenchEmptyState onOpenSqlEditor={handleOpenSqlEditor} />
        </div>
      )}
    </div>
  </section>
</div>
```

Add hooks at the top of the component:

```tsx
import { StageLeftRail } from './left-rail/stage-left-rail'

const leftRailWidth = useStageStore((s) => s.leftRailWidth)
const leftRailCollapsed = useStageStore((s) => s.leftRailCollapsed)
const setLeftRailWidth = useStageStore((s) => s.setLeftRailWidth)

const dividerStateRef = useRef<{ active: boolean; startX: number; startWidth: number }>({
  active: false, startX: 0, startWidth: 0,
})

function handleDividerPointerDown(e: React.PointerEvent) {
  dividerStateRef.current = { active: true, startX: e.clientX, startWidth: leftRailWidth }
  ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
}
function handleDividerPointerMove(e: React.PointerEvent) {
  if (!dividerStateRef.current.active) return
  const next = dividerStateRef.current.startWidth + (e.clientX - dividerStateRef.current.startX)
  setLeftRailWidth(next)
}
function handleDividerPointerUp() {
  dividerStateRef.current.active = false
}
```

- [ ] **Step 2: tsc check**

```bash
cd client && npx tsc --noEmit
```

- [ ] **Step 3: Visual smoke**

```bash
cd client && npm run dev
# manually: open chat → click monitor button → stage opens → verify left rail visible at 240px → drag divider → confirms width persists across reload
```

- [ ] **Step 4: Commit**

```bash
git add client/src/features/stage/components/stage-window.tsx
git commit -m "feat(stage-window): embed left rail + resizable divider"
```

---

### Task 12: Update `stage-window.test.tsx` for three-pane structure

**Files:**
- Modify: `client/src/features/stage/components/stage-window.test.tsx`

- [ ] **Step 1: Add three-pane structural test**

```tsx
it('renders left rail + tab bar + content pane when openTabIds is non-empty', () => {
  useStageStore.setState({
    workspaceTabs: [makeTab({ tabId: 'qe-1', title: 'one' })],
    openTabIds: new Set(['qe-1']),
    openTabIdsOrdered: ['qe-1'],
    activeWorkspaceTabId: 'qe-1',
    leftRailCollapsed: false,
  } as never, false)

  render(<StageWindow sessionId="sess-1" />)

  // Left rail
  expect(screen.getByText(/Workbench|工作台/i)).toBeInTheDocument()
  // Top tab bar (renders the tab title as a button)
  expect(screen.getByRole('tab', { name: /one/i })).toBeInTheDocument()
  // Content pane testid
  expect(screen.getByTestId('stage-workspace-pane')).toBeInTheDocument()
})

it('renders empty state in right pane when openTabIds is empty', () => {
  useStageStore.setState({
    workspaceTabs: [],
    openTabIds: new Set<string>(),
    activeWorkspaceTabId: null,
  } as never, false)

  render(<StageWindow sessionId="sess-1" />)

  expect(screen.getByTestId('stage-empty-workbench')).toBeInTheDocument()
})

it('clicking close X on a top-bar tab detaches but keeps it in left rail', () => {
  useStageStore.setState({
    workspaceTabs: [makeTab({ tabId: 'qe-1', title: 'one' })],
    openTabIds: new Set(['qe-1']),
    openTabIdsOrdered: ['qe-1'],
    activeWorkspaceTabId: 'qe-1',
  } as never, false)

  render(<StageWindow sessionId="sess-1" />)
  fireEvent.click(screen.getAllByRole('button', { name: /close/i })[0])

  expect(useStageStore.getState().openTabIds.has('qe-1')).toBe(false)
  // Tab still in left rail
  expect(screen.getByText('one')).toBeInTheDocument()
})
```

- [ ] **Step 2: Run + commit**

```bash
cd client && npx vitest run src/features/stage/components/stage-window.test.tsx
git add client/src/features/stage/components/stage-window.test.tsx
git commit -m "test(stage-window): assert 3-pane structure + close = detach semantics"
```

---

### Task 13: Update `<StageWorkbenchEmptyState />` "+" CTA

**Files:**
- Modify: `client/src/features/stage/components/stage-workbench-empty-state.tsx`

- [ ] **Step 1: Add CTA wiring**

The existing empty state has 4 cards. Keep them. Add a small banner above with an inline `<StageTabBarAddButton sessionId={sessionId ?? null} />` and helper text from `stage.leftRail.cta.openNew`.

```tsx
import { StageTabBarAddButton } from './stage-tab-bar-add-button'

// at top of return JSX, just inside the centered container:
<div className="flex items-center justify-center gap-2 mb-2">
  <StageTabBarAddButton sessionId={sessionId} />
  <span className="text-xs text-text-soft">{t('stage.leftRail.cta.openNew')}</span>
</div>
```

`sessionId` becomes a new optional prop. Update the parent `<StageWindow />` to pass it.

- [ ] **Step 2: tsc + commit**

```bash
cd client && npx tsc --noEmit
git add client/src/features/stage/components/stage-workbench-empty-state.tsx \
        client/src/features/stage/components/stage-window.tsx
git commit -m "feat(stage-empty-state): inline + button CTA"
```

---

## Phase 2E — Sidebar Cleanup + i18n Migration (Tasks 14–16)

### Task 14: Remove `<NavTabs />` from sidebar

**Files:**
- Modify: `client/src/features/workspace/components/app-sidebar.tsx`
- Delete: `client/src/features/workspace/components/nav-tabs.tsx`
- Delete: `client/src/features/workspace/components/nav-tabs-row.tsx`
- Delete: `client/src/features/workspace/components/nav-tabs-search.tsx`
- Delete: `client/src/features/workspace/components/__tests__/nav-tabs.test.tsx`

- [ ] **Step 1: Remove import + usage**

In `app-sidebar.tsx`:

```diff
-import { NavTabs } from './nav-tabs'
@@
         <NavSessions />
-        <NavTabs />
       </SidebarContent>
```

- [ ] **Step 2: Delete files**

```bash
git rm client/src/features/workspace/components/nav-tabs.tsx \
       client/src/features/workspace/components/nav-tabs-row.tsx \
       client/src/features/workspace/components/nav-tabs-search.tsx \
       client/src/features/workspace/components/__tests__/nav-tabs.test.tsx
```

- [ ] **Step 3: tsc**

```bash
cd client && npx tsc --noEmit
```

If errors complain about other files importing from those paths, grep and update:

```bash
grep -rn "features/workspace/components/nav-tabs" client/src --include="*.ts" --include="*.tsx"
```

Fix each to import from `features/stage/components/left-rail/...` if applicable.

- [ ] **Step 4: Commit**

```bash
git add client/src/features/workspace/components/app-sidebar.tsx
git commit -m "refactor(sidebar): remove NavTabs (migrated to stage left rail)"
```

---

### Task 15: i18n key migration sweep

**Files:**
- Modify: `client/src/i18n/messages.ts`

- [ ] **Step 1: Search for all `sidebar.tabs.*` references**

```bash
cd client && grep -rn "sidebar\.tabs\." src --include="*.ts" --include="*.tsx"
```

- [ ] **Step 2: Rename keys**

In `messages.ts`, replace each `sidebar.tabs.X` key with `stage.leftRail.X` in BOTH the zh-CN and en sections. Tasks 8 and 9 above have already added `stage.leftRail.*` keys; ensure they don't duplicate. The original `sidebar.tabs.contextMenu.*` keys may have analogues in `stage.leftRail.row.menu.*`; consolidate.

- [ ] **Step 3: Update all consumer code**

For each grep hit from Step 1, replace `t('sidebar.tabs.X')` with `t('stage.leftRail.X')` (or the new analogue). Most should already be in deleted files; remaining ones are likely tests.

- [ ] **Step 4: tsc + run i18n test**

```bash
cd client && npx tsc --noEmit
npx vitest run src/i18n
```

- [ ] **Step 5: Commit**

```bash
git add client/src/i18n/messages.ts client/src/  # any consumer updates
git commit -m "i18n(stage): rename sidebar.tabs.* to stage.leftRail.*"
```

---

### Task 16: Verify `useStageFind` works against unified library

**Files:**
- Modify: `client/src/services/find/use-stage-find.ts` (verify)

- [ ] **Step 1: Read current hook**

```bash
cd client && cat src/services/find/use-stage-find.ts
```

- [ ] **Step 2: If it sends `scope` as a filter param, remove it**

The hook calls `/api/stage/find` (or similar). Since Phase 1 removed `scope` from the backend filter, ensure the hook does not send `scope`. If the hook is currently used by `<NavTabs />` (now deleted), it may need re-purposing for the left rail's deeper search-with-FTS feature later (Phase 3+ — out of scope for P2).

For now: P2 left rail uses simple in-memory `title.toLowerCase().includes(query)` (already implemented in Task 9). So `useStageFind` is **not** wired to the left rail in P2; deeper FTS-backed search is a future enhancement.

- [ ] **Step 3: If unused after P2, mark hook with a `@deprecated` JSDoc note pointing to potential P3+ revival**

```ts
/**
 * @deprecated 2026-04-28: The Phase 2 left rail uses in-memory filtering.
 * Server-backed FTS search will be re-introduced in a follow-up plan.
 */
```

- [ ] **Step 4: Commit (if any change)**

```bash
git add client/src/services/find/use-stage-find.ts
git commit -m "chore(stage-find): scope filter removed; mark hook as deferred"
```

---

## Phase 2F — Visual Contract Review + Final Regression (Tasks 17–18)

### Task 17: Visual contract review checklist

This is a **manual review** step paired with vitest snapshot assertions.

**Files:** read-only review of left-rail, tab bar, empty state.

- [ ] **Step 1: Build dev server**

```bash
cd client && npm run dev
```

- [ ] **Step 2: Token map walk-through (light theme)**

For each control below, confirm idle / hover / focus / active(or selected) / disabled states map to the expected token. Do this in DevTools (inspect background-color, border, color).

| Control | idle | hover | focus | selected | disabled | Pass? |
|---|---|---|---|---|---|---|
| Search input container | `bg.panel` + `border.default` | `interaction.hover` | `border.strong` + `interaction.focusRing` | n/a | `interaction.disabled` | ☐ |
| Rail row (not in workset) | `text.muted` font-400 | `interaction.hover` | `interaction.focusRing` outer | `interaction.selected` + `text.strong` + `accent.primary` 2px bar | n/a | ☐ |
| Rail row (in workset, non-active) | `text.base` font-500 + 6px dot | `interaction.hover` | ring | upgrade to active | n/a | ☐ |
| Rail row (archived) | opacity 0.6 + `text.soft` | `interaction.hover` | ring | n/a | n/a | ☐ |
| Kebab trigger | invisible until row hover; then `text.muted` | `interaction.hover` + `text.base` | ring | `interaction.selected` + `accent.primary` | n/a | ☐ |
| Kebab menu Trash item | `status.danger` | `status.dangerSurface` | ring + `status.danger` | n/a | n/a | ☐ |
| Top tab "+" button | `text.muted` | `interaction.hover` + `text.base` | ring | (data-state=open) `interaction.selected` + `accent.primary` | n/a (always enabled) | ☐ |
| Add menu items (SQL row) | `text.base` | `interaction.hover` | `interaction.selected` + `text.strong` | n/a | n/a | ☐ |
| Add menu items (placeholder) | `text.soft` | n/a | n/a | n/a | `interaction.disabled` + Pending pill | ☐ |
| Top tab idle | `tabIdle: text.muted` | `interaction.hover` | ring | n/a (active = `accent.primary`) | n/a | ☐ |
| Top tab active | `tabActive: accent.primary` + 2px underline | n/a | n/a | n/a | n/a | ☐ |
| Top tab close X | `text.muted` | `text.base` | ring | n/a | n/a | ☐ |
| Empty state CTA | (delegates to add button) | | | | | ☐ |

- [ ] **Step 3: Switch to dark theme; repeat the table**

Toggle theme in app settings. Verify each row remains correct. No light-theme-only colors should leak.

- [ ] **Step 4: Snapshot test for the most token-heavy components**

In `stage-rail-row.test.tsx` (create if missing), add:

```tsx
it('row in workset/non-active/non-archived produces canonical className snapshot', () => {
  const tab = makeTab({ tabId: 'a', title: 'one' })
  const { container } = render(<StageRailRow tab={tab} active={false} inWorkset={true} onClick={() => {}} />)
  expect(container.firstChild).toMatchSnapshot()
})

it('archived row applies opacity-60 + text-soft', () => {
  const tab = makeTab({ tabId: 'a', title: 'frozen', archived: true })
  const { container } = render(<StageRailRow tab={tab} active={false} inWorkset={false} onClick={() => {}} />)
  expect(container.firstChild).toMatchSnapshot()
})
```

- [ ] **Step 5: Run snapshot tests, expect PASS or update**

```bash
cd client && npx vitest run src/features/stage/components/left-rail
```

- [ ] **Step 6: Document the review pass**

Add a short note to PR description. No commit needed (review checklist is in this plan).

---

### Task 18: Full regression + register plan as Active

**Files:**
- Modify: `docs/exec-plans/index.md`

- [ ] **Step 1: Register P2 in index Active section**

Add right after the P1 row (which should already be in Active from P1's Task 22):

```markdown
- [Shared Stage Workbench · Phase 2 — Frontend Layout Migration](./2026-04-28-shared-stage-workbench-p2-frontend-layout-plan.md) — 2026-04-28 — IDEA-style 3-pane stage layout: left rail library (search + active/archived groups + kebab menu) + top tab bar workset (with `+` add button) + content pane. NavTabs deleted from sidebar; archived tab focus shows confirm-and-unarchive in UI; close = detach (DB unchanged). All new controls map 5 states to `client/DESIGN.md` tokens.
```

- [ ] **Step 2: Run full client tests**

```bash
cd client && npm run test
```

Expected: all green.

- [ ] **Step 3: Type check**

```bash
cd client && npx tsc --noEmit
```

Expected: 0 errors.

- [ ] **Step 4: Server compile (sanity, no backend changes in P2)**

```bash
cd server && mvn compile -q
```

Expected: PASS.

- [ ] **Step 5: Commit index update**

```bash
git add docs/exec-plans/index.md
git commit -m "docs(plans): register P2 in Active"
```

- [ ] **Step 6: Verification matrix self-check (per spec §11)**

| Acceptance | Verified by |
|---|---|
| V7 (left rail click → top bar add + active; X → detach not delete) | `stage-window.test.tsx` PASS + manual smoke |
| V8 (archive → default view hidden; trash → DB delete) | `stage-rail-row-menu` tests + manual |
| V11 (search box + row + kebab token mapping) | Task 17 review checklist + snapshot tests |
| V12 (npm test green; tsc clean) | green |
| (V1, V2, V4 verified after P3) | n/a in P2 |

---

## Self-Review

**Spec coverage**: P2 implements spec §3.1 items 3–4 (UI layout), §7 entire (前端布局与视觉契约 — left rail / top bar / right pane / 5-state tokens / i18n keys), §9.4 P2 task table 2.1–2.9, the IDE library/workset semantics from §5.5 (`detach` / `archive` / `trash` / `focus`), and the "archived tab UI click → unarchive confirm" UX from §7.3.

State globalization (`*BySession` removal) is explicitly **deferred to P3** — P2 keeps `tabsBySession` / `activeWorkspaceTabId` shapes intact and adds `openTabIds` as new state on top.

**Placeholder scan**: No `TBD` / `TODO` / "implement later". Every code block is complete. The visual contract review (Task 17) is intentionally a manual checklist with snapshot back-up — that's standard practice per `client/DESIGN.md` workflow and matches the saved feedback memory.

**Type consistency**: `ensureOpenInWorkset` / `detachFromWorkset` / `archiveTab(id, archived)` / `trashTab(id)` / `focusTab` signatures are consistent across Task 1 (definition), Tasks 8, 9, 11 (consumers), Task 12 (tests). `StageLeftRail`'s `sessionId?: string` prop matches what `<StageWindow />` already passes (kept until P3 globalization). `loadLeftRailWidth` / `loadLeftRailCollapsed` helpers are used both at module init and at `setLeftRailWidth` / `toggleLeftRailCollapsed` time.

---

**Plan complete and saved.** This is Phase 2 of three. P3 (State Globalization & Polish) drafts next.
