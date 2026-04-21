import type { ReactNode } from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, within } from '@testing-library/react'
import { StageWindow } from './stage-window'
import { StageSidebar } from './stage-sidebar'
import { StageTabBar } from './stage-tab-bar'
import { useStageStore } from '@/stores/stage-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'

vi.mock('./stage-tool-row', () => ({
  StageToolRow: () => <div data-testid="stage-tool-row">tool row</div>,
}))

vi.mock('./stage-resource-browser', () => ({
  StageResourceBrowser: () => <div data-testid="stage-resource-browser">resource browser</div>,
}))

vi.mock('./stage-tab-content', () => ({
  StageTabContent: () => <div data-testid="stage-tab-content">tab content</div>,
}))

vi.mock('@/components/ui/context-menu', () => ({
  ContextMenu: ({ children }: { children?: ReactNode }) => <>{children}</>,
  ContextMenuTrigger: ({ render, children }: { render?: ReactNode; children?: ReactNode }) => <>{render ?? children}</>,
  ContextMenuContent: ({ children }: { children?: ReactNode }) => <div data-testid="context-menu-content">{children}</div>,
  ContextMenuItem: ({ children, onSelect }: { children?: ReactNode; onSelect?: () => void }) => (
    <button type="button" onClick={onSelect}>
      {children}
    </button>
  ),
  ContextMenuSeparator: () => <span data-testid="context-menu-separator" />,
}))

const stageTabs = [
  { tabId: 'left', title: 'Left', type: 'query_editor' },
  { tabId: 'active', title: 'Active', type: 'query_editor' },
  { tabId: 'right', title: 'Right', type: 'query_editor' },
]

describe('StageTabBar', () => {
  it('marks the active tab with a semantic state and keeps close clickable', () => {
    const onClose = vi.fn()

    render(
      <StageTabBar
        tabs={stageTabs}
        activeId="active"
        onClose={onClose}
      />
    )

    const activeTab = screen.getByText('Active').closest('[data-tab-id="active"]')
    expect(activeTab?.getAttribute('data-state')).toBe('active')

    fireEvent.click(within(activeTab as HTMLElement).getByLabelText('关闭'))

    expect(onClose).toHaveBeenCalledWith('active')
  })

  it('keeps close others / close all / close left / close right menu actions wired', async () => {
    const onClose = vi.fn()
    const onCloseOthers = vi.fn()
    const onCloseAll = vi.fn()
    const onCloseLeft = vi.fn()
    const onCloseRight = vi.fn()

    render(
      <StageTabBar
        tabs={stageTabs}
        activeId="active"
        onClose={onClose}
        onCloseOthers={onCloseOthers}
        onCloseAll={onCloseAll}
        onCloseLeft={onCloseLeft}
        onCloseRight={onCloseRight}
      />
    )

    const activeTab = screen.getByText('Active').closest('[data-tab-id="active"]') as HTMLElement
    const activeMenu = activeTab.nextElementSibling as HTMLElement

    fireEvent.click(within(activeMenu).getByText('关闭其他'))
    expect(onCloseOthers).toHaveBeenCalledWith('active')

    fireEvent.click(within(activeMenu).getByText('关闭全部'))
    expect(onCloseAll).toHaveBeenCalledWith()

    fireEvent.click(within(activeMenu).getByText('关闭左侧标签页'))
    expect(onCloseLeft).toHaveBeenCalledWith('active')

    fireEvent.click(within(activeMenu).getByText('关闭右侧标签页'))
    expect(onCloseRight).toHaveBeenCalledWith('active')

    fireEvent.click(within(activeMenu).getByText('关闭'))
    expect(onClose).toHaveBeenCalledWith('active')
  })
})

describe('StageWindow', () => {
  beforeEach(() => {
    useStageStore.setState({
      openBySession: new Map([['s1', true]]),
      autoOpenedSessions: new Set(),
      maximizedBySession: new Map(),
      sidebarCollapsedBySession: new Map(),
      sidebarSelectionBySession: new Map(),
      resourceTreeExpandedBySession: new Map(),
      workspaceTabs: [],
      tabsBySession: new Map(),
      activeWorkspaceTabId: null,
      activeTabIdBySession: new Map(),
    })
    useOntologyStore.setState({ artifactsBySession: new Map() })
    useTimelineStore.setState({
      orderBySession: new Map(),
      activeBySession: new Map(),
      manualBySession: new Map(),
    })
  })

  it('渲染默认标题 Stage + 关闭 / 最大化 按钮', () => {
    render(<StageWindow sessionId="s1"><div>body</div></StageWindow>)
    expect(screen.getByLabelText('关闭')).toBeTruthy()
    expect(screen.getByLabelText('最大化')).toBeTruthy()
    expect(screen.getByText('Stage', { selector: 'span' })).toBeTruthy()
  })

  it('点关闭触发 closeStage(sessionId)', () => {
    render(<StageWindow sessionId="s1"><div>body</div></StageWindow>)
    fireEvent.click(screen.getByLabelText('关闭'))
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
  })

  it('点最大化切换 maximizedBySession', () => {
    render(<StageWindow sessionId="s1"><div>body</div></StageWindow>)
    fireEvent.click(screen.getByLabelText('最大化'))
    expect(useStageStore.getState().maximizedBySession.get('s1')).toBe(true)
    fireEvent.click(screen.getByLabelText('还原'))
    expect(useStageStore.getState().maximizedBySession.get('s1')).toBe(false)
  })

  it('标题随 active artifact 变化', () => {
    useOntologyStore.getState().upsertArtifact('s1', { id: 'a1', version: 2, kind: 'chart' })
    useTimelineStore.setState({
      orderBySession: new Map([['s1', ['a1']]]),
      activeBySession: new Map([['s1', 'a1']]),
      manualBySession: new Map(),
    })
    render(<StageWindow sessionId="s1"><div>body</div></StageWindow>)
    expect(screen.getByText(/Stage · 图 v2/)).toBeTruthy()
  })

  it('children 渲染在 body slot', () => {
    render(<StageWindow sessionId="s1"><div data-testid="child">CHILD</div></StageWindow>)
    expect(screen.getByTestId('child')).toBeTruthy()
  })

  it('renders tab bar when store has tabs for session', () => {
    useStageStore.setState({
      tabsBySession: new Map([['s-1', [
        { tabId: 'q1', type: 'query_editor', title: 'SQL', scope: 'session' as const,
          originSessionId: 's-1', createdAt: 0, payload: {} },
      ]]]),
      activeTabIdBySession: new Map([['s-1', 'q1']]),
    })
    render(<StageWindow sessionId="s-1"><div>ai content</div></StageWindow>)
    expect(screen.getByText('SQL')).toBeTruthy()
  })

  it('shows children when store has no tabs', () => {
    useStageStore.setState({
      tabsBySession: new Map(),
      activeTabIdBySession: new Map(),
    })
    render(<StageWindow sessionId="s-1"><div>ai content</div></StageWindow>)
    expect(screen.getByText('ai content')).toBeTruthy()
  })

  it('renders workspace tab content inside a session stage when no session tab is active', () => {
    useStageStore.setState({
      workspaceTabs: [
        { tabId: 'w1', type: 'query_editor', title: 'Global SQL', scope: 'workspace' as const, createdAt: 0, payload: {} },
      ],
      activeWorkspaceTabId: 'w1',
      tabsBySession: new Map(),
      activeTabIdBySession: new Map([['s1', null]]),
    })

    render(<StageWindow sessionId="s1"><div>body</div></StageWindow>)

    expect(screen.getByText('Global SQL')).toBeTruthy()
    expect(screen.getByTestId('stage-tab-content')).toBeTruthy()
    expect(screen.queryByText('body')).toBeNull()
  })

  it('clears the session-scoped active tab when a workspace tab is focused from the shared tab bar', () => {
    useStageStore.setState({
      workspaceTabs: [
        { tabId: 'w1', type: 'query_editor', title: 'Global SQL', scope: 'workspace' as const, createdAt: 0, payload: {} },
      ],
      activeWorkspaceTabId: 'w1',
      tabsBySession: new Map([['s1', [
        { tabId: 's-tab', type: 'query_editor', title: 'Session SQL', scope: 'session' as const, originSessionId: 's1', createdAt: 0, payload: {} },
      ]]]),
      activeTabIdBySession: new Map([['s1', 's-tab']]),
    })

    render(<StageWindow sessionId="s1"><div>body</div></StageWindow>)

    fireEvent.click(screen.getByText('Global SQL'))

    expect(useStageStore.getState().activeTabIdBySession.get('s1')).toBeNull()
  })

  it('renders the sidebar shell in expanded mode with slot placeholders', () => {
    render(
      <StageSidebar
        collapsed={false}
        onToggleCollapsed={() => {}}
        toolRowSlot={<div data-testid="tool-row-slot">tool row</div>}
        resourceBrowserSlot={<div data-testid="resource-browser-slot">resource browser</div>}
      />
    )

    expect(screen.getByTestId('stage-sidebar')).toBeTruthy()
    expect(screen.getByTestId('tool-row-slot')).toBeTruthy()
    expect(screen.getByTestId('resource-browser-slot')).toBeTruthy()
    expect(screen.getByRole('button', { name: '收起资源栏' })).toBeTruthy()
  })

  it('renders the collapsed sidebar shell and hides inner slots', () => {
    render(
      <StageSidebar
        collapsed
        onToggleCollapsed={() => {}}
        toolRowSlot={<div data-testid="tool-row-slot">tool row</div>}
        resourceBrowserSlot={<div data-testid="resource-browser-slot">resource browser</div>}
      />
    )

    expect(screen.getByTestId('stage-sidebar').getAttribute('data-state')).toBe('collapsed')
    expect(screen.getByTestId('tool-row-slot')).toBeTruthy()
    expect(screen.queryByTestId('resource-browser-slot')).toBeNull()
    expect(screen.queryByText('展开资源栏')).toBeNull()
    expect(screen.getByRole('button', { name: '展开资源栏' })).toBeTruthy()
  })

  it('wires the Stage shell without rendering the bottom dock', () => {
    useStageStore.setState({
      sidebarCollapsedBySession: new Map([['s1', false]]),
      tabsBySession: new Map([['s1', [
        { tabId: 'q1', type: 'query_editor', title: 'SQL', scope: 'session' as const,
          originSessionId: 's1', createdAt: 0, payload: {} },
      ]]]),
      activeTabIdBySession: new Map([['s1', 'q1']]),
    })

    render(<StageWindow sessionId="s1"><div>body</div></StageWindow>)

    expect(screen.getByTestId('stage-sidebar')).toBeTruthy()
    expect(screen.getByTestId('stage-tool-row')).toBeTruthy()
    expect(screen.getByTestId('stage-resource-browser')).toBeTruthy()
    expect(screen.queryByTestId('stage-dock')).toBeNull()
    expect(screen.getByText('SQL')).toBeTruthy()
    expect(screen.getByTestId('stage-workspace-pane')).toBeTruthy()
  })

  it('shows a collapsed-sidebar expand entry when the session state is collapsed', () => {
    useStageStore.setState({
      sidebarCollapsedBySession: new Map([['s1', true]]),
      tabsBySession: new Map(),
      activeTabIdBySession: new Map(),
    })

    render(<StageWindow sessionId="s1"><div>body</div></StageWindow>)

    expect(screen.getByRole('button', { name: '展开资源栏' })).toBeTruthy()
  })
})
