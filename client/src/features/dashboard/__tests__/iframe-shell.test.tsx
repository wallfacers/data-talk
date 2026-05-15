import { render, screen, waitFor, act } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('../services/dashboard-api', () => ({
  fetchDashboardHtml: vi.fn().mockResolvedValue('<html><body>Hello</body></html>'),
}))

import { DashboardIframeShell } from '../iframe-shell'
import { fetchDashboardHtml } from '../services/dashboard-api'

const mockedFetchHtml = vi.mocked(fetchDashboardHtml)

describe('DashboardIframeShell', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedFetchHtml.mockResolvedValue('<html><body>Hello</body></html>')
  })

  it('renders loading then iframe when html present', async () => {
    render(<DashboardIframeShell dashboardId="dash_x" />)
    expect(screen.getByRole('status')).toBeInTheDocument()
    // Flush the fetch promise and React re-render
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50))
    })
    expect(mockedFetchHtml).toHaveBeenCalledWith('dash_x')
    await waitFor(() => {
      const iframe = document.querySelector('iframe')
      expect(iframe).toBeInTheDocument()
    }, { timeout: 3000 })
  })

  it('renders empty hint when html is null (404)', async () => {
    mockedFetchHtml.mockResolvedValue(null)
    render(<DashboardIframeShell dashboardId="dash_v1" />)
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50))
    })
    await waitFor(() => expect(screen.getByText(/v1 dashboard/)).toBeInTheDocument(), { timeout: 3000 })
  })

  it('flips to ready on postMessage', async () => {
    render(<DashboardIframeShell dashboardId="dash_x" />)
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50))
    })
    await waitFor(() => expect(document.querySelector('iframe')).toBeInTheDocument(), { timeout: 3000 })
    act(() => {
      window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready', jsonHash: 'sha256:abc' } }))
    })
    await waitFor(() => {
      expect(document.querySelector('iframe')?.getAttribute('data-status')).toBe('ready')
    }, { timeout: 3000 })
  })

  it('keeps loader overlay above iframe until ready signal', async () => {
    render(<DashboardIframeShell dashboardId="dash_overlay" />)
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50))
    })
    // After HTML arrives the iframe is mounted (so CDN/JS load can start in
    // parallel) — but the loader must still be present, otherwise the user
    // sees a long white screen until the iframe internals post 'ready'.
    await waitFor(() => expect(document.querySelector('iframe')).toBeInTheDocument(), { timeout: 3000 })
    expect(document.querySelector('iframe')?.getAttribute('data-status')).toBe('loading')
    expect(screen.getByRole('status')).toBeInTheDocument()
  })

  it('removes loader overlay after ready postMessage', async () => {
    render(<DashboardIframeShell dashboardId="dash_overlay_ready" />)
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50))
    })
    await waitFor(() => expect(document.querySelector('iframe')).toBeInTheDocument(), { timeout: 3000 })
    act(() => {
      window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready', jsonHash: null } }))
    })
    await waitFor(() => {
      expect(screen.queryByRole('status')).not.toBeInTheDocument()
    }, { timeout: 3000 })
    expect(document.querySelector('iframe')).toBeInTheDocument()
  })
})
