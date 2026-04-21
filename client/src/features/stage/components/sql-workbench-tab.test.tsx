import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { StageTab } from '@/stores/stage-store'
import { useSqlWorkbenchStore } from '../stores/sql-workbench-store'
import { SqlWorkbenchTab } from './sql-workbench-tab'

const executeMock = vi.hoisted(() => vi.fn())

vi.mock('@monaco-editor/react', () => ({
  default: ({ value, onChange }: { value?: string; onChange?: (value: string) => void }) => (
    <textarea
      data-testid="monaco-editor"
      value={value ?? ''}
      onChange={(event) => onChange?.(event.target.value)}
    />
  ),
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
    useSqlWorkbenchStore.setState({ tabsById: {} })
  })

  it('renders the new workbench shell and editor mount area', () => {
    render(<SqlWorkbenchTab tab={tab} />)

    expect(screen.getByTestId('sql-workbench-tab')).toBeTruthy()
    expect(screen.getByTestId('sql-monaco-editor')).toBeTruthy()
    expect(screen.getByRole('button', { name: /运行 SQL|Run SQL/i })).toBeTruthy()
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

    fireEvent.click(screen.getByRole('button', { name: /运行 SQL|Run SQL/i }))

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
})
