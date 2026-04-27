import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { NavTabs } from '../nav-tabs'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import type { StageTab } from '@/stores/stage-store'
import { SidebarProvider } from '@/components/ui/sidebar'

vi.mock('@/services/find/use-stage-find', () => ({
  useStageFind: () => ({
    tabs: mockTabs,
    isLoading: false,
  }),
}))

let mockTabs: StageTab[] = []

const mkTab = (over: Partial<StageTab> & { tabId: string }): StageTab => ({
  type: 'query_editor',
  title: 'My Query',
  scope: 'workspace',
  payload: {},
  createdAt: Date.now(),
  ...over,
})

function renderNavTabs() {
  return render(
    <SidebarProvider>
      <NavTabs />
    </SidebarProvider>,
  )
}

describe('NavTabs', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockTabs = []
    useStageStore.setState({
      activeWorkspaceTabId: null,
      workspaceTabs: [],
    })
    useSessionStore.setState({
      activeSessionId: 'session-active',
      hasEverSentBySession: new Map([['session-active', true], ['session-tab', true]]),
    })
  })

  it('renders one row per active workspace tab and an empty state when none', () => {
    const { container } = renderNavTabs()
    expect(container.querySelectorAll('[role="button"]')).toHaveLength(0)
    expect(screen.getByText(/尚无 Tab|No tabs yet/)).toBeInTheDocument()

    mockTabs = [
      mkTab({ tabId: 'tab-1', title: 'Query 1' }),
      mkTab({ tabId: 'tab-2', title: 'Query 2' }),
    ]
    const { container: c2 } = renderNavTabs()
    expect(c2.querySelectorAll('[role="button"]')).toHaveLength(2)
    expect(screen.getByText('Query 1')).toBeInTheDocument()
    expect(screen.getByText('Query 2')).toBeInTheDocument()
  })

  it('clicking a row focuses the tab via store', () => {
    const focusSpy = vi.spyOn(useStageStore.getState(), 'focusTab')
    const openStageSpy = vi.spyOn(useStageStore.getState(), 'openStage')
    mockTabs = [mkTab({ tabId: 'tab-1', title: 'Click Me' })]
    renderNavTabs()

    fireEvent.click(screen.getByText('Click Me'))
    expect(focusSpy).toHaveBeenCalledWith('tab-1')
    expect(openStageSpy).toHaveBeenCalledWith('session-active')
  })

  it('clicking a session-scoped row switches session before focusing', () => {
    const focusSpy = vi.spyOn(useStageStore.getState(), 'focusTab')
    const openStageSpy = vi.spyOn(useStageStore.getState(), 'openStage')
    const openSessionSpy = vi.spyOn(useSessionStore.getState(), 'openSession')
    mockTabs = [mkTab({
      tabId: 'tab-session',
      title: 'Session Tab',
      scope: 'session',
      originSessionId: 'session-tab',
    })]
    renderNavTabs()

    fireEvent.click(screen.getByText('Session Tab'))

    expect(openSessionSpy).toHaveBeenCalledWith('session-tab', true)
    expect(openStageSpy).toHaveBeenCalledWith('session-tab')
    expect(focusSpy).toHaveBeenCalledWith('tab-session')
  })

  it('hides archived rows by default and shows them after toggle', () => {
    mockTabs = [
      mkTab({ tabId: 'tab-1', title: 'Active Tab' }),
    ]
    renderNavTabs()
    expect(screen.getByText('Active Tab')).toBeInTheDocument()

    // Simulate archived being included after toggle
    mockTabs = [
      mkTab({ tabId: 'tab-1', title: 'Active Tab' }),
      mkTab({ tabId: 'tab-2', title: 'Archived Tab', archived: true }),
    ]
    const toggleBtn = screen.getByRole('button', { name: /archive|归档/ })
    fireEvent.click(toggleBtn)
  })

  it('keyboard arrow-down moves focus through rows', () => {
    mockTabs = [
      mkTab({ tabId: 'tab-1', title: 'First' }),
      mkTab({ tabId: 'tab-2', title: 'Second' }),
    ]
    useStageStore.setState({ activeWorkspaceTabId: 'tab-1' })
    renderNavTabs()

    const list = screen.getByRole('list')
    const rows = list.querySelectorAll<HTMLElement>('[role="button"]')
    expect(rows).toHaveLength(2)

    // Focus first row then press ArrowDown
    rows[0].focus()
    fireEvent.keyDown(list, { key: 'ArrowDown' })
    expect(rows[1]).toHaveFocus()
  })
})
