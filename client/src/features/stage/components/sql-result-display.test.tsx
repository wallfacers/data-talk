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
        'stage.queryEditor.result.viewCell': '查看完整内容',
        'stage.queryEditor.result.cellDetailTitle': '单元格内容',
        'stage.queryEditor.result.cellDetailDescription': '第 1 行 · payload · 13 字符',
        'stage.queryEditor.result.formatContent': '格式化',
        'stage.queryEditor.result.wrapContent': '自动换行',
        'stage.queryEditor.result.pageIndicator': 'Page 1 / 3',
        'stage.queryEditor.summary.rows': '3 rows · 8ms',
        'stage.queryEditor.summary.truncated': 'Top 3 rows · 8ms',
        'stage.queryEditor.result.exportScope': '导出范围',
        'stage.queryEditor.result.exportPage': '当前页',
        'stage.queryEditor.result.exportResult': '已返回结果',
        'stage.queryEditor.result.copyCsv': '复制 CSV',
        'stage.queryEditor.result.copyJson': '复制 JSON',
        'stage.queryEditor.result.downloadCsv': '下载 CSV',
        'stage.queryEditor.result.copyCsvAria': '复制当前 SQL 结果为 CSV',
        'stage.queryEditor.result.copyJsonAria': '复制当前 SQL 结果为 JSON',
        'stage.queryEditor.result.downloadCsvAria': '下载当前 SQL 结果为 CSV',
        'stage.queryEditor.result.copied': '已复制',
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

vi.mock('@/components/ui/dialog', () => ({
  Dialog: ({
    open,
    children,
    onOpenChange,
  }: { open?: boolean; children?: ReactNode; onOpenChange?: (open: boolean) => void }) => open ? (
    <div
      data-testid="dialog-root"
      tabIndex={-1}
      onMouseDown={() => onOpenChange?.(false)}
      onKeyDown={(event) => {
        if (event.key === 'Escape') onOpenChange?.(false)
      }}
    >
      {children}
    </div>
  ) : null,
  DialogContent: ({ children, className }: { children?: ReactNode; className?: string }) => (
    <div
      data-testid="cell-detail-dialog"
      className={className}
      onMouseDown={(event) => event.stopPropagation()}
    >
      {children}
    </div>
  ),
  DialogDescription: ({ children }: { children?: ReactNode }) => <p>{children}</p>,
  DialogHeader: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  DialogTitle: ({ children }: { children?: ReactNode }) => <h2>{children}</h2>,
}))

vi.mock('@/components/ui/select', () => ({
  Select: ({ children, value, onValueChange }: { children?: ReactNode; value?: string; onValueChange?: (value: string) => void }) => (
    <div data-testid="select-root" data-value={value} data-testid-select>
      {children}
      <select
        aria-label="导出范围"
        value={value}
        onChange={(event) => onValueChange?.(event.target.value)}
        data-testid="select-native"
      >
        <option value="page">当前页</option>
        <option value="result">已返回结果</option>
      </select>
    </div>
  ),
  SelectTrigger: ({ children, 'aria-label': ariaLabel }: { children?: ReactNode; 'aria-label'?: string }) => (
    <button type="button" role="combobox" aria-label={ariaLabel} data-testid="select-trigger">
      {children}
    </button>
  ),
  SelectContent: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  SelectItem: ({ children, value, onClick }: { children?: ReactNode; value: string; onClick?: () => void }) => (
    <div role="option" aria-label={String(children)} data-value={value} onClick={onClick}>
      {children}
    </div>
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

  it('aligns the vertical scrollbar with the sticky header without leaving a blank corner', () => {
    render(
      <SqlResultTable
        result={{
          resultId: 'scrollbar-header-gap-result',
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

    const viewport = screen.getByTestId('sql-result-table-scroll')
    expect(viewport.getAttribute('data-result-scrollbar')).toBe('header-offset')
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
    expect(within(menu).getByRole('button', { name: '查看完整内容' })).toBeTruthy()
    expect(within(menu).getByRole('button', { name: '复制单元格' })).toBeTruthy()
    expect(within(menu).getByRole('button', { name: '复制行' })).toBeTruthy()
    expect(within(menu).getByRole('button', { name: '复制列名' })).toBeTruthy()
  })

  it('opens full cell content from the context menu without click or Enter shortcuts', () => {
    const longText = 'first line\nsecond line\nthird line'
    render(
      <SqlResultTable
        result={{
          resultId: 'detail-result',
          kind: 'result_set',
          title: 'orders',
          statementIndex: 0,
          statementText: 'select payload from orders',
          columns: ['payload'],
          rows: [[longText]],
          rowCount: 1,
          executionMs: 8,
          truncated: false,
        }}
      />,
    )

    const cell = screen.getByRole('cell', { name: longText })
    fireEvent.click(cell)
    fireEvent.keyDown(cell, { key: 'Enter' })
    expect(screen.queryByTestId('cell-detail-dialog')).toBeNull()

    fireEvent.contextMenu(cell)
    fireEvent.click(screen.getByRole('button', { name: '查看完整内容' }))

    const dialog = screen.getByTestId('cell-detail-dialog')
    expect(within(dialog).getByRole('heading', { name: '单元格内容' })).toBeTruthy()
    expect(within(dialog).getByText('第 1 行 · payload · 13 字符')).toBeTruthy()
    expect(dialog.className).toContain('h-[min(720px,calc(100vh-4rem))]')
    expect(dialog.querySelector('[data-component="markdown-code"]')).not.toBeNull()
    expect(within(dialog).queryByRole('button', { name: '关闭单元格内容' })).toBeNull()
    expect(within(dialog).getByTestId('sql-result-cell-detail-content').textContent).toBe(longText)
  })

  it('formats JSON and XML values from the full content dialog', () => {
    const { rerender } = render(
      <SqlResultTable
        result={{
          resultId: 'json-detail-result',
          kind: 'result_set',
          title: 'orders',
          statementIndex: 0,
          statementText: 'select payload from orders',
          columns: ['payload'],
          rows: [['{"customer":{"id":1},"items":[{"sku":"A"}]}']],
          rowCount: 1,
          executionMs: 8,
          truncated: false,
        }}
      />,
    )

    fireEvent.contextMenu(screen.getByRole('cell', { name: '{"customer":{"id":1},"items":[{"sku":"A"}]}' }))
    fireEvent.click(screen.getByRole('button', { name: '查看完整内容' }))
    fireEvent.click(screen.getByRole('button', { name: '格式化' }))
    expect(screen.getByTestId('sql-result-cell-detail-content').textContent).toContain('\n  "customer"')

    rerender(
      <SqlResultTable
        result={{
          resultId: 'xml-detail-result',
          kind: 'result_set',
          title: 'orders',
          statementIndex: 0,
          statementText: 'select payload from orders',
          columns: ['payload'],
          rows: [['<order><id>1</id><status>paid</status></order>']],
          rowCount: 1,
          executionMs: 8,
          truncated: false,
        }}
      />,
    )

    fireEvent.contextMenu(screen.getByRole('cell', { name: '<order><id>1</id><status>paid</status></order>' }))
    fireEvent.click(screen.getByRole('button', { name: '查看完整内容' }))
    fireEvent.click(screen.getByRole('button', { name: '格式化' }))
    expect(screen.getByTestId('sql-result-cell-detail-content').textContent).toContain('\n  <id>')
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

  it('copies CSV for the current result page', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, { clipboard: { writeText } })

    render(
      <SqlResultTable
        result={{
          resultId: 'export-result',
          kind: 'result_set',
          title: 'orders',
          statementIndex: 0,
          statementText: 'select * from orders',
          columns: ['id', 'status'],
          rows: Array.from({ length: 105 }, (_, index) => [index + 1, index === 100 ? 'needs,quote' : 'paid']),
          rowCount: 105,
          executionMs: 8,
          truncated: false,
        }}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '复制当前 SQL 结果为 CSV' }))

    await waitFor(() => expect(writeText).toHaveBeenCalled())
    expect(writeText.mock.calls[0][0]).toContain('id,status')
    expect(writeText.mock.calls[0][0]).toContain('100,paid')
    expect(writeText.mock.calls[0][0]).not.toContain('101,"needs,quote"')
  })

  it('copies JSON for all returned rows when export scope is changed', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, { clipboard: { writeText } })

    render(
      <SqlResultTable
        result={{
          resultId: 'json-export-result',
          kind: 'result_set',
          title: 'orders',
          statementIndex: 0,
          statementText: 'select * from orders',
          columns: ['id', 'status'],
          rows: [[1, 'paid'], [2, 'pending']],
          rowCount: 2,
          executionMs: 8,
          truncated: false,
        }}
      />,
    )

    const nativeSelect = screen.getByTestId('select-native') as HTMLSelectElement
    fireEvent.change(nativeSelect, { target: { value: 'result' } })

    fireEvent.click(screen.getByRole('button', { name: '复制当前 SQL 结果为 JSON' }))

    await waitFor(() => expect(writeText).toHaveBeenCalledWith(JSON.stringify([
      { id: 1, status: 'paid' },
      { id: 2, status: 'pending' },
    ], null, 2)))
  })
})
