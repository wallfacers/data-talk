import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { DiagnosticsPanel, type DiagnosticsPanelEntry } from './diagnostics-panel'
import { translateMessage } from '@/i18n/messages'

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    language: 'en-US',
    setLanguage: vi.fn(),
    t: (key: Parameters<typeof translateMessage>[1], values?: Record<string, string | number>) =>
      translateMessage('en-US', key, values),
  }),
}))

describe('DiagnosticsPanel', () => {
  it('shows empty state when no entry', () => {
    render(<DiagnosticsPanel entry={null} onOpenInWorkbench={() => {}} />)

    expect(screen.getByText(/run explain to view the execution plan/i)).toBeInTheDocument()
  })

  it('shows sql snippet and warning count when entry present', () => {
    const entry: DiagnosticsPanelEntry = {
      sqlSnippet: 'SELECT * FROM users WHERE email = ?',
      warningCount: 3,
      recommendationCount: 1,
    }

    render(<DiagnosticsPanel entry={entry} onOpenInWorkbench={() => {}} />)

    expect(screen.getByText(/SELECT \* FROM users WHERE email = \?/)).toBeInTheDocument()
    expect(screen.getByText(/3 warning\(s\)/)).toBeInTheDocument()
    expect(screen.getByText(/1 recommendation\(s\)/)).toBeInTheDocument()
  })

  it('truncates sql snippet longer than 60 characters', () => {
    const longSql = 'SELECT u.id, u.name, u.email, o.order_id FROM users u JOIN orders o ON u.id = o.user_id WHERE u.active = 1'
    const entry: DiagnosticsPanelEntry = {
      sqlSnippet: longSql,
      warningCount: 0,
      recommendationCount: 0,
    }

    render(<DiagnosticsPanel entry={entry} onOpenInWorkbench={() => {}} />)

    expect(screen.getByText(/SELECT u\.id, u\.name, u\.email, o\.order_id FROM users u JOIN o\.\.\./)).toBeInTheDocument()
  })

  it('renders Open in Workbench button', () => {
    const entry: DiagnosticsPanelEntry = {
      sqlSnippet: 'SELECT 1',
      warningCount: 0,
      recommendationCount: 0,
    }

    render(<DiagnosticsPanel entry={entry} onOpenInWorkbench={() => {}} />)

    expect(screen.getByRole('button', { name: /open in workbench/i })).toBeInTheDocument()
  })
})
