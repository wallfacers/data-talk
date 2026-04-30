import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ArtifactRefBlock } from '../artifact-ref-block'
import { useOntologyStore } from '@/stores/ontology-store'
import { useSessionStore } from '@/stores/session-store'

vi.mock('../chart-renderer', () => ({
  ChartRenderer: ({ option }: { option: Record<string, unknown> }) => (
    <div data-testid="chart-renderer-mock" data-option={JSON.stringify(option)} />
  ),
}))

const CHART_OPTION = { series: [{ type: 'line', data: [3, 2, 1] }] }

describe('ArtifactRefBlock', () => {
  beforeEach(() => {
    useSessionStore.setState({ activeSessionId: 's1' } as any)
    useOntologyStore.setState({
      artifactsBySession: new Map([
        [
          's1',
          new Map([
            [
              'art-1',
              {
                id: 'art-1',
                version: 1,
                kind: 'chart',
                payload: { echartsOption: CHART_OPTION },
              },
            ],
          ]),
        ],
      ]),
    } as any)
  })

  it('renders the referenced chart inside a shrinkable full-width wrapper', () => {
    render(<ArtifactRefBlock artifactId="art-1" />)

    expect(screen.getByTestId('chart-renderer-mock').parentElement).toHaveClass('w-full', 'min-w-0', 'max-w-full')
  })
})
