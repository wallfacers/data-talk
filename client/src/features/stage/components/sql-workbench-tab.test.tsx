import { fireEvent, render, screen, waitFor, act } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { StageTab } from '@/stores/stage-store'
import { SqlRiskError } from '@/services/api/sql'
import { translateMessage } from '@/i18n/messages'
import { useSqlWorkbenchStore } from '../stores/sql-workbench-store'
import { SqlMonacoEditor } from './sql-monaco-editor'
import { SqlWorkbenchTab } from './sql-workbench-tab'

const executeMock = vi.hoisted(() => vi.fn())
const formatSqlMock = vi.hoisted(() => vi.fn((sql: string) => `formatted: ${sql}`))
const listConnectionsMock = vi.hoisted(() => vi.fn())
const setConnectionsMock = vi.hoisted(() => vi.fn())
const connectionStoreSnapshot = vi.hoisted(() => ({
  activeConnectionId: 'conn-1' as string | null,
  connections: [{ id: 'conn-1', name: 'Primary Connection', kind: 'postgres' }] as Array<{ id: string; name: string; kind: string }>,
}))
const editorHarness = vi.hoisted(() => {
  const harness = {
    lastProps: null as null | {
      value?: string
      options?: unknown
    },
    position: { lineNumber: 1, column: 1 },
    cursorListener: null as null | ((event: { position?: { lineNumber: number; column: number } }) => void),
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

vi.mock('@/features/stage/hooks/use-sql-execute', () => ({
  useSqlExecute: () => ({
    execute: executeMock,
    result: null,
    risk: null,
    status: 'idle' as const,
    errorMessage: null,
    reset: vi.fn(),
  }),
}))

vi.mock('../utils/format-sql', () => ({
  formatSql: formatSqlMock,
}))

vi.mock('@/services/api/connection', () => ({
  listConnections: listConnectionsMock,
}))

vi.mock('@/features/connection/store', () => ({
  useConnectionStore: (selector: (state: {
    activeConnectionId: string | null
    connections: Array<{ id: string; name: string; kind: string }>
    setConnections: (connections: Array<{ id: string; name: string; kind: string }>) => void
  }) => unknown) => selector({
    activeConnectionId: connectionStoreSnapshot.activeConnectionId,
    connections: connectionStoreSnapshot.connections,
    setConnections: setConnectionsMock,
  }),
}))

vi.mock('@/features/session/hooks/use-session-data-context', () => ({
  useSessionDataContext: () => ({
    context: null,
    isLoading: false,
    error: null,
    refresh: vi.fn(),
    resolveUseTarget: vi.fn(),
    setSessionDataContext: vi.fn(),
    validateSessionDataContext: vi.fn(),
  }),
}))

vi.mock('./activity-rail/stage-activity-rail', () => ({
  StageActivityRail: ({ sessionId }: { sessionId: string | null }) => (
    <div data-testid="stage-activity-rail-stub" data-session-id={sessionId ?? ''} />
  ),
}))

const tab: StageTab = {
  tabId: 'tab-1',
  type: 'query_editor',
  title: 'SQL',
  scope: 'workspace',
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
    executeMock.mockReset()
    formatSqlMock.mockClear()
    listConnectionsMock.mockReset()
    setConnectionsMock.mockReset()
    connectionStoreSnapshot.activeConnectionId = 'conn-1'
    connectionStoreSnapshot.connections = [{ id: 'conn-1', name: 'Primary Connection', kind: 'postgres' }]
    editorHarness.lastProps = null
    editorHarness.position = { lineNumber: 1, column: 1 }
    editorHarness.cursorListener = null
    editorHarness.decorations = []
    editorHarness.fakeEditor?.addCommand.mockClear()
    editorHarness.fakeEditor?.deltaDecorations.mockClear()
    editorHarness.fakeEditor?.getPosition.mockClear()
    editorHarness.fakeEditor?.setPosition.mockClear()
    editorHarness.fakeEditor?.revealLineNearTop.mockClear()
    useSqlWorkbenchStore.setState({ tabsById: {} })
    window.localStorage.clear()
  })

  it('renders the new workbench shell and editor mount area', () => {
    render(<SqlWorkbenchTab tab={tab} />)

    expect(screen.getByTestId('sql-workbench-tab')).toBeTruthy()
    expect(screen.queryByTestId('sql-editor-breadcrumb')).toBeNull()
    expect(screen.getByTestId('sql-monaco-editor')).toBeTruthy()
    expect(screen.getByTestId('sql-monaco-editor').className).toContain('rounded-b-xl')
    expect(screen.getByTestId('sql-monaco-editor').className).toContain('border border-border/50')
    expect(screen.getByRole('button', { name: t('stage.toolbar.run') })).toBeTruthy()
    expect(screen.getByTestId('sql-editor-toolbar')).toBeTruthy()
    expect(screen.queryByTestId('sql-workbench-status-bar')).toBeNull()
    expect(screen.queryByRole('tablist')).toBeNull()
    expect(screen.queryByTestId('sql-workbench-result-splitter')).toBeNull()
    expect(screen.queryByTestId('sql-result-shell')).toBeNull()
  })

  it('loads connections to resolve missing connection names for current tab context', async () => {
    connectionStoreSnapshot.activeConnectionId = 'conn-2'
    connectionStoreSnapshot.connections = [{ id: 'conn-1', name: 'Primary Connection', kind: 'postgres' }]
    listConnectionsMock.mockResolvedValue([
      { id: 'conn-1', name: 'Primary Connection', kind: 'postgres' },
      { id: 'conn-2', name: 'Analytics', kind: 'postgres' },
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
        { id: 'conn-1', name: 'Primary Connection', kind: 'postgres' },
        { id: 'conn-2', name: 'Analytics', kind: 'postgres' },
      ])
    })
  })

  it('loads connection options when context is not pinned and selectable', async () => {
    connectionStoreSnapshot.activeConnectionId = null
    connectionStoreSnapshot.connections = []
    listConnectionsMock.mockResolvedValue([
      { id: 'conn-1', name: 'Primary Connection', kind: 'postgres' },
      { id: 'conn-2', name: 'Analytics', kind: 'postgres' },
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
        { id: 'conn-1', name: 'Primary Connection', kind: 'postgres' },
        { id: 'conn-2', name: 'Analytics', kind: 'postgres' },
      ])
    })
  })

  it('auto-runs direct SQL query tabs on mount when payload.autoRun is true', async () => {
    executeMock.mockResolvedValue({
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

    await waitFor(() => expect(executeMock).toHaveBeenCalledTimes(1))
  })

  it('auto-runs again when switching to another direct SQL tab id', async () => {
    executeMock.mockResolvedValue({
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

    await waitFor(() => expect(executeMock).toHaveBeenCalledTimes(1))

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

    await waitFor(() => expect(executeMock).toHaveBeenCalledTimes(2))
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

  it('injects the tab limit into select-like SQL before execution', async () => {
    executeMock.mockResolvedValue({
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

    await waitFor(() => expect(executeMock).toHaveBeenCalled())

    expect(executeMock).toHaveBeenCalledWith(
      'select 1 LIMIT 10;',
      'conn-1',
      'user',
      expect.objectContaining({
        database: 'db_main',
        schema: null,
      }),
      expect.anything(),
    )
  })

  it('keeps an existing LIMIT clause untouched and can cancel the running query', async () => {
    let abortHandler: (() => void) | null = null
    executeMock.mockImplementation(
      (_sql: string, _connectionId: string, _source: 'ai' | 'user', _context: unknown, signal?: AbortSignal) =>
        new Promise((_resolve, reject) => {
          abortHandler = () => reject(new DOMException('The operation was aborted.', 'AbortError'))
          signal?.addEventListener('abort', () => abortHandler?.(), { once: true })
        }),
    )

    render(<SqlWorkbenchTab tab={tab} />)

    fireEvent.change(screen.getByTestId('monaco-editor'), { target: { value: 'select 1 limit 5' } })
    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.run') }))

    await waitFor(() => expect(executeMock).toHaveBeenCalled())
    expect(executeMock).toHaveBeenCalledWith(
      'select 1 limit 5',
      'conn-1',
      'user',
      expect.any(Object),
      expect.any(AbortSignal),
    )
    expect(screen.getByRole('button', { name: t('stage.toolbar.cancel') })).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.cancel') }))
    await waitFor(() => expect(screen.queryByTestId('sql-workbench-status-bar')).toBeNull())
    expect(abortHandler).not.toBeNull()
  })

  it('executes SQL and switches among result_set / dml_summary / error panels', async () => {
    executeMock.mockResolvedValue({
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

    await waitFor(() => expect(executeMock).toHaveBeenCalled())

    expect(screen.getByTestId('sql-workbench-result-splitter')).toBeTruthy()
    expect(screen.getByRole('tab', { name: 'Result 1' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: 'DML' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: 'Error' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: t('stage.queryEditor.result.rowNumber') })).toBeTruthy()
    expect(screen.getByTestId('sql-result-shell').className).toContain('rounded-b-xl')

    fireEvent.click(screen.getByRole('tab', { name: 'DML' }))
    expect(screen.getByText(/3/)).toBeTruthy()

    fireEvent.click(screen.getByRole('tab', { name: 'Error' }))
    expect(screen.getByText(/relation missing does not exist/)).toBeTruthy()
  })

  it('supports dragging the horizontal splitter above result tabs', async () => {
    executeMock.mockResolvedValue({
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
    executeMock.mockResolvedValue({
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

    await waitFor(() => expect(executeMock).toHaveBeenCalled())

    const history = useSqlWorkbenchStore.getState().tabsById['tab-1']?.history
    expect(history).toHaveLength(1)
    expect(history?.[0]).toMatchObject({
      sql: 'select 1;',
      status: 'ok',
      resultCount: 1,
    })
  })

  it('appends a history entry when execution is blocked by risk', async () => {
    executeMock.mockRejectedValue(new SqlRiskError({
      riskLevel: 'high',
      riskReason: 'writes are not allowed',
    }))

    render(<SqlWorkbenchTab tab={tab} />)
    act(() => {
      useSqlWorkbenchStore.getState().setLimit('tab-1', null)
    })

    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.run') }))

    await waitFor(() => expect(executeMock).toHaveBeenCalled())

    const history = useSqlWorkbenchStore.getState().tabsById['tab-1']?.history
    expect(history).toHaveLength(1)
    expect(history?.[0]).toMatchObject({
      sql: 'select 1;',
      status: 'risk_blocked',
      errorSummary: 'writes are not allowed',
    })
  })

  it('appends a history entry when execution fails with an error', async () => {
    executeMock.mockRejectedValue(new Error('connection lost'))

    render(<SqlWorkbenchTab tab={tab} />)
    act(() => {
      useSqlWorkbenchStore.getState().setLimit('tab-1', null)
    })

    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.run') }))

    await waitFor(() => expect(executeMock).toHaveBeenCalled())

    const history = useSqlWorkbenchStore.getState().tabsById['tab-1']?.history
    expect(history).toHaveLength(1)
    expect(history?.[0]).toMatchObject({
      sql: 'select 1;',
      status: 'error',
      errorSummary: 'connection lost',
    })
  })

  it('routes execution errors into an error result tab without showing the error status tag', async () => {
    executeMock.mockRejectedValue(new Error('SQL execution failed'))

    render(<SqlWorkbenchTab tab={tab} />)

    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.run') }))

    await waitFor(() => expect(executeMock).toHaveBeenCalled())

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
    expect(screen.getByTestId('sql-monaco-editor').className).toContain('rounded-b-xl')
    expect(screen.getByTestId('sql-monaco-editor').className).toContain('border border-border/50')
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

  it('renders the activity rail inside the SQL tab and propagates the origin sessionId', () => {
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
    expect(rail.getAttribute('data-session-id')).toBe('sess-99')

    const tabRoot = screen.getByTestId('sql-workbench-tab')
    expect(tabRoot.contains(rail)).toBe(true)
    expect(tabRoot.className).toContain('flex-row')
  })

  it('passes empty sessionId to the rail when the SQL tab has no origin session', () => {
    render(<SqlWorkbenchTab tab={{ ...tab, tabId: 'tab-rail-workspace' }} />)

    const rail = screen.getByTestId('stage-activity-rail-stub')
    expect(rail.getAttribute('data-session-id')).toBe('')
  })
})
