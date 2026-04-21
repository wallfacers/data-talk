import { describe, it, expect, beforeEach, vi } from 'vitest'
import { BangQueryAdapter } from '../BangQueryAdapter'
import { useStageStore, type StageTab } from '@/stores/stage-store'

function makeTab(tabId: string): StageTab {
  return {
    tabId, type: 'bang_query', title: 'SELECT 1', scope: 'workspace',
    connectionId: 'conn-1', connectionName: 'conn-name', database: 'analytics', schema: 'public', originSessionId: 'sess-1', pinned: false,
    payload: { sql: 'SELECT 1', lastRun: { columns: ['c'], rowCount: 1, durationMs: 5, truncated: false }, rows: [{ c: 1 }] },
    createdAt: 1,
  }
}

describe('BangQueryAdapter', () => {
  beforeEach(() => {
    useStageStore.setState({ workspaceTabs: [], tabsBySession: new Map(), activeWorkspaceTabId: null, activeTabIdBySession: new Map() } as unknown as Record<string, unknown>)
    useStageStore.getState().openTab(makeTab('bq1'))
  })

  it('read state excludes rows, includes sql + lastRun', () => {
    const adapter = new BangQueryAdapter('bq1')
    const state = adapter.read('state') as Record<string, unknown>
    expect(state.sql).toBe('SELECT 1')
    expect(state.lastRun).toBeDefined()
    expect(state.rows).toBeUndefined()
    expect(state.connectionId).toBe('conn-1')
    expect(state.pinned).toBe(false)
  })

  it('patch allows /pinned only', async () => {
    const adapter = new BangQueryAdapter('bq1')
    const ok = await adapter.patch([{ op: 'replace', path: '/pinned', value: true }])
    expect(ok.status).toBe('applied')
    expect(useStageStore.getState().workspaceTabs[0].pinned).toBe(true)
  })

  it('exec rerun calls executeQuery + updates payload', async () => {
    const spy = vi.fn(async () => ({ columns: ['c'], rows: [{ c: 2 }], durationMs: 3, rowCount: 1 }))
    const adapter = new BangQueryAdapter('bq1', { executeQuery: spy })
    const res = await adapter.exec('rerun')
    expect(res.success).toBe(true)
    expect(spy).toHaveBeenCalledWith({
      connectionId: 'conn-1',
      sql: 'SELECT 1',
      sessionId: 'sess-1',
      database: 'analytics',
      schema: 'public',
    })
    const updated = useStageStore.getState().workspaceTabs[0].payload as { rows: unknown[] }
    expect(updated.rows).toEqual([{ c: 2 }])
  })

  it('exec close removes tab', async () => {
    const adapter = new BangQueryAdapter('bq1')
    await adapter.exec('close')
    expect(useStageStore.getState().workspaceTabs).toHaveLength(0)
  })

  it('exec focus clears the current session-active tab for workspace bang_query tabs', async () => {
    useStageStore.setState({
      activeTabIdBySession: new Map([['sess-1', 'session-q1']]),
    } as unknown as Record<string, unknown>)

    const adapter = new BangQueryAdapter('bq1')
    const result = await adapter.exec('focus')

    expect(result).toEqual({ success: true })
    expect(useStageStore.getState().activeWorkspaceTabId).toBe('bq1')
    expect(useStageStore.getState().activeTabIdBySession.get('sess-1')).toBeNull()
  })
})
