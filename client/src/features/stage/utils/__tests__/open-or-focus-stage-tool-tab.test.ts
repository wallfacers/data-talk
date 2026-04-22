import { beforeEach, describe, expect, it } from 'vitest'
import { openOrFocusStageToolTab } from '../open-or-focus-stage-tool-tab'
import { useStageStore } from '@/stores/stage-store'

describe('openOrFocusStageToolTab', () => {
  beforeEach(() => {
    useStageStore.setState({
      openBySession: new Map(),
      autoOpenedSessions: new Set(),
      maximizedBySession: new Map(),
      revealOrigin: null,
      sidebarCollapsedBySession: new Map(),
      sidebarSelectionBySession: new Map(),
      resourceTreeExpandedBySession: new Map(),
      workspaceTabs: [],
      tabsBySession: new Map(),
      activeWorkspaceTabId: null,
      activeTabIdBySession: new Map(),
    } as unknown as Record<string, unknown>)
  })

  it('reuses existing workspace tool tab for the same tool type', () => {
    const first = openOrFocusStageToolTab({
      getState: useStageStore.getState,
      sessionId: 's1',
      target: { kind: 'global_tool', tool: 'sql', title: 'SQL 编辑器' },
    })
    const second = openOrFocusStageToolTab({
      getState: useStageStore.getState,
      sessionId: 's1',
      target: { kind: 'global_tool', tool: 'sql', title: 'SQL 编辑器' },
    })

    expect(first.created).toBe(true)
    expect(second.created).toBe(false)
    expect(second.tabId).toBe(first.tabId)
    expect(useStageStore.getState().workspaceTabs).toHaveLength(1)
    expect(useStageStore.getState().activeWorkspaceTabId).toBe(first.tabId)
  })

  it('creates a new workspace tool tab when reuseExisting is false', () => {
    const first = openOrFocusStageToolTab({
      getState: useStageStore.getState,
      sessionId: 's1',
      target: { kind: 'global_tool', tool: 'sql', title: 'SQL 编辑器' },
      reuseExisting: false,
    })
    const second = openOrFocusStageToolTab({
      getState: useStageStore.getState,
      sessionId: 's1',
      target: { kind: 'global_tool', tool: 'sql', title: 'SQL 编辑器' },
      reuseExisting: false,
    })

    expect(first.created).toBe(true)
    expect(second.created).toBe(true)
    expect(second.tabId).not.toBe(first.tabId)
    expect(useStageStore.getState().workspaceTabs).toHaveLength(2)
    expect(useStageStore.getState().workspaceTabs.map((tab) => tab.title)).toEqual([
      'SQL 编辑器',
      'SQL 编辑器2',
    ])
  })

  it('reuses existing session tool tab for the same resource context', () => {
    const first = openOrFocusStageToolTab({
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
    const second = openOrFocusStageToolTab({
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

    expect(first.created).toBe(true)
    expect(second.created).toBe(false)
    expect(second.tabId).toBe(first.tabId)
    expect(useStageStore.getState().tabsBySession.get('s1')).toHaveLength(1)
    expect(useStageStore.getState().activeTabIdBySession.get('s1')).toBe(first.tabId)
  })

  it('opens a different tab when schema differs', () => {
    const first = openOrFocusStageToolTab({
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
    const second = openOrFocusStageToolTab({
      getState: useStageStore.getState,
      sessionId: 's1',
      target: {
        kind: 'resource_tool',
        tool: 'sql',
        title: 'SQL 编辑器',
        connectionId: 'c1',
        database: 'analytics',
        schema: 'sales',
      },
    })

    expect(second.created).toBe(true)
    expect(second.tabId).not.toBe(first.tabId)
    expect(useStageStore.getState().tabsBySession.get('s1')).toHaveLength(2)
    expect(useStageStore.getState().tabsBySession.get('s1')?.map((tab) => tab.title)).toEqual([
      'SQL 编辑器',
      'SQL 编辑器2',
    ])
  })

  it('rejects opening a session resource tool without active session', () => {
    expect(() =>
      openOrFocusStageToolTab({
        getState: useStageStore.getState,
        sessionId: null,
        target: {
          kind: 'resource_tool',
          tool: 'sql',
          title: 'SQL 编辑器',
          connectionId: 'c1',
          database: 'analytics',
          schema: 'public',
        },
      })
    ).toThrow('session-scoped stage tool requires active session')
  })
})
