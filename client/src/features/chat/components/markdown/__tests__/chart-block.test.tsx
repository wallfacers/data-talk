import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ChartBlock } from '../chart-block'
import { useOntologyStore } from '@/stores/ontology-store'
import { useSessionStore } from '@/stores/session-store'

vi.mock('../chart-renderer', () => ({
  ChartRenderer: () => <div data-testid="chart-renderer-mock" />,
}))

vi.mock('../chart-expand-modal', () => ({
  ChartExpandModal: () => <div data-testid="chart-expand-modal" />,
}))

const MIN_OPTION = { series: [{ type: 'bar', data: [1, 2, 3] }] }

beforeEach(() => {
  useSessionStore.setState({ activeSessionId: 's1' } as any)
  useOntologyStore.setState({ artifactsBySession: new Map() } as any)
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('ChartBlock', () => {
  it('renders skeleton placeholder when streaming and JSON is incomplete', () => {
    render(<ChartBlock json={'{"series":[{"type":"bar","dat'} streaming={true} messageId="m" blockIndex={0} />)
    expect(screen.getByTestId('chart-skeleton')).toBeInTheDocument()
  })

  it('renders chart in preview state when streaming and JSON is valid', () => {
    render(<ChartBlock json={JSON.stringify(MIN_OPTION)} streaming={true} messageId="m" blockIndex={0} />)
    expect(screen.queryByTestId('chart-skeleton')).not.toBeInTheDocument()
    expect(screen.getByTestId('chart-canvas-host')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /打开到工作台/ })).toBeDisabled()
  })

  it('renders stable chart with enabled toolbar when streaming=false and JSON valid', () => {
    render(<ChartBlock json={JSON.stringify(MIN_OPTION)} streaming={false} messageId="m" blockIndex={0} />)
    expect(screen.getByRole('button', { name: /打开到工作台/ })).toBeEnabled()
  })

  it('renders error state when streaming=false and JSON invalid', () => {
    render(<ChartBlock json={'{"series":[{"type":"bar","dat'} streaming={false} messageId="m" blockIndex={0} />)
    expect(screen.getByTestId('chart-error')).toBeInTheDocument()
  })

  it('preserves last valid option when JSON goes valid to invalid during streaming', () => {
    const { rerender } = render(
      <ChartBlock json={JSON.stringify(MIN_OPTION)} streaming={true} messageId="m" blockIndex={0} />,
    )
    expect(screen.getByTestId('chart-canvas-host')).toBeInTheDocument()

    rerender(<ChartBlock json={'{"series":[{"type":"bar","'} streaming={true} messageId="m" blockIndex={0} />)

    expect(screen.queryByTestId('chart-skeleton')).not.toBeInTheDocument()
    expect(screen.getByTestId('chart-canvas-host')).toBeInTheDocument()
  })

  it('shows 已在工作台 when an ontology chart matches origin fields', () => {
    useOntologyStore.setState({
      artifactsBySession: new Map([
        [
          's1',
          new Map([
            [
              'art-42',
              {
                id: 'art-42',
                version: 1,
                kind: 'chart',
                originMessageId: 'm',
                originPartId: 'p0',
              },
            ],
          ]),
        ],
      ]),
    } as any)

    render(
      <ChartBlock
        json={JSON.stringify(MIN_OPTION)}
        streaming={false}
        messageId="m"
        partId="p0"
        blockIndex={0}
      />,
    )

    expect(screen.getByRole('button', { name: /已在工作台/ })).toBeInTheDocument()
  })
})
