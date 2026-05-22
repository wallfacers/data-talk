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

  it('single surface (BUG-0064/0089): always renders its own canvas; a matching artifact only flips the button to "already in workbench"', () => {
    // ChartBlock is now the single in-chat chart surface — datatalk_render_chart
    // no longer paints a canvas (ArtifactCreated is a compact reference). So a
    // matching chart artifact (e.g. created when this block was promoted) must
    // NOT collapse the chart; it only flips the promote button to
    // "已在工作台" and re-focuses the existing tab instead of creating a duplicate.
    useOntologyStore.setState({
      artifactsBySession: new Map([
        [
          's1',
          new Map([
            [
              'art-existing',
              {
                id: 'art-existing',
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

    // Chart stays visible; no collapse hint exists anymore.
    expect(screen.getByTestId('chart-canvas-host')).toBeInTheDocument()
    expect(screen.queryByTestId('chart-deduped-hint')).not.toBeInTheDocument()
    // Promote button stays, flipped to "already in workbench"; click re-focuses (no duplicate promote).
    const openButton = screen.getByRole('button', { name: /已在工作台/ })
    expect(openButton).toBeInTheDocument()
    fireEvent.click(openButton)
    expect(promoteChartToStage).not.toHaveBeenCalled()
    // Expand / copy stay available.
    expect(screen.getByRole('button', { name: /放大图表/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /复制 JSON/ })).toBeInTheDocument()
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
