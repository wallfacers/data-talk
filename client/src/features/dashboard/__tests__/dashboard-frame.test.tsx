import { render, screen, waitFor, act } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('../services/dashboard-api', () => ({
  fetchDashboardHtml: vi.fn().mockResolvedValue('<html><body>Hello</body></html>'),
}))

import { DashboardFrame } from '../dashboard-frame'
import { fetchDashboardHtml } from '../services/dashboard-api'

const mockedFetchHtml = vi.mocked(fetchDashboardHtml)

describe('DashboardFrame', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedFetchHtml.mockResolvedValue('<html><body>Hello</body></html>')
  })

  it('renders loading then iframe when html present', async () => {
    render(<DashboardFrame dashboardId="dash_x" />)
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
    render(<DashboardFrame dashboardId="dash_v1" />)
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50))
    })
    await waitFor(() => expect(screen.getByText(/not found/)).toBeInTheDocument(), { timeout: 3000 })
  })

  it('flips to ready on postMessage', async () => {
    render(<DashboardFrame dashboardId="dash_x" />)
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
    render(<DashboardFrame dashboardId="dash_overlay" />)
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50))
    })
    await waitFor(() => expect(document.querySelector('iframe')).toBeInTheDocument(), { timeout: 3000 })
    expect(document.querySelector('iframe')?.getAttribute('data-status')).toBe('loading')
    expect(screen.getByRole('status')).toBeInTheDocument()
  })

  it('removes loader overlay after ready postMessage', async () => {
    render(<DashboardFrame dashboardId="dash_overlay_ready" />)
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

  it('reloads iframe when version prop changes', async () => {
    const { rerender } = render(<DashboardFrame dashboardId="dash_x" version={1} />)
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50))
    })
    await waitFor(() => expect(document.querySelector('iframe')).toBeInTheDocument(), { timeout: 3000 })

    expect(mockedFetchHtml).toHaveBeenCalledTimes(1)
    rerender(<DashboardFrame dashboardId="dash_x" version={2} />)
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50))
    })
    expect(mockedFetchHtml).toHaveBeenCalledTimes(2)
  })
})
