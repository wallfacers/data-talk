import { beforeEach, describe, expect, it, vi } from 'vitest'
import { openDirectSqlQueryEditorTab } from '../open-direct-sql-query-editor-tab'
import { executeQuery } from '@/services/api/query'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore } from '@/stores/stage-store'

vi.mock('@/services/api/query', () => ({
  executeQuery: vi.fn(),
}))

describe('openDirectSqlQueryEditorTab', () => {
  beforeEach(() => {
    vi.mocked(executeQuery).mockReset()
    useConnectionStore.setState({
      activeConnectionId: null,
      connections: [
        { id: 'conn-1', name: 'orders-prod' },
      ] as any,
    })
    useSessionStore.setState({
      activeSessionId: 'sess-1',
      dataContextBySession: new Map([['sess-1', {
        sessionId: 'sess-1',
        connectionId: 'conn-1',
        connectionNameSnapshot: 'orders-prod',
        database: 'session-db',
        schema: 'session-schema',
        selectedLevel: 'schema',
        updatedAt: 1,
      }]]),
    } as any)
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
    } as any)
  })

  it('executes immediately and opens a session-scoped query editor tab with normalized payload', async () => {
    vi.mocked(executeQuery).mockResolvedValue({
      columns: ['id', 'name'],
      rows: [{ id: 1, name: 'alpha' }],
      durationMs: 17,
      rowCount: 2,
      resolvedContext: {
        connectionId: 'conn-resolved',
        connectionName: 'warehouse-prod',
        database: 'analytics',
        schema: 'reporting',
        selectedLevel: 'schema',
      },
      contextNotice: 'Using reporting schema',
    } as any)

    const tabId = await openDirectSqlQueryEditorTab({
      sessionId: 'sess-1',
      connectionId: 'conn-1',
      sql: 'SELECT id, name FROM users',
    })

    expect(executeQuery).toHaveBeenCalledWith({
      connectionId: 'conn-1',
      sql: 'SELECT id, name FROM users',
      sessionId: 'sess-1',
      database: 'session-db',
      schema: 'session-schema',
    })

    expect(tabId).toContain('query_editor_')
    expect(useStageStore.getState().openBySession.get('sess-1')).toBe(true)

    const tabs = useStageStore.getState().tabsBySession.get('sess-1') ?? []
    expect(tabs).toHaveLength(1)
    expect(tabs[0]).toEqual(expect.objectContaining({
      type: 'query_editor',
      scope: 'session',
      originSessionId: 'sess-1',
      connectionId: 'conn-resolved',
      connectionName: 'warehouse-prod',
      database: 'analytics',
      schema: 'reporting',
      payload: expect.objectContaining({
        entryMode: 'direct_sql',
        initialSql: 'SELECT id, name FROM users',
        source: 'user',
        autoRun: false,
        contextNotice: 'Using reporting schema',
        initialResult: {
          columns: ['id', 'name'],
          rows: [[1, 'alpha']],
          rowCount: 2,
          executionMs: 17,
          truncated: true,
        },
        lastRun: {
          columns: ['id', 'name'],
          rowCount: 2,
          executionMs: 17,
          truncated: true,
        },
      }),
    }))
  })

  it('falls back to the provided connection and connection name when the API does not resolve context', async () => {
    vi.mocked(executeQuery).mockResolvedValue({
      columns: ['id'],
      rows: [{ id: 1 }],
      durationMs: 8,
      rowCount: 1,
      resolvedContext: null,
      contextNotice: null,
    } as any)

    await openDirectSqlQueryEditorTab({
      sessionId: 'sess-1',
      connectionId: 'conn-1',
      sql: 'SELECT 1',
    })

    const tabs = useStageStore.getState().tabsBySession.get('sess-1') ?? []
    expect(tabs[0]).toEqual(expect.objectContaining({
      connectionId: 'conn-1',
      connectionName: 'orders-prod',
      database: 'session-db',
      schema: 'session-schema',
    }))
  })

  it('throws when no connectionId is provided', async () => {
    await expect(openDirectSqlQueryEditorTab({
      sessionId: 'sess-1',
      connectionId: null,
      sql: 'SELECT 1',
    })).rejects.toThrow()
  })
})
