import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { DatatalkArchiveArtifact } from '../datatalk-archive-artifact'
import { useFileArtifactsStore } from '@/features/stage/stores/file-artifacts-store'
import type { ToolPart } from '@/services/channel/types'
import type { ActionDescriptor } from '@/features/actions/registry'
import type { FileArtifact } from '@/services/api/file-artifacts'

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

const mockOpenStage = vi.fn()
const mockOpenTab = vi.fn()

vi.mock('@/stores/stage-store', () => ({
  useStageStore: {
    getState: () => ({
      openStage: mockOpenStage,
      openTab: mockOpenTab,
      tabs: [],
    }),
  },
}))

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    language: 'en-US',
    t: (key: string) => key,
  }),
}))

const baseArtifact: FileArtifact = {
  id: 'fa_1',
  scope: 'session',
  status: 'candidate',
  kind: 'er_diagram',
  sessionId: 'ses_a',
  connectionId: null,
  filename: 'orders-er.md',
  physicalPath: '/x/orders-er.md',
  sizeBytes: 8_400,
  mimeType: 'text/markdown',
  title: 'Orders ER',
  summary: 'covers orders / order_items / payments',
  createdAt: '2026-04-29T00:00:00Z',
  updatedAt: '2026-04-29T00:00:00Z',
  archivedAt: null,
  metadata: {},
}

const descriptor: ActionDescriptor = {
  id: 'datatalk_archive_artifact',
  executor: 'OPENCODE',
  description: 'Archive artifact',
  inputSchema: {},
  outputSchema: {},
  produces: [],
  sideEffects: [],
  requiresConnection: false,
  timeoutMs: 30_000,
  category: 'artifact',
}

function makePart(state: Partial<ToolPart['state']>): ToolPart {
  return {
    id: 'part_1',
    sessionID: 'ses_a',
    messageID: 'msg_1',
    type: 'tool',
    tool: 'datatalk_archive_artifact',
    state: {
      status: 'completed',
      input: { path: 'orders-er.md', kind: 'er_diagram' },
      output: { fileArtifactId: 'fa_1' },
      ...state,
    } as ToolPart['state'],
  }
}

describe('DatatalkArchiveArtifact', () => {
  beforeEach(() => {
    mockOpenStage.mockReset()
    mockOpenTab.mockReset()
    useFileArtifactsStore.setState({
      bySessionId: { ses_a: [baseArtifact] },
      byConnectionId: {},
      loading: false,
      error: null,
    })
  })

  it('shows Candidate badge when store has the file as candidate', () => {
    render(<DatatalkArchiveArtifact part={makePart({})} descriptor={descriptor} />)
    expect(screen.getByText('orders-er.md')).toBeInTheDocument()
    expect(screen.getByLabelText('files.status.candidate')).toBeInTheDocument()
  })

  it('updates badge after applyDtEvent file_artifact.archived', async () => {
    render(<DatatalkArchiveArtifact part={makePart({})} descriptor={descriptor} />)
    expect(screen.getByLabelText('files.status.candidate')).toBeInTheDocument()
    useFileArtifactsStore.getState().applyDtEvent({
      type: 'file_artifact.archived',
      data: {
        fileArtifactId: 'fa_1',
        sessionId: 'ses_a',
        connectionId: 'conn_x',
        filename: 'orders-er.md',
        physicalPath: '/w/orders-er.md',
      },
    })
    await waitFor(() => {
      expect(screen.getByLabelText('files.status.archived')).toBeInTheDocument()
    })
  })

  it('archive button is hidden when status is already archived', () => {
    useFileArtifactsStore.setState({
      bySessionId: {},
      byConnectionId: {
        conn_x: [{ ...baseArtifact, status: 'archived', scope: 'workspace', connectionId: 'conn_x' }],
      },
    })
    render(<DatatalkArchiveArtifact part={makePart({})} descriptor={descriptor} />)
    expect(screen.queryByRole('button', { name: 'files.chatCard.archiveNow' })).not.toBeInTheDocument()
  })

  it('clicking [View in Stage] opens the FILES tab', async () => {
    render(<DatatalkArchiveArtifact part={makePart({})} descriptor={descriptor} />)
    fireEvent.click(screen.getByRole('button', { name: 'files.chatCard.viewInStage' }))
    expect(mockOpenStage).toHaveBeenCalled()
    expect(mockOpenTab).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'files' }),
    )
  })

  it('clicking [Archive now] calls store.archive(sessionId, fid)', async () => {
    const archive = vi.spyOn(useFileArtifactsStore.getState(), 'archive').mockResolvedValue()
    render(<DatatalkArchiveArtifact part={makePart({})} descriptor={descriptor} />)
    fireEvent.click(screen.getByRole('button', { name: 'files.chatCard.archiveNow' }))
    await waitFor(() => expect(archive).toHaveBeenCalledWith('ses_a', 'fa_1'))
    archive.mockRestore()
  })

  it('clicking [Discard] calls store.discard(fid)', async () => {
    const discard = vi.spyOn(useFileArtifactsStore.getState(), 'discard').mockResolvedValue()
    render(<DatatalkArchiveArtifact part={makePart({})} descriptor={descriptor} />)
    fireEvent.click(screen.getByRole('button', { name: 'files.action.discard' }))
    await waitFor(() => expect(discard).toHaveBeenCalledWith('fa_1'))
    discard.mockRestore()
  })
})
