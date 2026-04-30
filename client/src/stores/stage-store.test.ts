import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useStageStore } from './stage-store'

function reset() {
  localStorage.removeItem('stage.workset.order')
  localStorage.removeItem('stage.workset.active')
  localStorage.removeItem('stage.open')
  localStorage.removeItem('stage.userClosed')
  useStageStore.setState({
    open: false, maximized: false,
    sidebarCollapsed: false, sidebarSelection: null,
    resourceTreeExpanded: [], activeRailPanel: null,
    revealOrigin: null,
    tabs: [], openTabIds: new Set(), openTabIdsOrdered: [],
    activeTabId: null,
    leftRailWidth: 240, leftRailCollapsed: false,
  } as never, false)
}

function makeTab(over: Record<string, unknown> = {}) {
  return {
    tabId: 'default',
    type: 'query_editor',
    title: 'untitled',
    payload: {},
    payloadVersion: 1,
    createdAt: 0,
    lastTouchedAt: 0,
    archived: false,
    archivedAt: null as number | null,
    pinned: false,
    ...over,
  }
}

describe('useStageStore (single-flag stage panel)', () => {
  beforeEach(reset)

  describe('open / close / toggle persistence', () => {
    it('openStage flips open=true and persists stage.open=true', () => {
      useStageStore.getState().openStage()
      expect(useStageStore.getState().open).toBe(true)
      expect(localStorage.getItem('stage.open')).toBe('true')
    })

    it('closeStage flips open=false and removes the persisted key', () => {
      localStorage.setItem('stage.open', 'true')
      useStageStore.setState({ open: true } as never, false)
      useStageStore.getState().closeStage()
      expect(useStageStore.getState().open).toBe(false)
      expect(localStorage.getItem('stage.open')).toBeNull()
    })

    it('toggleStage flips and persists in both directions', () => {
      useStageStore.getState().toggleStage()
      expect(useStageStore.getState().open).toBe(true)
      expect(localStorage.getItem('stage.open')).toBe('true')

      useStageStore.getState().toggleStage()
      expect(useStageStore.getState().open).toBe(false)
      expect(localStorage.getItem('stage.open')).toBeNull()
    })

    it('legacy stage.userClosed key is cleaned up by the store module', async () => {
      // The legacy cleanup runs at module init time; reset and re-import
      // forces a fresh purge cycle.
      localStorage.setItem('stage.userClosed', 'true')
      vi.resetModules()
      await import('./stage-store')
      expect(localStorage.getItem('stage.userClosed')).toBeNull()
    })
  })

  describe('openTab / focusTab auto-reveal the panel', () => {
    it('openTab on a closed stage reveals it and persists open=true', () => {
      expect(useStageStore.getState().open).toBe(false)
      useStageStore.getState().openTab(makeTab({ tabId: 'a' }))
      const s = useStageStore.getState()
      expect(s.open).toBe(true)
      expect(s.tabs.map((t) => t.tabId)).toEqual(['a'])
      expect(s.openTabIdsOrdered).toEqual(['a'])
      expect(s.activeTabId).toBe('a')
      expect(localStorage.getItem('stage.open')).toBe('true')
    })

    it('openTab on an already-open stage stays open and does not double-write the key', () => {
      useStageStore.getState().openStage()
      const setItem = vi.spyOn(Storage.prototype, 'setItem')
      useStageStore.getState().openTab(makeTab({ tabId: 'a' }))
      expect(useStageStore.getState().open).toBe(true)
      expect(setItem.mock.calls.some(([k]) => k === 'stage.open')).toBe(false)
      setItem.mockRestore()
    })

    it('focusTab brings a library tab into the workset and reveals stage', () => {
      useStageStore.setState({
        tabs: [makeTab({ tabId: 'a' })],
      } as never, false)
      useStageStore.getState().focusTab('a')
      const s = useStageStore.getState()
      expect(s.openTabIds.has('a')).toBe(true)
      expect(s.activeTabId).toBe('a')
      expect(s.open).toBe(true)
      expect(localStorage.getItem('stage.open')).toBe('true')
    })

    it('focusTab on an archived tab is a no-op and does not reveal stage', () => {
      useStageStore.setState({
        tabs: [makeTab({ tabId: 'a', archived: true })],
      } as never, false)
      useStageStore.getState().focusTab('a')
      const s = useStageStore.getState()
      expect(s.openTabIds.has('a')).toBe(false)
      expect(s.activeTabId).toBe(null)
      expect(s.open).toBe(false)
      expect(localStorage.getItem('stage.open')).toBeNull()
    })
  })

  describe('tabs / library / workset', () => {
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

    it('detachFromWorkset keeps the stage open when the last workset tab is removed', () => {
      useStageStore.setState({
        open: true,
        tabs: [makeTab({ tabId: 'a' })],
        openTabIds: new Set(['a']),
        openTabIdsOrdered: ['a'],
        activeTabId: 'a',
      } as never, false)

      useStageStore.getState().detachFromWorkset('a')

      const s = useStageStore.getState()
      expect(s.openTabIdsOrdered).toEqual([])
      expect(s.activeTabId).toBe(null)
      expect(s.open).toBe(true)
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
      setItem.mockRestore()
    })
  })

  describe('__hydrateAll seedWorkset behavior', () => {
    it('seeds non-archived hydrated tabs into workset', () => {
      useStageStore.getState().__hydrateAll([
        makeTab({ tabId: 'a' }),
        makeTab({ tabId: 'b' }),
      ])
      const s = useStageStore.getState()
      expect(s.openTabIds.has('a')).toBe(true)
      expect(s.openTabIds.has('b')).toBe(true)
      expect(s.openTabIdsOrdered).toEqual(['a', 'b'])
    })

    it('skips archived hydrated tabs from workset', () => {
      useStageStore.getState().__hydrateAll([
        makeTab({ tabId: 'a', archived: false }),
        makeTab({ tabId: 'b', archived: true }),
      ])
      const s = useStageStore.getState()
      expect(s.openTabIds.has('a')).toBe(true)
      expect(s.openTabIds.has('b')).toBe(false)
      expect(s.openTabIdsOrdered).toEqual(['a'])
    })

    it('does not duplicate tabs already in workset', () => {
      useStageStore.setState({
        tabs: [makeTab({ tabId: 'a' })],
        openTabIds: new Set(['a']),
        openTabIdsOrdered: ['a'],
      } as never, false)
      useStageStore.getState().__hydrateAll([makeTab({ tabId: 'a' })])
      const s = useStageStore.getState()
      expect(s.openTabIdsOrdered).toEqual(['a'])
    })

    it('preserves existing workset order; appends newly seeded tabs to tail', () => {
      useStageStore.setState({
        tabs: [makeTab({ tabId: 'a' })],
        openTabIds: new Set(['a']),
        openTabIdsOrdered: ['a'],
      } as never, false)
      useStageStore.getState().__hydrateAll([
        makeTab({ tabId: 'b' }),
        makeTab({ tabId: 'c' }),
      ])
      const s = useStageStore.getState()
      expect(s.openTabIdsOrdered).toEqual(['a', 'b', 'c'])
    })

    it('restores only the persisted workset snapshot on hydrate when one exists', () => {
      localStorage.setItem('stage.workset.order', JSON.stringify(['b']))
      localStorage.setItem('stage.workset.active', 'b')

      useStageStore.getState().__hydrateAll([
        makeTab({ tabId: 'a' }),
        makeTab({ tabId: 'b' }),
        makeTab({ tabId: 'c' }),
      ])

      const s = useStageStore.getState()
      expect(s.openTabIdsOrdered).toEqual(['b'])
      expect(s.openTabIds.has('a')).toBe(false)
      expect(s.openTabIds.has('b')).toBe(true)
      expect(s.openTabIds.has('c')).toBe(false)
      expect(s.activeTabId).toBe('b')
    })

    it('does not auto-seed all tabs on hydrate when the persisted workset snapshot is empty', () => {
      localStorage.setItem('stage.workset.order', JSON.stringify([]))

      useStageStore.getState().__hydrateAll([
        makeTab({ tabId: 'a' }),
        makeTab({ tabId: 'b' }),
      ])

      const s = useStageStore.getState()
      expect(s.openTabIdsOrdered).toEqual([])
      expect(s.openTabIds.size).toBe(0)
      expect(s.activeTabId).toBe(null)
    })

    it('hydration leaves the current `open` value untouched when closed', () => {
      // Ctrl+R after the user closed Stage: persisted open is null, the
      // store starts closed, and tab metadata hydrates without flipping the
      // panel back open.
      expect(useStageStore.getState().open).toBe(false)
      useStageStore.getState().__hydrateAll([
        makeTab({ tabId: 'a' }),
      ])
      const s = useStageStore.getState()
      expect(s.open).toBe(false)
      expect(s.openTabIdsOrdered).toEqual(['a'])
      expect(s.activeTabId).toBe('a')
    })

    it('hydration leaves the current `open` value untouched when open', () => {
      // Ctrl+R while Stage was open: the store reads stage.open=true at
      // module init (covered separately), so by the time __hydrateAll runs
      // the panel is already open. Hydration must not reset that.
      useStageStore.setState({ open: true } as never, false)

      useStageStore.getState().__hydrateAll([
        makeTab({ tabId: 'a' }),
      ])

      const s = useStageStore.getState()
      expect(s.open).toBe(true)
      expect(s.openTabIdsOrdered).toEqual(['a'])
      expect(s.activeTabId).toBe('a')
    })

    it('module reads stage.open=true synchronously so the first paint is open', async () => {
      // The whole point of synchronous restore: no slide-in animation on
      // refresh. Verifying the module init path means the very first store
      // snapshot already has open=true.
      localStorage.setItem('stage.open', 'true')
      vi.resetModules()
      const mod = await import('./stage-store')
      expect(mod.useStageStore.getState().open).toBe(true)
    })
  })

  describe('trashTab rollback on persistence failure', () => {
    it('reverts workset state when coordinator.delete throws', async () => {
      const bootstrap = await import('@/features/stage/persistence/stage-persistence-bootstrap')
      const deleteSpy = vi.spyOn(bootstrap.coordinator, 'delete').mockRejectedValue(new Error('boom'))

      useStageStore.setState({
        tabs: [makeTab({ tabId: 'a' }), makeTab({ tabId: 'b' })],
        openTabIds: new Set(['a', 'b']),
        openTabIdsOrdered: ['a', 'b'],
        activeTabId: 'b',
      } as never, false)

      await expect(useStageStore.getState().trashTab('b')).rejects.toThrow('boom')

      const s = useStageStore.getState()
      expect(s.openTabIdsOrdered).toEqual(['a', 'b'])
      expect(s.openTabIds.has('b')).toBe(true)
      expect(s.activeTabId).toBe('b')
      expect(s.tabs.map((t) => t.tabId)).toEqual(['a', 'b'])

      deleteSpy.mockRestore()
    })
  })
})
