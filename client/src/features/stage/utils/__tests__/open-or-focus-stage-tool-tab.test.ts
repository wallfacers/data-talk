import { beforeEach, describe, expect, it, vi } from 'vitest'
import { openOrFocusStageToolTab } from '../open-or-focus-stage-tool-tab'
import { useStageStore } from '@/stores/stage-store'

describe('openOrFocusStageToolTab', () => {
  const openQueryEditorMock = vi.fn()

  beforeEach(() => {
    openQueryEditorMock.mockReset()
    useStageStore.setState({
      open: false,
      maximized: false,
      revealOrigin: null,
      sidebarCollapsed: false,
      sidebarSelection: null,
      resourceTreeExpanded: [],
      activeRailPanel: null,
      tabs: [],
      openTabIds: new Set(),
      openTabIdsOrdered: [],
      activeTabId: null,
      openQueryEditor: openQueryEditorMock,
    } as unknown as Record<string, unknown>)
  })

  it('delegates global SQL tabs to openQueryEditor and returns its result', () => {
    openQueryEditorMock.mockReturnValue({ tabId: 'workspace-sql', created: false })

    const opened = openOrFocusStageToolTab({
      getState: useStageStore.getState,
      sessionId: 's1',
      target: { kind: 'global_tool', tool: 'sql', title: 'SQL 编辑器' },
    })

    expect(opened).toEqual({ tabId: 'workspace-sql', created: false })
    expect(openQueryEditorMock).toHaveBeenCalledWith({
      sessionId: 's1',
      baseTitle: 'SQL 编辑器',
      openMode: 'always_new',
      entryMode: 'blank',
      connectionId: null,
      database: null,
      schema: null,
    })
  })

  it('delegates session resource SQL tabs to openQueryEditor and returns its result', () => {
    openQueryEditorMock.mockReturnValue({ tabId: 'session-sql', created: true })

    const opened = openOrFocusStageToolTab({
      getState: useStageStore.getState,
      sessionId: 's1',
      target: {
        kind: 'resource_tool',
        tool: 'sql',
        title: 'SQL 编辑器',
        connectionId: 'c1',
        database: 'analytics',
        schema: 'public',
      },
    })

    expect(opened).toEqual({ tabId: 'session-sql', created: true })
    expect(openQueryEditorMock).toHaveBeenCalledWith({
      sessionId: 's1',
      baseTitle: 'SQL 编辑器',
      openMode: 'reuse_by_resource_context',
      entryMode: 'resource_sql',
      connectionId: 'c1',
      database: 'analytics',
      schema: 'public',
    })
  })

  it('reuses existing workspace tool tab for the same non-SQL tool type', () => {
    const first = openOrFocusStageToolTab({
      getState: useStageStore.getState,
      sessionId: 's1',
      target: { kind: 'global_tool', tool: 'er', title: 'ER 图设计器' },
    })
    const second = openOrFocusStageToolTab({
      getState: useStageStore.getState,
      sessionId: 's1',
      target: { kind: 'global_tool', tool: 'er', title: 'ER 图设计器' },
    })

    expect(first.created).toBe(true)
    expect(second.created).toBe(false)
    expect(second.tabId).toBe(first.tabId)
    expect(useStageStore.getState().tabs).toHaveLength(1)
    expect(useStageStore.getState().activeTabId).toBe(first.tabId)
  })

  it('creates a new workspace non-SQL tool tab when reuseExisting is false', () => {
    const first = openOrFocusStageToolTab({
      getState: useStageStore.getState,
      sessionId: 's1',
      target: { kind: 'global_tool', tool: 'report', title: '报表' },
      reuseExisting: false,
    })
    const second = openOrFocusStageToolTab({
      getState: useStageStore.getState,
      sessionId: 's1',
      target: { kind: 'global_tool', tool: 'report', title: '报表' },
      reuseExisting: false,
    })

    expect(first.created).toBe(true)
    expect(second.created).toBe(true)
    expect(second.tabId).not.toBe(first.tabId)
    expect(useStageStore.getState().tabs).toHaveLength(2)
    expect(useStageStore.getState().tabs.map((tab) => tab.title)).toEqual([
      '报表',
      '报表2',
    ])
  })

  it('counts visible session tab titles when naming a new workspace non-SQL tool tab', () => {
    useStageStore.setState({
      tabs: [{
        tabId: 'session-sql',
        type: 'er_canvas',
        title: 'ER 图设计器',
        originSessionId: 's1',
        createdAt: 0,
        payload: {},
      }],
      activeTabId: 'session-sql',
      openTabIds: new Set(['session-sql']),
      openTabIdsOrdered: ['session-sql'],
    } as unknown as Record<string, unknown>)

    const created = openOrFocusStageToolTab({
      getState: useStageStore.getState,
      sessionId: 's1',
      target: { kind: 'global_tool', tool: 'er', title: 'ER 图设计器' },
      reuseExisting: false,
    })

    expect(created.created).toBe(true)
    expect(useStageStore.getState().tabs).toHaveLength(2)
    expect(useStageStore.getState().tabs[1]?.title).toBe('ER 图设计器2')
  })

  it('reuses existing non-SQL session tool tab for the same resource context', () => {
    const first = openOrFocusStageToolTab({
      getState: useStageStore.getState,
      sessionId: 's1',
      target: {
        kind: 'resource_tool',
        tool: 'er',
        title: 'ER 图设计器',
        connectionId: 'c1',
        database: 'analytics',
        schema: 'public',
      },
    })
    const second = openOrFocusStageToolTab({
      getState: useStageStore.getState,
      sessionId: 's1',
      target: {
        kind: 'resource_tool',
        tool: 'er',
        title: 'ER 图设计器',
        connectionId: 'c1',
        database: 'analytics',
        schema: 'public',
      },
    })

    expect(first.created).toBe(true)
    expect(second.created).toBe(false)
    expect(second.tabId).toBe(first.tabId)
    expect(useStageStore.getState().tabs).toHaveLength(1)
    expect(useStageStore.getState().activeTabId).toBe(first.tabId)
  })

  it('opens a different non-SQL session tool tab when schema differs', () => {
    const first = openOrFocusStageToolTab({
      getState: useStageStore.getState,
      sessionId: 's1',
      target: {
        kind: 'resource_tool',
        tool: 'er',
        title: 'ER 图设计器',
        connectionId: 'c1',
        database: 'analytics',
        schema: 'public',
      },
    })
    const second = openOrFocusStageToolTab({
      getState: useStageStore.getState,
      sessionId: 's1',
      target: {
        kind: 'resource_tool',
        tool: 'er',
        title: 'ER 图设计器',
        connectionId: 'c1',
        database: 'analytics',
        schema: 'sales',
      },
    })

    expect(second.created).toBe(true)
    expect(second.tabId).not.toBe(first.tabId)
    expect(useStageStore.getState().tabs).toHaveLength(2)
    expect(useStageStore.getState().tabs.map((tab) => tab.title)).toEqual([
      'ER 图设计器',
      'ER 图设计器2',
    ])
  })

  it('rejects opening a non-SQL session resource tool without active session', () => {
    expect(() =>
      openOrFocusStageToolTab({
        getState: useStageStore.getState,
        sessionId: null,
        target: {
          kind: 'resource_tool',
          tool: 'er',
          title: 'ER 图设计器',
          connectionId: 'c1',
          database: 'analytics',
          schema: 'public',
        },
      })
    ).toThrow('session-scoped stage tool requires active session')
  })
})
