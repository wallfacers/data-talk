import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useStageStore } from './stage-store'

function reset() {
  localStorage.removeItem('stage.workset.order')
  localStorage.removeItem('stage.workset.active')
  useStageStore.setState({
    open: false, maximized: false, autoOpened: false,
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

    it('detachFromWorkset keeps the stage open when the last workset tab is removed', () => {
      useStageStore.setState({
        open: true,
        autoOpened: true,
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
      expect(s.autoOpened).toBe(true)
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
