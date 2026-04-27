import { describe, expect, it } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { DiagnosticsTab, type DiagnosticsTabPayload } from './diagnostics-tab'

const basePayload: DiagnosticsTabPayload = {
  sql: 'SELECT * FROM orders WHERE user_id = 1',
  connectionName: 'prod-mysql',
  dialect: 'MySQL',
  plan: [
    {
      operation: 'Index Scan',
      table: 'orders',
      scanType: 'INDEX_SCAN',
      rows: 1,
      cost: 0.35,
      children: [],
    },
  ],
  recommendations: [
    {
      table: 'orders',
      columns: ['user_id'],
      indexType: 'BTREE',
      impact: 'HIGH',
      rationale: 'Index on user_id to speed up lookup.',
    },
  ],
  rawExplainText: '-> Index Scan on orders  (cost=0.35 rows=1)',
}

describe('DiagnosticsTab', () => {
  it('shows loading state', () => {
    render(<DiagnosticsTab payload={{ ...basePayload, loading: true }} />)
    expect(screen.getByText(/Running EXPLAIN/)).toBeTruthy()
  })

  it('shows error state', () => {
    render(<DiagnosticsTab payload={{ ...basePayload, error: 'Connection refused' }} />)
    expect(screen.getByText('Connection refused')).toBeTruthy()
  })

  it('renders plan and recommendations sections', () => {
    render(<DiagnosticsTab payload={basePayload} />)
    expect(screen.getByText('Execution Plan')).toBeTruthy()
    expect(screen.getByText('Index Recommendations')).toBeTruthy()
    expect(screen.getByText('Index Scan')).toBeTruthy()
    expect(screen.getByText('HIGH')).toBeTruthy()
  })

  it('expands raw EXPLAIN on click', () => {
    render(<DiagnosticsTab payload={basePayload} />)

    // Raw text not visible initially
    expect(screen.queryByText(/Index Scan on orders/)).toBeNull()

    // Click to expand
    fireEvent.click(screen.getByLabelText('Toggle raw EXPLAIN output'))
    expect(screen.getByText(/Index Scan on orders/)).toBeTruthy()
  })

  it('renders connection chip and dialect badge', () => {
    render(<DiagnosticsTab payload={basePayload} />)
    expect(screen.getByText('prod-mysql')).toBeTruthy()
    expect(screen.getByText('MySQL')).toBeTruthy()
  })

  it('truncates SQL snippet to 80 characters', () => {
    const longSql = 'SELECT ' + 'x'.repeat(90)
    render(<DiagnosticsTab payload={{ ...basePayload, sql: longSql }} />)
    const snippet = screen.getByText(new RegExp('^SELECT x{73}…$'))
    expect(snippet).toBeTruthy()
  })

  it('does not render raw EXPLAIN section when no rawExplainText', () => {
    render(
      <DiagnosticsTab payload={{ ...basePayload, rawExplainText: undefined }} />,
    )
    expect(screen.queryByLabelText('Toggle raw EXPLAIN output')).toBeNull()
  })
})
