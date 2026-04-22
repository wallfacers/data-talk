import { fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import type { HistoryEntry } from '@/features/stage/stores/sql-workbench-store'
import { HistoryPanel } from './history-panel'

const history: HistoryEntry[] = [
  { id: 'h-1', at: 1, sql: 'select 1', status: 'ok' },
  { id: 'h-2', at: 2, sql: 'update users set active = 0', status: 'risk_blocked' },
]

describe('HistoryPanel', () => {
  it('renders history entries and appends SQL when an entry is clicked', () => {
    const onAppendSql = vi.fn()
    const onClear = vi.fn()

    render(<HistoryPanel entries={history} onAppendSql={onAppendSql} onClear={onClear} />)

    fireEvent.click(screen.getByRole('button', { name: /select 1/i }))
    expect(onAppendSql).toHaveBeenCalledWith('select 1')

    fireEvent.click(screen.getByRole('button', { name: /update users set active = 0/i }))
    expect(onAppendSql).toHaveBeenCalledWith('update users set active = 0')
  })

  it('clears the visible history list when clear is clicked', () => {
    function Harness() {
      const [entries, setEntries] = useState(history)
      return (
        <HistoryPanel
          entries={entries}
          onAppendSql={vi.fn()}
          onClear={() => setEntries([])}
        />
      )
    }

    render(<Harness />)

    fireEvent.click(screen.getByRole('button', { name: /clear history/i }))

    expect(screen.getByText(/No history yet/i)).toBeTruthy()
  })
})
