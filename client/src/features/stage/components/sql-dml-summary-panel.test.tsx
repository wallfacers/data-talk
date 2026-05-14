import type { ReactNode } from 'react'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { SqlDmlSummaryPanel } from './sql-dml-summary-panel'
import { useSqlWorkbenchStore } from '@/features/stage/stores/sql-workbench-store'

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    t: (key: string) =>
      ({
        'stage.queryEditor.result.rowNumber': '序号',
        'stage.queryEditor.result.action': 'Action',
        'stage.queryEditor.result.affectedRows': 'Affected Rows',
        'stage.queryEditor.result.duration': 'Duration',
        'stage.queryEditor.result.sql': 'SQL',
      })[key] ?? key,
  }),
}))

vi.mock('@/components/ui/alert-dialog', () => ({
  AlertDialog: ({
    open,
    children,
    onOpenChange,
  }: {
    open?: boolean
    children?: ReactNode
    onOpenChange?: (open: boolean) => void
  }) =>
    open ? (
      <div data-testid="alert-dialog-root">
        <div data-testid="alert-dialog-backdrop" onClick={() => onOpenChange?.(false)} />
        {children}
      </div>
    ) : null,
  AlertDialogTrigger: ({ children }: { children?: ReactNode }) => <>{children}</>,
  AlertDialogPortal: ({ children }: { children?: ReactNode }) => <>{children}</>,
  AlertDialogOverlay: () => <div data-testid="alert-dialog-overlay" />,
  AlertDialogContent: ({
    children,
    showCloseButton,
  }: {
    children?: ReactNode
    showCloseButton?: boolean
  }) => (
    <div data-testid="alert-dialog-content">
      {children}
      {showCloseButton && <button data-testid="alert-dialog-close">X</button>}
    </div>
  ),
  AlertDialogHeader: ({ children }: { children?: ReactNode }) => (
    <div data-testid="alert-dialog-header">{children}</div>
  ),
  AlertDialogFooter: ({ children }: { children?: ReactNode }) => (
    <div data-testid="alert-dialog-footer">{children}</div>
  ),
  AlertDialogTitle: ({ children }: { children?: ReactNode }) => <h2>{children}</h2>,
  AlertDialogDescription: ({ children }: { children?: ReactNode }) => <p>{children}</p>,
  AlertDialogAction: ({
    children,
    onClick,
    disabled,
  }: {
    children?: ReactNode
    onClick?: () => void
    disabled?: boolean
  }) => (
    <button
      data-testid="alert-dialog-action"
      onClick={onClick}
      disabled={disabled}
    >
      {children}
    </button>
  ),
  AlertDialogCancel: ({
    children,
    disabled,
  }: {
    children?: ReactNode
    disabled?: boolean
  }) => (
    <button data-testid="alert-dialog-cancel" disabled={disabled}>
      {children}
    </button>
  ),
}))

const mockUndoDml = vi.fn()
vi.mock('@/services/api/sql', () => ({
  undoDml: (...args: unknown[]) => mockUndoDml(...args),
}))

const baseResult = {
  resultId: 'dml-1',
  kind: 'dml_summary' as const,
  title: 'update summary',
  statementIndex: 0,
  statementText: 'UPDATE users SET name = ? WHERE id = ?',
  columns: [],
  rows: [],
  rowCount: 0,
  executionMs: 12,
  truncated: false,
  affectedRows: 3,
  undoLogId: 'undo-log-1',
  undoable: true,
}

describe('SqlDmlSummaryPanel undo button', () => {
  beforeEach(() => {
    useSqlWorkbenchStore.setState({ tabsById: {} })
    mockUndoDml.mockReset()
  })

  it('renders an Undo button when result.undoable is true', () => {
    render(<SqlDmlSummaryPanel result={{ ...baseResult }} tabId="tab-1" />)

    expect(screen.getByRole('button', { name: /undo/i })).toBeTruthy()
  })

  it('does not render an Undo button when result.undoable is false', () => {
    render(
      <SqlDmlSummaryPanel
        result={{ ...baseResult, undoable: false }}
        tabId="tab-1"
      />,
    )

    expect(screen.queryByRole('button', { name: /undo/i })).toBeNull()
  })

  it('does not render an Undo button when result.undoable is undefined', () => {
    const { undoable: _, ...resultWithoutUndoable } = baseResult
    render(
      <SqlDmlSummaryPanel
        result={resultWithoutUndoable as typeof baseResult}
        tabId="tab-1"
      />,
    )

    expect(screen.queryByRole('button', { name: /undo/i })).toBeNull()
  })

  it('shows Reverted text instead of the Undo button when undo state is undone', () => {
    useSqlWorkbenchStore.getState().ensureTab('tab-1')
    const existing = useSqlWorkbenchStore.getState().tabsById['tab-1']!
    useSqlWorkbenchStore.setState({
      tabsById: {
        'tab-1': {
          ...existing,
          undoStates: {
            'dml-1': { status: 'undone' },
          },
        },
      },
    })

    render(<SqlDmlSummaryPanel result={{ ...baseResult }} tabId="tab-1" />)

    expect(screen.queryByRole('button', { name: /undo/i })).toBeNull()
    expect(screen.getByText('Reverted')).toBeTruthy()
  })

  it('shows error text alongside the Undo button when undo state is error', () => {
    useSqlWorkbenchStore.getState().ensureTab('tab-1')
    const existing = useSqlWorkbenchStore.getState().tabsById['tab-1']!
    useSqlWorkbenchStore.setState({
      tabsById: {
        'tab-1': {
          ...existing,
          undoStates: {
            'dml-1': { status: 'error', error: 'Connection lost' },
          },
        },
      },
    })

    render(<SqlDmlSummaryPanel result={{ ...baseResult }} tabId="tab-1" />)

    // The Undo button is still present when error occurs (user can retry)
    expect(screen.getByRole('button', { name: /undo/i })).toBeTruthy()
    expect(screen.getByText('Connection lost')).toBeTruthy()
  })

  it('opens the confirmation dialog when Undo is clicked and server requires confirmation', async () => {
    const inverseSql = 'DELETE FROM users WHERE id = 1'
    mockUndoDml.mockResolvedValueOnce({
      status: 'requires_confirmation',
      inverseSql,
      affectedRows: 3,
      tableName: 'users',
    })

    render(<SqlDmlSummaryPanel result={{ ...baseResult }} tabId="tab-1" />)

    fireEvent.click(screen.getByRole('button', { name: /undo/i }))

    await waitFor(() => {
      expect(mockUndoDml).toHaveBeenCalledWith({
        undoLogId: 'undo-log-1',
        confirmed: false,
      })
    })

    await waitFor(() => {
      expect(screen.getByText('Undo this change?')).toBeTruthy()
      expect(screen.getByText(inverseSql)).toBeTruthy()
    })
  })

  it('sets undone state when confirmation is accepted and undo succeeds', async () => {
    mockUndoDml
      .mockResolvedValueOnce({
        status: 'requires_confirmation',
        inverseSql: 'DELETE FROM users WHERE id = 1',
        affectedRows: 3,
        tableName: 'users',
      })
      .mockResolvedValueOnce({
        status: 'undone',
        affectedRows: 3,
      })

    render(<SqlDmlSummaryPanel result={{ ...baseResult }} tabId="tab-1" />)

    fireEvent.click(screen.getByRole('button', { name: /undo/i }))

    await waitFor(() => {
      expect(screen.getByText('Undo this change?')).toBeTruthy()
    })

    fireEvent.click(screen.getByTestId('alert-dialog-action'))

    await waitFor(() => {
      expect(mockUndoDml).toHaveBeenCalledTimes(2)
      expect(mockUndoDml).toHaveBeenNthCalledWith(2, {
        undoLogId: 'undo-log-1',
        confirmed: true,
        riskAck: 'L2',
      })
    })

    const state = useSqlWorkbenchStore.getState()
    expect(state.tabsById['tab-1']?.undoStates['dml-1']).toEqual({
      status: 'undone',
    })
  })
})
