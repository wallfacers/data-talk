import { describe, it, expect, beforeEach } from 'vitest'
import { useStageStore } from './stage-store'

describe('stage-store', () => {
  beforeEach(() => {
    useStageStore.setState({
      openBySession: new Map(),
      autoOpenedSessions: new Set(),
      maximizedBySession: new Map(),
      revealOrigin: null,
      sidebarCollapsedBySession: new Map(),
      sidebarSelectionBySession: new Map(),
      resourceTreeExpandedBySession: new Map(),
      activeRailPanelBySession: new Map(),
    })
  })

  it('openStage / closeStage 切换 openBySession', () => {
    const { openStage, closeStage } = useStageStore.getState()
    openStage('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
    closeStage('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
  })

  it('closeStage 隐式 markAutoOpened，禁止后续自弹', () => {
    const { closeStage, notifyArtifactArrived } = useStageStore.getState()
    closeStage('s1')
    expect(useStageStore.getState().autoOpenedSessions.has('s1')).toBe(true)
    notifyArtifactArrived('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
  })

  it('notifyArtifactArrived 首次自弹，幂等', () => {
    const { notifyArtifactArrived } = useStageStore.getState()
    notifyArtifactArrived('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
    expect(useStageStore.getState().autoOpenedSessions.has('s1')).toBe(true)
    notifyArtifactArrived('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
  })

  it('toggleStage 切换状态', () => {
    const { toggleStage } = useStageStore.getState()
    toggleStage('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
    toggleStage('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
  })

  it('syncCollapsed(true) 反向同步并标 autoOpened', () => {
    const { openStage, syncCollapsed } = useStageStore.getState()
    openStage('s1')
    syncCollapsed('s1', true)
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
    expect(useStageStore.getState().autoOpenedSessions.has('s1')).toBe(true)
  })

  it('syncCollapsed(false) 反向同步且不动 autoOpened', () => {
    const { syncCollapsed } = useStageStore.getState()
    syncCollapsed('s1', false)
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
    expect(useStageStore.getState().autoOpenedSessions.has('s1')).toBe(false)
  })

  it('clear 清空指定 session', () => {
    const { openStage, clear } = useStageStore.getState()
    openStage('s1')
    openStage('s2')
    clear('s1')
    expect(useStageStore.getState().openBySession.has('s1')).toBe(false)
    expect(useStageStore.getState().openBySession.get('s2')).toBe(true)
  })

  it('setRevealOrigin 写入 revealOrigin 字段', () => {
    const { setRevealOrigin } = useStageStore.getState()
    setRevealOrigin({ x: 100, y: 200 })
    expect(useStageStore.getState().revealOrigin).toEqual({ x: 100, y: 200 })
  })

  it('setRevealOrigin(null) 清空 revealOrigin', () => {
    const { setRevealOrigin } = useStageStore.getState()
    setRevealOrigin({ x: 100, y: 200 })
    setRevealOrigin(null)
    expect(useStageStore.getState().revealOrigin).toBeNull()
  })

  it('closeStage 不触碰 revealOrigin（供关闭动画复用）', () => {
    const { setRevealOrigin, closeStage } = useStageStore.getState()
    setRevealOrigin({ x: 50, y: 50 })
    closeStage('s1')
    expect(useStageStore.getState().revealOrigin).toEqual({ x: 50, y: 50 })
  })

  it('notifyArtifactArrived 不触碰 revealOrigin', () => {
    const { setRevealOrigin, notifyArtifactArrived } = useStageStore.getState()
    setRevealOrigin({ x: 50, y: 50 })
    notifyArtifactArrived('s1')
    expect(useStageStore.getState().revealOrigin).toEqual({ x: 50, y: 50 })
  })

  it('setActiveRailPanel / toggleRailPanel are isolated per session', () => {
    const { setActiveRailPanel, toggleRailPanel } = useStageStore.getState()

    setActiveRailPanel('s1', 'schema')
    expect(useStageStore.getState().activeRailPanelBySession.get('s1')).toBe('schema')

    toggleRailPanel('s1', 'schema')
    expect(useStageStore.getState().activeRailPanelBySession.get('s1')).toBeNull()

    toggleRailPanel('s1', 'history')
    expect(useStageStore.getState().activeRailPanelBySession.get('s1')).toBe('history')

    setActiveRailPanel('s2', 'outline')
    expect(useStageStore.getState().activeRailPanelBySession.get('s1')).toBe('history')
    expect(useStageStore.getState().activeRailPanelBySession.get('s2')).toBe('outline')
  })

  it('clear removes active rail panel state for the session', () => {
    const { setActiveRailPanel, clear } = useStageStore.getState()

    setActiveRailPanel('s1', 'schema')
    clear('s1')

    expect(useStageStore.getState().activeRailPanelBySession.has('s1')).toBe(false)
  })
})

describe('StageStore tabs', () => {
  beforeEach(() => { useStageStore.setState({
    openBySession: new Map(),
    autoOpenedSessions: new Set(),
    maximizedBySession: new Map(),
    revealOrigin: null,
    sidebarCollapsedBySession: new Map(),
    sidebarSelectionBySession: new Map(),
    resourceTreeExpandedBySession: new Map(),
    activeRailPanelBySession: new Map(),
    workspaceTabs: [], tabsBySession: new Map(),
    activeTabIdBySession: new Map(), activeWorkspaceTabId: null,
  } as unknown as Record<string, unknown>) })

  it('openTab(workspace) adds to workspaceTabs and sets activeWorkspaceTabId', () => {
    useStageStore.getState().openTab({ tabId: 't1', type: 'report', title: 'sql', scope: 'workspace', payload: {}, createdAt: 1 })
    expect(useStageStore.getState().workspaceTabs).toHaveLength(1)
    expect(useStageStore.getState().activeWorkspaceTabId).toBe('t1')
  })

  it('openTab(session) adds to tabsBySession and sets activeTabIdBySession', () => {
    useStageStore.getState().openTab({ tabId: 'a1', type: 'artifact', title: 'art', scope: 'session', originSessionId: 's1', payload: {}, createdAt: 1 })
    expect(useStageStore.getState().tabsBySession.get('s1')).toHaveLength(1)
    expect(useStageStore.getState().activeTabIdBySession.get('s1')).toBe('a1')
  })

  it('closeTab removes and clears active', () => {
    const st = useStageStore.getState()
    st.openTab({ tabId: 't1', type: 'report', title: 'x', scope: 'workspace', payload: {}, createdAt: 1 })
    st.closeTab('t1')
    expect(useStageStore.getState().workspaceTabs).toHaveLength(0)
    expect(useStageStore.getState().activeWorkspaceTabId).toBeNull()
  })

  it('focusTab switches active', () => {
    const st = useStageStore.getState()
    st.openTab({ tabId: 't1', type: 'report', title: 'x', scope: 'workspace', payload: {}, createdAt: 1 })
    st.openTab({ tabId: 't2', type: 'report', title: 'y', scope: 'workspace', payload: {}, createdAt: 2 })
    expect(useStageStore.getState().activeWorkspaceTabId).toBe('t2')
    st.focusTab('t1')
    expect(useStageStore.getState().activeWorkspaceTabId).toBe('t1')
  })

  it('listTabs(sid) merges workspace + session tabs', () => {
    const st = useStageStore.getState()
    st.openTab({ tabId: 't1', type: 'report', title: 'x', scope: 'workspace', payload: {}, createdAt: 1 })
    st.openTab({ tabId: 'a1', type: 'artifact', title: 'y', scope: 'session', originSessionId: 's1', payload: {}, createdAt: 2 })
    const merged = st.listTabs('s1')
    expect(merged.map(t => t.tabId).sort()).toEqual(['a1', 't1'])
  })

  it('toggleSidebarCollapsed persists sidebar collapsed state per session', () => {
    const st = useStageStore.getState()
    st.toggleSidebarCollapsed('s1')
    expect(useStageStore.getState().sidebarCollapsedBySession.get('s1')).toBe(true)
    st.toggleSidebarCollapsed('s1')
    expect(useStageStore.getState().sidebarCollapsedBySession.get('s1')).toBe(false)
  })

  it('setSidebarSelection stores current sidebar selection', () => {
    const st = useStageStore.getState()
    st.setSidebarSelection('s1', { kind: 'schema', connectionId: 'c1', database: 'analytics', schema: 'public' })
    expect(useStageStore.getState().sidebarSelectionBySession.get('s1')).toEqual({
      kind: 'schema',
      connectionId: 'c1',
      database: 'analytics',
      schema: 'public',
    })
  })

  it('toggleResourceExpanded adds and removes expanded node ids', () => {
    const st = useStageStore.getState()
    st.toggleResourceExpanded('s1', 'conn:c1')
    expect(useStageStore.getState().resourceTreeExpandedBySession.get('s1')).toEqual(['conn:c1'])
    st.toggleResourceExpanded('s1', 'conn:c1')
    expect(useStageStore.getState().resourceTreeExpandedBySession.get('s1')).toEqual([])
  })

  it('clear also removes sidebar state for the session', () => {
    const st = useStageStore.getState()
    st.toggleSidebarCollapsed('s1')
    st.setSidebarSelection('s1', { kind: 'connection', connectionId: 'c1' })
    st.toggleResourceExpanded('s1', 'conn:c1')
    st.clear('s1')

    expect(useStageStore.getState().sidebarCollapsedBySession.has('s1')).toBe(false)
    expect(useStageStore.getState().sidebarSelectionBySession.has('s1')).toBe(false)
    expect(useStageStore.getState().resourceTreeExpandedBySession.has('s1')).toBe(false)
  })
})
