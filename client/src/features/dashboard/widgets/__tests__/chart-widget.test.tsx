import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ChartWidget } from '../chart-widget'

vi.mock('@/features/chat/components/markdown/chart-renderer', () => ({
  ChartRenderer: ({ option }: { option: Record<string, unknown> }) => (
    <div data-testid="mock-chart-renderer">{JSON.stringify(option)}</div>
  ),
}))

describe('ChartWidget', () => {
  const baseProps = {
    widgetId: 'chart_w_aaaa',
    title: 'Sales Chart',
    option: { xAxis: { type: 'category' } } as Record<string, unknown>,
    height: 300,
  }

  it('renders shell + ChartRenderer container', () => {
    render(<ChartWidget {...baseProps} />)
    expect(screen.getByTestId('chart-canvas-host')).toBeInTheDocument()
    expect(screen.getByTestId('mock-chart-renderer')).toBeInTheDocument()
    expect(screen.getByText('Sales Chart')).toBeInTheDocument()
  })

  it('shows skeleton when loading=true', () => {
    render(<ChartWidget {...baseProps} loading={true} />)
    expect(screen.getByTestId('chart-skeleton')).toBeInTheDocument()
  })

  it('shows error card when error provided', () => {
    render(<ChartWidget {...baseProps} error="Query failed" />)
    expect(screen.getByText('Query failed')).toBeInTheDocument()
  })
})
