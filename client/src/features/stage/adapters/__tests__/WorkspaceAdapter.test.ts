import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import { WorkspaceAdapter } from '../WorkspaceAdapter'
import { useStageStore } from '@/stores/stage-store'
import { useDataSourcePickerStore } from '@/features/session/data-source-picker/data-source-picker-store'

const realOpenQueryEditor = useStageStore.getState().openQueryEditor

describe('WorkspaceAdapter', () => {
  beforeEach(() => {
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
      scope: 'session',
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
      scope: 'session',
      baseTitle: 'query_editor',
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
      scope: 'session',
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

  it('rejects session-scoped query_editor open without an active session', async () => {
    const adapter = new WorkspaceAdapter(() => null)
    const res = await adapter.exec('open', {
      type: 'query_editor',
      title: 'SQL',
      connection_id: 'conn-1',
      database: 'db-1',
      schema: 'public',
    })
    expect(res.success).toBe(false)
    expect(res.error).toContain('active session')
  })

  it('exec open sets the new tab as active', async () => {
    useStageStore.getState().openTab({
      tabId: 'session-q1',
      type: 'query_editor',
      title: 'Session SQL',
      scope: 'session',
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
    const state = adapter.read('state') as { tabs: unknown[]; activeTabId: string | null }
    expect(state.tabs.length).toBe(1)
  })

  it('read state prefers normalized payload connectionId for query_editor tabs', () => {
    useStageStore.getState().openTab({
      tabId: 'q-payload',
      type: 'query_editor',
      title: 'SQL',
      scope: 'session',
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
      scope: 'session',
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
      tabs: Array<{ tabId: string; connectionId?: string | null; contextOverride?: unknown }>
      activeTabId: string | null
    }

    expect(state.tabs).toEqual([
      expect.objectContaining({
        tabId,
        connectionId: 'conn-2',
        contextOverride: expect.objectContaining({
          connectionId: 'conn-2',
          database: 'warehouse',
          schema: 'reporting',
        }),
      }),
    ])
  })

  it('exec close archives the tab (alias of archive(archived=true))', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const opened = await adapter.exec('open', { type: 'er_canvas', title: 'ER' })
    const tabId = (opened.data as { tabId: string }).tabId
    const closed = await adapter.exec('close', { target: tabId })
    expect(closed.success).toBe(true)
    expect(useStageStore.getState().openTabIds.has(tabId)).toBe(false)
    expect(useStageStore.getState().tabs).toHaveLength(1)
    expect(useStageStore.getState().tabs[0]?.archived).toBe(true)
  })

  it('exec focus sets the target as active', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const opened = await adapter.exec('open', { type: 'er_canvas', title: 'ER' })

    const focused = await adapter.exec('focus', { target: (opened.data as { tabId: string }).tabId })

    expect(focused).toEqual({ success: true })
    expect(useStageStore.getState().activeTabId).toBe((opened.data as { tabId: string }).tabId)
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
        scope: 'workspace',
      }),
    ])
  })

  it('rejects open without type', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const res = await adapter.exec('open', {})
    expect(res.success).toBe(false)
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
})
