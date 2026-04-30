import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useConnectionStore } from '@/features/connection/store'
import { useStageStore, type StageTab } from '@/stores/stage-store'
import { useSqlWorkbenchStore } from '@/features/stage/stores/sql-workbench-store'
import { useSessionStore } from '@/stores/session-store'
import { QueryEditorAdapter } from '../QueryEditorAdapter'

function resetStageStore() {
  useStageStore.setState({
    tabs: [],
    activeTabId: null,
  } as unknown as Record<string, unknown>)
}

function resetWorkbenchStore() {
  useSqlWorkbenchStore.setState({ tabsById: {} })
}

function resetConnectionStore() {
  useConnectionStore.setState({
    activeConnectionId: null,
    connections: [],
  })
}

function resetSessionStore() {
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

function openTab(tab: StageTab) {
  useStageStore.getState().openTab(tab)
}

describe('QueryEditorAdapter', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    resetStageStore()
    resetWorkbenchStore()
    resetConnectionStore()
    resetSessionStore()
  })

  it('read actions exposes exactly the five query editor actions', () => {
    const adapter = new QueryEditorAdapter('q1')

    expect((adapter.read('actions') as Array<{ name: string }>).map((action) => action.name)).toEqual([
      'apply_text_edits',
      'set_context',
      'run_sql',
      'format_sql',
      'focus',
    ])
  })

  it('read full includes query editor capabilities and patch whitelist', () => {
    const adapter = new QueryEditorAdapter('q1')

    expect(adapter.patchCapabilities).toEqual([
      { pathPattern: '/content', ops: ['replace'] },
      { pathPattern: '/connectionId', ops: ['replace'] },
      { pathPattern: '/database', ops: ['replace'] },
      { pathPattern: '/schema', ops: ['replace'] },
    ])
    expect(adapter.read('full')).toEqual(expect.objectContaining({
      capabilities: {
        editableContent: true,
        acceptsTextEdits: true,
        runnable: true,
        formattable: true,
        supportsContextBinding: true,
        supportsResults: true,
      },
    }))
  })

  it('read state exposes document/runtime fields and summarized results without rows', () => {
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
      initialContent: 'select 42',
      autoRun: true,
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

    useSqlWorkbenchStore.setState((state) => ({
      tabsById: {
        ...state.tabsById,
        [tabId]: {
          ...state.tabsById[tabId],
          version: 3,
          selection: {
            startLine: 1,
            startColumn: 1,
            endLine: 1,
            endColumn: 7,
          },
          cursor: { line: 1, column: 8 },
          executeStatus: 'error',
          results: [
            {
              resultId: 'result-1',
              kind: 'result_set',
              title: 'Result 1',
              statementIndex: 0,
              statementText: 'select 42',
              columns: ['n'],
              rows: [[42]],
              rowCount: 1,
              executionMs: 7,
              truncated: false,
            },
            {
              resultId: 'result-2',
              kind: 'error',
              title: 'Error',
              statementIndex: 1,
              statementText: 'select from',
              columns: [],
              rows: [],
              rowCount: 0,
              executionMs: 2,
              truncated: false,
              errorMessage: 'syntax error',
            },
          ],
          activeResultId: 'result-2',
          savedSqlText: 'select 1',
          limit: 10,
        },
      },
    }))

    const adapter = new QueryEditorAdapter(tabId)
    const state = adapter.read('state') as {
      tabId: string
      title: string
      content: string
      language: 'sql'
      version: number
      dirty: boolean
      cursor: { line: number; column: number }
      selection: {
        startLine: number
        startColumn: number
        endLine: number
        endColumn: number
      } | null
      connectionId: string | null
      connectionName: string | null
      database: string | null
      schema: string | null
      useSessionContext: boolean
      contextSource: 'session' | 'override' | 'tab'
      contextOverride: unknown
      availableDatabases: string[]
      availableSchemas: string[]
      entryMode: string
      autoRun: boolean
      executeStatus: string
      results: Array<Record<string, unknown>>
      activeResultId: string | null
      limit: 10 | 100 | 1000 | null
      inWorkset: boolean
    }

    expect(state).toEqual({
      tabId,
      title: 'SQL',
      content: 'select 42',
      language: 'sql',
      version: 3,
      dirty: true,
      cursor: { line: 1, column: 8 },
      selection: {
        startLine: 1,
        startColumn: 1,
        endLine: 1,
        endColumn: 7,
      },
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
      availableDatabases: ['warehouse'],
      availableSchemas: ['reporting'],
      entryMode: 'ai_open',
      autoRun: true,
      executeStatus: 'error',
      results: [
        {
          resultId: 'result-1',
          statementIndex: 0,
          columns: ['n'],
          rowCount: 1,
          durationMs: 7,
          truncated: false,
        },
        {
          resultId: 'result-2',
          statementIndex: 1,
          columns: [],
          rowCount: 0,
          durationMs: 2,
          truncated: false,
          error: {
            message: 'syntax error',
          },
        },
      ],
      activeResultId: 'result-2',
      limit: 10,
      inWorkset: true,
    })
    expect(state.results[0]).not.toHaveProperty('rows')
    expect(state.results[1]).not.toHaveProperty('rows')
  })

  it('read state exposes inherited session context as the effective top-level context', () => {
    useConnectionStore.setState({
      activeConnectionId: null,
      connections: [
        { id: 'session-conn', name: 'Session Warehouse', kind: 'postgres', databaseName: 'session-db' } as never,
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
          connectionId: 'session-conn',
          connectionNameSnapshot: 'Session Warehouse',
          database: 'session-db',
          schema: 'session-schema',
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
      sessionId: 's1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 1',
    })

    const adapter = new QueryEditorAdapter(tabId, () => 's1')
    const state = adapter.read('state') as {
      connectionId: string | null
      connectionName: string | null
      database: string | null
      schema: string | null
      contextSource: 'session' | 'override' | 'tab'
      contextOverride: unknown
    }

    expect(state).toEqual(expect.objectContaining({
      connectionId: 'session-conn',
      connectionName: 'Session Warehouse',
      database: 'session-db',
      schema: 'session-schema',
      useSessionContext: true,
      contextSource: 'session',
      contextOverride: null,
    }))
  })

  it('read state prefers runtime manual mode over a payload default session mode', () => {
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
    })
    useSqlWorkbenchStore.getState().setTabContext(tabId, {
      connectionId: 'conn-1',
      connectionName: 'Warehouse',
      database: 'analytics',
      schema: 'public',
      source: 'api',
    })

    const state = new QueryEditorAdapter(tabId, () => 's1').read('state') as {
      connectionId: string | null
      database: string | null
      schema: string | null
      useSessionContext: boolean
      contextSource: 'session' | 'override' | 'tab'
    }

    expect(state).toEqual(expect.objectContaining({
      connectionId: 'conn-1',
      database: 'analytics',
      schema: 'public',
      useSessionContext: false,
      contextSource: 'override',
    }))
  })

  it('ignores stale tab metadata and resolvedContext when session context is newer and no override exists', () => {
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

    useSqlWorkbenchStore.setState((state) => ({
      tabsById: {
        ...state.tabsById,
        [tabId]: {
          ...state.tabsById[tabId],
          resolvedContext: {
            connectionId: 'conn-1',
            connectionName: 'Warehouse',
            database: 'analytics',
            schema: 'public',
            selectedLevel: 'schema',
          },
        },
      },
    }))

    const state = new QueryEditorAdapter(tabId, () => 's1').read('state') as {
      connectionId: string | null
      connectionName: string | null
      database: string | null
      schema: string | null
      contextSource: 'session' | 'override' | 'tab'
      contextOverride: unknown
    }

    expect(state).toEqual(expect.objectContaining({
      connectionId: 'conn-2',
      connectionName: 'Reporting Warehouse',
      database: 'warehouse',
      schema: 'reporting',
      contextSource: 'session',
      contextOverride: null,
    }))
  })

  it('getters expose effective context for list/filter surfaces', () => {
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
      entryMode: 'blank',
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

    const adapter = new QueryEditorAdapter(tabId, () => 's1')

    expect(adapter.connectionId).toBe('conn-2')
    expect(adapter.database).toBe('warehouse')
  })

  it('does not reuse the base connection name when payload override connectionId is unknown', () => {
    useConnectionStore.setState({
      activeConnectionId: null,
      connections: [
        { id: 'conn-1', name: 'Warehouse', kind: 'postgres', databaseName: 'analytics' } as never,
      ],
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
      contextOverride: {
        connectionId: 'override-missing',
        database: 'warehouse',
        schema: 'reporting',
      },
    }))

    const adapter = new QueryEditorAdapter(tabId, () => 's1')
    const state = adapter.read('state') as {
      connectionId: string | null
      connectionName: string | null
      database: string | null
      schema: string | null
      contextOverride: {
        connectionId: string
        connectionName?: string | null
      } | null
    }

    expect(state).toEqual(expect.objectContaining({
      connectionId: 'override-missing',
      connectionName: null,
      database: 'warehouse',
      schema: 'reporting',
      contextOverride: expect.objectContaining({
        connectionId: 'override-missing',
        connectionName: null,
      }),
    }))
  })

  it('preserves the base connection name when payload override keeps the same connectionId', () => {
    useConnectionStore.setState({
      activeConnectionId: null,
      connections: [],
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
      contextOverride: {
        connectionId: 'conn-1',
        database: 'warehouse',
        schema: 'reporting',
      },
    }))

    const adapter = new QueryEditorAdapter(tabId, () => 's1')
    const state = adapter.read('state') as {
      connectionId: string | null
      connectionName: string | null
      database: string | null
      schema: string | null
      contextOverride: {
        connectionId: string
        connectionName?: string | null
      } | null
    }

    expect(state).toEqual(expect.objectContaining({
      connectionId: 'conn-1',
      connectionName: 'Warehouse',
      database: 'warehouse',
      schema: 'reporting',
      contextOverride: expect.objectContaining({
        connectionId: 'conn-1',
        connectionName: null,
      }),
    }))
  })

  it('patch /content replaces the query editor document content', () => {
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 's1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 1',
    })

    const adapter = new QueryEditorAdapter(tabId)
    const result = adapter.patch([
      { op: 'replace', path: '/content', value: 'select 2', baseVersion: 1 },
    ])

    expect(result).toEqual({ status: 'applied' })
    expect(useSqlWorkbenchStore.getState().tabsById[tabId]).toMatchObject({
      sqlText: 'select 2',
      version: 2,
    })
  })

  it('patch /content rejects requests that omit baseVersion', () => {
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 's1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 1',
    })

    const adapter = new QueryEditorAdapter(tabId)
    const result = adapter.patch([
      { op: 'replace', path: '/content', value: 'select 2' },
    ])

    expect(result.status).toBe('error')
    expect(result.message).toContain('baseVersion')
    expect(useSqlWorkbenchStore.getState().tabsById[tabId]).toMatchObject({
      sqlText: 'select 1',
      version: 1,
    })
  })

  it('patch /content rejects baseVersion="auto" with a query-editor-specific error', () => {
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 's1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 1',
    })

    const adapter = new QueryEditorAdapter(tabId)
    const result = adapter.patch([
      { op: 'replace', path: '/content', value: 'select 2', baseVersion: 'auto' } as never,
    ])

    expect(result.status).toBe('error')
    expect((result as { detail?: { code?: string; message?: string; hint?: string } }).detail).toEqual({
      code: 'invalid_base_version',
      message: 'Patch /content requires numeric baseVersion',
      hint: 'Re-read with `ui_read(mode=\'state\')` and retry with the numeric `version` from the query editor state. `baseVersion: "auto"` is not supported for query_editor /content.',
    })
    expect(useSqlWorkbenchStore.getState().tabsById[tabId]).toMatchObject({
      sqlText: 'select 1',
      version: 1,
    })
  })

  it('patch /content returns version_conflict details when baseVersion is stale', () => {
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 's1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 1',
    })
    useSqlWorkbenchStore.getState().setSqlText(tabId, 'select 11')

    const adapter = new QueryEditorAdapter(tabId)
    const result = adapter.patch([
      { op: 'replace', path: '/content', value: 'select 2', baseVersion: 1 },
    ])

    expect(result.status).toBe('error')
    expect(result.message).toContain('version 2')
    expect((result as { detail?: { code?: string; currentState?: unknown } }).detail).toEqual({
      code: 'version_conflict',
      message: 'Editor content has advanced to version 2',
      hint: "Re-read with `ui_read(mode='state')` to get the latest content and version, then retry with a fresh baseVersion.",
      currentState: {
        tabId,
        version: 2,
        content: 'select 11',
      },
    })
  })

  it('patch /title fails cleanly', () => {
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 's1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
    })

    const adapter = new QueryEditorAdapter(tabId)
    const result = adapter.patch([
      { op: 'replace', path: '/title', value: 'Renamed SQL' },
    ])

    expect(result.status).toBe('error')
    expect(result.message).toContain('/title')
  })

  it('exec apply_text_edits returns a version-conflict style failure when baseVersion is stale', async () => {
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 's1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 1',
    })
    useStageStore.getState().replaceQueryEditorContent(tabId, 'select 11', 1)

    const adapter = new QueryEditorAdapter(tabId)
    const result = await adapter.exec('apply_text_edits', {
      baseVersion: 1,
      edits: [
        {
          range: {
            startLine: 1,
            startColumn: 8,
          endLine: 1,
          endColumn: 9,
        },
        text: '2',
        expectedText: '1',
      },
    ],
  })

    expect(result.success).toBe(false)
    expect(result.error).toContain('version 2')
    expect(result.data).toEqual(expect.objectContaining({
      code: 'version_conflict',
      currentState: {
        tabId,
        version: 2,
        content: 'select 11',
      },
    }))
  })

  it('exec apply_text_edits returns expected_text_mismatch details and keeps the batch unapplied', async () => {
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 's1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 1\r\nfrom dual',
    })

    const adapter = new QueryEditorAdapter(tabId)
    const result = await adapter.exec('apply_text_edits', {
      baseVersion: 1,
      edits: [
        {
          range: {
            startLine: 1,
            startColumn: 8,
            endLine: 2,
            endColumn: 5,
          },
          text: 'x',
          expectedText: '1\nFORM',
        },
      ],
    })

    expect(result.success).toBe(false)
    expect(result.error).toContain('expected text for edit 0')
    expect(result.data).toEqual({
      code: 'expected_text_mismatch',
      message: 'The expected text for edit 0 no longer matches the current content',
      hint: "Re-read with `ui_read(mode='state')` to get the latest content and version, then recompute the edit against the current text.",
      currentState: {
        tabId,
        version: 1,
        content: 'select 1\r\nfrom dual',
      },
      details: {
        editIndex: 0,
        expected: '1\nFORM',
        actual: '1\nfrom',
      },
    })
    expect(useSqlWorkbenchStore.getState().tabsById[tabId]).toMatchObject({
      sqlText: 'select 1\r\nfrom dual',
      version: 1,
    })
  })

  it('exec set_context rejects empty params', async () => {
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 's1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
    })

    const adapter = new QueryEditorAdapter(tabId)
    const result = await adapter.exec('set_context', {})

    expect(result.success).toBe(false)
    expect(result.error).toContain('connectionId')
    expect(result.error).toContain('database')
    expect(result.error).toContain('schema')
  })

  it('exec set_context accepts session mode and limit-only changes', async () => {
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 's1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      connectionId: 'conn-1',
      database: 'app',
      schema: 'public',
    })

    const adapter = new QueryEditorAdapter(tabId)

    await expect(adapter.exec('set_context', { useSessionContext: true })).resolves.toEqual({ success: true })
    expect(useSqlWorkbenchStore.getState().tabsById[tabId]?.useSessionContext).toBe(true)
    expect(useSqlWorkbenchStore.getState().tabsById[tabId]?.override).toBeNull()

    await expect(adapter.exec('set_context', { limit: 100 })).resolves.toEqual({ success: true })
    expect(useSqlWorkbenchStore.getState().tabsById[tabId]?.limit).toBe(100)
  })

  it('exec set_context accepts incremental database and schema changes when linked context already exists', async () => {
    useConnectionStore.setState({
      activeConnectionId: null,
      connections: [
        { id: 'conn-1', name: 'Primary', kind: 'postgres', databaseName: 'app' } as never,
      ],
    })
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 's1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      connectionId: 'conn-1',
      connectionName: 'Primary',
      database: 'app',
      schema: 'public',
    })

    const adapter = new QueryEditorAdapter(tabId)

    await expect(adapter.exec('set_context', { database: 'warehouse' })).resolves.toEqual({ success: true })
    expect(useSqlWorkbenchStore.getState().tabsById[tabId]?.override).toMatchObject({
      connectionId: 'conn-1',
      database: 'warehouse',
      schema: 'public',
    })

    await expect(adapter.exec('set_context', { schema: 'analytics' })).resolves.toEqual({ success: true })
    expect(useSqlWorkbenchStore.getState().tabsById[tabId]?.override).toMatchObject({
      connectionId: 'conn-1',
      database: 'warehouse',
      schema: 'analytics',
    })
  })

  it('exec set_context can pin the current effective context when useSessionContext is false', async () => {
    useConnectionStore.setState({
      activeConnectionId: null,
      connections: [
        { id: 'conn-session', name: 'Session', kind: 'postgres', databaseName: 'session_db' } as never,
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
      sessionId: 's1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
      initialContent: 'select 1',
    })

    const adapter = new QueryEditorAdapter(tabId, () => 's1')

    await expect(adapter.exec('set_context', { useSessionContext: false })).resolves.toEqual({ success: true })
    expect(useSqlWorkbenchStore.getState().tabsById[tabId]?.useSessionContext).toBe(false)
    expect(useSqlWorkbenchStore.getState().tabsById[tabId]?.override).toMatchObject({
      connectionId: 'conn-session',
      database: 'session_db',
      schema: 'session_schema',
    })
  })

  it('exec set_context rejects incomplete or conflicting linked context params', async () => {
    const { tabId } = useStageStore.getState().openQueryEditor({
      sessionId: 's1',
      baseTitle: 'SQL',
      openMode: 'always_new',
      entryMode: 'blank',
    })

    const adapter = new QueryEditorAdapter(tabId)

    await expect(adapter.exec('set_context', { schema: 'public' })).resolves.toEqual(expect.objectContaining({ success: false }))
    await expect(adapter.exec('set_context', { database: 'app' })).resolves.toEqual(expect.objectContaining({ success: false }))
    await expect(adapter.exec('set_context', { useSessionContext: true, connectionId: 'conn-1' })).resolves.toEqual(expect.objectContaining({ success: false }))
  })

  it('focus clears the current session-active tab when focusing a workspace query editor', async () => {
    openTab({
      tabId: 'ws-q1',
      type: 'query_editor',
      title: 'Workspace SQL',
      payload: {},
      createdAt: 0,
    })
    openTab({
      tabId: 'session-q1',
      type: 'query_editor',
      title: 'Session SQL',
      originSessionId: 's1',
      payload: {},
      createdAt: 0,
    })

    useStageStore.setState({
      activeTabId: 'session-q1',
    } as unknown as Record<string, unknown>)

    const adapter = new QueryEditorAdapter('ws-q1', () => 's1')
    const result = await adapter.exec('focus')

    expect(result).toEqual({ success: true })
    expect(useStageStore.getState().activeTabId).toBe('ws-q1')
  })

  it('rejects the removed close action and leaves the query editor tab unchanged', async () => {
    openTab({
      tabId: 'q1',
      type: 'query_editor',
      title: 'SQL',
      originSessionId: 's1',
      payload: {},
      createdAt: 0,
    })

    const adapter = new QueryEditorAdapter('q1')
    const result = await adapter.exec('close')

    expect(result).toEqual(expect.objectContaining({
      success: false,
      error: 'Unknown action: close',
    }))
    expect(useStageStore.getState().openTabIds.has('q1')).toBe(true)
    const tab = useStageStore.getState().tabs.find((t) => t.tabId === 'q1')
    expect(tab?.archived).toBeUndefined()
  })
})
