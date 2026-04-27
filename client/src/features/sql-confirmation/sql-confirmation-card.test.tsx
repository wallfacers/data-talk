import { describe, expect, it, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { translateMessage } from '@/i18n/messages'
import { SqlConfirmationCard } from './sql-confirmation-card'

// Use the real translation function for en-US to validate i18n keys exist
const t = (key: Parameters<typeof translateMessage>[1], values?: Record<string, string | number>) =>
  translateMessage('en-US', key, values)

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({ t: (key: string, values?: Record<string, string | number>) => t(key as never, values) }),
}))

const renderCard = (props: Partial<React.ComponentProps<typeof SqlConfirmationCard>> = {}) =>
  render(
    <SqlConfirmationCard
      risk={{ level: 'L2', reason: 'update_with_where', affectedObjects: ['orders'] }}
      sqlPreview="UPDATE orders SET status = 'paid' WHERE id = 1"
      onCancel={() => {}}
      onExecute={() => {}}
      {...props}
    />,
  )

describe('SqlConfirmationCard', () => {
  it('renders L2 visuals with bounded-mutation copy', () => {
    renderCard()
    expect(screen.getByText('Bounded mutation')).toBeInTheDocument()
    expect(screen.getByText(/will modify data in orders/i)).toBeInTheDocument()
    expect(screen.queryByText(/cannot be undone/i)).not.toBeInTheDocument()
  })

  it('renders L3 visuals with irreversibility warning', () => {
    renderCard({
      risk: { level: 'L3', reason: 'drop_table', affectedObjects: ['temp_log'] },
      sqlPreview: 'DROP TABLE temp_log',
    })
    expect(screen.getByText('Destructive operation')).toBeInTheDocument()
    expect(screen.getByText(/will permanently affect temp_log/i)).toBeInTheDocument()
    expect(screen.getByText('This action cannot be undone.')).toBeInTheDocument()
  })

  it('renders the SQL preview as a read-only code block', () => {
    renderCard()
    expect(screen.getByText(/UPDATE orders SET status/)).toBeInTheDocument()
  })

  it('lists each affected object', () => {
    renderCard({
      risk: { level: 'L2', reason: 'multi', affectedObjects: ['orders', 'order_items'] },
    })
    expect(screen.getByText('orders')).toBeInTheDocument()
    expect(screen.getByText('order_items')).toBeInTheDocument()
  })

  it('initial focus lands on Cancel to prevent accidental destructive activation', () => {
    renderCard({ risk: { level: 'L3', reason: 'drop_table', affectedObjects: ['t'] } })
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus()
  })

  it('calls onCancel and onExecute exactly once each on click', () => {
    const onCancel = vi.fn()
    const onExecute = vi.fn()
    renderCard({ onCancel, onExecute })
    fireEvent.click(screen.getByRole('button', { name: 'Execute' }))
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onExecute).toHaveBeenCalledTimes(1)
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('shows Executing... and disables both buttons while pending', () => {
    renderCard({ pending: true })
    expect(screen.getByRole('button', { name: 'Executing…' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
  })
})
