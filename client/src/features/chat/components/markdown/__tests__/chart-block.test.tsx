import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { ChartBlock } from '../chart-block'
import { useOntologyStore } from '@/stores/ontology-store'
import { useSessionStore } from '@/stores/session-store'
import { promoteChartToStage } from '@/services/artifacts/promote-chart'
import { showErrorToast } from '@/services/http-error'

vi.mock('@/services/artifacts/promote-chart', () => ({
  promoteChartToStage: vi.fn(),
}))

vi.mock('@/services/http-error', () => ({
  normalizeError: vi.fn((error: unknown) => ({
    message: error instanceof Error ? error.message : String(error),
    type: 'unknown',
  })),
  showErrorToast: vi.fn(),
}))

vi.mock('../chart-renderer', () => ({
  ChartRenderer: ({ option }: { option: Record<string, unknown> }) => {
    if (option.__throwRenderer) throw new Error('unsupported chart renderer')
    return <div data-testid="chart-renderer-mock" />
  },
}))

vi.mock('../chart-expand-modal', () => ({
  ChartExpandModal: () => <div data-testid="chart-expand-modal" />,
}))

const MIN_OPTION = { series: [{ type: 'bar', data: [1, 2, 3] }] }

beforeEach(() => {
  useSessionStore.setState({ activeSessionId: 's1' } as any)
  useOntologyStore.setState({ artifactsBySession: new Map() } as any)
  vi.mocked(promoteChartToStage).mockResolvedValue({ artifactId: 'art-created', version: 1 })
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

  it('keeps the chart block shrinkable inside narrow split panes', () => {
    render(<ChartBlock json={JSON.stringify(MIN_OPTION)} streaming={false} messageId="m" blockIndex={0} />)

    expect(screen.getByTestId('chart-canvas-host')).toHaveClass('w-full', 'min-w-0', 'max-w-full')
    expect(screen.getByTestId('chart-canvas-host').parentElement).toHaveClass('w-full', 'min-w-0', 'max-w-full')
  })

  it('renders error state when streaming=false and JSON invalid', () => {
    render(<ChartBlock json={'{"series":[{"type":"bar","dat'} streaming={false} messageId="m" blockIndex={0} />)
    expect(screen.getByTestId('chart-error')).toBeInTheDocument()
  })

  it('shows a friendly empty-body message instead of a raw JSON.parse error when the fence body is empty', () => {
    render(<ChartBlock json={''} streaming={false} messageId="m" blockIndex={0} />)

    const error = screen.getByTestId('chart-error')
    expect(error).toHaveTextContent('图表内容为空')
    expect(error).not.toHaveTextContent('JSON')
    // No raw <pre> dump when there is nothing to show.
    expect(error.querySelector('pre')).toBeNull()
  })

  it('renders skeleton (not empty error) when the body is still empty during streaming', () => {
    render(<ChartBlock json={'   '} streaming={true} messageId="m" blockIndex={0} />)
    expect(screen.getByTestId('chart-skeleton')).toBeInTheDocument()
    expect(screen.queryByTestId('chart-error')).not.toBeInTheDocument()
  })

  it('renders error state when the chart renderer throws', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})

    expect(() => {
      render(
        <ChartBlock
          json={JSON.stringify({ ...MIN_OPTION, __throwRenderer: true })}
          streaming={false}
          messageId="m"
          blockIndex={0}
        />,
      )
    }).not.toThrow()

    expect(screen.getByTestId('chart-error')).toHaveTextContent('unsupported chart renderer')
    consoleError.mockRestore()
  })

  it('rejects chart JSON larger than 256 KB before rendering', () => {
    const json = JSON.stringify({ series: [{ type: 'bar', data: ['x'.repeat(256 * 1024)] }] })

    render(<ChartBlock json={json} streaming={false} messageId="m" blockIndex={0} />)

    expect(screen.getByTestId('chart-error')).toHaveTextContent('256 KB')
    expect(screen.queryByTestId('chart-renderer-mock')).not.toBeInTheDocument()
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

  it('BUG-0064: skips ChartRenderer + hides promote button when a matching chart artifact already exists', () => {
    // When the LLM emits both `datatalk_render_chart` (which creates an
    // ontology chart artifact) AND a markdown chart fenced block in the same
    // reply, the artifact-created tool card already renders the chart above
    // and owns the "open to workbench" affordance. ChartBlock must dedup the
    // canvas AND hide its own promote button to avoid two competing entry
    // points.
    useOntologyStore.setState({
      artifactsBySession: new Map([
        [
          's1',
          new Map([
            [
              'art-dedup',
              {
                id: 'art-dedup',
                version: 1,
                kind: 'chart',
                producedBy: 'call_render_chart_01',
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

    expect(screen.queryByTestId('chart-canvas-host')).not.toBeInTheDocument()
    expect(screen.queryByTestId('chart-renderer-mock')).not.toBeInTheDocument()
    const hint = screen.getByTestId('chart-deduped-hint')
    expect(hint).toHaveAttribute('data-dedup-skipped', 'true')
    expect(hint).toHaveTextContent(/已在上方图表产物中展示/)
    // Promote / "open to workbench" must be owned by ArtifactCreated above; ChartBlock hides it on dedup.
    expect(screen.queryByRole('button', { name: /打开到工作台/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /已在工作台/ })).not.toBeInTheDocument()
    // Expand / copy stay available on ChartBlock as secondary affordances.
    expect(screen.getByRole('button', { name: /放大图表/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /复制 JSON/ })).toBeInTheDocument()
  })

  it('BUG-0089: does NOT collapse to the dedup hint when the matching artifact was promoted via REST (no tool card above)', () => {
    // Clicking "open in workbench" on a standalone ChartBlock creates a chart
    // artifact via the REST endpoint (producedBy = "rest:chart"), carrying this
    // block's own originMessageId/originPartId. Before the fix, findMatchedArtifact
    // matched it and the block collapsed to "已在上方图表产物中展示" — but there is
    // NO ArtifactCreated card above, so the chart simply vanished. The REST
    // artifact must only flip the button to "already in workbench".
    useOntologyStore.setState({
      artifactsBySession: new Map([
        [
          's1',
          new Map([
            [
              'art-self-promoted',
              {
                id: 'art-self-promoted',
                version: 1,
                kind: 'chart',
                producedBy: 'rest:chart',
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

    // Chart stays visible; no collapse hint.
    expect(screen.getByTestId('chart-canvas-host')).toBeInTheDocument()
    expect(screen.queryByTestId('chart-deduped-hint')).not.toBeInTheDocument()
    // Promote button stays, flipped to "already in workbench" and re-focuses on click.
    const openButton = screen.getByRole('button', { name: /已在工作台/ })
    expect(openButton).toBeInTheDocument()
    fireEvent.click(openButton)
    expect(promoteChartToStage).not.toHaveBeenCalled()
  })

  it('shows an error toast and resets the promote button when promotion fails', async () => {
    vi.mocked(promoteChartToStage).mockRejectedValueOnce(new Error('promotion failed'))
    render(<ChartBlock json={JSON.stringify(MIN_OPTION)} streaming={false} messageId="m" blockIndex={0} />)

    fireEvent.click(screen.getByRole('button', { name: /打开到工作台/ }))

    await waitFor(() => {
      expect(showErrorToast).toHaveBeenCalledWith(expect.objectContaining({ message: 'promotion failed' }))
    })
    expect(screen.getByRole('button', { name: /打开到工作台/ })).toBeEnabled()
  })
})
