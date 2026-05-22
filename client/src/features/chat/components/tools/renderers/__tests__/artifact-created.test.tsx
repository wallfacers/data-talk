import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ArtifactCreated } from '../artifact-created'
import { useOntologyStore } from '@/stores/ontology-store'
import { useSessionStore } from '@/stores/session-store'

vi.mock('@/features/chat/components/markdown/chart-renderer', () => ({
  ChartRenderer: ({ option }: { option: Record<string, unknown> }) => (
    <div data-testid="chart-renderer-mock" data-option={JSON.stringify(option)} />
  ),
}))

vi.mock('@/stores/stage-store', () => ({
  useStageStore: Object.assign(
    () => ({}),
    { getState: () => ({ openArtifactPreviewTab: vi.fn(), openStage: vi.fn() }) },
  ),
}))

vi.mock('@/stores/ui-settings-store', () => ({
  getCurrentLanguage: () => 'zh-CN',
}))

vi.mock('@/i18n/messages', () => ({
  translateMessage: (_lang: string, key: string, values?: Record<string, unknown>) => {
    const messages: Record<string, string> = {
      'chat.artifactFallbackTitle': '{kind} artifact',
      'chat.artifactKind.table': 'table',
      'chat.artifactKind.chart': 'chart',
    }
    const template = messages[key] ?? key
    return template.replace(/\{(\w+)\}/g, (_, name: string) => String(values?.[name] ?? `{${name}}`))
  },
}))

vi.mock('@/features/actions/registry', () => ({
  getRenderers: () => undefined,
}))

const CHART_OPTION = { series: [{ type: 'bar', data: [1, 2, 3] }] }

const CHART_ARTIFACT = {
  id: 'art-1',
  version: 1,
  kind: 'chart' as const,
  payload: { kind: 'chart', echartsOption: CHART_OPTION },
}

function makePart(overrides: Record<string, unknown> = {}) {
  return {
    id: 'part-1',
    sessionID: 'sess-1',
    messageID: 'msg-1',
    type: 'tool' as const,
    tool: 'datatalk_render_chart',
    state: {
      status: 'completed' as const,
      output: { artifactId: 'art-1', version: 1 },
      ...overrides,
    },
  }
}

const DESCRIPTOR = {
  id: 'datatalk.render_chart',
  executor: 'SERVER',
  description: '',
  inputSchema: {},
  outputSchema: {},
  produces: [],
  sideEffects: [],
  requiresConnection: false,
  timeoutMs: 5000,
}

beforeEach(() => {
  useSessionStore.setState({ activeSessionId: 'sess-1' } as any)
  useOntologyStore.setState({ artifactsBySession: new Map() } as any)
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('ArtifactCreated — compact reference (no inline chart canvas)', () => {
  it('does NOT render a chart canvas even when the chart artifact is in the store', () => {
    // ChartBlock (markdown fenced block) is the single in-chat chart surface.
    // This tool card is a compact reference only — it must never paint a second
    // canvas, regardless of whether the artifact / echartsOption is available.
    useOntologyStore.setState({
      artifactsBySession: new Map([
        ['sess-1', new Map([['art-1', CHART_ARTIFACT]])],
      ]),
    } as any)

    render(<ArtifactCreated part={makePart() as any} descriptor={DESCRIPTOR as any} />)

    expect(screen.queryByTestId('chart-renderer-mock')).not.toBeInTheDocument()
    expect(screen.queryByTestId('artifact-chart-canvas-host')).not.toBeInTheDocument()
  })

  it('renders the compact tool row with the title', () => {
    render(<ArtifactCreated part={makePart() as any} descriptor={DESCRIPTOR as any} />)

    // BasicTool renders the tool title (TextShimmer duplicates the text node, so use getAllByText)
    expect(screen.getAllByText(/chart artifact/).length).toBeGreaterThan(0)
  })

  it('renders the "view in stage" eye button', () => {
    render(<ArtifactCreated part={makePart() as any} descriptor={DESCRIPTOR as any} />)

    // aria-label maps through translateMessage; unmapped key falls back to the key string.
    expect(screen.getByRole('button', { name: 'chat.openStage' })).toBeInTheDocument()
  })
})
