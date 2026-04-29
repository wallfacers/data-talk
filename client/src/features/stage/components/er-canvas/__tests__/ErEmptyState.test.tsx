import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ErEmptyState } from '../ErEmptyState'

describe('<ErEmptyState>', () => {
  it('renders Oracle unsupported message', () => {
    render(<ErEmptyState reason="dialect_unsupported" dialect="oracle" />)
    expect(screen.getByText(/Oracle/)).toBeInTheDocument()
  })

  it('renders SQLite frontend-incomplete message', () => {
    render(<ErEmptyState reason="dialect_unsupported" dialect="sqlite" />)
    expect(screen.getByText(/SQLite/i)).toBeInTheDocument()
    expect(screen.getByText(/query_editor/i)).toBeInTheDocument()
  })

  it('renders generic empty state when no tables in selection', () => {
    render(<ErEmptyState reason="empty_selection" />)
    expect(screen.getByText(/尚未选择表/)).toBeInTheDocument()
  })
})
