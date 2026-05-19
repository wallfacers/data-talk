import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { StageTab } from '@/stores/stage-store'
import { ReportViewerTab } from '../components/report-viewer-tab'
import { coordinator } from '@/features/stage/persistence/stage-persistence-bootstrap'

vi.mock('@/features/stage/persistence/stage-persistence-bootstrap', () => ({
  coordinator: { ensureHydrated: vi.fn().mockResolvedValue(undefined) },
}))

vi.mock('../api', () => ({
  useReport: () => ({ data: undefined, isLoading: true }),
  useSystemStatus: () => ({ data: undefined }),
  reportDownloadUrl: (id: string, format: string) =>
    `/api/reports/${id}/download/${format}`,
}))

const baseTab: StageTab = {
  tabId: 'report-viewer:rep-1',
  type: 'report_viewer',
  title: 'Q1 Sales Report',
  originSessionId: 'sess-1',
  payload: { reportId: 'rep-1' },
  createdAt: 0,
}

describe('ReportViewerTab', () => {
  beforeEach(() => {
    vi.mocked(coordinator.ensureHydrated).mockClear()
  })

  it('renders report iframe when payload has reportId', () => {
    render(<ReportViewerTab tab={baseTab} />)

    const iframe = screen.getByTitle('report rep-1')
    expect(iframe).toBeInTheDocument()
    expect(iframe.getAttribute('src')).toContain('/api/reports/rep-1/download/html')
    expect(coordinator.ensureHydrated).not.toHaveBeenCalled()
  })

  it('calls ensureHydrated and shows loader when payload is empty', () => {
    render(<ReportViewerTab tab={{ ...baseTab, payload: {} }} />)

    expect(coordinator.ensureHydrated).toHaveBeenCalledWith('report-viewer:rep-1')
    expect(screen.getByRole('status')).toBeInTheDocument()
  })

  it('shows not-found fallback when hydration fails and payload stays empty', async () => {
    vi.mocked(coordinator.ensureHydrated).mockRejectedValueOnce(new Error('not found'))

    render(<ReportViewerTab tab={{ ...baseTab, payload: {} }} />)

    await waitFor(() => {
      expect(screen.getByText('报告不存在或加载失败')).toBeInTheDocument()
    })
  })

  it('stabilizes iframe src across re-renders (useMemo)', () => {
    const { rerender } = render(<ReportViewerTab tab={baseTab} />)

    const srcBefore = screen.getByTitle('report rep-1').getAttribute('src')

    rerender(<ReportViewerTab tab={{ ...baseTab, title: 'Updated Title' }} />)

    const srcAfter = screen.getByTitle('report rep-1').getAttribute('src')
    expect(srcBefore).toBe(srcAfter)
  })
})
