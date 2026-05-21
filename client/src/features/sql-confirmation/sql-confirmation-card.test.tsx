import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
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
      {...props}
    />,
  )

describe('SqlConfirmationCard', () => {
  it('renders the panel root with sql-risk-panel testid for E2E targeting', () => {
    renderCard()
    expect(screen.getByTestId('sql-risk-panel')).toBeInTheDocument()
  })

  it('does not render a duplicate L2/L3 title inside the card body (badge lives in the dialog header)', () => {
    const { rerender } = renderCard()
    // Card should NOT contain its own "Bounded mutation" heading — that label belongs in the dialog header badge.
    expect(screen.queryByText(/^Bounded mutation$/)).not.toBeInTheDocument()
    rerender(
      <SqlConfirmationCard
        risk={{ level: 'L3', reason: 'drop_table', affectedObjects: ['temp_log'] }}
        sqlPreview="DROP TABLE temp_log"
      />,
    )
    expect(screen.queryByText(/^Destructive operation$/)).not.toBeInTheDocument()
  })

  it('renders L2 body copy with affected objects interpolated', () => {
    renderCard()
    expect(screen.getByText(/will modify data in orders/i)).toBeInTheDocument()
    expect(screen.queryByText(/cannot be undone/i)).not.toBeInTheDocument()
  })

  it('renders L3 body copy plus the irreversibility warning', () => {
    renderCard({
      risk: { level: 'L3', reason: 'drop_table', affectedObjects: ['temp_log'] },
      sqlPreview: 'DROP TABLE temp_log',
    })
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

  it('renders the amber top band for L2 risk', () => {
    renderCard()
    const panel = screen.getByTestId('sql-risk-panel')
    const band = panel.querySelector('[aria-hidden]')
    expect(band).not.toBeNull()
    expect(band!.className).toMatch(/bg-\[var\(--dt-accent-warn\)\]/)
    expect(band!.className).not.toMatch(/bg-\[var\(--dt-status-danger\)\]/)
  })

  it('renders the danger top band for L3 risk', () => {
    renderCard({
      risk: { level: 'L3', reason: 'drop_table', affectedObjects: ['temp_log'] },
      sqlPreview: 'DROP TABLE temp_log',
    })
    const panel = screen.getByTestId('sql-risk-panel')
    const band = panel.querySelector('[aria-hidden]')
    expect(band).not.toBeNull()
    expect(band!.className).toMatch(/bg-\[var\(--dt-status-danger\)\]/)
    expect(band!.className).not.toMatch(/bg-\[var\(--dt-accent-warn\)\]/)
  })

  it('renders em-dash placeholder when affected objects is empty', () => {
    renderCard({
      risk: { level: 'L2', reason: 'update_no_where', affectedObjects: [] },
    })
    // Body interpolates "—" when no affected objects are listed
    expect(screen.getByText(/will modify data in —/i)).toBeInTheDocument()
  })
})
