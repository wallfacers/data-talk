import { fireEvent, render, screen, waitFor, act } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { StageTab } from '@/stores/stage-store'
import { useStageStore } from '@/stores/stage-store'
import { translateMessage } from '@/i18n/messages'
import { useSessionStore } from '@/stores/session-store'
import { useUISettingsStore } from '@/stores/ui-settings-store'
import { useSqlWorkbenchStore } from '../stores/sql-workbench-store'
import { SqlMonacoEditor } from './sql-monaco-editor'
import { SqlWorkbenchTab } from './sql-workbench-tab'

const executeSqlMock = vi.hoisted(() => vi.fn())
const formatSqlMock = vi.hoisted(() => vi.fn((sql: string) => `formatted: ${sql}`))
const listConnectionsMock = vi.hoisted(() => vi.fn())
const listConnectionTargetsMock = vi.hoisted(() => vi.fn())
const setConnectionsMock = vi.hoisted(() => vi.fn())
const sessionDataContextSnapshot = vi.hoisted(() => ({
  context: null as null | {
    sessionId: string
    connectionId: string | null
    connectionNameSnapshot: string | null
    database: string | null
    schema: string | null
    selectedLevel: 'connection' | 'database' | 'schema' | null
    updatedAt: number
  },
}))
const connectionStoreSnapshot = vi.hoisted(() => ({
  activeConnectionId: 'conn-1' as string | null,
  connections: [{ id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' }] as Array<{
    id: string
    name: string
    kind: string
    databaseName?: string | null
  }>,
}))
const editorHarness = vi.hoisted(() => {
  const harness = {
    lastProps: null as null | {
      value?: string
      options?: unknown
    },
    position: { lineNumber: 1, column: 1 },
    cursorListener: null as null | ((event: { position?: { lineNumber: number; column: number } }) => void),
    selectionListener: null as null | ((event: {
      selection?: {
        startLineNumber: number
        startColumn: number
        endLineNumber: number
        endColumn: number
        isEmpty?: () => boolean
      }
    }) => void),
    decorations: [] as Array<Record<string, unknown>>,
    fakeMonaco: {
      KeyMod: { CtrlCmd: 1024, Shift: 2048 },
      KeyCode: { Enter: 13, KeyF: 33 },
      editor: {
        defineTheme: vi.fn(),
        registerCompletionItemProvider: vi.fn(),
        registerHoverProvider: vi.fn(),
        registerFoldingRangeProvider: vi.fn(),
      },
    },
    fakeEditor: null as any,
  }
  harness.fakeEditor = {
    addCommand: vi.fn(),
    onDidChangeCursorPosition: vi.fn((listener: (event: { position?: { lineNumber: number; column: number } }) => void) => {
      harness.cursorListener = listener
      return { dispose: vi.fn() }
    }),
    onDidChangeCursorSelection: vi.fn((listener: (event: {
      selection?: {
        startLineNumber: number
        startColumn: number
        endLineNumber: number
        endColumn: number
        isEmpty?: () => boolean
      }
    }) => void) => {
      harness.selectionListener = listener
      return { dispose: vi.fn() }
    }),
    deltaDecorations: vi.fn((_previousIds: string[], nextDecorations: Array<Record<string, unknown>>) => {
      harness.decorations = nextDecorations
      return nextDecorations.map((_, index) => `decoration-${index}`)
    }),
    getPosition: vi.fn(() => harness.position),
    setPosition: vi.fn((position: { lineNumber: number; column: number }) => {
      harness.position = position
    }),
    revealLineNearTop: vi.fn(),
    focus: vi.fn(),
  }
  return harness
})

vi.mock('@monaco-editor/react', () => ({
  default: ({ value, onChange, beforeMount, onMount, options }: {
    value?: string
    onChange?: (value: string) => void
    beforeMount?: (monaco: typeof editorHarness.fakeMonaco) => void
    onMount?: (editor: NonNullable<typeof editorHarness.fakeEditor>, monaco: typeof editorHarness.fakeMonaco) => void
    options?: unknown
  }) => {
    editorHarness.lastProps = { value, options }
    beforeMount?.(editorHarness.fakeMonaco)
    onMount?.(editorHarness.fakeEditor!, editorHarness.fakeMonaco)

    return (
      <textarea
        data-testid="monaco-editor"
        value={value ?? ''}
        onChange={(event) => onChange?.(event.target.value)}
      />
    )
  },
}))

vi.mock('@/services/api/sql', async () => {
  const actual = await vi.importActual<typeof import('@/services/api/sql')>('@/services/api/sql')
  return {
    ...actual,
    executeSql: executeSqlMock,
  }
})

vi.mock('../utils/format-sql', () => ({
  formatSql: formatSqlMock,
}))

vi.mock('@/services/api/connection', () => ({
  listConnections: listConnectionsMock,
  getConnectionTargets: listConnectionTargetsMock,
}))

vi.mock('@/features/connection/store', () => {
  const store = Object.assign(
    (selector: (state: {
      activeConnectionId: string | null
      connections: Array<{ id: string; name: string; kind: string; databaseName?: string | null }>
      setConnections: (connections: Array<{ id: string; name: string; kind: string; databaseName?: string | null }>) => void
    }) => unknown) => selector({
      activeConnectionId: connectionStoreSnapshot.activeConnectionId,
      connections: connectionStoreSnapshot.connections,
      setConnections: setConnectionsMock,
    }),
    {
      getState: () => ({
        activeConnectionId: connectionStoreSnapshot.activeConnectionId,
        connections: connectionStoreSnapshot.connections,
        setConnections: setConnectionsMock,
      }),
    },
  )

  return {
    useConnectionStore: store,
  }
})

vi.mock('@/features/session/hooks/use-session-data-context', () => ({
  useSessionDataContext: () => ({
    context: sessionDataContextSnapshot.context,
    isLoading: false,
    error: null,
    refresh: vi.fn(),
    listConnectionTargets: listConnectionTargetsMock,
    resolveUseTarget: vi.fn(),
    setSessionDataContext: vi.fn(),
    validateSessionDataContext: vi.fn(),
  }),
}))

vi.mock('./activity-rail/stage-activity-rail', () => ({
  StageActivityRail: () => (
    <div data-testid="stage-activity-rail-stub" data-session-id="" />
  ),
}))

const tab: StageTab = {
  tabId: 'tab-1',
  type: 'query_editor',
  title: 'SQL',
  createdAt: 0,
  payload: {
    initialSql: 'select 1;',
    source: 'user',
  },
  connectionId: 'conn-1',
  connectionName: 'Primary Connection',
  database: 'db_main',
}

describe('SqlWorkbenchTab', () => {
  const t = (key: Parameters<typeof translateMessage>[1], values?: Record<string, string | number>) =>
    translateMessage('zh-CN', key, values)

  beforeEach(() => {
    executeSqlMock.mockReset()
    formatSqlMock.mockClear()
    listConnectionsMock.mockReset()
    listConnectionTargetsMock.mockReset()
    setConnectionsMock.mockReset()
    sessionDataContextSnapshot.context = null
    connectionStoreSnapshot.activeConnectionId = 'conn-1'
    connectionStoreSnapshot.connections = [{ id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' }]
    editorHarness.lastProps = null
    editorHarness.position = { lineNumber: 1, column: 1 }
    editorHarness.cursorListener = null
    editorHarness.selectionListener = null
    editorHarness.decorations = []
    editorHarness.fakeEditor?.addCommand.mockClear()
    editorHarness.fakeEditor?.onDidChangeCursorSelection.mockClear()
    editorHarness.fakeEditor?.deltaDecorations.mockClear()
    editorHarness.fakeEditor?.getPosition.mockClear()
    editorHarness.fakeEditor?.setPosition.mockClear()
    editorHarness.fakeEditor?.revealLineNearTop.mockClear()
    useStageStore.setState({
      open: false,
      maximized: false,
      revealOrigin: null,
      sidebarCollapsed: false,
      sidebarSelection: null,
      resourceTreeExpanded: [],
      activeRailPanel: null,
      tabs: [],
      activeTabId: null,
    })
    useSqlWorkbenchStore.setState({ tabsById: {} })
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
    useUISettingsStore.setState({ language: 'zh-CN' })
    window.localStorage.clear()
  })

  it('renders the new workbench shell and editor mount area', () => {
    render(<SqlWorkbenchTab tab={tab} />)

    expect(screen.getByTestId('sql-workbench-tab')).toBeTruthy()
    expect(screen.queryByTestId('sql-editor-breadcrumb')).toBeNull()
    expect(screen.getByTestId('sql-monaco-editor')).toBeTruthy()
    expect(screen.getByTestId('sql-editor-frame').className).not.toContain('px-2')
    expect(screen.getByTestId('sql-editor-frame').className).not.toContain('pb-2')
    expect(screen.getByTestId('sql-monaco-editor').className).toContain('rounded-none')
    expect(screen.getByTestId('sql-monaco-editor').className).not.toContain('border border-border/50')
    expect(screen.getByRole('button', { name: t('stage.toolbar.run') })).toBeTruthy()
    expect(screen.getByTestId('sql-editor-toolbar')).toBeTruthy()
    expect(screen.queryByTestId('sql-workbench-status-bar')).toBeNull()
    expect(screen.queryByRole('tablist')).toBeNull()
    expect(screen.queryByTestId('sql-workbench-result-splitter')).toBeNull()
    expect(screen.queryByTestId('sql-result-shell')).toBeNull()
  })

  it('loads connections to resolve missing connection names for current tab context', async () => {
    connectionStoreSnapshot.activeConnectionId = 'conn-2'
    connectionStoreSnapshot.connections = [{ id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' }]
    listConnectionsMock.mockResolvedValue([
      { id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' },
      { id: 'conn-2', name: 'Analytics', kind: 'postgres', databaseName: null },
    ])

    render(
      <SqlWorkbenchTab
        tab={{
          ...tab,
          tabId: 'tab-conn-name-hydration',
          connectionId: 'conn-2',
          connectionName: '',
          payload: {
            initialSql: 'select 1;',
            source: 'user',
            connectionId: 'conn-2',
          },
        }}
      />,
    )

    await waitFor(() => expect(listConnectionsMock).toHaveBeenCalledTimes(1))
    await waitFor(() => {
      expect(setConnectionsMock).toHaveBeenCalledWith([
        { id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' },
        { id: 'conn-2', name: 'Analytics', kind: 'postgres', databaseName: null },
      ])
    })
  })

  it('loads connection options when context is not pinned and selectable', async () => {
    connectionStoreSnapshot.activeConnectionId = null
    connectionStoreSnapshot.connections = []
    listConnectionsMock.mockResolvedValue([
      { id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' },
      { id: 'conn-2', name: 'Analytics', kind: 'postgres', databaseName: null },
    ])

    render(
      <SqlWorkbenchTab
        tab={{
          ...tab,
          tabId: 'tab-selectable-connections',
          connectionId: '',
          connectionName: '',
          payload: {
            initialSql: 'select 1;',
            source: 'user',
          },
        }}
      />,
    )

    await waitFor(() => expect(listConnectionsMock).toHaveBeenCalledTimes(1))
    await waitFor(() => {
      expect(setConnectionsMock).toHaveBeenCalledWith([
        { id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' },
        { id: 'conn-2', name: 'Analytics', kind: 'postgres', databaseName: null },
      ])
    })
  })

  it('loads session connection targets so the database dropdown can show discovered databases', async () => {
    connectionStoreSnapshot.connections = [{ id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: null }]
    listConnectionTargetsMock.mockResolvedValue({
      connectionId: 'conn-1',
      connectionName: 'Primary Connection',
      databases: ['warehouse'],
      schemas: ['reporting'],
    })

    render(
      <SqlWorkbenchTab
        tab={{
          ...tab,
          tabId: 'tab-context-targets',
          originSessionId: 'session-1',
          database: undefined,
          payload: {
            initialSql: 'select 1;',
            source: 'user',
          },
        }}
      />,
    )

    await waitFor(() => expect(listConnectionTargetsMock).toHaveBeenCalledWith('conn-1'))
  })

  it('shows the latest session context in the context panel even when tab metadata and resolvedContext are stale', async () => {
    sessionDataContextSnapshot.context = {
      sessionId: 'session-1',
      connectionId: 'conn-2',
      connectionNameSnapshot: 'Warehouse',
      database: 'warehouse',
      schema: 'analytics',
      selectedLevel: 'schema',
      updatedAt: 2,
    }
    connectionStoreSnapshot.connections = [
      { id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' },
      { id: 'conn-2', name: 'Warehouse', kind: 'postgres', databaseName: 'warehouse' },
    ]
    useSqlWorkbenchStore.setState({
      tabsById: {
        'tab-session-context-latest': {
          sqlText: 'select 1;',
          version: 1,
          selection: null,
          source: 'user',
          executeStatus: 'idle',
          results: [],
          activeResultId: null,
          resolvedContext: {
            connectionId: 'conn-1',
            connectionName: 'Primary Connection',
            database: 'db_main',
            schema: 'public',
            selectedLevel: 'schema',
          },
          contextNotice: null,
          errorMessage: null,
          confirmation: null,
          confirmationInvalid: null,
          lastRequest: null,
          override: null,
          history: [],
          savedSqlText: 'select 1;',
          limit: 100,
          cursor: { line: 1, column: 1 },
        },
      },
    })

    render(
      <SqlWorkbenchTab
        tab={{
          ...tab,
          tabId: 'tab-session-context-latest',
          originSessionId: 'session-1',
          connectionId: 'conn-1',
          connectionName: 'Primary Connection',
          database: 'db_main',
          schema: 'public',
          payload: {
            initialSql: 'select 1;',
            source: 'user',
            entryMode: 'blank',
            contextOverride: null,
            contextPinMode: 'session',
          },
        }}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: t('stage.context.tooltip.button') }))

    expect(screen.getByRole('combobox', { name: t('stage.context.field.connection') })).toHaveTextContent('Warehouse')
    expect(screen.getByRole('combobox', { name: t('stage.context.field.database') })).toHaveTextContent('warehouse')
    expect(screen.getByRole('combobox', { name: t('stage.context.field.schema') })).toHaveTextContent('analytics')
  })

  it('treats a legacy blank tab without a context override as pinned to the active session context', () => {
    sessionDataContextSnapshot.context = {
      sessionId: 'session-active',
      connectionId: 'conn-2',
      connectionNameSnapshot: 'Warehouse',
      database: 'warehouse',
      schema: 'analytics',
      selectedLevel: 'schema',
      updatedAt: 2,
    }
    connectionStoreSnapshot.connections = [
      { id: 'conn-2', name: 'Warehouse', kind: 'postgres', databaseName: 'warehouse' },
    ]
    useSessionStore.setState({
      activeSessionId: 'session-active',
      dataContextBySession: new Map([['session-active', sessionDataContextSnapshot.context]]),
    })

    render(
      <SqlWorkbenchTab
        tab={{
          ...tab,
          tabId: 'tab-legacy-active-session-context',
          originSessionId: undefined,
          connectionId: undefined,
          connectionName: undefined,
          database: undefined,
          schema: undefined,
          payload: {
            initialSql: 'select 1;',
            source: 'user',
            entryMode: 'blank',
            contextOverride: null,
          },
        }}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: t('stage.context.tooltip.button') }))

    expect(screen.getByText(t('stage.context.label.override'))).toBeTruthy()
    expect(screen.getByText('Warehouse')).toBeTruthy()
    expect(screen.getByText('warehouse')).toBeTruthy()
    expect(screen.getByText('analytics')).toBeTruthy()
    expect(screen.getByRole('button', { name: t('stage.context.action.useSession') })).toBeTruthy()
    expect(screen.queryByRole('button', { name: t('stage.context.action.applyOverride') })).toBeNull()
  })

  it('honors an explicit unpin marker for an active session context', () => {
    sessionDataContextSnapshot.context = {
      sessionId: 'session-active',
      connectionId: 'conn-2',
      connectionNameSnapshot: 'Warehouse',
      database: 'warehouse',
      schema: 'analytics',
      selectedLevel: 'schema',
      updatedAt: 2,
    }
    connectionStoreSnapshot.connections = [
      { id: 'conn-2', name: 'Warehouse', kind: 'postgres', databaseName: 'warehouse' },
    ]
    useSessionStore.setState({
      activeSessionId: 'session-active',
      dataContextBySession: new Map([['session-active', sessionDataContextSnapshot.context]]),
    })

    render(
      <SqlWorkbenchTab
        tab={{
          ...tab,
          tabId: 'tab-explicit-unpinned-active-session-context',
          originSessionId: undefined,
          connectionId: undefined,
          connectionName: undefined,
          database: undefined,
          schema: undefined,
          payload: {
            initialSql: 'select 1;',
            source: 'user',
            entryMode: 'blank',
            contextOverride: null,
            contextPinMode: 'session',
          },
        }}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: t('stage.context.tooltip.button') }))

    expect(screen.getByText(t('stage.context.label.session'))).toBeTruthy()
    expect(screen.getByRole('combobox', { name: t('stage.context.field.connection') })).toHaveTextContent('Warehouse')
    expect(screen.getByRole('button', { name: t('stage.context.action.applyOverride') })).toBeTruthy()
    expect(screen.queryByRole('button', { name: t('stage.context.action.useSession') })).toBeNull()
  })

  it('retries loading connection targets when reopening the SQL context after an initial failure', async () => {
    connectionStoreSnapshot.connections = [{ id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: null }]
    listConnectionTargetsMock
      .mockRejectedValueOnce(new Error('targets unavailable'))
      .mockResolvedValueOnce({
        connectionId: 'conn-1',
        connectionName: 'Primary Connection',
        databases: ['warehouse'],
        schemas: ['reporting'],
      })

    render(
      <SqlWorkbenchTab
        tab={{
          ...tab,
          tabId: 'tab-context-targets-retry',
          originSessionId: 'session-1',
          database: undefined,
          payload: {
            initialSql: 'select 1;',
            source: 'user',
          },
        }}
      />,
    )

    await waitFor(() => expect(listConnectionTargetsMock).toHaveBeenCalledTimes(1))

    fireEvent.click(screen.getByRole('button', { name: t('stage.context.tooltip.button') }))

    await waitFor(() => expect(listConnectionTargetsMock).toHaveBeenCalledTimes(2))
    expect(listConnectionTargetsMock).toHaveBeenNthCalledWith(1, 'conn-1')
    expect(listConnectionTargetsMock).toHaveBeenNthCalledWith(2, 'conn-1')
  })

  it('auto-runs direct SQL query tabs on mount when payload.autoRun is true', async () => {
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
          resultId: 'r-set',
          kind: 'result_set',
          title: 'Result 1',
          statementIndex: 0,
          statementText: 'select 1',
          columns: ['id'],
          rows: [[1]],
          rowCount: 1,
          executionMs: 5,
          truncated: false,
        },
      ],
    })

    render(
      <SqlWorkbenchTab
        tab={{
          ...tab,
          tabId: 'tab-auto-run',
          payload: {
            initialSql: 'select 1;',
            source: 'user',
            entryMode: 'direct_sql',
            autoRun: true,
            connectionId: 'conn-1',
          },
        }}
      />,
    )

    await waitFor(() => expect(executeSqlMock).toHaveBeenCalledTimes(1))
  })

  it('auto-runs again when switching to another direct SQL tab id', async () => {
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
          resultId: 'r-set',
          kind: 'result_set',
          title: 'Result 1',
          statementIndex: 0,
          statementText: 'select 1',
          columns: ['id'],
          rows: [[1]],
          rowCount: 1,
          executionMs: 5,
          truncated: false,
        },
      ],
    })

    const { rerender } = render(
      <SqlWorkbenchTab
        tab={{
          ...tab,
          tabId: 'tab-auto-run-1',
          payload: {
            initialSql: 'select 1',
            source: 'user',
            entryMode: 'direct_sql',
            autoRun: true,
            connectionId: 'conn-1',
          },
        }}
      />,
    )

    await waitFor(() => expect(executeSqlMock).toHaveBeenCalledTimes(1))

    rerender(
      <SqlWorkbenchTab
        tab={{
          ...tab,
          tabId: 'tab-auto-run-2',
          payload: {
            initialSql: 'select 1',
            source: 'user',
            entryMode: 'direct_sql',
            autoRun: true,
            connectionId: 'conn-1',
          },
        }}
      />,
    )

    await waitFor(() => expect(executeSqlMock).toHaveBeenCalledTimes(2))
  })

  it('tracks cursor position without rendering the removed breadcrumb row', async () => {
    render(
      <SqlWorkbenchTab
        tab={{
          ...tab,
          tabId: 'tab-outline',
          payload: {
            initialSql: `select 1;
update users set name = 'semi;colon'
where id = 1;
delete from sessions;`,
            source: 'user',
          },
        }}
      />,
    )

    act(() => {
      editorHarness.cursorListener?.({ position: { lineNumber: 3, column: 5 } })
    })

    await waitFor(() =>
      expect(useSqlWorkbenchStore.getState().tabsById['tab-outline']?.cursor).toMatchObject({ line: 3, column: 5 }),
    )
    expect(screen.queryByText('Ln 3')).toBeNull()
    expect(screen.queryByText('UPDATE')).toBeNull()
  })

  it('runs the exact selected SQL text instead of the whole editor buffer', async () => {
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

    render(
      <SqlWorkbenchTab
        tab={{
          ...tab,
          tabId: 'tab-selected-run',
          payload: {
            initialSql: 'select 1;\nselect 2\nselect 3;',
            source: 'user',
          },
        }}
      />,
    )

    act(() => {
      useSqlWorkbenchStore.getState().setLimit('tab-selected-run', null)
      editorHarness.selectionListener?.({
        selection: {
          startLineNumber: 2,
          startColumn: 1,
          endLineNumber: 2,
          endColumn: 9,
          isEmpty: () => false,
        },
      })
    })

    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.run') }))

    await waitFor(() => expect(executeSqlMock).toHaveBeenCalledTimes(1))
    expect(executeSqlMock).toHaveBeenCalledWith(expect.objectContaining({
      sql: 'select 2',
      connectionId: 'conn-1',
      source: 'user',
    }), expect.any(AbortSignal))
  })

  it('runs the exact selected SQL text from the Monaco run command', async () => {
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

    render(
      <SqlWorkbenchTab
        tab={{
          ...tab,
          tabId: 'tab-selected-command-run',
          payload: {
            initialSql: 'select 1;\nselect 2\nselect 3;',
            source: 'user',
          },
        }}
      />,
    )

    act(() => {
      useSqlWorkbenchStore.getState().setLimit('tab-selected-command-run', null)
      editorHarness.selectionListener?.({
        selection: {
          startLineNumber: 2,
          startColumn: 1,
          endLineNumber: 2,
          endColumn: 9,
          isEmpty: () => false,
        },
      })
    })

    const runCommand = editorHarness.fakeEditor?.addCommand.mock.calls[0]?.[1] as (() => void) | undefined
    act(() => {
      runCommand?.()
    })

    await waitFor(() => expect(executeSqlMock).toHaveBeenCalledTimes(1))
    expect(executeSqlMock).toHaveBeenCalledWith(expect.objectContaining({
      sql: 'select 2',
      connectionId: 'conn-1',
      source: 'user',
    }), expect.any(AbortSignal))
  })

  it('formats SQL and persists the draft without a save button', async () => {
    render(<SqlWorkbenchTab tab={tab} />)

    fireEvent.change(screen.getByTestId('monaco-editor'), { target: { value: 'select id from users' } })
    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.format') }))

    await waitFor(() => expect(formatSqlMock).toHaveBeenCalledWith('select id from users', 'postgres'))
    await waitFor(() => expect(screen.getByTestId('monaco-editor')).toHaveValue('formatted: select id from users'))
    expect(screen.queryByRole('button', { name: /Save/i })).toBeNull()
    expect(window.localStorage.getItem('data-talk:sql-workbench:draft:tab-1')).toBe(
      'formatted: select id from users',
    )
  })

  it('does not clobber another tab draft when switching tabs before the new draft loads', async () => {
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem')
    window.localStorage.setItem('data-talk:sql-workbench:draft:tab-2', 'saved draft from tab 2')

    const { rerender } = render(<SqlWorkbenchTab tab={tab} />)

    fireEvent.change(screen.getByTestId('monaco-editor'), { target: { value: 'edited tab 1 draft' } })
    await waitFor(() => {
      expect(window.localStorage.getItem('data-talk:sql-workbench:draft:tab-1')).toBe('edited tab 1 draft')
    })

    rerender(
      <SqlWorkbenchTab
        tab={{
          ...tab,
          tabId: 'tab-2',
          payload: {
            initialSql: '',
            source: 'user',
          },
        }}
      />,
    )

    expect(window.localStorage.getItem('data-talk:sql-workbench:draft:tab-2')).toBe('saved draft from tab 2')
    await waitFor(() => expect(screen.getByTestId('monaco-editor')).toHaveValue('saved draft from tab 2'))
    expect(setItemSpy).not.toHaveBeenCalledWith('data-talk:sql-workbench:draft:tab-2', '')
    expect(window.localStorage.getItem('data-talk:sql-workbench:draft:tab-2')).toBe('saved draft from tab 2')
    setItemSpy.mockRestore()
  })

  it('honors a persisted payload contextOverride after a tab is restored', async () => {
    connectionStoreSnapshot.connections = [
      { id: 'conn-1', name: 'Primary Connection', kind: 'postgres' },
      { id: 'conn-2', name: 'Warehouse', kind: 'postgres' },
    ]

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

    useStageStore.setState({
      tabs: [{
        ...tab,
        tabId: 'tab-restored-override',
        payload: {
          initialSql: 'select 1;',
          source: 'user',
          connectionId: 'conn-1',
          database: 'db_main',
          contextOverride: {
            connectionId: 'conn-2',
            database: 'warehouse',
            schema: 'analytics',
          },
        },
      }],
      activeTabId: 'tab-restored-override',
    })

    render(
      <SqlWorkbenchTab
        tab={{
          ...tab,
          tabId: 'tab-restored-override',
          payload: {
            initialSql: 'select 1;',
            source: 'user',
            connectionId: 'conn-1',
            database: 'db_main',
            contextOverride: {
              connectionId: 'conn-2',
              database: 'warehouse',
              schema: 'analytics',
            },
          },
        }}
      />,
    )

    act(() => {
      useSqlWorkbenchStore.getState().setLimit('tab-restored-override', null)
    })

    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.run') }))

    await waitFor(() => expect(executeSqlMock).toHaveBeenCalledWith(expect.objectContaining({
      sql: 'select 1;',
      connectionId: 'conn-2',
      database: 'warehouse',
      schema: 'analytics',
    }), expect.any(AbortSignal)))
  })

  it('injects the tab limit into select-like SQL before execution', async () => {
    executeSqlMock.mockResolvedValue({
      status: 'executed',
      resolvedContext: {
        connectionId: 'conn-1',
        connectionName: 'Primary Connection',
        database: 'db_main',
        schema: null,
        selectedLevel: 'database',
      },
      contextNotice: 'resolved by session context',
      results: [
        {
          resultId: 'r-set',
          kind: 'result_set',
          title: 'Result 1',
          statementIndex: 0,
          statementText: 'select 1',
          columns: ['id'],
          rows: [[1]],
          rowCount: 1,
          executionMs: 5,
          truncated: false,
        },
        {
          resultId: 'r-dml',
          kind: 'dml_summary',
          title: 'DML',
          statementIndex: 1,
          statementText: 'update t set a=1',
          columns: [],
          rows: [],
          rowCount: 0,
          executionMs: 2,
          truncated: false,
          affectedRows: 3,
        },
        {
          resultId: 'r-err',
          kind: 'error',
          title: 'Error',
          statementIndex: 2,
          statementText: 'select * from missing',
          columns: [],
          rows: [],
          rowCount: 0,
          executionMs: 1,
          truncated: false,
          errorMessage: 'relation missing does not exist',
        },
      ],
    })

    render(<SqlWorkbenchTab tab={tab} />)

    act(() => {
      useSqlWorkbenchStore.getState().setLimit('tab-1', 10)
    })
    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: t('stage.limit.aria') })).toHaveTextContent(
        t('stage.limit.rows', { count: 10 }),
      ),
    )

    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.run') }))

    await waitFor(() => expect(executeSqlMock).toHaveBeenCalled())

    expect(executeSqlMock).toHaveBeenCalledWith(expect.objectContaining({
      sql: 'select 1 LIMIT 10;',
      connectionId: 'conn-1',
      source: 'user',
    }), expect.any(AbortSignal))
  })

  it('keeps an existing LIMIT clause untouched and can cancel the running query', async () => {
    let abortHandler: (() => void) | null = null
    executeSqlMock.mockImplementation(
      (_req: unknown, signal?: AbortSignal) =>
        new Promise((_resolve, reject) => {
          abortHandler = () => reject(new DOMException('The operation was aborted.', 'AbortError'))
          signal?.addEventListener('abort', () => abortHandler?.(), { once: true })
        }),
    )

    render(<SqlWorkbenchTab tab={tab} />)

    fireEvent.change(screen.getByTestId('monaco-editor'), { target: { value: 'select 1 limit 5' } })
    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.run') }))

    await waitFor(() => expect(executeSqlMock).toHaveBeenCalled())
    expect(executeSqlMock).toHaveBeenCalledWith(expect.objectContaining({
      sql: 'select 1 limit 5',
      connectionId: 'conn-1',
      source: 'user',
    }), expect.any(AbortSignal))
    expect(screen.getByRole('button', { name: t('stage.toolbar.cancel') })).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.cancel') }))
    await waitFor(() => expect(screen.queryByTestId('sql-workbench-status-bar')).toBeNull())
    expect(abortHandler).not.toBeNull()
  })

  it('executes SQL and switches among result_set / dml_summary / error panels', async () => {
    executeSqlMock.mockResolvedValue({
      status: 'executed',
      resolvedContext: {
        connectionId: 'conn-1',
        connectionName: 'Primary Connection',
        database: 'db_main',
        schema: null,
        selectedLevel: 'database',
      },
      contextNotice: 'resolved by session context',
      results: [
        {
          resultId: 'r-set',
          kind: 'result_set',
          title: 'Result 1',
          statementIndex: 0,
          statementText: 'select 1',
          columns: ['id'],
          rows: [[1]],
          rowCount: 1,
          executionMs: 5,
          truncated: false,
        },
        {
          resultId: 'r-dml',
          kind: 'dml_summary',
          title: 'DML',
          statementIndex: 1,
          statementText: 'update t set a=1',
          columns: [],
          rows: [],
          rowCount: 0,
          executionMs: 2,
          truncated: false,
          affectedRows: 3,
        },
        {
          resultId: 'r-err',
          kind: 'error',
          title: 'Error',
          statementIndex: 2,
          statementText: 'select * from missing',
          columns: [],
          rows: [],
          rowCount: 0,
          executionMs: 1,
          truncated: false,
          errorMessage: 'relation missing does not exist',
        },
      ],
    })

    render(<SqlWorkbenchTab tab={tab} />)

    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.run') }))

    await waitFor(() => expect(executeSqlMock).toHaveBeenCalled())

    expect(screen.getByTestId('sql-workbench-result-splitter')).toBeTruthy()
    expect(screen.getByRole('tab', { name: 'Result 1' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: 'DML' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: 'Error' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: t('stage.queryEditor.result.rowNumber') })).toBeTruthy()
    expect(screen.getByTestId('sql-monaco-editor').className).toContain('rounded-none')
    expect(screen.getByTestId('sql-monaco-editor').className).not.toContain('border-x')
    expect(screen.getByTestId('sql-result-shell').className).toContain('rounded-none')
    expect(screen.getByTestId('sql-result-shell').className).not.toContain('border-x')
    expect(screen.getByTestId('sql-result-shell').className).not.toContain('border-b')

    fireEvent.click(screen.getByRole('tab', { name: 'DML' }))
    expect(screen.getByText(/3/)).toBeTruthy()

    fireEvent.click(screen.getByRole('tab', { name: 'Error' }))
    expect(screen.getByText(/relation missing does not exist/)).toBeTruthy()
  })

  it('restores vertical and horizontal scroll independently for each result set', async () => {
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
          resultId: 'r-a',
          kind: 'result_set',
          title: 'Result A',
          statementIndex: 0,
          statementText: 'select * from a',
          columns: Array.from({ length: 8 }, (_, index) => `a_col_${index + 1}`),
          rows: Array.from({ length: 20 }, (_, rowIndex) =>
            Array.from({ length: 8 }, (_, columnIndex) => `A${rowIndex + 1}-${columnIndex + 1}`),
          ),
          rowCount: 20,
          executionMs: 5,
          truncated: false,
        },
        {
          resultId: 'r-b',
          kind: 'result_set',
          title: 'Result B',
          statementIndex: 1,
          statementText: 'select * from b',
          columns: Array.from({ length: 8 }, (_, index) => `b_col_${index + 1}`),
          rows: Array.from({ length: 20 }, (_, rowIndex) =>
            Array.from({ length: 8 }, (_, columnIndex) => `B${rowIndex + 1}-${columnIndex + 1}`),
          ),
          rowCount: 20,
          executionMs: 6,
          truncated: false,
        },
      ],
    })

    render(<SqlWorkbenchTab tab={{ ...tab, tabId: 'tab-result-scroll' }} />)
    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.run') }))

    await waitFor(() => expect(screen.getByRole('tab', { name: 'Result A' })).toBeTruthy())

    const getViewport = () => screen.getByTestId('sql-result-table-scroll') as HTMLDivElement
    let viewport = getViewport()
    viewport.scrollTop = 80
    viewport.scrollLeft = 120
    fireEvent.scroll(viewport)

    fireEvent.click(screen.getByRole('tab', { name: 'Result B' }))
    viewport = getViewport()
    viewport.scrollTop = 30
    viewport.scrollLeft = 40
    fireEvent.scroll(viewport)

    fireEvent.click(screen.getByRole('tab', { name: 'Result A' }))
    await waitFor(() => {
      expect(getViewport().scrollTop).toBe(80)
      expect(getViewport().scrollLeft).toBe(120)
    })

    fireEvent.click(screen.getByRole('tab', { name: 'Result B' }))
    await waitFor(() => {
      expect(getViewport().scrollTop).toBe(30)
      expect(getViewport().scrollLeft).toBe(40)
    })
  })

  it('supports dragging the horizontal splitter above result tabs', async () => {
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
          resultId: 'r-set',
          kind: 'result_set',
          title: 'Result 1',
          statementIndex: 0,
          statementText: 'select 1',
          columns: ['id'],
          rows: [[1]],
          rowCount: 1,
          executionMs: 5,
          truncated: false,
        },
      ],
    })

    render(<SqlWorkbenchTab tab={tab} />)
    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.run') }))

    const splitter = await screen.findByTestId('sql-workbench-result-splitter')
    const layout = screen.getByTestId('sql-workbench-layout')
    const resultSection = screen.getByTestId('sql-result-pane')

    Object.defineProperty(layout, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({
        x: 0,
        y: 0,
        top: 0,
        left: 0,
        right: 1000,
        bottom: 1000,
        width: 1000,
        height: 1000,
        toJSON: () => ({}),
      }),
    })

    expect(resultSection.style.flexBasis).toBe('38%')

    fireEvent.mouseDown(splitter, { clientY: 620 })
    fireEvent.mouseMove(window, { clientY: 760 })
    fireEvent.mouseUp(window)

    expect(resultSection.style.flexBasis).toBe('24%')
  })

  it('appends a history entry after a successful execution', async () => {
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
          resultId: 'r-set',
          kind: 'result_set',
          title: 'Result 1',
          statementIndex: 0,
          statementText: 'select 1',
          columns: ['id'],
          rows: [[1]],
          rowCount: 1,
          executionMs: 5,
          truncated: false,
        },
      ],
    })

    render(<SqlWorkbenchTab tab={tab} />)
    act(() => {
      useSqlWorkbenchStore.getState().setLimit('tab-1', null)
    })

    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.run') }))

    await waitFor(() => expect(executeSqlMock).toHaveBeenCalled())

    const history = useSqlWorkbenchStore.getState().tabsById['tab-1']?.history
    expect(history).toHaveLength(1)
    expect(history?.[0]).toMatchObject({
      sql: 'select 1;',
      status: 'ok',
      resultCount: 1,
    })
  })

  it('appends a history entry when execution requires confirmation', async () => {
    executeSqlMock.mockResolvedValue({
      status: 'requires_confirmation',
      resolvedContext: null,
      contextNotice: null,
      confirmation: {
        level: 'L2',
        reason: 'This statement modifies data',
        affectedObjects: ['public.users'],
        sqlPreview: 'select 1;',
      },
    })

    render(<SqlWorkbenchTab tab={tab} />)
    act(() => {
      useSqlWorkbenchStore.getState().setLimit('tab-1', null)
    })

    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.run') }))

    await waitFor(() => expect(executeSqlMock).toHaveBeenCalled())

    const history = useSqlWorkbenchStore.getState().tabsById['tab-1']?.history
    expect(history).toHaveLength(1)
    expect(history?.[0]).toMatchObject({
      sql: 'select 1;',
      status: 'requires_confirmation',
      confirmationReason: 'This statement modifies data',
    })
  })

  it('appends a history entry when execution fails with an error', async () => {
    executeSqlMock.mockRejectedValue(new Error('connection lost'))

    render(<SqlWorkbenchTab tab={tab} />)
    act(() => {
      useSqlWorkbenchStore.getState().setLimit('tab-1', null)
    })

    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.run') }))

    await waitFor(() => expect(executeSqlMock).toHaveBeenCalled())

    const history = useSqlWorkbenchStore.getState().tabsById['tab-1']?.history
    expect(history).toHaveLength(1)
    expect(history?.[0]).toMatchObject({
      sql: 'select 1;',
      status: 'error',
      errorSummary: 'connection lost',
    })
  })

  it('routes execution errors into an error result tab without showing the error status tag', async () => {
    executeSqlMock.mockRejectedValue(new Error('SQL execution failed'))

    render(<SqlWorkbenchTab tab={tab} />)

    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.run') }))

    await waitFor(() => expect(executeSqlMock).toHaveBeenCalled())

    expect(screen.getByRole('tab', { name: t('stage.status.error') })).toBeTruthy()
    expect(screen.getByText('SQL execution failed')).toBeTruthy()
    expect(screen.queryAllByText('SQL execution failed')).toHaveLength(1)
    expect(screen.queryByTestId('sql-workbench-status-bar')).toBeNull()
  })

  it('exposes the Monaco imperative API and current-statement decoration', () => {
    const onChange = vi.fn()
    const onRun = vi.fn()
    const onFormat = vi.fn()
    const onCursorChange = vi.fn()
    const ref = {
      current: null as null | {
        insertAtCursor: (text: string) => void
        revealLineNearTop: (line: number) => void
        setPosition: (line: number, column: number) => void
      },
    }

    render(
      <SqlMonacoEditor
        ref={ref}
        value="abc"
        onChange={onChange}
        onRun={onRun}
        onFormat={onFormat}
        onCursorChange={onCursorChange}
        currentStatementRange={{ startLine: 2, endLine: 3 }}
      />,
    )

    expect(editorHarness.fakeEditor?.addCommand).toHaveBeenNthCalledWith(
      1,
      editorHarness.fakeMonaco.KeyMod.CtrlCmd | editorHarness.fakeMonaco.KeyCode.Enter,
      expect.any(Function),
    )
    expect(editorHarness.fakeEditor?.addCommand).toHaveBeenNthCalledWith(
      2,
      editorHarness.fakeMonaco.KeyMod.CtrlCmd | editorHarness.fakeMonaco.KeyMod.Shift | editorHarness.fakeMonaco.KeyCode.KeyF,
      expect.any(Function),
    )
    expect(screen.getByTestId('sql-monaco-editor').className).toContain('rounded-none')
    expect(screen.getByTestId('sql-monaco-editor').className).not.toContain('border border-border/50')
    expect(editorHarness.lastProps?.options).toMatchObject({
      overviewRulerLanes: 0,
      hideCursorInOverviewRuler: true,
      bracketPairColorization: { enabled: true },
      autoClosingBrackets: 'always',
      autoIndent: 'full',
    })
    expect(editorHarness.fakeEditor?.deltaDecorations).toHaveBeenCalled()
    expect(editorHarness.decorations[0]).toMatchObject({
      range: {
        startLineNumber: 2,
        startColumn: 1,
        endLineNumber: 3,
        endColumn: 1,
      },
    })

    editorHarness.cursorListener?.({ position: { lineNumber: 4, column: 2 } })
    expect(onCursorChange).toHaveBeenCalledWith({ line: 4, column: 2 })

    ref.current?.setPosition(1, 2)
    ref.current?.revealLineNearTop(7)
    ref.current?.insertAtCursor('X')

    expect(editorHarness.fakeEditor?.setPosition).toHaveBeenCalledWith({ lineNumber: 1, column: 2 })
    expect(editorHarness.fakeEditor?.revealLineNearTop).toHaveBeenCalledWith(7)
    expect(onChange).toHaveBeenCalledWith('aXbc')

    const runCommand = editorHarness.fakeEditor?.addCommand.mock.calls[0]?.[1] as (() => void) | undefined
    const formatCommand = editorHarness.fakeEditor?.addCommand.mock.calls[1]?.[1] as (() => void) | undefined
    runCommand?.()
    formatCommand?.()

    expect(onRun).toHaveBeenCalledTimes(1)
    expect(onFormat).toHaveBeenCalledTimes(1)
  })

  it('renders the activity rail inside the SQL tab', () => {
    render(
      <SqlWorkbenchTab
        tab={{
          ...tab,
          tabId: 'tab-rail',
          originSessionId: 'sess-99',
        }}
      />,
    )

    const rail = screen.getByTestId('stage-activity-rail-stub')
    expect(rail).toBeTruthy()

    const tabRoot = screen.getByTestId('sql-workbench-tab')
    expect(tabRoot.contains(rail)).toBe(true)
    expect(tabRoot.className).toContain('flex-row')
  })

  it('renders the activity rail when the SQL tab has no origin session', () => {
    render(<SqlWorkbenchTab tab={{ ...tab, tabId: 'tab-rail-workspace' }} />)

    const rail = screen.getByTestId('stage-activity-rail-stub')
    expect(rail).toBeTruthy()
  })

  it('opens AlertDialog when SQL requires L2 confirmation', async () => {
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

    render(<SqlWorkbenchTab tab={tab} />)

    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.run') }))

    await waitFor(() => expect(executeSqlMock).toHaveBeenCalled())

    expect(screen.getByTestId('sql-confirmation-dialog')).toBeTruthy()
    expect(screen.getByText('update users set active = false')).toBeTruthy()
    expect(screen.queryByTestId('sql-confirmation-invalid-message')).toBeNull()
  })

  it('L1 SQL runs without confirmation dialog', async () => {
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
          resultId: 'r-set',
          kind: 'result_set',
          title: 'Result 1',
          statementIndex: 0,
          statementText: 'select 1',
          columns: ['id'],
          rows: [[1]],
          rowCount: 1,
          executionMs: 5,
          truncated: false,
        },
      ],
    })

    render(<SqlWorkbenchTab tab={tab} />)
    act(() => {
      useSqlWorkbenchStore.getState().setLimit('tab-1', null)
    })

    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.run') }))

    await waitFor(() => expect(executeSqlMock).toHaveBeenCalled())

    expect(screen.queryByTestId('sql-confirmation-dialog')).toBeNull()
    expect(screen.getByRole('tab', { name: 'Result 1' })).toBeTruthy()
  })

  it('confirmation dialog shows invalid message on confirmation_invalid response', async () => {
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
        status: 'confirmation_invalid',
        resolvedContext: null,
        contextNotice: null,
        invalidConfirmation: {
          reason: 'risk_ack_insufficient',
          ackedRisk: 'L1',
          currentRisk: 'L2',
          message: 'Insufficient risk acknowledgment level',
        },
      })

    render(<SqlWorkbenchTab tab={tab} />)

    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.run') }))
    await waitFor(() => expect(executeSqlMock).toHaveBeenCalledTimes(1))

    expect(screen.getByTestId('sql-confirmation-dialog')).toBeTruthy()

    // Click the Execute button on the SqlConfirmationCard
    const confirmButtons = screen.getAllByRole('button')
    const executeConfirmBtn = confirmButtons.find((btn) => btn.textContent === t('sqlConfirmation.execute'))
    fireEvent.click(executeConfirmBtn!)

    await waitFor(() => expect(executeSqlMock).toHaveBeenCalledTimes(2))

    expect(screen.getByTestId('sql-confirmation-invalid-message')).toBeTruthy()
    expect(screen.getByTestId('sql-confirmation-invalid-message').textContent).toBe('Insufficient risk acknowledgment level')
  })
})
