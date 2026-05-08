import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DashboardBlock } from '../dashboard-block'

vi.mock('@/stores/stage-store', () => ({
  useStageStore: {
    getState: () => ({
      openTab: vi.fn(),
      openStage: vi.fn(),
    }),
  },
}))

const validDashboardJson = JSON.stringify({
  schemaVersion: 1,
  id: 'dash_test',
  title: 'Test Dashboard',
  parameters: [],
  widgets: [],
  layout: { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
  version: 1,
  createdAt: 0,
  updatedAt: 0,
})

describe('DashboardBlock', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders skeleton when streaming', () => {
    render(<DashboardBlock json="incomplete..." streaming={true} />)
    expect(screen.getByTestId('dashboard-skeleton')).toBeInTheDocument()
  })

  it('renders preview when complete and valid', () => {
    render(<DashboardBlock json={validDashboardJson} streaming={false} />)
    expect(screen.getByTestId('dashboard-preview')).toBeInTheDocument()
  })

  it('renders error card when invalid JSON', () => {
    render(<DashboardBlock json="{not valid json" streaming={false} />)
    expect(screen.getByTestId('dashboard-error')).toBeInTheDocument()
  })

  it('shows promote button when stable', () => {
    render(<DashboardBlock json={validDashboardJson} streaming={false} />)
    expect(screen.getByText('Open to workbench')).toBeInTheDocument()
  })
})
