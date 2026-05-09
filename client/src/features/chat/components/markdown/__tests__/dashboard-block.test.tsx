import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { DashboardBlock } from '../dashboard-block'

vi.mock('@/stores/stage-store', () => ({
  useStageStore: {
    getState: () => ({
      openTab: vi.fn(),
      openStage: vi.fn(),
    }),
  },
}))

const hydrateTabMock = vi.fn()

vi.mock('@/features/dashboard/stores/dashboard-tabs-store', () => ({
  useDashboardTabsStore: {
    getState: () => ({
      hydrateTab: hydrateTabMock,
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

const dashboardWithHyphenatedId = JSON.stringify({
  schemaVersion: 1,
  id: 'dash_a11fe047-8a8e-449b-876b-3d4d8ae5db80',
  title: 'Hyphenated ID',
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
    expect(screen.getByText('打开到工作台')).toBeInTheDocument()
  })

  it('sanitizes hyphenated dashboard ID on promote', async () => {
    render(<DashboardBlock json={dashboardWithHyphenatedId} streaming={false} />)
    fireEvent.click(screen.getByText('打开到工作台'))

    expect(hydrateTabMock).toHaveBeenCalledTimes(1)
    const promotedPayload = hydrateTabMock.mock.calls[0][1]
    expect(promotedPayload.id).toBe('dash_a11fe0478a8e449b876b3d4d8ae5db80')
  })
})
