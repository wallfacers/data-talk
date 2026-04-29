import { render, screen } from '@testing-library/react'
import { beforeAll, describe, expect, it, vi } from 'vitest'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import { ErInspectorTab } from '../er-inspector-tab'

vi.mock('@/features/stage/persistence/stage-persistence-bootstrap', () => ({
  coordinator: { ensureHydrated: vi.fn().mockResolvedValue(undefined) },
}))

const samplePayload = {
  kind: 'er_inspector' as const,
  connectionId: 'c1',
  selection: ['users'],
  neighborDepth: 1 as const,
  layout: 'dagre-LR' as const,
  tablesSnapshot: [
    {
      name: 'users',
      columns: [{ name: 'id', type: 'BIGINT', isPK: true, isFK: false, nullable: false }],
      fkOut: [],
    },
  ],
  positions: { users: { x: 0, y: 0 } },
  collapsed: [],
  virtualRelations: [],
  notes: {},
  viewport: { x: 0, y: 0, zoom: 1 },
}

describe('<ErInspectorTab>', () => {
  beforeAll(() => {
    global.ResizeObserver = class ResizeObserver {
      observe() {}
      unobserve() {}
      disconnect() {}
    }
  })

  it('renders the canvas after payload is hydrated', async () => {
    useErTabsStore.setState({ inspectors: new Map([['t-1', samplePayload]]), designers: new Map() })

    render(<ErInspectorTab tabId="t-1" />)

    expect(await screen.findByText('users')).toBeInTheDocument()
  })

  it('renders loading state when payload is missing', () => {
    useErTabsStore.setState({ inspectors: new Map(), designers: new Map() })

    render(<ErInspectorTab tabId="missing" />)

    expect(screen.getByText(/加载 ER/)).toBeInTheDocument()
  })
})
