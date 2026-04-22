import { fireEvent, render, screen, waitFor, act } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { StageTab } from '@/stores/stage-store'
import { SqlRiskError } from '@/services/api/sql'
import { useSqlWorkbenchStore } from '../stores/sql-workbench-store'
import { SqlMonacoEditor } from './sql-monaco-editor'
import { SqlWorkbenchTab } from './sql-workbench-tab'

const executeMock = vi.hoisted(() => vi.fn())
const formatMock = vi.hoisted(() => vi.fn((sql: string) => `formatted: ${sql}`))
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
      KeyMod: { CtrlCmd: 1 },
      KeyCode: { Enter: 2 },
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

vi.mock('sql-formatter', () => ({
  format: formatMock,
}))

vi.mock('@/features/connection/store', () => ({
  useConnectionStore: (selector: (state: {
    activeConnectionId: string | null
    connections: Array<{ id: string; name: string }>
  }) => unknown) => selector({
    activeConnectionId: 'conn-1',
    connections: [{ id: 'conn-1', name: 'Primary Connection' }],
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
  beforeEach(() => {
    executeMock.mockReset()
    formatMock.mockClear()
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
    expect(screen.getByTestId('sql-editor-breadcrumb')).toBeTruthy()
    expect(screen.getByTestId('sql-monaco-editor')).toBeTruthy()
    expect(screen.getByRole('button', { name: /Run/i })).toBeTruthy()
    expect(screen.getByTestId('sql-editor-toolbar')).toBeTruthy()
    expect(screen.getByTestId('sql-workbench-status-bar')).toBeTruthy()
  })

  it('tracks cursor position and shows the current statement kind in the breadcrumb', async () => {
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

    await waitFor(() => expect(screen.getByText('Ln 3')).toBeTruthy())
    expect(screen.getByText('UPDATE')).toBeTruthy()
    expect(screen.getByText('Ln 3, Col 5')).toBeTruthy()
  })

  it('formats SQL, saves the draft, and marks the tab as saved', async () => {
    render(<SqlWorkbenchTab tab={tab} />)

    fireEvent.change(screen.getByTestId('monaco-editor'), { target: { value: 'select id from users' } })
    fireEvent.click(screen.getByRole('button', { name: /Format/i }))

    await waitFor(() => expect(formatMock).toHaveBeenCalled())
    await waitFor(() => expect(screen.getByTestId('monaco-editor')).toHaveValue('formatted: select id from users'))

    fireEvent.click(screen.getByRole('button', { name: /Save/i }))

    expect(useSqlWorkbenchStore.getState().tabsById['tab-1']?.savedSqlText).toBe(
      'formatted: select id from users',
    )
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
    await waitFor(() => expect(screen.getByRole('combobox', { name: /Execution limit/i })).toHaveTextContent('10 rows'))

    fireEvent.click(screen.getByRole('button', { name: /Run/i }))

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
    fireEvent.click(screen.getByRole('button', { name: /Run/i }))

    await waitFor(() => expect(executeMock).toHaveBeenCalled())
    expect(executeMock).toHaveBeenCalledWith(
      'select 1 limit 5',
      'conn-1',
      'user',
      expect.any(Object),
      expect.any(AbortSignal),
    )
    expect(screen.getByRole('button', { name: /Cancel/i })).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: /Cancel/i }))
    await waitFor(() => expect(screen.getByText('Idle')).toBeTruthy())
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

    fireEvent.click(screen.getByRole('button', { name: /Run/i }))

    await waitFor(() => expect(executeMock).toHaveBeenCalled())

    expect(screen.getByRole('tab', { name: 'Result 1' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: 'DML' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: 'Error' })).toBeTruthy()
    expect(screen.getByText('1')).toBeTruthy()

    fireEvent.click(screen.getByRole('tab', { name: 'DML' }))
    expect(screen.getByText(/3/)).toBeTruthy()

    fireEvent.click(screen.getByRole('tab', { name: 'Error' }))
    expect(screen.getByText(/relation missing does not exist/)).toBeTruthy()
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

    fireEvent.click(screen.getByRole('button', { name: /Run/i }))

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

    fireEvent.click(screen.getByRole('button', { name: /Run/i }))

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

    fireEvent.click(screen.getByRole('button', { name: /Run/i }))

    await waitFor(() => expect(executeMock).toHaveBeenCalled())

    const history = useSqlWorkbenchStore.getState().tabsById['tab-1']?.history
    expect(history).toHaveLength(1)
    expect(history?.[0]).toMatchObject({
      sql: 'select 1;',
      status: 'error',
      errorSummary: 'connection lost',
    })
  })

  it('exposes the Monaco imperative API and current-statement decoration', () => {
    const onChange = vi.fn()
    const onRun = vi.fn()
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
        onCursorChange={onCursorChange}
        currentStatementRange={{ startLine: 2, endLine: 3 }}
      />,
    )

    expect(editorHarness.fakeEditor?.addCommand).toHaveBeenCalledWith(3, expect.any(Function))
    expect(editorHarness.lastProps?.options).toMatchObject({
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
  })
})
