import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore } from '@/stores/stage-store'
import { normalizeQueryEditorPayload } from './normalize-query-editor-payload'
import { useSqlWorkbenchStore } from '../stores/sql-workbench-store'
import {
  formatQueryEditorSql,
  runQueryEditorSql,
  setQueryEditorContext,
  resetQueryEditorContext,
  confirmQueryEditorSql,
  cancelQueryEditorConfirmation,
} from './query-editor-actions'

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

const toastErrorMock = vi.hoisted(() => vi.fn())
vi.mock('sonner', () => ({
  toast: { error: toastErrorMock, success: vi.fn() },
}))

function resetStores() {
  useStageStore.setState({
    open: false,
    maximized: false,
    revealOrigin: null,
    sidebarCollapsed: false,
    sidebarSelection: null,
    resourceTreeExpanded: [],
    activeRailPanel: null,
    tabs: [],
    openTabIds: new Set(),
    openTabIdsOrdered: [],
    activeTabId: null,
  } as never)
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
  return state.tabs.find((tab) => tab.tabId === tabId) ?? null
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
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 1;',
      connectionId: 'conn-1',
      connectionName: 'Primary Connection',
      database: 'db_main',
    })

    executeSqlMock.mockResolvedValue({
      status: 'executed',
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
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select * from users;\nselectd * from orders;',
      connectionId: 'conn-1',
      connectionName: 'Primary Connection',
      database: 'db_main',
    })

    executeSqlMock.mockResolvedValue({
      status: 'executed',
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

  it('runs an explicit SQL override instead of the full editor content', async () => {
    useConnectionStore.setState({
      activeConnectionId: 'conn-1',
      connections: [
        { id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' } as any,
      ],
    })

    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 'sess-1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 1;\nselect 2;',
      connectionId: 'conn-1',
      connectionName: 'Primary Connection',
      database: 'db_main',
    })

    executeSqlMock.mockResolvedValue({
      status: 'executed',
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
      limit: null,
      sqlOverride: 'select 2',
    })

    expect(executeSqlMock).toHaveBeenCalledWith({
      sql: 'select 2',
      connectionId: 'conn-1',
      source: 'user',
      sessionId: 'sess-1',
      database: 'db_main',
    }, expect.any(AbortSignal))
  })

  it('stores requires_confirmation executions in workbench state and history', async () => {
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 'sess-1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'delete from users',
      connectionId: 'conn-1',
    })

    executeSqlMock.mockResolvedValue({
      status: 'requires_confirmation',
      resolvedContext: null,
      contextNotice: null,
      confirmation: {
        level: 'L2',
        reason: 'This statement modifies data',
        affectedObjects: ['public.users'],
        sqlPreview: 'delete from users',
      },
    })

    await expect(runQueryEditorSql({
      tabId,
      sessionId: 'sess-1',
      limit: null,
    })).resolves.toEqual({
      executeStatus: 'requires_confirmation',
      activeResultId: null,
    })

    const tabState = useSqlWorkbenchStore.getState().tabsById[tabId]
    expect(tabState).toMatchObject({
      executeStatus: 'requires_confirmation',
      confirmation: {
        level: 'L2',
        reason: 'This statement modifies data',
        affectedObjects: ['public.users'],
      },
    })
    expect(tabState?.lastRequest).toMatchObject({
      sql: 'delete from users',
      connectionId: 'conn-1',
    })
    expect(tabState?.history).toHaveLength(1)
    expect(tabState?.history[0]).toMatchObject({
      sql: 'delete from users',
      status: 'requires_confirmation',
      confirmationReason: 'This statement modifies data',
    })
  })

  it('confirmQueryEditorSql re-executes with confirmed=true and riskAck', async () => {
    useConnectionStore.setState({
      activeConnectionId: 'conn-1',
      connections: [
        { id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' } as any,
      ],
    })

    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 'sess-1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'update users set active = false',
      connectionId: 'conn-1',
    })

    executeSqlMock
      .mockResolvedValueOnce({
        status: 'requires_confirmation',
        resolvedContext: null,
        contextNotice: null,
        confirmation: {
          level: 'L2',
          reason: 'This statement modifies data',
          affectedObjects: ['public.users'],
          sqlPreview: 'update users set active = false',
        },
      })
      .mockResolvedValueOnce({
        status: 'executed',
        resolvedContext: null,
        contextNotice: null,
        results: [
          {
            resultId: 'result-confirmed',
            kind: 'dml_summary',
            title: 'DML',
            statementIndex: 0,
            statementText: 'update users set active = false',
            columns: [],
            rows: [],
            rowCount: 0,
            executionMs: 5,
            truncated: false,
            affectedRows: 10,
          },
        ],
      })

    await runQueryEditorSql({ tabId, sessionId: 'sess-1', limit: null })

    await expect(confirmQueryEditorSql({
      tabId,
      sessionId: 'sess-1',
      level: 'L2',
    })).resolves.toEqual({
      executeStatus: 'success',
      activeResultId: 'result-confirmed',
    })

    expect(executeSqlMock).toHaveBeenCalledTimes(2)
    expect(executeSqlMock).toHaveBeenNthCalledWith(2, expect.objectContaining({
      confirmed: true,
      riskAck: 'L2',
      sql: 'update users set active = false',
    }), expect.any(AbortSignal))
  })

  it('confirmQueryEditorSql network failure toasts and clears stale confirmation', async () => {
    useConnectionStore.setState({
      activeConnectionId: 'conn-1',
      connections: [
        { id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' } as any,
      ],
    })

    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 'sess-1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'update users set active = false',
      connectionId: 'conn-1',
    })

    executeSqlMock
      .mockResolvedValueOnce({
        status: 'requires_confirmation',
        resolvedContext: null,
        contextNotice: null,
        confirmation: {
          level: 'L2',
          reason: 'This statement modifies data',
          affectedObjects: ['public.users'],
          sqlPreview: 'update users set active = false',
        },
      })
      .mockRejectedValueOnce(new Error('network down'))

    await runQueryEditorSql({ tabId, sessionId: 'sess-1', limit: null })

    toastErrorMock.mockClear()
    const result = await confirmQueryEditorSql({ tabId, sessionId: 'sess-1', level: 'L2' })

    expect(result.executeStatus).toBe('error')
    expect(toastErrorMock).toHaveBeenCalledTimes(1)
    expect(toastErrorMock.mock.calls[0]?.[0]).toMatch(/Confirmation request did not reach the server/i)
    const tabState = useSqlWorkbenchStore.getState().tabsById[tabId]
    expect(tabState?.executeStatus).toBe('error')
    expect(tabState?.confirmation).toBeNull()
    expect(tabState?.errorMessage).toMatch(/network down/)
  })

  it('cancelQueryEditorConfirmation resets to idle', async () => {
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 'sess-1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'update users set active = false',
      connectionId: 'conn-1',
    })

    executeSqlMock.mockResolvedValue({
      status: 'requires_confirmation',
      resolvedContext: null,
      contextNotice: null,
      confirmation: {
        level: 'L2',
        reason: 'This statement modifies data',
        affectedObjects: ['public.users'],
        sqlPreview: 'update users set active = false',
      },
    })

    await runQueryEditorSql({ tabId, sessionId: 'sess-1', limit: null })
    expect(useSqlWorkbenchStore.getState().tabsById[tabId]?.executeStatus).toBe('requires_confirmation')

    cancelQueryEditorConfirmation(tabId)
    expect(useSqlWorkbenchStore.getState().tabsById[tabId]?.executeStatus).toBe('idle')
    expect(useSqlWorkbenchStore.getState().tabsById[tabId]?.confirmation).toBeNull()
    expect(useSqlWorkbenchStore.getState().tabsById[tabId]?.lastRequest).toBeNull()
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

  it('writes context overrides through the shared context action and keeps them until explicit reset', () => {
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 'sess-1',
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

    expect(useSqlWorkbenchStore.getState().tabsById[tabId]?.override).toMatchObject({
      connectionId: 'conn-1',
      database: 'db_main',
      schema: 'public',
      source: 'api',
    })
    expect(normalizeQueryEditorPayload(getStageTab(tabId)?.payload).contextOverride).toEqual({
      connectionId: 'conn-1',
      database: 'db_main',
      schema: 'public',
    })
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
      status: 'executed',
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

  it('clears a pinned tab context so execution returns to the latest session context', async () => {
    useConnectionStore.setState({
      activeConnectionId: 'conn-fixed',
      connections: [
        { id: 'conn-fixed', name: 'Fixed', kind: 'postgres', databaseName: 'fixed_db' } as any,
        { id: 'conn-session', name: 'Session', kind: 'postgres', databaseName: 'session_db' } as any,
      ],
    })
    useSessionStore.setState({
      activeSessionId: 'sess-1',
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      dataContextBySession: new Map([[
        'sess-1',
        {
          sessionId: 'sess-1',
          connectionId: 'conn-session',
          connectionNameSnapshot: 'Session',
          database: 'session_db',
          schema: 'session_schema',
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
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'resource_sql',
      initialContent: 'select 1',
      connectionId: 'conn-fixed',
      connectionName: 'Fixed',
      database: 'fixed_db',
      schema: 'fixed_schema',
    })

    expect(normalizeQueryEditorPayload(getStageTab(tabId)?.payload).contextOverride).toEqual({
      connectionId: 'conn-fixed',
      database: 'fixed_db',
      schema: 'fixed_schema',
    })

    resetQueryEditorContext(tabId)

    expect(useSqlWorkbenchStore.getState().tabsById[tabId]?.override).toBeNull()
    expect(normalizeQueryEditorPayload(getStageTab(tabId)?.payload).contextOverride).toBeNull()
    expect(normalizeQueryEditorPayload(getStageTab(tabId)?.payload).contextPinMode).toBe('session')

    executeSqlMock.mockResolvedValue({
      status: 'executed',
      resolvedContext: {
        connectionId: 'conn-session',
        connectionName: 'Session',
        database: 'session_db',
        schema: 'session_schema',
        selectedLevel: 'schema',
      },
      contextNotice: null,
      results: [],
    })

    await runQueryEditorSql({ tabId, sessionId: 'sess-1', limit: null })

    expect(executeSqlMock).toHaveBeenCalledWith({
      sql: 'select 1',
      connectionId: 'conn-session',
      source: 'user',
      sessionId: 'sess-1',
      database: 'session_db',
      schema: 'session_schema',
    }, expect.any(AbortSignal))
  })

  it('uses the latest session context as the default execution context even when tab metadata and resolvedContext are stale', async () => {
    useConnectionStore.setState({
      activeConnectionId: 'conn-1',
      connections: [
        { id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' } as any,
        { id: 'conn-2', name: 'Warehouse', kind: 'postgres', databaseName: 'warehouse' } as any,
      ],
    })
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
      sessionId: 'sess-1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 1',
      connectionId: 'conn-1',
      connectionName: 'Primary Connection',
      database: 'db_main',
      schema: 'public',
    })
    useStageStore.getState().updateTabPayload(tabId, (payload) => ({
      ...(payload as Record<string, unknown>),
      contextOverride: null,
      contextPinMode: 'session',
    }))

    useSqlWorkbenchStore.setState((state) => ({
      tabsById: {
        ...state.tabsById,
        [tabId]: {
          ...state.tabsById[tabId],
          resolvedContext: {
            connectionId: 'conn-1',
            connectionName: 'Primary Connection',
            database: 'db_main',
            schema: 'public',
            selectedLevel: 'schema',
          },
        },
      },
    }))

    executeSqlMock.mockResolvedValue({
      status: 'executed',
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
      sql: 'select 1',
      connectionId: 'conn-2',
      source: 'user',
      sessionId: 'sess-1',
      database: 'warehouse',
      schema: 'analytics',
    }, expect.any(AbortSignal))
  })
})
