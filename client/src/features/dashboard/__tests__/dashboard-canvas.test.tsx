import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DashboardCanvas } from '../dashboard-canvas'
import { useDashboardTabsStore } from '../stores/dashboard-tabs-store'
import type { Dashboard } from '../schema'

// Mock react-grid-layout to avoid DOM measurement issues in jsdom
vi.mock('react-grid-layout', () => ({
  WidthProvider: (_Comp: React.ComponentType<Record<string, unknown>>) =>
    function MockWidthProvider(props: Record<string, unknown>) {
      return (
        <div data-testid="grid-layout-mock" {...props}>
          {props.children as React.ReactNode}
        </div>
      )
    },
  Responsive: function MockResponsive(props: Record<string, unknown>) {
    return (
      <div data-testid="grid-layout-responsive" {...props}>
        {props.children as React.ReactNode}
      </div>
    )
  },
}))

// Mock widget components
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
  it('renders without crashing', () => {
    useDashboardTabsStore.getState().hydrateTab('tab-1', sampleDashboard)
    render(<DashboardCanvas tabId="tab-1" mode="viewer" />)
    expect(screen.getByTestId('grid-layout-mock')).toBeInTheDocument()
  })

  it('renders chart and markdown widgets', () => {
    useDashboardTabsStore.getState().hydrateTab('tab-1', sampleDashboard)
    render(<DashboardCanvas tabId="tab-1" mode="viewer" />)
    expect(screen.getByTestId('chart-widget-chart_w_aaaa')).toBeInTheDocument()
    expect(screen.getByTestId('markdown-widget-md_w_bbbb')).toBeInTheDocument()
  })
})
