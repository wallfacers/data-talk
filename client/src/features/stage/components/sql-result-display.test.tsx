import type { ReactNode } from 'react'
import { render, screen, waitFor, within } from '@testing-library/react'
import { fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { SqlDmlSummaryPanel } from './sql-dml-summary-panel'
import { SqlErrorResultPanel } from './sql-error-result-panel'
import { SqlResultPanel } from './sql-result-panel'
import { SqlResultTable } from './sql-result-table'

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    t: (key: string) =>
      ({
        'stage.queryEditor.cell.null': 'NULL',
        'stage.queryEditor.result.rowNumber': '序号',
        'stage.queryEditor.result.action': 'Action',
        'stage.queryEditor.result.affectedRows': 'Affected Rows',
        'stage.queryEditor.result.duration': 'Duration',
        'stage.queryEditor.result.sql': 'SQL',
        'stage.queryEditor.result.previousPage': 'Previous',
        'stage.queryEditor.result.nextPage': 'Next',
        'stage.queryEditor.result.copyCell': '复制单元格',
        'stage.queryEditor.result.copyRow': '复制行',
        'stage.queryEditor.result.copyColumnName': '复制列名',
        'stage.queryEditor.result.pageIndicator': 'Page 1 / 3',
        'stage.queryEditor.summary.rows': '3 rows · 8ms',
        'stage.queryEditor.summary.truncated': 'Top 3 rows · 8ms',
        'stage.queryEditor.runFailed': 'SQL execution failed',
        'stage.status.error': 'Error',
      })[key] ?? key,
  }),
}))

vi.mock('@/components/ui/context-menu', () => ({
  ContextMenu: ({ children }: { children?: ReactNode }) => <>{children}</>,
  ContextMenuTrigger: ({ render, children }: { render?: ReactNode; children?: ReactNode }) => <>{render ?? children}</>,
  ContextMenuContent: ({ children }: { children?: ReactNode }) => <div data-testid="sql-result-context-menu">{children}</div>,
  ContextMenuItem: ({
    children,
    onClick,
    disabled,
  }: { children?: ReactNode; onClick?: () => void; disabled?: boolean }) => (
    <button type="button" onClick={disabled ? undefined : onClick} disabled={disabled}>
      {children}
    </button>
  ),
}))

describe('Sql result displays', () => {
  it('shows row count summary and a row-number column for result sets', () => {
    render(
      <SqlResultTable
        result={{
          resultId: 'result-1',
          kind: 'result_set',
          title: 'orders',
          statementIndex: 0,
          statementText: 'select * from orders',
          columns: ['id', 'status'],
          rows: [
            ['O-1', 'paid'],
            ['O-2', null],
            ['O-3', 'pending'],
          ],
          rowCount: 3,
          executionMs: 8,
          truncated: false,
        }}
      />,
    )

    expect(screen.getByText('3 rows · 8ms')).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: '序号' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: 'id' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: '1' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: '2' })).toBeTruthy()
    expect(screen.getByText('NULL')).toBeTruthy()
  })

  it('keeps the horizontal result scrollbar on the visible result viewport', () => {
    const { container } = render(
      <SqlResultTable
        result={{
          resultId: 'wide-result',
          kind: 'result_set',
          title: 'wide orders',
          statementIndex: 0,
          statementText: 'select * from orders',
          columns: Array.from({ length: 12 }, (_, index) => `column_${index + 1}`),
          rows: Array.from({ length: 120 }, (_, rowIndex) =>
            Array.from({ length: 12 }, (_, columnIndex) => `R${rowIndex + 1}-C${columnIndex + 1}`),
          ),
          rowCount: 120,
          executionMs: 8,
          truncated: false,
        }}
      />,
    )

    const viewport = screen.getByTestId('sql-result-table-scroll')
    const tableContainer = container.querySelector('[data-slot="table-container"]') as HTMLElement
    expect(viewport.className).toContain('overflow-auto')
    expect(tableContainer.className).not.toContain('overflow-x-auto')
  })

  it('keeps result headers opaque and above rows while scrolling vertically', () => {
    const { container } = render(
      <SqlResultTable
        result={{
          resultId: 'sticky-header-result',
          kind: 'result_set',
          title: 'orders',
          statementIndex: 0,
          statementText: 'select * from orders',
          columns: ['id', 'status'],
          rows: Array.from({ length: 120 }, (_, index) => [`O-${index + 1}`, 'paid']),
          rowCount: 120,
          executionMs: 8,
          truncated: false,
        }}
      />,
    )

    const tableHeader = container.querySelector('[data-slot="table-header"]') as HTMLElement
    expect(tableHeader.className).not.toContain('bg-muted/40')

    for (const headerCell of screen.getAllByRole('columnheader')) {
      expect(headerCell.className).toContain('sticky')
      expect(headerCell.className).toContain('top-0')
      expect(headerCell.className).toContain('z-20')
      expect(headerCell.className).toContain('bg-muted')
    }
  })

  it('renders localized context menu actions for result cells', () => {
    render(
      <SqlResultTable
        result={{
          resultId: 'context-result',
          kind: 'result_set',
          title: 'orders',
          statementIndex: 0,
          statementText: 'select * from orders',
          columns: ['id', 'status'],
          rows: [['O-1', 'paid']],
          rowCount: 1,
          executionMs: 8,
          truncated: false,
        }}
      />,
    )

    const menu = screen.getByTestId('sql-result-context-menu')
    expect(within(menu).getByRole('button', { name: '复制单元格' })).toBeTruthy()
    expect(within(menu).getByRole('button', { name: '复制行' })).toBeTruthy()
    expect(within(menu).getByRole('button', { name: '复制列名' })).toBeTruthy()
  })

  it('renders dml summary as a table row instead of plain text blocks', () => {
    render(
      <SqlDmlSummaryPanel
        result={{
          resultId: 'dml-1',
          kind: 'dml_summary',
          title: 'update summary',
          statementIndex: 1,
          statementText: 'update orders set status = ? where id = ?',
          columns: [],
          rows: [],
          rowCount: 0,
          executionMs: 12,
          truncated: false,
          affectedRows: 2,
        }}
      />,
    )

    expect(screen.getByRole('columnheader', { name: '序号' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: 'Action' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: 'Affected Rows' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: 'Duration' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: 'SQL' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: '1' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: 'update summary' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: '2' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: '12ms' })).toBeTruthy()
  })

  it('renders markdown-formatted errors in a scrollable document shell and hides the source sql text', async () => {
    const { container } = render(
      <SqlErrorResultPanel
        result={{
          resultId: 'err-1',
          kind: 'error',
          title: 'query failed',
          statementIndex: 2,
          statementText: 'select * from missing_table',
          columns: [],
          rows: [],
          rowCount: 0,
          executionMs: 1,
          truncated: false,
          errorMessage: '## Connection failed\n\n- Host: `127.0.0.1`\n- Port: `3306`\n\n```text\nrelation "missing_table" does not exist\n```',
        }}
      />,
    )

    await waitFor(() =>
      expect(container.querySelector('[data-component="markdown"]')).not.toBeNull(),
    )
    expect(screen.getByRole('heading', { name: 'Connection failed' })).toBeTruthy()
    expect(screen.getByText('127.0.0.1')).toBeTruthy()
    expect(screen.getByText('relation "missing_table" does not exist')).toBeTruthy()
    expect(screen.queryByText('select * from missing_table')).toBeNull()
    expect(container.firstElementChild?.className).toContain('overflow-auto')
    expect(container.firstElementChild?.className).not.toContain('items-center')
    expect(container.firstElementChild?.className).not.toContain('justify-center')
    expect(
      within(container.firstElementChild as HTMLElement).getByText('relation "missing_table" does not exist'),
    ).toBeTruthy()
  })

  it('reuses the markdown error shell for execute-status fallback errors', async () => {
    const { container } = render(
      <SqlResultPanel
        executeStatus="error"
        activeResult={null}
        risk={null}
        errorMessage={'## Connection failed\n\nMySQL is down.'}
      />,
    )

    await waitFor(() =>
      expect(container.querySelector('[data-component="markdown"]')).not.toBeNull(),
    )
    expect(screen.getByRole('heading', { name: 'Connection failed' })).toBeTruthy()
    expect(screen.getByText('MySQL is down.')).toBeTruthy()
    expect(container.firstElementChild?.className).toContain('overflow-auto')
    expect(container.firstElementChild?.className).not.toContain('items-center')
    expect(container.firstElementChild?.className).not.toContain('justify-center')
  })

  it('paginates result sets in pages of 100 rows', () => {
    render(
      <SqlResultTable
        result={{
          resultId: 'result-paged',
          kind: 'result_set',
          title: 'orders',
          statementIndex: 0,
          statementText: 'select * from orders',
          columns: ['id'],
          rows: Array.from({ length: 205 }, (_, index) => [`ORD-${index + 1}`]),
          rowCount: 205,
          executionMs: 18,
          truncated: false,
        }}
      />,
    )

    expect(screen.getByRole('cell', { name: '100' })).toBeTruthy()
    expect(screen.queryByRole('cell', { name: '101' })).toBeNull()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(screen.getByText('Page 1 / 3')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Next' }))

    expect(screen.getByRole('cell', { name: '101' })).toBeTruthy()
    expect(screen.queryByRole('cell', { name: '100' })).toBeNull()
  })
})
