import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { SqlResultTabs } from './sql-result-tabs'

const results = [
  {
    resultId: 'r1',
    kind: 'result_set' as const,
    title: 'Result 1',
    statementIndex: 0,
    statementText: 'select 1',
    columns: ['id'],
    rows: [[1]],
    rowCount: 1,
    executionMs: 9,
    truncated: false,
  },
  {
    resultId: 'r2',
    kind: 'error' as const,
    title: 'Error 2',
    statementIndex: 1,
    statementText: 'select * from nope',
    columns: [],
    rows: [],
    rowCount: 0,
    executionMs: 1,
    truncated: false,
    errorMessage: 'relation nope does not exist',
  },
]

describe('SqlResultTabs', () => {
  it('renders one tab button per result and marks the active one', () => {
    render(<SqlResultTabs results={results} activeResultId="r1" onSelect={() => {}} />)

    expect(screen.getByRole('tab', { name: 'Result 1' }).getAttribute('data-state')).toBe('active')
    expect(screen.getByRole('tab', { name: 'Error 2' }).getAttribute('data-state')).toBe('inactive')
  })

  it('calls onSelect when a result tab is clicked', () => {
    const onSelect = vi.fn()
    render(<SqlResultTabs results={results} activeResultId="r1" onSelect={onSelect} />)

    fireEvent.click(screen.getByRole('tab', { name: 'Error 2' }))

    expect(onSelect).toHaveBeenCalledWith('r2')
  })
})
