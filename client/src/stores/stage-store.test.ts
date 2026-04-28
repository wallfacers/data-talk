import { describe, it, expect, beforeEach } from 'vitest'
import { useSqlWorkbenchStore } from '@/features/stage/stores/sql-workbench-store'
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

  beforeEach(() => {
    useSqlWorkbenchStore.setState({ tabsById: {} })
  })

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

  it('allocates SQL editor titles from visible workspace + session query editors', () => {
    const store = useStageStore.getState()

    store.openQueryEditor({
      sessionId: 's1',
      scope: 'workspace',
      baseTitle: 'SQL 编辑器',
      openMode: 'always_new',
      entryMode: 'blank',
    })
    store.openQueryEditor({
      sessionId: 's1',
      scope: 'session',
      baseTitle: 'SQL 编辑器',
      openMode: 'always_new',
      entryMode: 'direct_sql',
      initialContent: 'select 1',
    })

    const titles = store
      .listTabs('s1')
      .filter((tab) => tab.type === 'query_editor')
      .map((tab) => tab.title)

    expect(titles).toEqual(['SQL 编辑器', 'SQL 编辑器2'])
  })

  it('keeps the origin session on workspace query editors so context targets can load', () => {
    const store = useStageStore.getState()

    const opened = store.openQueryEditor({
      sessionId: 's1',
      scope: 'workspace',
      baseTitle: 'SQL 编辑器',
      openMode: 'always_new',
      entryMode: 'blank',
    })

    const tab = useStageStore.getState().workspaceTabs.find((candidate) => candidate.tabId === opened.tabId)

    expect(tab?.originSessionId).toBe('s1')
  })

  it('reuses the same resource-scoped query editor when openMode is reuse_by_resource_context', () => {
    const store = useStageStore.getState()

    const first = store.openQueryEditor({
      sessionId: 's1',
      scope: 'session',
      baseTitle: 'SQL 编辑器',
      openMode: 'reuse_by_resource_context',
      entryMode: 'ui_exec',
      connectionId: 'conn-1',
      database: 'analytics',
      schema: 'public',
    })
    const second = store.openQueryEditor({
      sessionId: 's1',
      scope: 'session',
      baseTitle: 'SQL 编辑器',
      openMode: 'reuse_by_resource_context',
      entryMode: 'ui_exec',
      connectionId: 'conn-1',
      database: 'analytics',
      schema: 'public',
    })

    expect(second).toEqual({ tabId: first.tabId, created: false })
  })

  it('bootstraps workbench state and keeps new query editor payloads free of live sql content', () => {
    const store = useStageStore.getState()
    const opened = store.openQueryEditor({
      sessionId: 's1',
      scope: 'session',
      baseTitle: 'SQL 编辑器',
      openMode: 'always_new',
      entryMode: 'direct_sql',
      initialContent: 'select 1',
    })

    expect(useSqlWorkbenchStore.getState().tabsById[opened.tabId]).toMatchObject({
      sqlText: 'select 1',
      version: 1,
    })

    let tab = useStageStore.getState().listTabs('s1').find((candidate) => candidate.tabId === opened.tabId)
    expect(tab?.payload).not.toHaveProperty('initialSql')

    const result = store.applyQueryEditorTextEdits(opened.tabId, {
      baseVersion: 1,
      edits: [
        {
          range: {
            startLine: 1,
            startColumn: 8,
            endLine: 1,
            endColumn: 9,
          },
          text: '2',
          expectedText: '1',
        },
      ],
    })

    expect(result).toEqual({
      ok: true,
      version: 2,
      content: 'select 2',
    })
    expect(useSqlWorkbenchStore.getState().tabsById[opened.tabId]).toMatchObject({
      sqlText: 'select 2',
      version: 2,
    })
    tab = useStageStore.getState().listTabs('s1').find((candidate) => candidate.tabId === opened.tabId)
    expect(tab?.payload).not.toHaveProperty('initialSql')
  })

  it('setQueryEditorCursor rejects unknown tab ids', () => {
    expect(() => useStageStore.getState().setQueryEditorCursor('missing', { line: 3, column: 4 })).toThrow(
      'Unknown sql workbench tab: missing',
    )
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

describe('StageStore persistence mutation API', () => {
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

  beforeEach(() => {
    useSqlWorkbenchStore.setState({ tabsById: {} })
  })

  it('findTab returns tab by id from workspace or session', () => {
    const st = useStageStore.getState()
    st.openTab({ tabId: 't1', type: 'query_editor', title: 'WS', scope: 'workspace', payload: {}, createdAt: 1 })
    st.openTab({ tabId: 's1', type: 'artifact_preview', title: 'Sess', scope: 'session', originSessionId: 'sess1', payload: {}, createdAt: 2 })

    expect(st.findTab('t1')).toMatchObject({ tabId: 't1' })
    expect(st.findTab('s1')).toMatchObject({ tabId: 's1' })
    expect(st.findTab('missing')).toBeNull()
  })

  it('__hydrateWorkspaceTabs merges server items into existing workspace tabs', () => {
    const st = useStageStore.getState()
    st.openTab({ tabId: 't1', type: 'query_editor', title: 'Local', scope: 'workspace', payload: {}, createdAt: 1 })

    st.__hydrateWorkspaceTabs([
      { tabId: 't1', type: 'query_editor', title: 'Hydrated', scope: 'workspace' as const, payload: { sql: 'server' }, payloadVersion: 5, createdAt: 1, lastTouchedAt: 100 },
      { tabId: 't2', type: 'query_editor', title: 'New From Server', scope: 'workspace' as const, payload: {}, createdAt: 2 },
    ] as never)

    const tabs = useStageStore.getState().workspaceTabs
    expect(tabs).toHaveLength(2)
    expect(tabs.find((t) => t.tabId === 't1')?.title).toBe('Hydrated')
    expect(tabs.find((t) => t.tabId === 't2')?.title).toBe('New From Server')
  })

  it('__hydrateAll merges hydrated tabs into workspace storage', () => {
    const st = useStageStore.getState()
    st.openTab({ tabId: 't1', type: 'query_editor', title: 'Local', scope: 'workspace', payload: {}, createdAt: 1 })

    st.__hydrateAll([
      { tabId: 't1', type: 'query_editor', title: 'Hydrated', scope: 'workspace' as const, payload: { sql: 'server' }, payloadVersion: 5, createdAt: 1, lastTouchedAt: 100 },
      { tabId: 't2', type: 'query_editor', title: 'Shared From Server', scope: 'workspace' as const, originSessionId: 'sess1', payload: {}, createdAt: 2 },
    ] as never)

    const tabs = useStageStore.getState().workspaceTabs
    expect(tabs).toHaveLength(2)
    expect(tabs.find((t) => t.tabId === 't1')?.title).toBe('Hydrated')
    expect(tabs.find((t) => t.tabId === 't2')?.originSessionId).toBe('sess1')
    expect(useStageStore.getState().tabsBySession.get('sess1')).toBeUndefined()
  })

  it('__hydrateSessionTabs aliases hydration into workspace storage', () => {
    const st = useStageStore.getState()
    st.__hydrateSessionTabs('sess1', [
      { tabId: 'a1', type: 'artifact_preview', title: 'Server Art', scope: 'workspace' as const, originSessionId: 'sess1', payload: {}, createdAt: 1, payloadVersion: 2 },
    ] as never)

    expect(useStageStore.getState().workspaceTabs.find((tab) => tab.tabId === 'a1')?.title).toBe('Server Art')
    expect(useStageStore.getState().tabsBySession.get('sess1')).toBeUndefined()
  })

  it('__hydrateSessionTabs remains an alias to __hydrateAll for compatibility', () => {
    const st = useStageStore.getState()

    st.__hydrateSessionTabs('sess1', [
      { tabId: 'a1', type: 'artifact_preview', title: 'Server Art', scope: 'workspace' as const, originSessionId: 'sess1', payload: {}, createdAt: 1, payloadVersion: 2 },
    ] as never)

    expect(useStageStore.getState().workspaceTabs.find((tab) => tab.tabId === 'a1')?.originSessionId).toBe('sess1')
    expect(useStageStore.getState().tabsBySession.get('sess1')).toBeUndefined()
  })

  it('__hydratePayload sets payload and version on target tab', () => {
    const st = useStageStore.getState()
    st.openTab({ tabId: 't1', type: 'query_editor', title: 'Q', scope: 'workspace', payload: {}, createdAt: 1 })

    st.__hydratePayload('t1', { sqlText: 'SELECT 1' }, 7)

    const tab = useStageStore.getState().findTab('t1')
    expect(tab?.payload).toEqual({ sqlText: 'SELECT 1' })
    expect(tab?.payloadVersion).toBe(7)
  })

  it('archiveTab sets archived flag on tab', () => {
    const st = useStageStore.getState()
    st.openTab({ tabId: 't1', type: 'query_editor', title: 'Q', scope: 'workspace', payload: {}, createdAt: 1 })

    st.archiveTab('t1', true)
    expect(useStageStore.getState().findTab('t1')?.archived).toBe(true)

    st.archiveTab('t1', false)
    expect(useStageStore.getState().findTab('t1')?.archived).toBe(false)
  })

  it('setTabPinned sets pinned flag on tab', () => {
    const st = useStageStore.getState()
    st.openTab({ tabId: 't1', type: 'query_editor', title: 'Q', scope: 'workspace', payload: {}, createdAt: 1 })

    st.setTabPinned('t1', true)
    expect(useStageStore.getState().findTab('t1')?.pinned).toBe(true)
  })

  it('setTabTitle updates title on tab', () => {
    const st = useStageStore.getState()
    st.openTab({ tabId: 't1', type: 'query_editor', title: 'Old', scope: 'workspace', payload: {}, createdAt: 1 })

    st.setTabTitle('t1', 'New Title')
    expect(useStageStore.getState().findTab('t1')?.title).toBe('New Title')
  })
})
