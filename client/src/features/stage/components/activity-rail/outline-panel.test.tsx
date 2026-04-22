import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { SqlOutlineStatement } from '@/features/stage/utils/parse-sql-outline'
import { OutlinePanel } from './outline-panel'

const statements: SqlOutlineStatement[] = [
  { line: 2, kind: 'SELECT', summary: 'select 1', highRiskHint: false },
  { line: 5, kind: 'UPDATE', summary: 'update users set active = 0', highRiskHint: true },
]

describe('OutlinePanel', () => {
  it('renders the parsed statements and jumps to a line on click', () => {
    const onJumpToLine = vi.fn()

    render(<OutlinePanel statements={statements} onJumpToLine={onJumpToLine} />)

    fireEvent.click(screen.getByRole('button', { name: /Ln 5 UPDATE/i }))

    expect(onJumpToLine).toHaveBeenCalledWith(5)
  })
})
