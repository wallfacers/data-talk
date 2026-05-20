import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { DashboardBlock } from '../dashboard-block'

const openTabMock = vi.fn()
const openStageMock = vi.fn()

vi.mock('@/stores/stage-store', () => ({
  useStageStore: {
    getState: () => ({
      openTab: openTabMock,
      openStage: openStageMock,
    }),
  },
}))

vi.mock('@/features/dashboard/services/dashboard-api', () => ({
  promoteDashboard: vi.fn().mockImplementation(async (dashboard) => ({
    id: dashboard.id,
    version: dashboard.version ?? 1,
  })),
}))

const validDashboardJson = JSON.stringify({
  schemaVersion: 3,
  id: 'dash_test',
  title: 'Test Dashboard',
  theme: 'industry-default',
  renderer: 'bezel',
  parameters: [],
  widgets: [],
  layout: { engine: 'free', template: 'grid-equal' },
  version: 1,
  createdAt: 0,
  updatedAt: 0,
})

const dashboardWithHyphenatedId = JSON.stringify({
  schemaVersion: 3,
  id: 'dash_a11fe047-8a8e-449b-876b-3d4d8ae5db80',
  title: 'Hyphenated ID',
  theme: 'industry-default',
  renderer: 'bezel',
  parameters: [],
  widgets: [],
  layout: { engine: 'free', template: 'grid-equal' },
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

  it('shows promote button for valid v3 dashboard', () => {
    render(<DashboardBlock json={validDashboardJson} streaming={false} />)
    expect(screen.getByText('打开到工作台')).toBeInTheDocument()
  })

  it('sanitizes hyphenated dashboard ID on promote', async () => {
    render(<DashboardBlock json={dashboardWithHyphenatedId} streaming={false} />)
    fireEvent.click(screen.getByText('打开到工作台'))

    await vi.waitFor(() => {
      expect(openTabMock).toHaveBeenCalledTimes(1)
    })
    const promotedPayload = openTabMock.mock.calls[0][0].payload
    expect(promotedPayload.id).toBe('dash_a11fe0478a8e449b876b3d4d8ae5db80')
  })

  it('renders error for legacy v1 schema', () => {
    const legacyV1 = JSON.stringify({
      schemaVersion: 1,
      id: 'dash_legacy',
      title: 'Legacy V1',
      parameters: [],
      widgets: [],
      layout: { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
      version: 1,
      createdAt: 0,
      updatedAt: 0,
    })
    render(<DashboardBlock json={legacyV1} streaming={false} />)
    expect(screen.getByTestId('dashboard-error')).toBeInTheDocument()
  })

  describe('error humanization', () => {
    function makeDashboard(overrides: Record<string, unknown>): string {
      return JSON.stringify({
        schemaVersion: 3,
        id: 'dash_test',
        title: 'Test Dashboard',
        theme: 'industry-default',
        renderer: 'bezel',
        parameters: [],
        widgets: [],
        layout: { engine: 'free', template: 'grid-equal' },
        version: 1,
        createdAt: 0,
        updatedAt: 0,
        ...overrides,
      })
    }

    function expandErrorCard() {
      const trigger = screen.getByTestId('dashboard-error').querySelector('[data-component="tool-trigger"]')
      expect(trigger).not.toBeNull()
      fireEvent.click(trigger as HTMLElement)
    }

    it('shows widget id suffix-too-short message in zh-CN', () => {
      const json = makeDashboard({
        widgets: [
          {
            id: 'kpi_w_gmv',
            type: 'kpi',
            slot: 'main',
            title: 'GMV',
            patternId: 'kpi.label',
            options: {},
          },
        ],
      })
      render(<DashboardBlock json={json} streaming={false} />)
      expandErrorCard()

      const items = screen.getAllByTestId('dashboard-error-issue')
      expect(items.length).toBeGreaterThan(0)
      const widgetIdItem = items.find((el) => el.textContent?.includes('widgets[0].id'))
      expect(widgetIdItem).toBeTruthy()
      expect(widgetIdItem!.textContent).toContain('kpi_w_gmv')
      expect(widgetIdItem!.textContent).toMatch(/3|≥4/)
    })

    it('shows dashboard id format error in zh-CN', () => {
      const json = makeDashboard({ id: 'my-dashboard' })
      render(<DashboardBlock json={json} streaming={false} />)
      expandErrorCard()

      const items = screen.getAllByTestId('dashboard-error-issue')
      const idItem = items.find((el) => el.textContent?.startsWith('id') || el.textContent?.includes('dashboard.id'))
      expect(idItem).toBeTruthy()
      expect(idItem!.textContent).toContain('dash_')
    })

    it('lists all issues when multiple violations co-occur', () => {
      const json = makeDashboard({
        theme: 'modern',
        widgets: [
          {
            id: 'kpi_w_gmv',
            type: 'kpi',
            slot: 'main',
            title: 'GMV',
            patternId: 'kpi.label',
            options: {},
          },
        ],
      })
      render(<DashboardBlock json={json} streaming={false} />)
      expandErrorCard()

      const items = screen.getAllByTestId('dashboard-error-issue')
      expect(items.length).toBeGreaterThanOrEqual(2)
      const paths = items.map((el) => el.querySelector('span')?.textContent).filter(Boolean)
      expect(paths).toEqual(expect.arrayContaining(['widgets[0].id', 'theme']))
    })

    it('falls back to raw zod message for unknown issue', () => {
      const json = makeDashboard({ version: '1' })
      render(<DashboardBlock json={json} streaming={false} />)
      expandErrorCard()

      const items = screen.getAllByTestId('dashboard-error-issue')
      const versionItem = items.find((el) => el.textContent?.startsWith('version'))
      expect(versionItem).toBeTruthy()
      expect(versionItem!.textContent).toMatch(/expected number|received string/i)
    })

    it('exposes raw JSON in expandable region without disabling text selection', () => {
      const buggy = makeDashboard({
        widgets: [
          {
            id: 'kpi_w_gmv',
            type: 'kpi',
            slot: 'main',
            title: 'GMV',
            patternId: 'kpi.label',
            options: {},
          },
        ],
      })
      render(<DashboardBlock json={buggy} streaming={false} />)
      expandErrorCard()

      const rawToggle = screen.getByText('原始 JSON')
      fireEvent.click(rawToggle)

      const pre = screen.getByTestId('dashboard-error-raw-json')
      expect(pre).toBeInTheDocument()
      expect(pre.textContent).toContain('kpi_w_gmv')
      expect(pre.className).toContain('select-text')
      expect(pre.className).not.toContain('select-none')
    })

    it('renders summary "N 项错误" in error card header', () => {
      const json = makeDashboard({
        theme: 'modern',
        widgets: [
          {
            id: 'kpi_w_gmv',
            type: 'kpi',
            slot: 'main',
            title: 'GMV',
            patternId: 'kpi.label',
            options: {},
          },
        ],
      })
      render(<DashboardBlock json={json} streaming={false} />)
      const summary = screen.getByTestId('dashboard-error-summary')
      expect(summary.textContent).toMatch(/\d+ 项错误/)
    })
  })
})
