import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { StageRailRow } from './stage-rail-row'
import { useStageStore, type StageTab } from '@/stores/stage-store'

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({ t: (k: string, vars?: Record<string, unknown>) => {
    if (vars && typeof vars.title === 'string') return `${k}:${vars.title}`
    return k
  } }),
}))

vi.mock('@/features/stage/registry/tab-type-registry', () => ({
  getTabTypeDescriptor: () => ({
    icon: () => null,
    labelKey: 'tabType.queryEditor',
  }),
}))

vi.mock('@/features/stage/persistence/stage-persistence-bootstrap', () => ({
  coordinator: { delete: vi.fn().mockResolvedValue(undefined) },
}))

function makeTab(over: Partial<StageTab> = {}): StageTab {
  return {
    tabId: 'qe-1',
    type: 'query_editor',
    title: 'tab-1',
    payload: {},
    payloadVersion: 1,
    createdAt: 0,
    lastTouchedAt: 0,
    archived: false,
    pinned: false,
    ...over,
  }
}

describe('StageRailRowMenu trash dialog', () => {
  beforeEach(() => {
    useStageStore.setState({
      tabs: [makeTab()],
      openTabIds: new Set(['qe-1']),
      openTabIdsOrdered: ['qe-1'],
      activeTabId: 'qe-1',
      open: true,
    } as never, false)
  })

  it('confirming trash does not re-focus the deleted tab via click bubbling', async () => {
    const tab = makeTab()
    const onClick = vi.fn(() => useStageStore.getState().focusTab(tab.tabId))

    render(
      <StageRailRow
        tab={tab}
        active
        inWorkset
        onClick={onClick}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'stage.leftRail.row.menu' }))
    fireEvent.click(screen.getByRole('menuitem', { name: /stage\.leftRail\.row\.menu\.trash/ }))
    onClick.mockClear()
    fireEvent.click(screen.getByRole('button', { name: 'stage.leftRail.confirmTrash.confirm' }))

    await waitFor(() => {
      expect(useStageStore.getState().tabs).toHaveLength(0)
    })

    const state = useStageStore.getState()
    expect(state.activeTabId).toBeNull()
    expect(state.openTabIdsOrdered).toEqual([])
    expect(Array.from(state.openTabIds)).toEqual([])
    expect(onClick).not.toHaveBeenCalled()
  })
})
