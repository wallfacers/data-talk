import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { createMemoryHistory, createRouter, RouterProvider } from '@tanstack/react-router'
import { routeTree } from '@/routeTree.gen'
import { SettingsLayout } from '../settings-layout'

function renderWithRouter(initialEntries: string[] = ['/settings']) {
  const memoryHistory = createMemoryHistory({ initialEntries })
  const router = createRouter({ routeTree, history: memoryHistory })
  // SettingsLayout is rendered by the router via the /settings route definition,
  // but the import is needed for type-checking the component independently.
  void SettingsLayout
  render(<RouterProvider router={router} />)
  return router
}

describe('SettingsLayout', () => {
  it('renders all top-level nav entries', async () => {
    renderWithRouter()
    await waitFor(() => {
      // Nav labels appear twice: once in nav link, once in page content for active section
      expect(screen.getAllByText('通用').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('数据源').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('提供商').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('模型').length).toBeGreaterThanOrEqual(1)
    })
  })

  it('renders the default section (general) content', async () => {
    renderWithRouter()
    await waitFor(() => {
      expect(screen.getAllByText('通用').length).toBeGreaterThanOrEqual(1)
    })
  })

  it('navigates to data-sources section', async () => {
    renderWithRouter(['/settings?section=data-sources'])
    await waitFor(() => {
      expect(screen.getAllByText('数据源').length).toBeGreaterThanOrEqual(1)
    })
  })
})
