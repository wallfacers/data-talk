import { describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { SqlConfirmationCard } from '@/features/sql-confirmation/sql-confirmation-card'
import { translateMessage } from '@/i18n/messages'
import { cn } from '@/lib/utils'

const tEn = (key: Parameters<typeof translateMessage>[1], values?: Record<string, string | number>) =>
  translateMessage('en-US', key, values)

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({ t: (key: string, values?: Record<string, string | number>) => tEn(key as never, values) }),
}))

// Mirrors the JSX block in sql-workbench-tab.tsx that renders the full risk-confirmation dialog.
function ConfirmationDialogFixture({
  level,
  pending = false,
  showInvalid = false,
}: {
  level: 'L2' | 'L3'
  pending?: boolean
  showInvalid?: boolean
}) {
  const t = (k: Parameters<typeof translateMessage>[1], v?: Record<string, string | number>) =>
    translateMessage('en-US', k, v)
  const isL3 = level === 'L3'
  return (
    <AlertDialog open>
      <AlertDialogContent data-testid="sql-confirmation-dialog">
        <AlertDialogHeader>
          <div className="flex flex-wrap items-center gap-2">
            <AlertDialogTitle>{t('stage.queryEditor.confirmation.title')}</AlertDialogTitle>
            <Badge
              variant={isL3 ? 'destructive' : 'secondary'}
              className={cn(
                !isL3
                  && 'bg-[var(--dt-accent-warn-surface)] text-[var(--dt-accent-warn)] border-[color-mix(in_srgb,var(--dt-accent-warn)_30%,transparent)]',
              )}
            >
              {isL3 ? t('sqlConfirmation.l3.title') : t('sqlConfirmation.l2.title')}
            </Badge>
          </div>
        </AlertDialogHeader>
        <SqlConfirmationCard
          risk={{
            level,
            reason: isL3 ? 'drop_table' : 'update_with_where',
            affectedObjects: isL3 ? ['temp_log'] : ['orders'],
          }}
          sqlPreview={isL3 ? 'DROP TABLE temp_log' : "UPDATE orders SET status='paid' WHERE id=1"}
        />
        {showInvalid && (
          <p data-testid="sql-confirmation-invalid-message" className="text-sm text-[var(--dt-status-danger)]">
            Current SQL risk is L3, but acked risk is L2. Please review again.
          </p>
        )}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={pending}>{t('sqlConfirmation.cancel')}</AlertDialogCancel>
          <AlertDialogAction
            variant={isL3 ? 'destructive' : 'warning'}
            disabled={pending}
          >
            {pending ? t('sqlConfirmation.executing') : t('sqlConfirmation.execute')}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}

describe('SQL confirmation dialog (parent + card integration)', () => {
  it('renders exactly one alert-dialog content layer (no nested card)', () => {
    render(<ConfirmationDialogFixture level="L2" />)
    const dialogs = screen.getAllByTestId('sql-confirmation-dialog')
    expect(dialogs).toHaveLength(1)
    // Card root should NOT itself be a separate dialog container.
    const panel = screen.getByTestId('sql-risk-panel')
    expect(panel).toBeInTheDocument()
    expect(panel.getAttribute('data-slot')).not.toBe('alert-dialog-content')
  })

  it('renders title and badge inline in the header (single title structure)', () => {
    render(<ConfirmationDialogFixture level="L2" />)
    const title = screen.getByText('Confirm execution')
    const badge = screen.getByText('Bounded mutation')
    expect(title).toBeInTheDocument()
    expect(badge).toBeInTheDocument()
    // Both should share a flex parent (the wrapping <div>)
    expect(title.parentElement).toBe(badge.parentElement)
    expect(title.parentElement?.className).toMatch(/flex/)
  })

  it('L3 uses destructive Badge variant and danger top band', () => {
    render(<ConfirmationDialogFixture level="L3" />)
    expect(screen.getByText('Destructive operation')).toBeInTheDocument()
    const panel = screen.getByTestId('sql-risk-panel')
    const band = panel.querySelector('[aria-hidden]')
    expect(band?.className).toMatch(/bg-\[var\(--dt-status-danger\)\]/)
    expect(screen.getByText('This action cannot be undone.')).toBeInTheDocument()
  })

  it('L2 uses amber Badge styling and amber top band, no irreversibility text', () => {
    render(<ConfirmationDialogFixture level="L2" />)
    const badge = screen.getByText('Bounded mutation')
    expect(badge.className).toMatch(/bg-\[var\(--dt-accent-warn-surface\)\]/)
    expect(badge.className).toMatch(/text-\[var\(--dt-accent-warn\)\]/)
    const panel = screen.getByTestId('sql-risk-panel')
    const band = panel.querySelector('[aria-hidden]')
    expect(band?.className).toMatch(/bg-\[var\(--dt-accent-warn\)\]/)
    expect(screen.queryByText('This action cannot be undone.')).not.toBeInTheDocument()
  })

  it('Cancel and Execute buttons render inside AlertDialogFooter (not inside the risk panel)', () => {
    render(<ConfirmationDialogFixture level="L3" />)
    const footer = document.querySelector('[data-slot="alert-dialog-footer"]')
    expect(footer).not.toBeNull()
    const panel = screen.getByTestId('sql-risk-panel')

    const cancelBtn = screen.getByRole('button', { name: 'Cancel' })
    const executeBtn = screen.getByRole('button', { name: 'Execute' })

    expect(footer!.contains(cancelBtn)).toBe(true)
    expect(footer!.contains(executeBtn)).toBe(true)
    expect(panel.contains(cancelBtn)).toBe(false)
    expect(panel.contains(executeBtn)).toBe(false)
  })

  it('Execute button uses destructive (solid red) variant for L3', () => {
    render(<ConfirmationDialogFixture level="L3" />)
    const executeBtn = screen.getByRole('button', { name: 'Execute' })
    // Upgraded destructive: solid bg-destructive + text-white (no /10 transparency)
    expect(executeBtn.className).toMatch(/bg-destructive\b/)
    expect(executeBtn.className).toMatch(/text-white/)
  })

  it('Execute button uses warning (solid amber) variant for L2', () => {
    render(<ConfirmationDialogFixture level="L2" />)
    const executeBtn = screen.getByRole('button', { name: 'Execute' })
    // New warning variant: solid amber + white text — visual weight between default and destructive
    expect(executeBtn.className).toMatch(/bg-\[var\(--dt-accent-warn\)\]/)
    expect(executeBtn.className).toMatch(/text-white/)
    expect(executeBtn.className).not.toMatch(/bg-destructive/)
    expect(executeBtn.className).not.toMatch(/bg-primary\b/)
  })

  it('invalid message renders between the risk panel and the footer', () => {
    render(<ConfirmationDialogFixture level="L3" showInvalid />)
    const dialog = document.querySelector('[data-testid="sql-confirmation-dialog"]')!
    const children = Array.from(dialog.children) as HTMLElement[]
    const panelIdx = children.findIndex((c) => c.getAttribute('data-testid') === 'sql-risk-panel')
    const invalidIdx = children.findIndex((c) => c.getAttribute('data-testid') === 'sql-confirmation-invalid-message')
    const footerIdx = children.findIndex((c) => c.getAttribute('data-slot') === 'alert-dialog-footer')
    expect(panelIdx).toBeGreaterThanOrEqual(0)
    expect(invalidIdx).toBeGreaterThan(panelIdx)
    expect(footerIdx).toBeGreaterThan(invalidIdx)
  })

  it('pending state disables both buttons and switches Execute label to Executing…', () => {
    render(<ConfirmationDialogFixture level="L3" pending />)
    const cancelBtn = screen.getByRole('button', { name: 'Cancel' })
    const executeBtn = screen.getByRole('button', { name: 'Executing…' })
    expect(cancelBtn).toBeDisabled()
    expect(executeBtn).toBeDisabled()
  })

  it('preserves all three e2e-relevant testids', () => {
    render(<ConfirmationDialogFixture level="L2" showInvalid />)
    expect(screen.getByTestId('sql-confirmation-dialog')).toBeInTheDocument()
    expect(screen.getByTestId('sql-risk-panel')).toBeInTheDocument()
    expect(screen.getByTestId('sql-confirmation-invalid-message')).toBeInTheDocument()
  })

  it('SQL preview and affected objects remain readable text inside the panel', () => {
    render(<ConfirmationDialogFixture level="L2" />)
    const panel = screen.getByTestId('sql-risk-panel')
    expect(within(panel).getByText(/UPDATE orders SET status/)).toBeInTheDocument()
    expect(within(panel).getByText('orders')).toBeInTheDocument()
    expect(within(panel).getByText(/will modify data in orders/i)).toBeInTheDocument()
  })

  it('SQL preview block uses amber surface for L2 (matches Badge & top band)', () => {
    render(<ConfirmationDialogFixture level="L2" />)
    const panel = screen.getByTestId('sql-risk-panel')
    const pre = panel.querySelector('pre')!
    expect(pre.className).toMatch(/bg-\[var\(--dt-accent-warn-surface\)\]/)
    expect(pre.className).not.toMatch(/bg-\[var\(--dt-status-danger-surface\)\]/)
  })

  it('SQL preview block uses danger surface for L3 (matches Badge & top band)', () => {
    render(<ConfirmationDialogFixture level="L3" />)
    const panel = screen.getByTestId('sql-risk-panel')
    const pre = panel.querySelector('pre')!
    expect(pre.className).toMatch(/bg-\[var\(--dt-status-danger-surface\)\]/)
    expect(pre.className).not.toMatch(/bg-\[var\(--dt-accent-warn-surface\)\]/)
  })

  it('AlertDialogFooter has neither top border nor muted background by default', () => {
    render(<ConfirmationDialogFixture level="L2" />)
    const footer = document.querySelector('[data-slot="alert-dialog-footer"]')!
    // Footer is now stripped at the primitive level — no need for per-instance overrides.
    expect(footer.className).not.toMatch(/border-t\b/)
    expect(footer.className).not.toMatch(/bg-muted/)
  })
})
