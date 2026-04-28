import type { ReactNode } from 'react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, within } from '@testing-library/react'
import { StageWindow } from './stage-window'
import { StageTabBar } from './stage-tab-bar'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'

const realOpenQueryEditor = useStageStore.getState().openQueryEditor

vi.mock('./sql-workbench-tab', () => ({
  SqlWorkbenchTab: ({ tab }: { tab: { title: string } }) => (
    <div data-testid="sql-workbench-tab">{tab.title}</div>
  ),
}))

vi.mock('./left-rail/stage-left-rail', () => ({
  StageLeftRail: () => (
    <div data-testid="stage-left-rail-mock">left-rail:none</div>
  ),
}))

vi.mock('./stage-tab-bar-add-button', () => ({
  StageTabBarAddButton: () => (
    <button type="button" data-testid="stage-tab-bar-add-button-mock">add:none</button>
  ),
}))

vi.mock('./file-preview-tab', () => ({
  FilePreviewTab: () => <div data-testid="file-preview-tab">file preview tab</div>,
}))

vi.mock('@/components/ui/context-menu', () => ({
  ContextMenu: ({ children }: { children?: ReactNode }) => <>{children}</>,
  ContextMenuTrigger: ({ render, children }: { render?: ReactNode; children?: ReactNode }) => <>{render ?? children}</>,
  ContextMenuContent: ({ children }: { children?: ReactNode }) => <div data-testid="context-menu-content">{children}</div>,
  ContextMenuItem: ({
    children,
    onClick,
    disabled,
  }: { children?: ReactNode; onClick?: () => void; disabled?: boolean }) => (
    <button type="button" onClick={disabled ? undefined : onClick} disabled={disabled}>
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

function installOpenQueryEditorSpy() {
  const spy = vi.fn((input: Parameters<typeof realOpenQueryEditor>[0]) => realOpenQueryEditor(input))
  useStageStore.setState({ openQueryEditor: spy })
  return spy
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
    pinned: false,
    ...over,
  }
}

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
    useSessionStore.setState({ activeSessionId: null })
    useStageStore.setState({
      open: true,
      autoOpened: false,
      maximized: false,
      sidebarCollapsed: false,
      sidebarSelection: null,
      resourceTreeExpanded: [],
      activeRailPanel: null,
      tabs: [],
      openTabIds: new Set<string>(),
      openTabIdsOrdered: [],
      activeTabId: null,
      openQueryEditor: realOpenQueryEditor,
    })
    useOntologyStore.setState({ artifactsBySession: new Map() })
    useTimelineStore.setState({
      orderBySession: new Map(),
      activeBySession: new Map(),
      manualBySession: new Map(),
    })
  })

  it('渲染默认标题 工作台 + 关闭 / 最大化 按钮', () => {
    render(<StageWindow />)
    expect(screen.getByLabelText('关闭')).toBeTruthy()
    expect(screen.getByLabelText('最大化')).toBeTruthy()
    expect(screen.getByText('工作台', { selector: 'span' })).toBeTruthy()
  })

  it('renders the Stage shell container around the workspace pane', () => {
    const { container } = render(<StageWindow />)
    const shell = container.firstElementChild as HTMLElement | null

    expect(shell).toBeTruthy()
    expect(within(shell as HTMLElement).getByTestId('stage-workspace-pane')).toBeTruthy()
  })

  it('点关闭触发 closeStage()', () => {
    render(<StageWindow />)
    fireEvent.click(screen.getByLabelText('关闭'))
    expect(useStageStore.getState().open).toBe(false)
  })

  it('点最大化切换 maximized', () => {
    render(<StageWindow />)
    fireEvent.click(screen.getByLabelText('最大化'))
    expect(useStageStore.getState().maximized).toBe(true)
    fireEvent.click(screen.getByLabelText('还原'))
    expect(useStageStore.getState().maximized).toBe(false)
  })

  it('keeps the shell title stable when the active artifact changes', () => {
    useOntologyStore.getState().upsertArtifact('s1', { id: 'a1', version: 2, kind: 'chart' })
    useTimelineStore.setState({
      orderBySession: new Map([['s1', ['a1']]]),
      activeBySession: new Map([['s1', 'a1']]),
      manualBySession: new Map(),
    })
    render(<StageWindow />)
    expect(screen.getByText('工作台', { selector: 'span' })).toBeTruthy()
    expect(screen.queryByText(/工作台 · 图 v2/)).toBeNull()
  })

  it('renders tab bar when store has tabs', () => {
    useStageStore.setState({
      tabs: [
        { tabId: 'q1', type: 'query_editor', title: 'SQL',
          originSessionId: 's-1', createdAt: 0, payload: {} },
      ],
      activeTabId: 'q1',
      openTabIds: new Set(['q1']),
      openTabIdsOrdered: ['q1'],
    })
    render(<StageWindow />)
    // Both the tab bar label and the mocked SqlWorkbenchTab content render "SQL"
    const sqlElements = screen.getAllByText('SQL')
    expect(sqlElements.length).toBeGreaterThanOrEqual(1)
  })

  it('renders the new Stage empty workbench instead of the legacy child path when there are no tabs', () => {
    useStageStore.setState({
      tabs: [],
      activeTabId: null,
    })

    render(<StageWindow />)

    expect(screen.getByTestId('stage-empty-workbench')).toBeTruthy()
    expect(screen.getByRole('button', { name: /SQL 编辑器/ })).toBeTruthy()
    expect(screen.getByRole('button', { name: /ER 图设计器/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /报表/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Dashboard/ })).toBeDisabled()
  })

  it('does not render the activity rail at the window level (rail moved into SQL tab)', () => {
    useStageStore.setState({
      tabs: [],
      activeTabId: null,
      activeRailPanel: null,
    })

    render(<StageWindow />)

    expect(screen.queryByTestId('stage-activity-rail')).toBeNull()
    expect(screen.queryByTestId('stage-sidebar')).toBeNull()
    expect(screen.queryByTestId('stage-resource-browser')).toBeNull()
    expect(screen.queryByTestId('stage-tool-row')).toBeNull()
  })

  it('clicking the empty-state CTA opens the SQL editor via openQueryEditor directly', () => {
    const openQueryEditorSpy = installOpenQueryEditorSpy()
    useStageStore.setState({
      tabs: [],
      activeTabId: null,
    })

    render(<StageWindow />)

    fireEvent.click(screen.getByRole('button', { name: /SQL 编辑器/ }))

    expect(openQueryEditorSpy).toHaveBeenCalledWith({
      sessionId: null,
      baseTitle: 'SQL 编辑器',
      openMode: 'always_new',
      entryMode: 'blank',
    })
    expect(useStageStore.getState().tabs).toHaveLength(1)
    expect(useStageStore.getState().activeTabId).toBeTruthy()
    expect(screen.getByTestId('sql-workbench-tab').textContent).toBe('SQL 编辑器')
  })

  it.each([
    ['er_canvas', 'ER Canvas'],
    ['report', 'Report'],
    ['dashboard', 'Dashboard'],
  ])('does not render legacy %s tabs in Stage content', (type, title) => {
    useStageStore.setState({
      tabs: [
        { tabId: 'w1', type: type as 'er_canvas' | 'report' | 'dashboard', title, createdAt: 0, payload: {} },
      ],
      activeTabId: 'w1',
      openTabIds: new Set(['w1']),
      openTabIdsOrdered: ['w1'],
    })

    render(<StageWindow />)

    expect(screen.queryByTestId('stage-placeholder-tab')).toBeNull()
    expect(screen.queryByTestId('sql-workbench-tab')).toBeNull()
    expect(screen.queryByText(title)).toBeTruthy()
  })

  it('renders workspace tab content inside a session stage when no session tab is active', () => {
    useStageStore.setState({
      tabs: [
        { tabId: 'w1', type: 'query_editor', title: 'Global SQL', createdAt: 0, payload: {} },
      ],
      activeTabId: 'w1',
      openTabIds: new Set(['w1']),
      openTabIdsOrdered: ['w1'],
    })

    render(<StageWindow />)

    expect(screen.getByRole('tab', { name: 'Global SQL' })).toBeTruthy()
    expect(screen.getByTestId('sql-workbench-tab')).toBeTruthy()
  })

  it('switches to the Stage start page when clicking the + button', () => {
    useStageStore.setState({
      tabs: [
        { tabId: 'w1', type: 'query_editor', title: 'Global SQL', createdAt: 0, payload: {} },
      ],
      activeTabId: 'w1',
      openTabIds: new Set(['w1']),
      openTabIdsOrdered: ['w1'],
    })

    render(<StageWindow />)

    fireEvent.click(screen.getByLabelText('开始页'))

    expect(screen.getByTestId('stage-empty-workbench')).toBeTruthy()
    expect(screen.queryByTestId('sql-workbench-tab')).toBeNull()
  })

  it('creates a new workspace SQL tab and clears the session-scoped active tab after using the start-page SQL action', () => {
    const openQueryEditorSpy = installOpenQueryEditorSpy()
    useSessionStore.setState({ activeSessionId: 's1' })
    useStageStore.setState({
      tabs: [
        {
          tabId: 'workspace-sql',
          type: 'query_editor',
          title: 'SQL 编辑器',
          createdAt: 1,
          payload: {},
        },
        {
          tabId: 'session-sql',
          type: 'query_editor',
          title: 'Session SQL',
          originSessionId: 's1',
          createdAt: 0,
          payload: {},
        },
      ],
      activeTabId: 'session-sql',
      openTabIds: new Set(['workspace-sql', 'session-sql']),
      openTabIdsOrdered: ['workspace-sql', 'session-sql'],
    })

    render(<StageWindow />)

    fireEvent.click(screen.getByLabelText('开始页'))
    fireEvent.click(screen.getByRole('button', { name: /SQL 编辑器/ }))

    expect(openQueryEditorSpy).toHaveBeenCalledWith({
      sessionId: null,
      baseTitle: 'SQL 编辑器',
      openMode: 'always_new',
      entryMode: 'blank',
    })
    expect(useStageStore.getState().tabs).toHaveLength(3)
    expect(useStageStore.getState().activeTabId).toBeTruthy()
    expect(screen.getByTestId('sql-workbench-tab').textContent).toBe('SQL 编辑器2')
  })

  it('does not fall back to the empty state when a file_preview tab is active', () => {
    useSessionStore.setState({ activeSessionId: 's1' })
    useStageStore.setState({
      tabs: [
        {
          tabId: 'preview-1',
          type: 'file_preview',
          title: 'README.md',
          originSessionId: 's1',
          createdAt: 0,
          payload: { sourceKey: 'readme' },
        },
      ],
      activeTabId: 'preview-1',
      openTabIds: new Set(['preview-1']),
      openTabIdsOrdered: ['preview-1'],
    })

    render(<StageWindow />)

    expect(screen.queryByTestId('stage-empty-workbench')).toBeNull()
    expect(screen.getByTestId('file-preview-tab')).toBeTruthy()
  })

  it('clears the session-scoped active tab when a workspace tab is focused from the shared tab bar', () => {
    useStageStore.setState({
      tabs: [
        { tabId: 'w1', type: 'query_editor', title: 'Global SQL', createdAt: 0, payload: {} },
        { tabId: 's-tab', type: 'query_editor', title: 'Session SQL', originSessionId: 's1', createdAt: 0, payload: {} },
      ],
      activeTabId: 's-tab',
      openTabIds: new Set(['w1', 's-tab']),
      openTabIdsOrdered: ['w1', 's-tab'],
    })

    render(<StageWindow />)

    fireEvent.click(screen.getByRole('tab', { name: 'Global SQL' }))

    expect(useStageStore.getState().activeTabId).toBe('w1')
  })

  it('wires the Stage shell without rendering the bottom dock', () => {
    useStageStore.setState({
      tabs: [
        { tabId: 'q1', type: 'query_editor', title: 'SQL',
          originSessionId: 's1', createdAt: 0, payload: {} },
      ],
      activeTabId: 'q1',
      openTabIds: new Set(['q1']),
      openTabIdsOrdered: ['q1'],
    })

    render(<StageWindow />)

    expect(screen.queryByTestId('stage-dock')).toBeNull()
    // Both the tab bar label and the mocked SqlWorkbenchTab content render "SQL"
    const sqlElements = screen.getAllByText('SQL')
    expect(sqlElements.length).toBeGreaterThanOrEqual(1)
    expect(screen.getByTestId('stage-workspace-pane')).toBeTruthy()
  })

  it('renders left rail + tab bar + content pane when openTabIds is non-empty', () => {
    useStageStore.setState({
      tabs: [makeTab({ tabId: 'qe-1', title: 'one' })],
      openTabIds: new Set(['qe-1']),
      openTabIdsOrdered: ['qe-1'],
      activeTabId: 'qe-1',
      leftRailCollapsed: false,
    } as never, false)

    render(<StageWindow />)

    // Left rail visible (mocked)
    expect(screen.getByTestId('stage-left-rail-mock')).toBeInTheDocument()
    // Content pane testid
    expect(screen.getByTestId('stage-workspace-pane')).toBeInTheDocument()
    // Tab bar renders the tab
    expect(screen.getByRole('tab', { name: /one/ })).toBeTruthy()
  })

  it('renders empty state in right pane when openTabIds is empty', () => {
    useStageStore.setState({
      tabs: [],
      openTabIds: new Set<string>(),
      activeTabId: null,
    } as never, false)

    render(<StageWindow />)

    // Empty state should render
    expect(screen.getByTestId('stage-empty-workbench')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /SQL 编辑器/ })).toBeTruthy()
  })

  it('clicking close X on a top-bar tab detaches but keeps it in left rail', () => {
    useStageStore.setState({
      tabs: [makeTab({ tabId: 'qe-1', title: 'one' })],
      openTabIds: new Set(['qe-1']),
      openTabIdsOrdered: ['qe-1'],
      activeTabId: 'qe-1',
    } as never, false)

    render(<StageWindow />)
    // Find the tab's close button (inside the tab element)
    const tab = screen.getByRole('tab', { name: /one/ })
    const closeBtn = within(tab).getByRole('button', { name: /关闭/ })
    fireEvent.click(closeBtn)

    expect(useStageStore.getState().openTabIds.has('qe-1')).toBe(false)
    // Tab still in left rail (the tab still exists in tabs)
    expect(useStageStore.getState().tabs.find((t) => t.tabId === 'qe-1')).toBeDefined()
  })

})
