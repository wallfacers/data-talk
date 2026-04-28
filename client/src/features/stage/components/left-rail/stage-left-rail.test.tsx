import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { StageLeftRail } from './stage-left-rail'
import { useStageStore } from '@/stores/stage-store'

// Mock i18n to return keys as labels
vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({ t: (k: string) => k }),
}))

// Mock tab-type-registry
vi.mock('@/features/stage/registry/tab-type-registry', () => ({
  getTabTypeDescriptor: () => ({
    icon: () => null,
    labelKey: 'tabType.queryEditor',
  }),
}))

// Mock StageRailRowMenu since it has complex dependencies
vi.mock('./stage-rail-row-menu', () => ({
  StageRailRowMenu: () => null,
}))

function makeTab(over: Record<string, unknown> = {}) {
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

describe('StageLeftRail', () => {
  beforeEach(() => {
    useStageStore.setState({
      tabs: [],
      openTabIds: new Set<string>(),
      openTabIdsOrdered: [],
      activeTabId: null,
      leftRailCollapsed: false,
    } as never, true)
  })

  it('shows empty state when no tabs', () => {
    render(<StageLeftRail />)
    expect(screen.getByText('stage.leftRail.empty')).toBeInTheDocument()
  })

  it('renders active tabs sorted by pinned first then lastTouchedAt desc', () => {
    useStageStore.setState({
      tabs: [
        makeTab({ tabId: 'a', title: 'old', lastTouchedAt: 1, pinned: false }),
        makeTab({ tabId: 'b', title: 'newer', lastTouchedAt: 5, pinned: false }),
        makeTab({ tabId: 'c', title: 'pinned', lastTouchedAt: 2, pinned: true }),
      ],
    } as never, false)
    render(<StageLeftRail />)
    const rows = screen.getAllByRole('button', { pressed: false })
    const titles = rows.map((r) => r.textContent ?? '')
    expect(titles[0]).toContain('pinned')
  })

  it('collapsed rail shows expand chevron only', () => {
    useStageStore.setState({ leftRailCollapsed: true } as never, false)
    render(<StageLeftRail />)
    expect(screen.queryByText('stage.leftRail.empty')).not.toBeInTheDocument()
    expect(screen.getByLabelText('stage.leftRail.expand')).toBeInTheDocument()
  })

  it('clicking an archived row opens unarchive confirm dialog', () => {
    useStageStore.setState({
      tabs: [makeTab({ tabId: 'a', title: 'frozen', archived: true })],
    } as never, false)
    render(<StageLeftRail />)
    // First, toggle archived group open by finding and clicking it
    fireEvent.click(screen.getByText('stage.leftRail.group.archived'))
    // Then click the archived row
    const archivedRow = screen.getByText('frozen').closest('[role="button"]')!
    fireEvent.click(archivedRow)
    expect(screen.getByText('stage.leftRail.confirmUnarchive.title')).toBeInTheDocument()
  })

  it('search filters tabs by title', () => {
    useStageStore.setState({
      tabs: [
        makeTab({ tabId: 'a', title: 'users monthly' }),
        makeTab({ tabId: 'b', title: 'orders trend' }),
      ],
    } as never, false)
    render(<StageLeftRail />)
    const input = screen.getByPlaceholderText('stage.leftRail.search.placeholder')
    fireEvent.change(input, { target: { value: 'USER' } })
    expect(screen.getByText('users monthly')).toBeInTheDocument()
    expect(screen.queryByText('orders trend')).not.toBeInTheDocument()
  })
})
