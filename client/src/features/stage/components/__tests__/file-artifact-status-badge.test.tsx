import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { FileArtifactStatusBadge } from '../file-artifact-status-badge'

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    language: 'en-US',
    t: (key: string) => key,
  }),
}))

describe('FileArtifactStatusBadge', () => {
  it('renders 📄 for temporary with text-muted token', () => {
    render(<FileArtifactStatusBadge status="temporary" />)
    const badge = screen.getByTestId('file-artifact-status-badge')
    expect(badge).toHaveTextContent('📄')
    expect(badge.className).toContain('text-muted')
  })

  it('renders 📌 for candidate with status-warningSurface token + accent-warn left border', () => {
    render(<FileArtifactStatusBadge status="candidate" />)
    const badge = screen.getByTestId('file-artifact-status-badge')
    expect(badge).toHaveTextContent('📌')
    expect(badge.className).toContain('bg-status-warningSurface')
    expect(badge.className).toContain('border-l-accent-warn')
  })

  it('renders 📦 for archived with bg-panel token', () => {
    render(<FileArtifactStatusBadge status="archived" />)
    const badge = screen.getByTestId('file-artifact-status-badge')
    expect(badge).toHaveTextContent('📦')
    expect(badge.className).toContain('bg-panel')
  })

  it('renders nothing for discarded (hidden visual)', () => {
    const { container } = render(<FileArtifactStatusBadge status="discarded" />)
    expect(container).toBeEmptyDOMElement()
  })

  it('exposes aria-label for accessibility (icon + color double channel)', () => {
    render(<FileArtifactStatusBadge status="candidate" />)
    expect(screen.getByTestId('file-artifact-status-badge')).toHaveAttribute('aria-label', 'files.status.candidate')
  })
})
