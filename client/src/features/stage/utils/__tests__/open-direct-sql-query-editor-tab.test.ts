import { beforeEach, describe, expect, it } from 'vitest'
import { openDirectSqlQueryEditorTab } from '../open-direct-sql-query-editor-tab'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore } from '@/stores/stage-store'

describe('openDirectSqlQueryEditorTab', () => {
  beforeEach(() => {
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

  it('opens a session-scoped query editor tab and marks it auto-run', async () => {
    const tabId = await openDirectSqlQueryEditorTab({
      sessionId: 'sess-1',
      connectionId: 'conn-1',
      sql: 'SELECT id, name FROM users',
    })

    expect(tabId).toContain('query_editor_')
    expect(useStageStore.getState().openBySession.get('sess-1')).toBe(true)

    const tabs = useStageStore.getState().tabsBySession.get('sess-1') ?? []
    expect(tabs).toHaveLength(1)
    expect(tabs[0]).toEqual(expect.objectContaining({
      type: 'query_editor',
      scope: 'session',
      originSessionId: 'sess-1',
      connectionId: 'conn-1',
      connectionName: 'orders-prod',
      database: 'session-db',
      schema: 'session-schema',
      payload: expect.objectContaining({
        entryMode: 'direct_sql',
        initialSql: 'SELECT id, name FROM users',
        source: 'user',
        autoRun: true,
      }),
    }))
  })

  it('uses a unique tab title when opening multiple direct SQL query editors', async () => {
    await openDirectSqlQueryEditorTab({
      sessionId: 'sess-1',
      connectionId: 'conn-1',
      sql: 'SELECT 1',
    })
    await openDirectSqlQueryEditorTab({
      sessionId: 'sess-1',
      connectionId: 'conn-1',
      sql: 'SELECT 2',
    })

    const tabs = useStageStore.getState().tabsBySession.get('sess-1') ?? []
    expect(tabs).toHaveLength(2)
    const firstTitle = tabs[0]?.title ?? ''
    expect(firstTitle.length).toBeGreaterThan(0)
    expect(tabs[1]?.title).toBe(`${firstTitle}2`)
  })

  it('throws when no connectionId is provided', async () => {
    await expect(openDirectSqlQueryEditorTab({
      sessionId: 'sess-1',
      connectionId: null,
      sql: 'SELECT 1',
    })).rejects.toThrow()
  })

  it('respects explicit autoRun override', async () => {
    await openDirectSqlQueryEditorTab({
      sessionId: 'sess-1',
      connectionId: 'conn-1',
      sql: 'WITH cte AS (SELECT 1) SELECT * FROM cte',
      autoRun: false,
    })

    const tabs = useStageStore.getState().tabsBySession.get('sess-1') ?? []
    expect(tabs).toHaveLength(1)
    expect((tabs[0]?.payload as { autoRun?: boolean } | undefined)?.autoRun).toBe(false)
  })
})
