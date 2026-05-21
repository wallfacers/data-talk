import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'

// ── Mocks ─────────────────────────────────────────────────────────────────

// Mock API
const mockListOpLogs = vi.fn().mockResolvedValue({
  items: [
    {
      id: 'log-1',
      sessionId: 'sess-1',
      sessionTitle: 'Test Session',
      connectionId: 'conn-1',
      databaseName: 'testdb',
      schemaName: null,
      tableName: 'orders',
      operation: 'INSERT',
      affectedRows: 3,
      undoable: true,
      status: 'active',
      expiresAt: Date.now() + 86400000,
      createdAt: Date.now() - 3600000,
      undoneAt: null,
    },
  ],
  total: 1,
  page: 0,
  size: 50,
})

vi.mock('@/services/api/connection-op-log', () => ({
  listOpLogs: (...args: unknown[]) => mockListOpLogs(...args),
  batchUndoOpLogs: vi.fn(),
}))

// Mock SSE
vi.mock('@/services/api/connection-op-log-sse', () => ({
  subscribeOpLogStream: vi.fn().mockReturnValue({ dispose: vi.fn() }),
}))

// Mock child components to isolate the OperationLogTab under test
vi.mock('./op-log-filter-bar', () => ({
  OpLogFilterBar: () => <div data-testid="filter-bar">FilterBar</div>,
}))

vi.mock('./op-log-table', () => ({
  OpLogTable: ({ data }: { data: Array<{ id: string; tableName: string; operation: string; status: string }> }) => (
    <div data-testid="op-log-table">
      {data.map(item => (
        <div key={item.id} data-testid={`row-${item.id}`}>
          <span>{item.tableName}</span>
          <span>{item.operation}</span>
          <span>{item.status}</span>
        </div>
      ))}
    </div>
  ),
}))

vi.mock('./batch-undo-bar', () => ({
  BatchUndoBar: ({ count }: { count: number }) => (
    <div data-testid="batch-undo-bar">{count} selected</div>
  ),
}))

vi.mock('./batch-undo-confirm-dialog', () => ({
  BatchUndoConfirmDialog: () => <div data-testid="batch-undo-dialog" />,
}))

// ── Imports (after mocks) ─────────────────────────────────────────────────

import { OperationLogTab } from './operation-log-tab'
import type { StageTab } from '@/stores/stage-store'

// ── Helpers ───────────────────────────────────────────────────────────────

function renderWithProviders(ui: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      {ui}
    </QueryClientProvider>,
  )
}

function makeOpLogTab(overrides: Partial<StageTab> = {}): StageTab {
  return {
    tabId: 'tab-1',
    type: 'operation_log',
    title: 'Test Connection — Operations',
    connectionId: 'conn-1',
    connectionName: 'Test Connection',
    payload: {
      kind: 'operation_log',
      connectionId: 'conn-1',
      connectionName: 'Test Connection',
    },
    createdAt: Date.now(),
    ...overrides,
  }
}

// ── Tests ─────────────────────────────────────────────────────────────────

describe('OperationLogTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders filter bar and table after data loads', async () => {
    renderWithProviders(<OperationLogTab tab={makeOpLogTab()} />)

    expect(screen.getByTestId('filter-bar')).toBeInTheDocument()
    expect(await screen.findByTestId('op-log-table')).toBeInTheDocument()
  })

  it('passes connectionId from the tab to listOpLogs', async () => {
    renderWithProviders(<OperationLogTab tab={makeOpLogTab()} />)

    // Wait for query to fire
    await screen.findByTestId('op-log-table')

    expect(mockListOpLogs).toHaveBeenCalledWith('conn-1', 0, 50, {})
  })

  it('renders table row data from the API response', async () => {
    renderWithProviders(<OperationLogTab tab={makeOpLogTab()} />)

    const row = await screen.findByTestId('row-log-1')
    expect(row).toHaveTextContent('orders')
    expect(row).toHaveTextContent('INSERT')
    expect(row).toHaveTextContent('active')
  })

  it('renders an empty state when API returns no items', async () => {
    mockListOpLogs.mockResolvedValueOnce({
      items: [],
      total: 0,
      page: 0,
      size: 50,
    })

    renderWithProviders(<OperationLogTab tab={makeOpLogTab()} />)

    const table = await screen.findByTestId('op-log-table')
    expect(table).toBeInTheDocument()
    // No rows rendered
    expect(table.querySelectorAll('[data-testid^="row-"]')).toHaveLength(0)
  })

  it('does not render batch undo bar when no undoable items are selected', async () => {
    renderWithProviders(<OperationLogTab tab={makeOpLogTab()} />)

    await screen.findByTestId('op-log-table')
    expect(screen.queryByTestId('batch-undo-bar')).not.toBeInTheDocument()
  })
})
