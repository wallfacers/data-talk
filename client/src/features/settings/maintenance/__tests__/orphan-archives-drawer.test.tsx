import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { OrphanArchivesDrawer } from '../orphan-archives-drawer'
import * as maintenanceApi from '@/services/api/maintenance'

vi.mock('@/services/api/maintenance')
vi.mock('@/services/api/file-artifacts', () => ({
  discardFile: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({ t: (k: string, params?: Record<string, unknown>) => {
    if (k === 'maintenance.orphans.drawer.title') return `Orphaned Archives (${params?.n ?? 0})`
    return k
  }})
}))
vi.mock('@/stores/connection-store', () => ({
  useConnectionStore: () => ({ connections: [{ id: 'conn_1', name: 'Prod DB' }] })
}))

const files: maintenanceApi.OrphanedFileDto[] = [
  { id: 'fa_1', filename: 'orders-er.md', kind: 'er_diagram', sizeBytes: 8400, title: null, summary: null,
    orphanedFromConnection: 'prod-mysql', orphanedFromConnectionId: 'conn_x', orphanedAt: 1000, archivedAt: '2026-04-29T00:00:00Z' },
  { id: 'fa_2', filename: 'report.md', kind: 'report', sizeBytes: 32400, title: null, summary: null,
    orphanedFromConnection: 'dev-db', orphanedFromConnectionId: 'conn_y', orphanedAt: 2000, archivedAt: '2026-04-28T00:00:00Z' },
]

describe('OrphanArchivesDrawer', () => {
  it('renders files with original connection names', async () => {
    render(<OrphanArchivesDrawer files={files} onClose={vi.fn()} />)
    expect(screen.getByText('orders-er.md')).toBeInTheDocument()
    expect(screen.getByText('report.md')).toBeInTheDocument()
    expect(screen.getByText(/prod-mysql/)).toBeInTheDocument()
  })

  it('select all and batch discard triggers confirm', async () => {
    render(<OrphanArchivesDrawer files={files} onClose={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: /selectAll/ }))
    fireEvent.click(screen.getByRole('button', { name: /discardBulk/ }))

    expect(screen.getByText(/discardBulk/)).toBeInTheDocument()
  })
})
