import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { MaintenancePage } from '../maintenance-page'
import * as maintenanceApi from '@/services/api/maintenance'

vi.mock('@/services/api/maintenance')

describe('MaintenancePage', () => {
  it('renders storage overview with breakdown items', async () => {
    vi.mocked(maintenanceApi.getStorageOverview).mockResolvedValue({
      workdir: '/home/user/.data-talk',
      totalBytes: 358_000_000,
      breakdown: {
        opencodeInfra: { bytes: 286_000_000, label: 'OpenCode 基础设施' },
        sessions: { bytes: 21_000_000, label: 'Sessions' },
        workspaces: { bytes: 28_000_000, label: 'Workspaces (资产)' },
        trash: { bytes: 5_000_000, label: '_trash' },
        legacy: { bytes: 2_000_000, label: '_legacy' },
      },
      lastHousekeepingRunAt: null,
    })
    vi.mocked(maintenanceApi.getOrphanedFiles).mockResolvedValue([])

    render(<MaintenancePage />)

    await waitFor(() => {
      expect(screen.getByText('/home/user/.data-talk')).toBeInTheDocument()
      expect(screen.getByText(/OpenCode/)).toBeInTheDocument()
    })
  })

  it('shows refresh button that reloads data', async () => {
    vi.mocked(maintenanceApi.getStorageOverview).mockResolvedValue({
      workdir: '/tmp', totalBytes: 0, breakdown: {}, lastHousekeepingRunAt: null,
    })
    vi.mocked(maintenanceApi.getOrphanedFiles).mockResolvedValue([])
    render(<MaintenancePage />)
    await waitFor(() => expect(screen.getByText('/tmp')).toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: /maintenance\.storageOverview\.refresh/ }))
    expect(maintenanceApi.getStorageOverview).toHaveBeenCalledTimes(2)
  })
})
