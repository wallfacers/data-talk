import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ErEmptyState } from '../ErEmptyState'

describe('<ErEmptyState>', () => {
  it('renders dialect unsupported title with no button and reason-tagged icon', () => {
    render(<ErEmptyState reason="dialect_unsupported" />)

    expect(screen.getByText(/Dialect not supported|不支持的数据库方言/)).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.getByTestId('er-empty-icon')).toHaveAttribute('data-er-empty-reason', 'dialect_unsupported')
  })

  it('renders empty selection title with no button and reason-tagged icon', () => {
    render(<ErEmptyState reason="empty_selection" />)

    expect(screen.getByRole('heading', { name: /Nothing selected|尚未选择表/ })).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.getByTestId('er-empty-icon')).toHaveAttribute('data-er-empty-reason', 'empty_selection')
  })

  it('renders empty designer title and calls the CTA once when provided', () => {
    const onAction = vi.fn()
    render(<ErEmptyState reason="empty_designer" actionLabel="Add table" onAction={onAction} />)

    expect(screen.getByText(/Empty designer|空白 ER 设计稿/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Add table' }))

    expect(onAction).toHaveBeenCalledTimes(1)
  })

  it('renders oversized title and reason-tagged icon', () => {
    render(<ErEmptyState reason="oversized" />)

    expect(screen.getByText(/Diagram too large|ER 图表过大/)).toBeInTheDocument()
    expect(screen.getByTestId('er-empty-icon')).toHaveAttribute('data-er-empty-reason', 'oversized')
  })

  it('keeps Oracle-specific unsupported body copy', () => {
    render(<ErEmptyState reason="dialect_unsupported" dialect="oracle" />)

    expect(screen.getByText(/Oracle/i)).toBeInTheDocument()
  })

  it('keeps SQLite-specific unsupported body copy', () => {
    render(<ErEmptyState reason="dialect_unsupported" dialect="sqlite" />)

    expect(screen.getByText(/SQLite/i)).toBeInTheDocument()
  })
})
