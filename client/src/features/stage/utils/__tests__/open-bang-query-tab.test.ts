import { describe, it, expect, beforeEach, vi } from 'vitest'
import { openBangQueryTab } from '../open-bang-query-tab'
import { useConnectionStore } from '@/features/connection/store'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { executeQuery } from '@/services/api/query'

vi.mock('@/services/api/query', () => ({
  executeQuery: vi.fn(async () => ({
    columns: ['c'],
    rows: [{ c: 1 }],
    durationMs: 5,
    rowCount: 1,
    resolvedContext: {
      connectionId: 'c1',
      connectionName: 'orders-prod',
      database: 'analytics',
      schema: 'reporting',
      selectedLevel: 'schema',
    },
    contextNotice: '已自动使用 reporting schema',
  })),
}))

describe('openBangQueryTab', () => {
  beforeEach(() => {
    useConnectionStore.setState({
      activeConnectionId: null,
      connections: [{
        id: 'c1',
        name: 'orders-prod',
        kind: 'mysql',
        host: 'prod.db.local',
        port: 3306,
        databaseName: 'orders',
        username: 'root',
        createdAt: 1,
        connectTimeout: 3000,
        lastTestStatus: 'ok',
        lastTestAt: 1,
      }],
    })
    useSessionStore.setState({
      activeSessionId: 's1',
      dataContextBySession: new Map([['s1', {
        sessionId: 's1',
        connectionId: 'c1',
        connectionNameSnapshot: 'orders-prod',
        database: null,
        schema: null,
        selectedLevel: 'connection',
        updatedAt: 1,
      }]]),
    } as any)
    useStageStore.setState({
      workspaceTabs: [],
      tabsBySession: new Map(),
      activeWorkspaceTabId: null,
      activeTabIdBySession: new Map(),
      openBySession: new Map(),
      autoOpenedSessions: new Set(),
    } as unknown as Record<string, unknown>)
  })

  it('passes session context to query execution and persists resolved context on tab', async () => {
    await openBangQueryTab({ sessionId: 's1', connectionId: 'c1', sql: 'SELECT 1' })

    expect(executeQuery).toHaveBeenCalledWith({
      connectionId: 'c1',
      sql: 'SELECT 1',
      sessionId: 's1',
      database: null,
      schema: null,
    })

    const tabs = useStageStore.getState().workspaceTabs
    expect(tabs).toHaveLength(1)
    expect(tabs[0].connectionId).toBe('c1')
    expect(tabs[0].database).toBe('analytics')
    expect(tabs[0].schema).toBe('reporting')
    expect((tabs[0].payload as any).contextNotice).toBe('已自动使用 reporting schema')
  })

  it('throws when no connectionId provided', async () => {
    await expect(openBangQueryTab({ sessionId: 's1', connectionId: null, sql: 'SELECT 1' })).rejects.toThrow()
  })

  it('clears the current session-active tab when opening a bang_query workspace tab', async () => {
    useStageStore.setState({
      activeTabIdBySession: new Map([['s1', 'session-q1']]),
    } as unknown as Record<string, unknown>)

    await openBangQueryTab({ sessionId: 's1', connectionId: 'c1', sql: 'SELECT 1' })

    expect(useStageStore.getState().activeWorkspaceTabId).toBeTruthy()
    expect(useStageStore.getState().activeTabIdBySession.get('s1')).toBeNull()
  })
})
