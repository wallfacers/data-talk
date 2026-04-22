import { render, screen, within } from '@testing-library/react'
import { fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { SqlDmlSummaryPanel } from './sql-dml-summary-panel'
import { SqlErrorResultPanel } from './sql-error-result-panel'
import { SqlResultTable } from './sql-result-table'

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    t: (key: string) =>
      ({
        'stage.queryEditor.cell.null': 'NULL',
        'stage.queryEditor.result.rowNumber': '#',
        'stage.queryEditor.result.action': 'Action',
        'stage.queryEditor.result.affectedRows': 'Affected Rows',
        'stage.queryEditor.result.duration': 'Duration',
        'stage.queryEditor.result.sql': 'SQL',
        'stage.queryEditor.result.previousPage': 'Previous',
        'stage.queryEditor.result.nextPage': 'Next',
        'stage.queryEditor.result.pageIndicator': 'Page 1 / 3',
        'stage.queryEditor.summary.rows': '3 rows · 8ms',
        'stage.queryEditor.summary.truncated': 'Top 3 rows · 8ms',
      })[key] ?? key,
  }),
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
    expect(screen.getByRole('columnheader', { name: '#' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: 'id' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: '1' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: '2' })).toBeTruthy()
    expect(screen.getByText('NULL')).toBeTruthy()
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

    expect(screen.getByRole('columnheader', { name: '#' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: 'Action' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: 'Affected Rows' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: 'Duration' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: 'SQL' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: '1' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: 'update summary' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: '2' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: '12ms' })).toBeTruthy()
  })

  it('centers the error message and hides the source sql text', () => {
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
          errorMessage: 'relation "missing_table" does not exist',
        }}
      />,
    )

    expect(screen.getByText('relation "missing_table" does not exist')).toBeTruthy()
    expect(screen.queryByText('select * from missing_table')).toBeNull()
    expect(container.firstElementChild?.className).toContain('items-center')
    expect(container.firstElementChild?.className).toContain('justify-center')
    expect(within(container.firstElementChild as HTMLElement).getByText('relation "missing_table" does not exist')).toBeTruthy()
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
