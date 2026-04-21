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

  it('read state returns tabs + active', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    await adapter.exec('open', { type: 'bang_query', title: 'Q' })
    const state = adapter.read('state') as { tabs: unknown[]; activeTabId: string | null }
    expect(state.tabs.length).toBe(1)
  })

  it('exec close removes tab', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const opened = await adapter.exec('open', { type: 'bang_query', title: 'Q' })
    const tabId = (opened.data as { tabId: string }).tabId
    const closed = await adapter.exec('close', { target: tabId })
    expect(closed.success).toBe(true)
    expect(useStageStore.getState().workspaceTabs).toHaveLength(0)
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
