import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { FilesTab } from '../files-tab'
import { useFileArtifactsStore } from '@/features/stage/stores/file-artifacts-store'
import type { FileArtifact } from '@/services/api/file-artifacts'

const mockActiveSessionId = vi.fn<() => string | null>(() => 'ses_a')

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

vi.mock('@/stores/session-store', () => ({
  useSessionStore: <T,>(selector: (s: { activeSessionId: string | null }) => T) =>
    selector({ activeSessionId: mockActiveSessionId() }),
}))

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    language: 'en-US',
    t: (key: string) => key,
  }),
}))

const tempFile: FileArtifact = {
  id: 'fa_temp',
  scope: 'session',
  status: 'temporary',
  kind: 'dataset',
  sessionId: 'ses_a',
  connectionId: null,
  filename: 'sample.csv',
  physicalPath: '/x/sample.csv',
  sizeBytes: 2_100_000,
  mimeType: 'text/csv',
  title: null,
  summary: null,
  createdAt: '2026-04-29T14:23:00Z',
  updatedAt: '2026-04-29T14:23:00Z',
  archivedAt: null,
  metadata: {},
}

const candidateFile: FileArtifact = {
  ...tempFile,
  id: 'fa_cand',
  status: 'candidate',
  kind: 'er_diagram',
  filename: 'orders-er.md',
  title: 'Orders ER',
  summary: 'covers orders / order_items / payments',
  sizeBytes: 8_400,
}

describe('FilesTab', () => {
  beforeEach(() => {
    mockActiveSessionId.mockReturnValue('ses_a')
    useFileArtifactsStore.setState({
      bySessionId: { ses_a: [tempFile, candidateFile] },
      byConnectionId: {},
      loading: false,
      error: null,
    })
  })

  it('renders TEMPORARY and ARCHIVE CANDIDATES groups', async () => {
    render(<FilesTab />)
    expect(screen.getByText('files.section.temporary')).toBeInTheDocument()
    expect(screen.getByText('files.section.candidates')).toBeInTheDocument()
    expect(screen.getByText('sample.csv')).toBeInTheDocument()
    expect(screen.getByText('orders-er.md')).toBeInTheDocument()
  })

  it('shows empty session state when no active session', () => {
    mockActiveSessionId.mockReturnValue(null)
    render(<FilesTab />)
    expect(screen.getByText('files.empty.noSession')).toBeInTheDocument()
  })

  it('shows empty file state when active session has zero files', () => {
    useFileArtifactsStore.setState({ bySessionId: { ses_a: [] } })
    render(<FilesTab />)
    expect(screen.getByText('files.empty.noFiles')).toBeInTheDocument()
  })

  it('exposes aria-label on icon-only action buttons', () => {
    render(<FilesTab />)
    expect(screen.getAllByRole('button', { name: 'files.action.open' })[0]).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'files.action.markAsCandidate' })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'files.action.discard' })[0]).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'files.action.archive' })).toBeInTheDocument()
  })

  it('clicking [Mark as candidate] calls store.promote', async () => {
    const promote = vi.spyOn(useFileArtifactsStore.getState(), 'promote').mockResolvedValue()
    render(<FilesTab />)
    fireEvent.click(screen.getByRole('button', { name: 'files.action.markAsCandidate' }))
    await waitFor(() => expect(promote).toHaveBeenCalledWith('fa_temp'))
    promote.mockRestore()
  })

  it('clicking [Archive] on a candidate calls store.archive(sessionId, fileId)', async () => {
    const archive = vi.spyOn(useFileArtifactsStore.getState(), 'archive').mockResolvedValue()
    render(<FilesTab />)
    fireEvent.click(screen.getByRole('button', { name: 'files.action.archive' }))
    await waitFor(() => expect(archive).toHaveBeenCalledWith('ses_a', 'fa_cand'))
    archive.mockRestore()
  })

  it('refetches when active session id changes', async () => {
    const fetchForSession = vi.spyOn(useFileArtifactsStore.getState(), 'fetchForSession').mockResolvedValue()
    const { rerender } = render(<FilesTab />)
    await waitFor(() => expect(fetchForSession).toHaveBeenCalledWith('ses_a'))

    mockActiveSessionId.mockReturnValue('ses_b')
    useFileArtifactsStore.setState({ bySessionId: {} })
    rerender(<FilesTab />)
    await waitFor(() => expect(fetchForSession).toHaveBeenCalledWith('ses_b'))
    fetchForSession.mockRestore()
  })
})
