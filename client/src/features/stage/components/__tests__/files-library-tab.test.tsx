import { describe, expect, it, vi, beforeEach } from 'vitest'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { FilesLibraryTab } from '../files-library-tab'
import { useFileArtifactsStore } from '@/features/stage/stores/file-artifacts-store'
import type { FileArtifact } from '@/services/api/file-artifacts'

const mockActiveConnectionId = vi.fn<() => string | null>(() => 'conn_x')

vi.mock('@/services/api/file-artifacts', async () => {
  const actual = await vi.importActual<typeof import('@/services/api/file-artifacts')>('@/services/api/file-artifacts')
  return {
    ...actual,
    listSessionFiles: vi.fn().mockResolvedValue([]),
    listConnectionFiles: vi.fn().mockResolvedValue([]),
    markCandidate: vi.fn().mockResolvedValue(undefined),
    archiveFile: vi.fn().mockResolvedValue(undefined),
    discardFile: vi.fn().mockResolvedValue(undefined),
  }
})

vi.mock('@/features/connection/store', () => ({
  useConnectionStore: <T,>(selector: (s: { activeConnectionId: string | null }) => T) =>
    selector({ activeConnectionId: mockActiveConnectionId() }),
}))

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    language: 'en-US',
    t: (key: string) => key,
  }),
}))

function makeFile(overrides: Partial<FileArtifact>): FileArtifact {
  return {
    id: 'fa_x',
    scope: 'workspace',
    status: 'archived',
    kind: 'er_diagram',
    sessionId: 'ses_a',
    connectionId: 'conn_x',
    filename: 'orders-er.md',
    physicalPath: '/w/orders-er.md',
    sizeBytes: 8_400,
    mimeType: 'text/markdown',
    title: 'Orders ER',
    summary: 'covers orders / order_items / payments',
    createdAt: '2026-04-29T00:00:00Z',
    updatedAt: '2026-04-29T00:00:00Z',
    archivedAt: '2026-04-29T00:00:00Z',
    metadata: {},
    ...overrides,
  }
}

function seedStore() {
  useFileArtifactsStore.setState({
    bySessionId: {},
    byConnectionId: {
      conn_x: [
        makeFile({ id: 'er_1', kind: 'er_diagram', filename: 'orders-er.md', title: 'Orders ER' }),
        makeFile({ id: 'er_2', kind: 'er_diagram', filename: 'users-er.md', title: 'Users ER' }),
        makeFile({ id: 'rep_1', kind: 'report', filename: 'weekly.md', title: 'Weekly report', summary: 'top customers Q3' }),
        makeFile({ id: 'sql_1', kind: 'sql_script', filename: 'cohort.sql', title: 'Cohort SQL' }),
      ],
    },
    loading: false,
    error: null,
  })
}

describe('FilesLibraryTab', () => {
  beforeEach(() => {
    mockActiveConnectionId.mockReturnValue('conn_x')
  })

  it('renders sections per kind with non-empty groups', () => {
    seedStore()
    render(<FilesLibraryTab />)
    expect(screen.getByText('files.library.section.er_diagram (2)')).toBeInTheDocument()
    expect(screen.getByText('files.library.section.report (1)')).toBeInTheDocument()
    expect(screen.getByText('files.library.section.sql_script (1)')).toBeInTheDocument()
    expect(screen.queryByText(/files\.library\.section\.dataset/)).not.toBeInTheDocument()
  })

  it('search filters by filename / title / summary', () => {
    seedStore()
    render(<FilesLibraryTab />)
    const input = screen.getByPlaceholderText('files.library.search.placeholder')
    fireEvent.change(input, { target: { value: 'cohort' } })
    expect(screen.getByText('cohort.sql')).toBeInTheDocument()
    expect(screen.queryByText('orders-er.md')).not.toBeInTheDocument()
    expect(screen.queryByText('weekly.md')).not.toBeInTheDocument()
  })

  it('search finds files by summary text', () => {
    seedStore()
    render(<FilesLibraryTab />)
    const input = screen.getByPlaceholderText('files.library.search.placeholder')
    fireEvent.change(input, { target: { value: 'top customers' } })
    expect(screen.getByText('weekly.md')).toBeInTheDocument()
    expect(screen.queryByText('cohort.sql')).not.toBeInTheDocument()
  })

  it('shows empty state when no active connection', () => {
    seedStore()
    mockActiveConnectionId.mockReturnValue(null)
    render(<FilesLibraryTab />)
    expect(screen.getByText('files.empty.noConnection')).toBeInTheDocument()
  })

  it('shows empty archived state when connection has zero archived files', () => {
    useFileArtifactsStore.setState({
      bySessionId: {},
      byConnectionId: { conn_x: [] },
      loading: false,
      error: null,
    })
    render(<FilesLibraryTab />)
    expect(screen.getByText('files.empty.noArchived')).toBeInTheDocument()
  })

  it('refetches when active connection id changes', async () => {
    seedStore()
    const fetchForConnection = vi
      .spyOn(useFileArtifactsStore.getState(), 'fetchForConnection')
      .mockResolvedValue()
    const { rerender } = render(<FilesLibraryTab />)
    await waitFor(() => expect(fetchForConnection).toHaveBeenCalledWith('conn_x'))

    mockActiveConnectionId.mockReturnValue('conn_y')
    await act(async () => {
      useFileArtifactsStore.setState({ byConnectionId: {} })
    })
    rerender(<FilesLibraryTab />)
    await waitFor(() => expect(fetchForConnection).toHaveBeenCalledWith('conn_y'))
    fetchForConnection.mockRestore()
  })
})
