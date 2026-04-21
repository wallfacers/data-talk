import { describe, it, expect, beforeEach, vi } from 'vitest'
import { WorkspaceAdapter } from '../WorkspaceAdapter'
import { useStageStore } from '@/stores/stage-store'
import { useDataSourcePickerStore } from '@/features/session/data-source-picker/data-source-picker-store'

describe('WorkspaceAdapter', () => {
  beforeEach(() => {
    useStageStore.setState({
      workspaceTabs: [], tabsBySession: new Map(),
      activeWorkspaceTabId: null, activeTabIdBySession: new Map(),
    } as unknown as Record<string, unknown>)
  })

  it('exec open for query_editor uses the open-or-focus rule', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const res = await adapter.exec('open', {
      type: 'query_editor',
      title: 'SQL',
      connection_id: 'conn-1',
      database: 'db-1',
      schema: 'public',
    })
    expect(res.success).toBe(true)
    const tabs = useStageStore.getState().tabsBySession.get('s1') ?? []
    expect(tabs).toHaveLength(1)
    expect(tabs[0].type).toBe('query_editor')
    expect(tabs[0].connectionId).toBe('conn-1')
    expect(tabs[0].schema).toBe('public')
    expect(tabs[0].originSessionId).toBe('s1')
  })

  it('exec open for query_editor allows missing connection_id and keeps the tab session-scoped', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const res = await adapter.exec('open', {
      type: 'query_editor',
      title: 'SQL',
    })

    expect(res.success).toBe(true)
    const tabs = useStageStore.getState().tabsBySession.get('s1') ?? []
    expect(tabs).toEqual([
      expect.objectContaining({
        tabId: (res.data as { tabId: string }).tabId,
        type: 'query_editor',
        title: 'SQL',
        scope: 'session',
        originSessionId: 's1',
        connectionId: undefined,
        database: undefined,
        schema: undefined,
      }),
    ])
  })

  it('exec open deduplicates query_editor tabs by type + connection + database + schema', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    await adapter.exec('open', {
      type: 'query_editor',
      title: 'SQL',
      connection_id: 'conn-1',
      database: 'db-1',
      schema: 'public',
    })
    await adapter.exec('open', {
      type: 'query_editor',
      title: 'SQL',
      connection_id: 'conn-1',
      database: 'db-1',
      schema: 'public',
    })

    const tabs = useStageStore.getState().tabsBySession.get('s1') ?? []
    expect(tabs).toHaveLength(1)
    expect(useStageStore.getState().activeTabIdBySession.get('s1')).toBe(tabs[0].tabId)
  })

  it('exec open applies payload and title updates when query_editor deduplicates to an existing tab', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    await adapter.exec('open', {
      type: 'query_editor',
      title: 'SQL A',
      connection_id: 'conn-1',
      database: 'db-1',
      schema: 'public',
      payload: { initialSql: 'select 1' },
    })
    await adapter.exec('open', {
      type: 'query_editor',
      title: 'SQL B',
      connection_id: 'conn-1',
      database: 'db-1',
      schema: 'public',
      payload: { initialSql: 'select 2' },
    })

    const tabs = useStageStore.getState().tabsBySession.get('s1') ?? []
    expect(tabs).toHaveLength(1)
    expect(tabs[0]).toEqual(expect.objectContaining({
      title: 'SQL B',
      payload: expect.objectContaining({ initialSql: 'select 2' }),
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

  it('exec open preserves legacy bang_query as a workspace-scoped tab', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const res = await adapter.exec('open', {
      type: 'bang_query',
      title: 'Direct SQL',
    })

    expect(res.success).toBe(true)
    expect(useStageStore.getState().workspaceTabs).toEqual([
      expect.objectContaining({
        tabId: (res.data as { tabId: string }).tabId,
        type: 'bang_query',
        title: 'Direct SQL',
        scope: 'workspace',
      }),
    ])
    expect(useStageStore.getState().tabsBySession.get('s1') ?? []).toEqual([])
  })

  it('exec open clears the current session-active tab when opening a workspace tab', async () => {
    useStageStore.getState().openTab({
      tabId: 'session-q1',
      type: 'query_editor',
      title: 'Session SQL',
      scope: 'session',
      originSessionId: 's1',
      payload: {},
      createdAt: 0,
    })
    useStageStore.setState({
      activeTabIdBySession: new Map([['s1', 'session-q1']]),
    } as unknown as Record<string, unknown>)

    const adapter = new WorkspaceAdapter(() => 's1')
    const opened = await adapter.exec('open', { type: 'report', title: 'Revenue' })

    expect(opened.success).toBe(true)
    expect(useStageStore.getState().activeWorkspaceTabId).toBe((opened.data as { tabId: string }).tabId)
    expect(useStageStore.getState().activeTabIdBySession.get('s1')).toBeNull()
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

  it('exec close removes tab', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const opened = await adapter.exec('open', { type: 'er_canvas', title: 'ER' })
    const tabId = (opened.data as { tabId: string }).tabId
    const closed = await adapter.exec('close', { target: tabId })
    expect(closed.success).toBe(true)
    expect(useStageStore.getState().workspaceTabs).toHaveLength(0)
  })

  it('exec focus clears the current session-active tab when targeting a workspace tab', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const opened = await adapter.exec('open', { type: 'er_canvas', title: 'ER' })

    useStageStore.setState({
      activeTabIdBySession: new Map([['s1', 'session-q1']]),
    } as unknown as Record<string, unknown>)

    const focused = await adapter.exec('focus', { target: (opened.data as { tabId: string }).tabId })

    expect(focused).toEqual({ success: true })
    expect(useStageStore.getState().activeWorkspaceTabId).toBe((opened.data as { tabId: string }).tabId)
    expect(useStageStore.getState().activeTabIdBySession.get('s1')).toBeNull()
  })

  it.each([
    ['er_canvas', 'ER Canvas'],
    ['report', 'Report'],
    ['dashboard', 'Dashboard'],
  ])('exec open treats %s as a workspace-scoped tab', async (type, title) => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const opened = await adapter.exec('open', { type, title })

    expect(opened.success).toBe(true)
    expect(useStageStore.getState().workspaceTabs).toEqual([
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
  })
})
