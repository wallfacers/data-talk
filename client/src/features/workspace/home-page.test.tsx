import { render } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { HomePage } from './home-page'

vi.mock('@/features/actions/use-bootstrap-actions', () => ({
  useBootstrapActions: vi.fn(),
}))

vi.mock('@/features/session/session-canvas', () => ({
  SessionCanvas: () => <div data-testid="session-canvas" />,
}))

vi.mock('./components/app-sidebar', () => ({
  AppSidebar: () => <aside data-testid="app-sidebar" />,
}))

describe('HomePage', () => {
  it('removes the inset main border so the workbench embeds without a doubled page frame', () => {
    const { container } = render(<HomePage />)

    const inset = container.querySelector('[data-slot="sidebar-inset"]')
    expect(inset?.className).toContain('md:border-0!')
  })
})
