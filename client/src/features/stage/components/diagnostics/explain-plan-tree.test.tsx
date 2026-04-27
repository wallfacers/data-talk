import { describe, expect, it } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ExplainPlanTree } from './explain-plan-tree'
import type { ExplainNode } from '@/features/stage/types/diagnostics'

const fullScanNode: ExplainNode = {
  operation: 'Seq Scan',
  table: 'orders',
  scanType: 'FULL_SCAN',
  rows: 5000,
  cost: 12.5,
  children: [],
}

const indexScanNode: ExplainNode = {
  operation: 'Index Scan',
  table: 'users',
  scanType: 'INDEX_SCAN',
  rows: 1,
  cost: 0.3,
  children: [],
}

const parentNode: ExplainNode = {
  operation: 'Hash Join',
  scanType: 'OTHER',
  rows: 200,
  cost: 45.0,
  children: [fullScanNode, indexScanNode],
}

describe('ExplainPlanTree', () => {
  it('renders full scan node with danger styling', () => {
    const { container } = render(<ExplainPlanTree nodes={[fullScanNode]} />)
    const row = container.querySelector('[role="treeitem"]')!
    expect(row.querySelector('div')!.className).toContain('bg-destructive')
    expect(screen.getByText('Seq Scan')).toBeTruthy()
  })

  it('renders index scan node with success styling', () => {
    const { container } = render(<ExplainPlanTree nodes={[indexScanNode]} />)
    const row = container.querySelector('[role="treeitem"]')!
    expect(row.querySelector('div')!.className).toContain('bg-green')
    expect(screen.getByText('Index Scan')).toBeTruthy()
  })

  it('renders empty state when no nodes', () => {
    render(<ExplainPlanTree nodes={[]} />)
    expect(screen.getByText('No plan nodes available.')).toBeTruthy()
  })

  it('renders rows in mono font with locale formatting', () => {
    render(<ExplainPlanTree nodes={[fullScanNode]} />)
    const rowText = screen.getByText(/5,000 rows/)
    expect(rowText).toBeTruthy()
    expect(rowText.className).toContain('font-mono')
  })

  it('renders children and allows collapse/expand', () => {
    render(<ExplainPlanTree nodes={[parentNode]} />)
    // Parent and children all visible initially
    expect(screen.getByText('Hash Join')).toBeTruthy()
    expect(screen.getByText('Seq Scan')).toBeTruthy()
    expect(screen.getByText('Index Scan')).toBeTruthy()

    // Collapse
    const collapseBtn = screen.getAllByLabelText('Collapse')[0]
    fireEvent.click(collapseBtn)

    // Children should be hidden
    expect(screen.queryByText('Seq Scan')).toBeNull()
    expect(screen.queryByText('Index Scan')).toBeNull()

    // Expand
    const expandBtn = screen.getAllByLabelText('Expand')[0]
    fireEvent.click(expandBtn)
    expect(screen.getByText('Seq Scan')).toBeTruthy()
  })

  it('renders cost when present', () => {
    render(<ExplainPlanTree nodes={[fullScanNode]} />)
    expect(screen.getByText(/cost 12\.5/)).toBeTruthy()
  })
})
