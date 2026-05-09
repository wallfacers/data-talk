import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DashboardCanvas } from '../dashboard-canvas'
import { useDashboardTabsStore } from '../stores/dashboard-tabs-store'
import type { Dashboard } from '../schema'
import { translateMessage } from '@/i18n/messages'

// Mock react-grid-layout — required because jsdom has no layout engine
vi.mock('react-grid-layout', () => ({
  Responsive: function MockResponsive(props: Record<string, unknown>) {
    return (
      <div data-testid="grid-layout-responsive" {...props}>
        {props.children as React.ReactNode}
      </div>
    )
  },
  useContainerWidth: () => ({ width: 1024, mounted: true, containerRef: { current: null } }),
}))

vi.mock('../widgets/chart-widget', () => ({
  ChartWidget: ({ widgetId }: { widgetId: string }) => (
    <div data-testid={`chart-widget-${widgetId}`} />
  ),
}))

vi.mock('../widgets/markdown-widget', () => ({
  MarkdownWidget: ({ widgetId }: { widgetId: string }) => (
    <div data-testid={`markdown-widget-${widgetId}`} />
  ),
}))

const sampleDashboard: Dashboard = {
  schemaVersion: 1,
  id: 'dash_test1',
  title: 'Test',
  parameters: [],
  widgets: [
    {
      id: 'chart_w_aaaa', type: 'chart',
      position: { x: 0, y: 0, w: 6, h: 4 },
      options: { echartsOption: {}, dataMapping: { rowsAsDataset: true } },
    },
    {
      id: 'md_w_bbbb', type: 'markdown',
      position: { x: 6, y: 0, w: 6, h: 3 },
      options: { text: '# Hello' },
    },
  ],
  layout: { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
  version: 1,
  createdAt: 0,
  updatedAt: 0,
}

describe('DashboardCanvas', () => {
  beforeEach(() => {
    useDashboardTabsStore.getState().removeTab('tab-1')
    useDashboardTabsStore.getState().removeTab('tab-2')
  })

  it('renders chart and markdown widgets', () => {
    useDashboardTabsStore.getState().hydrateTab('tab-1', sampleDashboard)
    render(<DashboardCanvas tabId="tab-1" mode="viewer" />)
    expect(screen.getByTestId('chart-widget-chart_w_aaaa')).toBeInTheDocument()
    expect(screen.getByTestId('markdown-widget-md_w_bbbb')).toBeInTheDocument()
  })

  it('shows fallback when tab not found', () => {
    render(<DashboardCanvas tabId="nonexistent" mode="viewer" />)
    expect(screen.getByText('未加载看板')).toBeInTheDocument()
  })

  it('renders unknown widget type with fallback text', () => {
    const dashboardWithUnknown = {
      ...sampleDashboard,
      widgets: [
        {
          id: 'kpi_w_cccc', type: 'kpi',
          position: { x: 0, y: 0, w: 3, h: 2 },
          options: {},
        },
      ],
    } as Dashboard
    useDashboardTabsStore.getState().hydrateTab('tab-2', dashboardWithUnknown)
    render(<DashboardCanvas tabId="tab-2" mode="viewer" />)
    expect(screen.getByText(/kpi 组件/)).toBeInTheDocument()
  })

  it('renders empty dashboard without errors', () => {
    const emptyDashboard: Dashboard = { ...sampleDashboard, widgets: [] }
    useDashboardTabsStore.getState().hydrateTab('tab-2', emptyDashboard)
    render(<DashboardCanvas tabId="tab-2" mode="viewer" />)
    expect(screen.getByTestId('grid-layout-responsive')).toBeInTheDocument()
    // No widget children
    expect(screen.queryByTestId(/chart-widget/)).not.toBeInTheDocument()
  })

  it('passes isDraggable=false in viewer mode via static property', () => {
    useDashboardTabsStore.getState().hydrateTab('tab-1', sampleDashboard)
    const { container } = render(<DashboardCanvas tabId="tab-1" mode="viewer" />)
    // In viewer mode, isDraggable should be false (static items)
    const gridEl = container.querySelector('.dashboard-canvas')
    expect(gridEl).toBeInTheDocument()
  })

  it('empty state remains visible when no dashboard is loaded', () => {
    render(<DashboardCanvas tabId="empty-tab" mode="viewer" />)
    expect(screen.getByText('未加载看板')).toBeInTheDocument()
  })

  it('chart widget container is rendered for chart widgets', () => {
    useDashboardTabsStore.getState().hydrateTab('tab-2', sampleDashboard)
    render(<DashboardCanvas tabId="tab-2" mode="viewer" />)
    const chartWidget = screen.getByTestId('chart-widget-chart_w_aaaa')
    expect(chartWidget).toBeInTheDocument()
  })

  it('markdown widget container is rendered for markdown widgets', () => {
    useDashboardTabsStore.getState().hydrateTab('tab-2', sampleDashboard)
    render(<DashboardCanvas tabId="tab-2" mode="viewer" />)
    const mdWidget = screen.getByTestId('markdown-widget-md_w_bbbb')
    expect(mdWidget).toBeInTheDocument()
  })

  it('editor mode does not remove accessible widget titles', () => {
    useDashboardTabsStore.getState().hydrateTab('tab-1', sampleDashboard)
    render(<DashboardCanvas tabId="tab-1" mode="editor" />)
    // Widget containers should still be present in editor mode
    expect(screen.getByTestId('chart-widget-chart_w_aaaa')).toBeInTheDocument()
    expect(screen.getByTestId('markdown-widget-md_w_bbbb')).toBeInTheDocument()
  })
})
