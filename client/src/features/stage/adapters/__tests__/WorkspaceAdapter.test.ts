import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import { WorkspaceAdapter } from '../WorkspaceAdapter'
import { useStageStore } from '@/stores/stage-store'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import { useDataSourcePickerStore } from '@/features/session/data-source-picker/data-source-picker-store'
import { useSqlWorkbenchStore } from '@/features/stage/stores/sql-workbench-store'

const realOpenQueryEditor = useStageStore.getState().openQueryEditor

describe('WorkspaceAdapter', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))
    useStageStore.setState({
      tabs: [],
      openTabIds: new Set(),
      openTabIdsOrdered: [],
      activeTabId: null,
      openQueryEditor: realOpenQueryEditor,
    } as unknown as Record<string, unknown>)
    useConnectionStore.setState({
      activeConnectionId: null,
      connections: [],
    })
    useSqlWorkbenchStore.setState({ tabsById: {} })
    useSessionStore.setState({
      activeSessionId: null,
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      dataContextBySession: new Map(),
      pendingPrompt: null,
      composerRestoreDraft: null,
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('exec open for query_editor with connection context delegates to openQueryEditor only', async () => {
    const openQueryEditor = vi.fn().mockReturnValue({ tabId: 'qe-1', created: true })
    useStageStore.setState({ openQueryEditor } as unknown as Record<string, unknown>)

    const adapter = new WorkspaceAdapter(() => 's1')
    const result = await adapter.exec('open', {
      type: 'query_editor',
      title: 'SQL',
      connection_id: 'conn-1',
      database: 'db-1',
      schema: 'public',
      payload: {
        initialSql: 'select 1',
        autoRun: true,
        connectionId: 'payload-conn',
        database: 'payload-db',
      },
    })

    expect(result).toEqual({
      success: true,
      data: { tabId: 'qe-1' },
    })
    expect(openQueryEditor).toHaveBeenCalledWith({
      sessionId: 's1',
      baseTitle: 'SQL',
      openMode: 'reuse_by_resource_context',
      entryMode: 'ui_exec',
      initialContent: 'select 1',
      autoRun: true,
      connectionId: 'conn-1',
      connectionName: undefined,
      database: 'db-1',
      schema: 'public',
    })
  })

  it('exec open for query_editor without connection context delegates to openQueryEditor only', async () => {
    const openQueryEditor = vi.fn().mockReturnValue({ tabId: 'qe-2', created: true })
    useStageStore.setState({ openQueryEditor } as unknown as Record<string, unknown>)

    const adapter = new WorkspaceAdapter(() => 's1')
    const result = await adapter.exec('open', {
      type: 'query_editor',
      title: 'SQL',
      payload: {
        initialSql: 'select 2',
      },
    })

    expect(result).toEqual({
      success: true,
      data: { tabId: 'qe-2' },
    })
    expect(openQueryEditor).toHaveBeenCalledWith({
      sessionId: 's1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'ui_exec',
      initialContent: 'select 2',
      autoRun: false,
      connectionId: undefined,
      connectionName: undefined,
      database: undefined,
      schema: undefined,
    })
  })

  it('exec open for query_editor accepts payload.content as initial SQL', async () => {
    const openQueryEditor = vi.fn().mockReturnValue({ tabId: 'qe-content', created: true })
    useStageStore.setState({ openQueryEditor } as unknown as Record<string, unknown>)

    const adapter = new WorkspaceAdapter(() => 's1')
    const result = await adapter.exec('open', {
      type: 'query_editor',
      title: 'Content SQL',
      payload: {
        content: 'DROP DATABASE ecommerce;',
      },
    })

    expect(result).toEqual({
      success: true,
      data: { tabId: 'qe-content' },
    })
    expect(openQueryEditor).toHaveBeenCalledWith(expect.objectContaining({
      initialContent: 'DROP DATABASE ecommerce;',
    }))
  })

  it('exec open for query_editor carries payload-only canonical context into openQueryEditor', async () => {
    const openQueryEditor = vi.fn().mockReturnValue({ tabId: 'qe-3', created: true })
    useStageStore.setState({ openQueryEditor } as unknown as Record<string, unknown>)

    const adapter = new WorkspaceAdapter(() => 's1')
    const result = await adapter.exec('open', {
      type: 'query_editor',
      title: 'Payload SQL',
      payload: {
        initialSql: 'select 3',
        autoRun: true,
        connectionId: 'payload-conn',
        connectionName: 'Payload Warehouse',
        database: 'payload-db',
        schema: 'payload-schema',
      },
    })

    expect(result).toEqual({
      success: true,
      data: { tabId: 'qe-3' },
    })
    expect(openQueryEditor).toHaveBeenCalledWith({
      sessionId: 's1',
      baseTitle: 'Payload SQL',
      openMode: 'reuse_by_resource_context',
      entryMode: 'ui_exec',
      initialContent: 'select 3',
      autoRun: true,
      connectionId: 'payload-conn',
      connectionName: 'Payload Warehouse',
      database: 'payload-db',
      schema: 'payload-schema',
    })
  })

  it('exec open for query_editor does not carry a mismatched payload connectionName when top-level connection_id wins', async () => {
    const openQueryEditor = vi.fn().mockReturnValue({ tabId: 'qe-4', created: true })
    useStageStore.setState({ openQueryEditor } as unknown as Record<string, unknown>)

    const adapter = new WorkspaceAdapter(() => 's1')
    await adapter.exec('open', {
      type: 'query_editor',
      title: 'Mismatch Test',
      connection_id: 'top-conn',
      payload: {
        connectionId: 'payload-conn',
        connectionName: 'Payload Warehouse',
      },
    })

    expect(openQueryEditor).toHaveBeenCalledWith(expect.objectContaining({
      connectionId: 'top-conn',
      connectionName: undefined,
    }))
  })

  it('opens a global query_editor without an active session', async () => {
    const openQueryEditor = vi.fn().mockReturnValue({ tabId: 'qe-global', created: true })
    useStageStore.setState({ openQueryEditor } as unknown as Record<string, unknown>)

    const adapter = new WorkspaceAdapter(() => null)
    const res = await adapter.exec('open', {
      type: 'query_editor',
      title: 'SQL',
      connection_id: 'conn-1',
      database: 'db-1',
      schema: 'public',
    })
    expect(res).toEqual({
      success: true,
      data: { tabId: 'qe-global' },
    })
    expect(openQueryEditor).toHaveBeenCalledWith(expect.objectContaining({
      sessionId: null,
      baseTitle: 'SQL',
      connectionId: 'conn-1',
    }))
  })

  it('exec open sets the new tab as active', async () => {
    useStageStore.getState().openTab({
      tabId: 'session-q1',
      type: 'query_editor',
      title: 'Session SQL',
      originSessionId: 's1',
      payload: {},
      createdAt: 0,
    })

    const adapter = new WorkspaceAdapter(() => 's1')
    const opened = await adapter.exec('open', { type: 'report', title: 'Revenue' })

    expect(opened.success).toBe(true)
    expect(useStageStore.getState().activeTabId).toBe((opened.data as { tabId: string }).tabId)
  })

  it('read state returns tabs + active', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    await adapter.exec('open', { type: 'er_canvas', title: 'ER' })
    const state = adapter.read('state') as { open: boolean; maximized: boolean; tabs: unknown[]; activeTabId: string | null }
    expect(state.tabs.length).toBe(1)
    expect(state.open).toBe(true)
    expect(state.maximized).toBe(false)
  })

  it('read state reflects stage panel open/maximized from store', () => {
    useStageStore.setState({ open: false, maximized: true } as unknown as Record<string, unknown>)
    const adapter = new WorkspaceAdapter(() => 's1')
    const state = adapter.read('state') as { open: boolean; maximized: boolean }
    expect(state.open).toBe(false)
    expect(state.maximized).toBe(true)
  })

  it('read state prefers normalized payload connectionId for query_editor tabs', () => {
    useStageStore.getState().openTab({
      tabId: 'q-payload',
      type: 'query_editor',
      title: 'SQL',
      originSessionId: 's1',
      payload: {
        initialSql: 'select 1',
        connectionId: 'payload-conn',
      },
      createdAt: 0,
    })
    const adapter = new WorkspaceAdapter(() => 's1')

    const state = adapter.read('state') as {
      tabs: Array<{ tabId: string; connectionId?: string | null }>
      activeTabId: string | null
    }

    expect(state.tabs).toEqual([
      expect.objectContaining({
        tabId: 'q-payload',
        connectionId: 'payload-conn',
      }),
    ])
  })

  it('read state exposes effective query editor context in workspace summary while keeping contextOverride separate', () => {
    useConnectionStore.setState({
      activeConnectionId: null,
      connections: [
        { id: 'conn-1', name: 'Warehouse', kind: 'postgres', databaseName: 'analytics' } as never,
        { id: 'conn-2', name: 'Reporting Warehouse', kind: 'postgres', databaseName: 'warehouse' } as never,
      ],
    })
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 's1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'ai_open',
      initialContent: 'select 1',
      connectionId: 'conn-1',
      connectionName: 'Warehouse',
      database: 'analytics',
      schema: 'public',
    })
    useStageStore.getState().updateTabPayload(tabId, (payload) => ({
      ...(payload as Record<string, unknown>),
      contextOverride: {
        connectionId: 'conn-2',
        database: 'warehouse',
        schema: 'reporting',
      },
    }))

    const adapter = new WorkspaceAdapter(() => 's1')
    const state = adapter.read('state') as {
      tabs: Array<{
        tabId: string
        connectionId?: string | null
        connectionName?: string | null
        database?: string | null
        schema?: string | null
        useSessionContext?: boolean
        contextSource?: 'session' | 'override' | 'tab'
        contextOverride?: unknown
        limit?: 10 | 100 | 1000 | null
      }>
      activeTabId: string | null
    }

    expect(state.tabs).toEqual([
      expect.objectContaining({
        tabId,
        connectionId: 'conn-2',
        connectionName: 'Reporting Warehouse',
        database: 'warehouse',
        schema: 'reporting',
        useSessionContext: false,
        contextSource: 'override',
        contextOverride: expect.objectContaining({
          connectionId: 'conn-2',
          database: 'warehouse',
          schema: 'reporting',
        }),
        limit: 100,
      }),
    ])
  })

  it('read state exposes latest inherited session context for query editor tabs so workspace-level scans can read it directly', () => {
    useConnectionStore.setState({
      activeConnectionId: null,
      connections: [
        { id: 'conn-1', name: 'Warehouse', kind: 'postgres', databaseName: 'analytics' } as never,
        { id: 'conn-2', name: 'Reporting Warehouse', kind: 'postgres', databaseName: 'warehouse' } as never,
      ],
    })
    useSessionStore.setState({
      activeSessionId: 's1',
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      dataContextBySession: new Map([[
        's1',
        {
          sessionId: 's1',
          connectionId: 'conn-2',
          connectionNameSnapshot: 'Reporting Warehouse',
          database: 'warehouse',
          schema: 'reporting',
          selectedLevel: 'schema',
          updatedAt: 2,
        },
      ]]),
      pendingPrompt: null,
      composerRestoreDraft: null,
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
    })

    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 's1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 1',
      connectionId: 'conn-1',
      connectionName: 'Warehouse',
      database: 'analytics',
      schema: 'public',
    })
    useStageStore.getState().updateTabPayload(tabId, (payload) => ({
      ...(payload as Record<string, unknown>),
      contextOverride: null,
      useSessionContext: true,
    }))
    useSqlWorkbenchStore.getState().resetTabContext(tabId)

    const adapter = new WorkspaceAdapter(() => 's1')
    const state = adapter.read('state') as {
      tabs: Array<{
        tabId: string
        connectionId?: string | null
        connectionName?: string | null
        database?: string | null
        schema?: string | null
        useSessionContext?: boolean
        contextSource?: 'session' | 'override' | 'tab'
        contextOverride?: unknown
        limit?: 10 | 100 | 1000 | null
      }>
    }

    expect(state.tabs).toEqual([
      expect.objectContaining({
        tabId,
        connectionId: 'conn-2',
        connectionName: 'Reporting Warehouse',
        database: 'warehouse',
        schema: 'reporting',
        useSessionContext: true,
        contextSource: 'session',
        contextOverride: null,
        limit: 100,
      }),
    ])
  })

  it('exec close is rejected after alias removal and leaves the tab in place', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const opened = await adapter.exec('open', { type: 'er_canvas', title: 'ER' })
    const tabId = (opened.data as { tabId: string }).tabId
    const closed = await adapter.exec('close', { target: tabId })
    expect(closed).toEqual(expect.objectContaining({
      success: false,
      error: 'Unknown action: close',
    }))
    expect(useStageStore.getState().openTabIds.has(tabId)).toBe(true)
    expect(useStageStore.getState().tabs).toHaveLength(1)
    expect(useStageStore.getState().tabs[0]?.archived).toBeUndefined()
  })

  it('exec detach removes the tab from the workset without archiving it', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const opened = await adapter.exec('open', { type: 'er_canvas', title: 'ER' })
    const tabId = (opened.data as { tabId: string }).tabId

    const detached = await adapter.exec('detach', { target: tabId })

    expect(detached).toEqual({ success: true })
    expect(useStageStore.getState().openTabIds.has(tabId)).toBe(false)
    expect(useStageStore.getState().tabs).toEqual([
      expect.objectContaining({ tabId }),
    ])
    expect(useStageStore.getState().tabs[0]?.archived).toBeUndefined()
  })

  it('exec archive toggles archived state and cascades detach when archiving', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const opened = await adapter.exec('open', { type: 'er_canvas', title: 'ER' })
    const tabId = (opened.data as { tabId: string }).tabId

    const archived = await adapter.exec('archive', { target: tabId })
    const unarchived = await adapter.exec('archive', { target: tabId, archived: false })

    expect(archived).toEqual({ success: true, data: { succeeded: [tabId] } })
    expect(unarchived).toEqual({ success: true, data: { succeeded: [tabId] } })
    expect(useStageStore.getState().openTabIds.has(tabId)).toBe(false)
    expect(useStageStore.getState().tabs).toEqual([
      expect.objectContaining({ tabId, archived: false, archivedAt: null }),
    ])
  })

  it('exec trash permanently deletes the tab', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const opened = await adapter.exec('open', { type: 'er_canvas', title: 'ER' })
    const tabId = (opened.data as { tabId: string }).tabId

    const trashed = await adapter.exec('trash', { target: tabId })

    expect(trashed).toEqual({ success: true, data: { succeeded: [tabId] } })
    expect(useStageStore.getState().tabs).toHaveLength(0)
    expect(useStageStore.getState().openTabIds.has(tabId)).toBe(false)
  })

  it('exec focus sets the target as active', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const opened = await adapter.exec('open', { type: 'er_canvas', title: 'ER' })

    const focused = await adapter.exec('focus', { target: (opened.data as { tabId: string }).tabId })

    expect(focused).toEqual({ success: true })
    expect(useStageStore.getState().activeTabId).toBe((opened.data as { tabId: string }).tabId)
  })

  it('exec focus reveals the stage panel when it is currently closed', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const opened = await adapter.exec('open', { type: 'er_canvas', title: 'ER' })
    const tabId = (opened.data as { tabId: string }).tabId
    useStageStore.getState().closeStage()
    expect(useStageStore.getState().open).toBe(false)

    const focused = await adapter.exec('focus', { target: tabId })

    expect(focused).toEqual({ success: true })
    expect(useStageStore.getState().open).toBe(true)
    expect(useStageStore.getState().activeTabId).toBe(tabId)
  })

  it('exec focus rejects archived tabs with a structured tab_archived error', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const opened = await adapter.exec('open', { type: 'er_canvas', title: 'ER' })
    const tabId = (opened.data as { tabId: string }).tabId
    useStageStore.getState().archiveTab(tabId, true)

    const focused = await adapter.exec('focus', { target: tabId })

    expect(focused.success).toBe(false)
    expect(focused.data).toEqual(expect.objectContaining({
      code: 'tab_archived',
      message: expect.stringContaining('archived'),
      hint: expect.stringContaining('archived=false'),
    }))
  })

  it.each([
    ['er_canvas', 'ER Canvas'],
    ['report', 'Report'],
    ['dashboard', 'Dashboard'],
  ])('exec open treats %s as a workspace-scoped tab', async (type, title) => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const opened = await adapter.exec('open', { type, title })

    expect(opened.success).toBe(true)
    expect(useStageStore.getState().tabs).toEqual([
      expect.objectContaining({
        tabId: (opened.data as { tabId: string }).tabId,
        type,
        title,
      }),
    ])
  })

  it('rejects open without type', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const res = await adapter.exec('open', {})
    expect(res.success).toBe(false)
  })

  it('rejects open without title', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const res = await adapter.exec('open', { type: 'query_editor' })
    expect(res.success).toBe(false)
  })

  it('open_er_designer registers the stage tab BEFORE updating erTabsStore so persistence subscriber sees the tab', async () => {
    // The persistence layer's erTabsStore subscriber resolves the stage tab
    // via useStageStore.findTab(tabId) before scheduling a content write.
    // If hydrateDesigner runs before openTab, the tab is missing and the
    // content write is silently dropped — leaving the server with no payload
    // row and producing 404 on the next ensureHydrated.
    let stageTabPresentWhenDesignerWritten: boolean | null = null
    const unsubscribe = useErTabsStore.subscribe((state, prev) => {
      if (state.designers === prev.designers) return
      for (const [tabId] of state.designers) {
        if (prev.designers.has(tabId)) continue
        stageTabPresentWhenDesignerWritten = !!useStageStore.getState().findTab(tabId)
      }
    })

    try {
      const adapter = new WorkspaceAdapter(() => 's1')
      const opened = await adapter.exec('open_er_designer', { dialect: 'mysql', title: 'Persistence Test' })
      expect(opened.success).toBe(true)
      expect(stageTabPresentWhenDesignerWritten).toBe(true)
    } finally {
      unsubscribe()
      useErTabsStore.setState({ designers: new Map(), inspectors: new Map() } as never)
    }
  })

  it('choose_connection returns selected connection from chooser', async () => {
    vi.spyOn(useDataSourcePickerStore.getState(), 'requestPick').mockResolvedValue({
      connectionId: 'c9',
      connectionName: 'warehouse-prod',
    })
    const adapter = new WorkspaceAdapter(() => 's1')
    const res = await adapter.exec('choose_connection', { preferredConnectionId: 'c1' })
    expect(res.success).toBe(true)
    expect(res.data).toEqual({
      connectionId: 'c9',
      connectionName: 'warehouse-prod',
    })
    expect(useConnectionStore.getState().activeConnectionId).toBe('c9')
  })

  describe('batch operations', () => {
    it('batch open creates multiple tabs and returns tabIds', async () => {
      const adapter = new WorkspaceAdapter(() => 's1')
      const res = await adapter.exec('open', {
        tabs: [
          { type: 'er_canvas', title: 'ER 1' },
          { type: 'dashboard', title: 'Dashboard 1' },
        ],
      })

      expect(res.success).toBe(true)
      const data = res.data as { tabIds: string[] }
      expect(data.tabIds).toHaveLength(2)
      const tabIds = useStageStore.getState().tabs.map((t) => t.tabId)
      expect(tabIds).toEqual(data.tabIds)
    })

    it('batch open falls back to single-open format when tabs is absent', async () => {
      const adapter = new WorkspaceAdapter(() => 's1')
      const res = await adapter.exec('open', { type: 'er_canvas', title: 'Single' })

      expect(res.success).toBe(true)
      const data = res.data as { tabId: string }
      expect(typeof data.tabId).toBe('string')
    })

    it('batch archive archives multiple tabs', async () => {
      const adapter = new WorkspaceAdapter(() => 's1')
      await adapter.exec('open', { type: 'er_canvas', title: 'A' })
      await adapter.exec('open', { type: 'dashboard', title: 'B' })
      const ids = useStageStore.getState().tabs.map((t) => t.tabId)

      const res = await adapter.exec('archive', { targets: ids })

      expect(res.success).toBe(true)
      const data = res.data as { succeeded: string[] }
      expect(data.succeeded).toHaveLength(2)
      for (const t of useStageStore.getState().tabs) {
        expect(t.archived).toBe(true)
      }
    })

    it('batch archive skips non-existent tabs', async () => {
      const adapter = new WorkspaceAdapter(() => 's1')
      await adapter.exec('open', { type: 'er_canvas', title: 'A' })
      const [realId] = useStageStore.getState().tabs.map((t) => t.tabId)

      const res = await adapter.exec('archive', { targets: [realId, 'ghost'] })

      expect(res.success).toBe(true)
      const data = res.data as { succeeded: string[] }
      expect(data.succeeded).toEqual([realId])
    })

    it('batch trash deletes multiple tabs', async () => {
      const adapter = new WorkspaceAdapter(() => 's1')
      await adapter.exec('open', { type: 'er_canvas', title: 'A' })
      await adapter.exec('open', { type: 'dashboard', title: 'B' })
      const ids = useStageStore.getState().tabs.map((t) => t.tabId)

      const res = await adapter.exec('trash', { targets: ids })

      expect(res.success).toBe(true)
      const data = res.data as { succeeded: string[] }
      expect(data.succeeded).toHaveLength(2)
      expect(useStageStore.getState().tabs).toHaveLength(0)
    })

    it('batch trash collects per-item failures', async () => {
      const adapter = new WorkspaceAdapter(() => 's1')
      await adapter.exec('open', { type: 'er_canvas', title: 'A' })
      const [realId] = useStageStore.getState().tabs.map((t) => t.tabId)

      // Mock coordinator.delete to fail for 'ghost'
      const { coordinator } = await import('@/features/stage/persistence/stage-persistence-bootstrap')
      const origDelete = coordinator.delete.bind(coordinator)
      vi.spyOn(coordinator, 'delete').mockImplementation(async (id: string) => {
        if (id === 'ghost') throw new Error('not found')
        return origDelete(id)
      })

      const res = await adapter.exec('trash', { targets: [realId, 'ghost'] })

      expect(res.success).toBe(true) // partial success
      const data = res.data as { succeeded: string[]; failed: { target: string; error: string }[] }
      expect(data.succeeded).toEqual([realId])
      expect(data.failed).toEqual([{ target: 'ghost', error: 'not found' }])
    })

    it('archive falls back to single target when targets is absent', async () => {
      const adapter = new WorkspaceAdapter(() => 's1')
      const opened = await adapter.exec('open', { type: 'er_canvas', title: 'A' })
      const tabId = (opened.data as { tabId: string }).tabId

      const res = await adapter.exec('archive', { target: tabId })

      expect(res.success).toBe(true)
      expect(useStageStore.getState().tabs[0]?.archived).toBe(true)
    })

    it('trash falls back to single target when targets is absent', async () => {
      const adapter = new WorkspaceAdapter(() => 's1')
      const opened = await adapter.exec('open', { type: 'er_canvas', title: 'A' })
      const tabId = (opened.data as { tabId: string }).tabId

      const res = await adapter.exec('trash', { target: tabId })

      expect(res.success).toBe(true)
      expect(useStageStore.getState().tabs).toHaveLength(0)
    })
  })
})
