import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { MaintenancePage } from '../maintenance-page'
import * as maintenanceApi from '@/services/api/maintenance'

vi.mock('@/services/api/maintenance')
vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({ t: (k: string, params?: Record<string, unknown>) => {
    if (k === 'maintenance.storageOverview.orphanedCount') return `Orphaned (${params?.n ?? 0})`
    return k
  }})
}))
vi.mock('@/hooks/use-toast', () => ({ useToast: () => ({ toast: vi.fn() }) }))

const qc = new QueryClient()

function wrap(ui: React.ReactElement) {
  return <QueryClientProvider client={qc}>{ui}</QueryClientProvider>
}

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
      resourceDirectories: {
        dashboards: { count: 0, sizeBytes: 0 },
        reports: { count: 0, sizeBytes: 0 },
        exports: { count: 0, sizeBytes: 0 },
        semantic: { count: 0, sizeBytes: 0 },
        uploads: { count: 0, sizeBytes: 0 },
      },
    })
    vi.mocked(maintenanceApi.getOrphanedFiles).mockResolvedValue([])

    render(wrap(<MaintenancePage />))

    await waitFor(() => {
      expect(screen.getByText('/home/user/.data-talk')).toBeInTheDocument()
      expect(screen.getByText(/OpenCode/)).toBeInTheDocument()
    })
  })

  it('shows refresh button that reloads data', async () => {
    vi.mocked(maintenanceApi.getStorageOverview).mockResolvedValue({
      workdir: '/tmp', totalBytes: 0, breakdown: {}, lastHousekeepingRunAt: null,
      resourceDirectories: {
        dashboards: { count: 0, sizeBytes: 0 },
        reports: { count: 0, sizeBytes: 0 },
        exports: { count: 0, sizeBytes: 0 },
        semantic: { count: 0, sizeBytes: 0 },
        uploads: { count: 0, sizeBytes: 0 },
      },
    })
    vi.mocked(maintenanceApi.getOrphanedFiles).mockResolvedValue([])
    render(wrap(<MaintenancePage />))
    await waitFor(() => expect(screen.getByText('/tmp')).toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: /maintenance\.storageOverview\.refresh/ }))
    expect(maintenanceApi.getStorageOverview).toHaveBeenCalledTimes(3)
  })
})
