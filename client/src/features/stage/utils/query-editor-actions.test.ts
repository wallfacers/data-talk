import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useConnectionStore } from '@/features/connection/store'
import { SqlRiskError } from '@/services/api/sql'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore } from '@/stores/stage-store'
import { normalizeQueryEditorPayload } from './normalize-query-editor-payload'
import { useSqlWorkbenchStore } from '../stores/sql-workbench-store'
import { formatQueryEditorSql, runQueryEditorSql, setQueryEditorContext } from './query-editor-actions'

const executeSqlMock = vi.hoisted(() => vi.fn())
const formatSqlMock = vi.hoisted(() => vi.fn((sql: string) => `formatted: ${sql}`))

vi.mock('@/services/api/sql', async () => {
  const actual = await vi.importActual<typeof import('@/services/api/sql')>('@/services/api/sql')
  return {
    ...actual,
    executeSql: executeSqlMock,
  }
})

vi.mock('./format-sql', () => ({
  formatSql: formatSqlMock,
}))

function resetStores() {
  useStageStore.setState({
    openBySession: new Map(),
    autoOpenedSessions: new Set(),
    maximizedBySession: new Map(),
    revealOrigin: null,
    sidebarCollapsedBySession: new Map(),
    sidebarSelectionBySession: new Map(),
    resourceTreeExpandedBySession: new Map(),
    activeRailPanelBySession: new Map(),
    workspaceTabs: [],
    tabsBySession: new Map(),
    activeWorkspaceTabId: null,
    activeTabIdBySession: new Map(),
  })
  useSqlWorkbenchStore.setState({ tabsById: {} })
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
}

function getStageTab(tabId: string) {
  const state = useStageStore.getState()
  return state.workspaceTabs.find((tab) => tab.tabId === tabId)
    ?? Array.from(state.tabsBySession.values()).flat().find((tab) => tab.tabId === tabId)
    ?? null
}

describe('query-editor-actions', () => {
  beforeEach(() => {
    executeSqlMock.mockReset()
    formatSqlMock.mockClear()
    resetStores()
  })

  it('runs SQL with limit injection, updates workbench state, and appends history', async () => {
    useConnectionStore.setState({
      activeConnectionId: 'conn-1',
      connections: [
        { id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' } as any,
      ],
    })

    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 'sess-1',
      scope: 'session',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 1;',
      connectionId: 'conn-1',
      connectionName: 'Primary Connection',
      database: 'db_main',
    })

    executeSqlMock.mockResolvedValue({
      resolvedContext: {
        connectionId: 'conn-1',
        connectionName: 'Primary Connection',
        database: 'db_main',
        schema: null,
        selectedLevel: 'database',
      },
      contextNotice: null,
      results: [
        {
          resultId: 'result-1',
          kind: 'result_set',
          title: 'Result 1',
          statementIndex: 0,
          statementText: 'select 1',
          columns: ['n'],
          rows: [[1]],
          rowCount: 1,
          executionMs: 5,
          truncated: false,
        },
      ],
    })

    await expect(runQueryEditorSql({
      tabId,
      sessionId: 'sess-1',
      limit: 10,
    })).resolves.toEqual({
      executeStatus: 'success',
      activeResultId: 'result-1',
    })

    expect(executeSqlMock).toHaveBeenCalledWith({
      sql: 'select 1 LIMIT 10;',
      connectionId: 'conn-1',
      source: 'user',
      sessionId: 'sess-1',
      database: 'db_main',
    }, expect.any(AbortSignal))

    const tabState = useSqlWorkbenchStore.getState().tabsById[tabId]
    expect(tabState).toMatchObject({
      executeStatus: 'success',
      activeResultId: 'result-1',
    })
    expect(tabState?.history).toHaveLength(1)
    expect(tabState?.history[0]).toMatchObject({
      sql: 'select 1 LIMIT 10;',
      status: 'ok',
      resultCount: 1,
    })
  })

  it('injects limit into each select statement for multi-statement SQL', async () => {
    useConnectionStore.setState({
      activeConnectionId: 'conn-1',
      connections: [
        { id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' } as any,
      ],
    })

    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 'sess-1',
      scope: 'session',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select * from users;\nselectd * from orders;',
      connectionId: 'conn-1',
      connectionName: 'Primary Connection',
      database: 'db_main',
    })

    executeSqlMock.mockResolvedValue({
      resolvedContext: {
        connectionId: 'conn-1',
        connectionName: 'Primary Connection',
        database: 'db_main',
        schema: null,
        selectedLevel: 'database',
      },
      contextNotice: null,
      results: [],
    })

    await runQueryEditorSql({
      tabId,
      sessionId: 'sess-1',
      limit: 10,
    })

    expect(executeSqlMock).toHaveBeenCalledWith({
      sql: 'select * from users LIMIT 10;\nselectd * from orders;',
      connectionId: 'conn-1',
      source: 'user',
      sessionId: 'sess-1',
      database: 'db_main',
    }, expect.any(AbortSignal))
  })

  it('stores risk-blocked executions in workbench state and history', async () => {
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 'sess-1',
      scope: 'session',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'delete from users',
      connectionId: 'conn-1',
    })

    executeSqlMock.mockRejectedValue(new SqlRiskError({
      riskLevel: 'high',
      riskReason: 'writes are not allowed',
    }))

    await expect(runQueryEditorSql({
      tabId,
      sessionId: 'sess-1',
      limit: null,
    })).resolves.toEqual({
      executeStatus: 'risk_blocked',
      activeResultId: null,
    })

    const tabState = useSqlWorkbenchStore.getState().tabsById[tabId]
    expect(tabState).toMatchObject({
      executeStatus: 'risk_blocked',
      risk: {
        riskLevel: 'high',
        riskReason: 'writes are not allowed',
      },
    })
    expect(tabState?.history).toHaveLength(1)
    expect(tabState?.history[0]).toMatchObject({
      sql: 'delete from users',
      status: 'risk_blocked',
      errorSummary: 'writes are not allowed',
    })
  })

  it('formats SQL through the shared stage-store document path', () => {
    useConnectionStore.setState({
      activeConnectionId: 'conn-2',
      connections: [
        { id: 'conn-2', name: 'Analytics', kind: 'mysql', databaseName: 'analytics' } as any,
      ],
    })

    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 'sess-1',
      scope: 'session',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'resource_sql',
      initialContent: 'select id from users',
      connectionId: 'conn-2',
      connectionName: 'Analytics',
      database: 'analytics',
    })

    expect(formatQueryEditorSql(tabId)).toEqual({
      version: 2,
      content: 'formatted: select id from users',
    })

    expect(formatSqlMock).toHaveBeenCalledWith('select id from users', 'mysql')
    expect(useSqlWorkbenchStore.getState().tabsById[tabId]).toMatchObject({
      sqlText: 'formatted: select id from users',
      version: 2,
    })
  })

  it('writes context overrides through the shared context action and clears them when returning to the base context', () => {
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 'sess-1',
      scope: 'session',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'resource_sql',
      initialContent: 'select 1',
      connectionId: 'conn-1',
      connectionName: 'Primary Connection',
      database: 'db_main',
      schema: 'public',
    })

    setQueryEditorContext({
      tabId,
      connectionId: 'conn-2',
      database: 'warehouse',
      schema: 'analytics',
    })

    expect(useSqlWorkbenchStore.getState().tabsById[tabId]?.override).toMatchObject({
      connectionId: 'conn-2',
      database: 'warehouse',
      schema: 'analytics',
      source: 'api',
    })
    expect(normalizeQueryEditorPayload(getStageTab(tabId)?.payload).contextOverride).toEqual({
      connectionId: 'conn-2',
      database: 'warehouse',
      schema: 'analytics',
    })

    setQueryEditorContext({
      tabId,
      connectionId: 'conn-1',
      database: 'db_main',
      schema: 'public',
    })

    expect(useSqlWorkbenchStore.getState().tabsById[tabId]?.override).toBeNull()
    expect(normalizeQueryEditorPayload(getStageTab(tabId)?.payload).contextOverride).toBeNull()
    expect(getStageTab(tabId)).toEqual(expect.objectContaining({
      connectionId: 'conn-1',
      database: 'db_main',
      schema: 'public',
    }))
  })

  it('runs SQL against a persisted payload contextOverride when no runtime override is hydrated yet', async () => {
    useConnectionStore.setState({
      activeConnectionId: 'conn-1',
      connections: [
        { id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' } as any,
        { id: 'conn-2', name: 'Warehouse', kind: 'postgres', databaseName: 'warehouse' } as any,
      ],
    })

    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 'sess-1',
      scope: 'session',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 42',
      connectionId: 'conn-1',
      connectionName: 'Primary Connection',
      database: 'db_main',
    })

    useStageStore.getState().updateTabPayload(tabId, (payload) => ({
      ...(payload as Record<string, unknown>),
      contextOverride: {
        connectionId: 'conn-2',
        database: 'warehouse',
        schema: 'analytics',
      },
    }))

    executeSqlMock.mockResolvedValue({
      resolvedContext: {
        connectionId: 'conn-2',
        connectionName: 'Warehouse',
        database: 'warehouse',
        schema: 'analytics',
        selectedLevel: 'schema',
      },
      contextNotice: null,
      results: [],
    })

    await runQueryEditorSql({
      tabId,
      sessionId: 'sess-1',
      limit: null,
    })

    expect(executeSqlMock).toHaveBeenCalledWith({
      sql: 'select 42',
      connectionId: 'conn-2',
      source: 'user',
      sessionId: 'sess-1',
      database: 'warehouse',
      schema: 'analytics',
    }, expect.any(AbortSignal))
  })

  it('creates and persists an override when explicitly pinning the current inherited context', () => {
    useSessionStore.setState({
      activeSessionId: 'sess-1',
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      dataContextBySession: new Map([[
        'sess-1',
        {
          sessionId: 'sess-1',
          connectionId: 'conn-2',
          connectionNameSnapshot: 'Warehouse',
          database: 'warehouse',
          schema: 'analytics',
          selectedLevel: 'schema',
          updatedAt: 1,
        },
      ]]),
      pendingPrompt: null,
      composerRestoreDraft: null,
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
    })

    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 'sess-1',
      scope: 'session',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 1',
    })

    setQueryEditorContext({
      tabId,
      connectionId: 'conn-2',
      database: 'warehouse',
      schema: 'analytics',
    })

    expect(useSqlWorkbenchStore.getState().tabsById[tabId]?.override).toMatchObject({
      connectionId: 'conn-2',
      database: 'warehouse',
      schema: 'analytics',
      source: 'api',
    })
    expect(normalizeQueryEditorPayload(getStageTab(tabId)?.payload).contextOverride).toEqual({
      connectionId: 'conn-2',
      database: 'warehouse',
      schema: 'analytics',
    })
  })
})
