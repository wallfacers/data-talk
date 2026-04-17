import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, createRouter, RouterProvider } from '@tanstack/react-router'
import { routeTree } from '@/routeTree.gen'
import { SettingsLayout } from '../settings-layout'

const qc = new QueryClient({
  defaultOptions: { queries: { retry: false, gcTime: 0 } },
})

function renderWithRouter(initialEntries: string[] = ['/settings']) {
  const memoryHistory = createMemoryHistory({ initialEntries })
  const router = createRouter({ routeTree, history: memoryHistory })
  void SettingsLayout
  render(
    <QueryClientProvider client={qc}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  )
  return router
}

describe('SettingsLayout', () => {
  it('renders all top-level nav entries', async () => {
    renderWithRouter()
    await waitFor(() => {
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
